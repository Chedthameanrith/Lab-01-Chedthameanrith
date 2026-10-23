package edu.itc.se.lab01.challenge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

public final class WordCount {
    private static final int QUEUE_CAPACITY = 16;
    private static final Path POISON = Path.of("__word_count_poison__");

    private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final AtomicInteger totalFiles = new AtomicInteger();
    private final LongAdder wordsDone = new LongAdder();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean resultPrinted = new AtomicBoolean();
    private final AtomicBoolean hasErrors = new AtomicBoolean();
    private final AtomicLong shutdownAtNanos = new AtomicLong();
    private final Path dir;
    private final int topN;
    private final int workerCount;
    private final long timeoutSeconds;
    private final boolean virtual;
    private final boolean showProgress;
    private final Thread scanner;
    private final List<Thread> workers = new ArrayList<>();
    private Thread reporter;
    private Thread deadlineThread;

    public WordCount(Path dir, int topN, int workerCount, long timeoutSeconds,
                     boolean virtual) {
        this(dir, topN, workerCount, timeoutSeconds, virtual, true);
    }

    WordCount(Path dir, int topN, int workerCount, long timeoutSeconds,
              boolean virtual, boolean showProgress) {
        if (topN < 1 || workerCount < 1 || timeoutSeconds < 0) {
            throw new IllegalArgumentException("topN and workers must be positive; timeout cannot be negative");
        }
        this.dir = dir.toAbsolutePath().normalize();
        this.topN = topN;
        this.workerCount = workerCount;
        this.timeoutSeconds = timeoutSeconds;
        this.virtual = virtual;
        this.showProgress = showProgress;
        scanner = new Thread(this::scan, "word-count-scanner");
        scanner.setDaemon(true);
        for (int i = 0; i < workerCount; i++) {
            Thread worker = virtual
                    ? Thread.ofVirtual().name("word-count-worker-" + i).unstarted(this::work)
                    : new Thread(this::work, "word-count-worker-" + i);
            if (!virtual) {
                worker.setDaemon(true);
            }
            workers.add(worker);
        }
    }

    public void run() throws InterruptedException {
        execute(true, showProgress);
    }

    public static Map<String, Long> countParallel(Path dir, int workerCount)
            throws InterruptedException {
        WordCount counter = new WordCount(dir, 10, workerCount, 0, false, false);
        return counter.execute(false, false);
    }

    private Map<String, Long> execute(boolean installShutdownHook, boolean progress)
            throws InterruptedException {
        Thread hook = null;
        if (installShutdownHook) {
            hook = new Thread(() -> {
                if (!finished.get()) {
                    shutdown();
                    awaitStopped(Duration.ofSeconds(2));
                    printResult(true);
                }
            }, "word-count-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
        }

        if (progress) {
            reporter = new Thread(this::report, "word-count-reporter");
            reporter.setDaemon(true);
            reporter.start();
        }
        if (timeoutSeconds > 0) {
            deadlineThread = new Thread(() -> {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(timeoutSeconds));
                    if (!finished.get()) {
                        System.err.println("deadline reached - stopping");
                        shutdown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "word-count-deadline");
            deadlineThread.setDaemon(true);
            deadlineThread.start();
        }

        for (Thread worker : workers) {
            worker.start();
        }
        scanner.start();
        try {
            while (scanner.isAlive() && !stopRequested.get()) {
                scanner.join(100);
            }
            if (stopRequested.get()) {
                awaitStopped(Duration.ofSeconds(2));
            } else {
                for (Thread worker : workers) {
                    worker.join();
                }
            }
        } catch (InterruptedException e) {
            shutdown();
            awaitStopped(Duration.ofSeconds(2));
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            finished.set(true);
            if (reporter != null) {
                reporter.interrupt();
            }
            if (deadlineThread != null) {
                deadlineThread.interrupt();
            }
        }

        boolean partial = stopRequested.get();
        if (progress) {
            printResult(partial);
        }
        if (installShutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException e) {
                // The JVM is already shutting down; the hook owns final reporting.
            }
        }
        return snapshot();
    }

    private void scan() {
        try (Stream<Path> paths = Files.walk(dir)) {
            var iterator = paths.filter(Files::isRegularFile).iterator();
            while (!Thread.currentThread().isInterrupted() && iterator.hasNext()) {
                Path path = iterator.next();
                totalFiles.incrementAndGet();
                queue.put(path);
            }
        } catch (IOException e) {
            hasErrors.set(true);
            System.err.println("Could not scan " + dir + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < workerCount; i++) {
                    try {
                        queue.put(POISON);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Path path = queue.take();
                if (path == POISON) {
                    return;
                }
                countFile(path);
                filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void countFile(Path path) {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                String[] words = line.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
                for (String word : words) {
                    if (!word.isEmpty()) {
                        counts.computeIfAbsent(word, ignored -> new LongAdder()).increment();
                        wordsDone.increment();
                    }
                }
            }
        } catch (IOException e) {
            hasErrors.set(true);
            System.err.println("Could not read " + path + ": " + e.getMessage());
        }
    }

    private void report() {
        long startNanos = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted() && !finished.get()) {
                Thread.sleep(500);
                if (!finished.get()) {
                    long elapsed = System.nanoTime() - startNanos;
                    double seconds = elapsed / 1_000_000_000.0;
                    System.out.printf("[%.1fs] files %d/%d  words %,d  %,.0f w/s  top: %s%n",
                            seconds, filesDone.get(), totalFiles.get(), wordsDone.sum(),
                            wordsDone.sum() / seconds, formatTop(3));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdown() {
        shutdownAtNanos.compareAndSet(0, System.nanoTime());
        if (stopRequested.compareAndSet(false, true)) {
            scanner.interrupt();
            for (Thread worker : workers) {
                worker.interrupt();
            }
        }
    }

    private void awaitStopped(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        long shutdownStart = shutdownAtNanos.get();
        if (shutdownStart != 0) {
            deadline = Math.min(deadline, shutdownStart + timeout.toNanos());
        }
        List<Thread> allThreads = new ArrayList<>(workers);
        allThreads.add(scanner);
        for (Thread thread : allThreads) {
            if (!thread.isAlive()) {
                continue;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            try {
                thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Map<String, Long> snapshot() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counts.forEach((word, count) -> result.put(word, count.sum()));
        return Map.copyOf(result);
    }

    private List<Map.Entry<String, Long>> rankedEntries() {
        return snapshot().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
    }

    private List<Map.Entry<String, Long>> top(int limit) {
        return rankedEntries().stream().limit(limit).toList();
    }

    private String formatTop(int limit) {
        return String.join(" ", top(limit).stream()
                .map(entry -> entry.getKey() + "(" + entry.getValue() + ")")
                .toList());
    }

    private void printResult(boolean partial) {
        if (!resultPrinted.compareAndSet(false, true)) {
            return;
        }
        long wordTotal = wordsDone.sum();
        double elapsedSeconds = (System.nanoTime() - startNanos()) / 1_000_000_000.0;
        boolean incomplete = partial || hasErrors.get();
        System.out.printf("%s files=%d/%d words=%d elapsed=%.2fs mode=%s%n",
                incomplete ? "[PARTIAL]" : "DONE", filesDone.get(), totalFiles.get(),
                wordTotal, elapsedSeconds,
                virtual ? "virtual(" + workerCount + ")" : "platform(" + workerCount + ")");
        System.out.println(" rank  word        count     share");
        List<Map.Entry<String, Long>> entries = top(topN);
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Long> entry = entries.get(i);
            double percentage = wordTotal == 0 ? 0 : entry.getValue() * 100.0 / wordTotal;
            System.out.printf("%5d  %-12s %,9d  %5.2f%%%n",
                    i + 1, entry.getKey(), entry.getValue(), percentage);
        }
        writeCsv(rankedEntries());
    }

    private long startNanos() {
        return startTimeNanos;
    }

    private final long startTimeNanos = System.nanoTime();

    private void writeCsv(List<Map.Entry<String, Long>> entries) {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of("result.csv"),
                StandardCharsets.UTF_8)) {
            writer.write("word,count,percentage");
            writer.newLine();
            long total = wordsDone.sum();
            for (Map.Entry<String, Long> entry : entries) {
                double percentage = total == 0 ? 0 : entry.getValue() * 100.0 / total;
                writer.write(entry.getKey() + "," + entry.getValue() + ","
                        + String.format(Locale.ROOT, "%.4f", percentage));
                writer.newLine();
            }
            System.out.println("result.csv written");
        } catch (IOException e) {
            System.err.println("Could not write result.csv: " + e.getMessage());
        }
    }

    public static Map<String, Long> countSequential(Path dir) throws IOException {
        ConcurrentHashMap<String, Long> result = new ConcurrentHashMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        for (String word : line.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
                            if (!word.isEmpty()) {
                                result.merge(word, 1L, Long::sum);
                            }
                        }
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Usage: WordCount <dir> [--top N] [--timeout SECONDS] [--virtual]");
        }
        Path dir = Path.of(args[0]);
        int top = 10;
        int workers = Runtime.getRuntime().availableProcessors();
        long timeout = 0;
        boolean virtual = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--top" -> top = Integer.parseInt(args[++i]);
                case "--timeout" -> timeout = Long.parseLong(args[++i]);
                case "--virtual" -> virtual = true;
                case "--workers" -> workers = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        new WordCount(dir, top, workers, timeout, virtual).run();
    }
}
