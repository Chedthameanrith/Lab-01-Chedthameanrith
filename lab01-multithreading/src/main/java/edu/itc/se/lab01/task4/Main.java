package edu.itc.se.lab01.task4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    private static final long LIMIT = 4_000_000;
    private static final int CHUNKS = 16;
    private static final int DOWNLOADS = 500;

    static long countPrimes(long from, long to) {
        try {
            return new PrimeCounter(from, to).call();
        } catch (Exception e) {
            throw new IllegalStateException("Prime counting failed", e);
        }
    }

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n",
                single, elapsedMs(start));

        start = System.nanoTime();
        long parallel;
        try (ExecutorService pool =
                     Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int chunk = 0; chunk < CHUNKS; chunk++) {
                long from = LIMIT * chunk / CHUNKS;
                long to = LIMIT * (chunk + 1) / CHUNKS;
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }
            parallel = 0;
            for (Future<Long> future : futures) {
                parallel += future.get();
            }
        }
        System.out.printf("pool (%d threads): %d primes in %d ms%n",
                Runtime.getRuntime().availableProcessors(), parallel, elapsedMs(start));
        if (parallel != single) {
            throw new AssertionError("parallel and sequential prime counts differ");
        }

        runDownloads(false);
        runDownloads(true);
    }

    private static void runDownloads(boolean virtual) throws InterruptedException {
        String label = virtual ? "virtual threads" : "fixed(20) platform threads";
        long start = System.nanoTime();
        int ok = 0;
        int failed = 0;
        int timedOut = 0;

        try (ExecutorService executor = virtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(20)) {
            List<Future<String>> futures = new ArrayList<>(DOWNLOADS);
            for (int i = 0; i < DOWNLOADS; i++) {
                futures.add(executor.submit(new FakeDownload(i)));
            }

            Future<String> slow = futures.get(1);
            try {
                slow.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (TimeoutException e) {
                slow.cancel(true);
                timedOut++;
            } catch (ExecutionException e) {
                failed++;
            }

            for (int i = 0; i < futures.size(); i++) {
                if (i == 1) {
                    continue;
                }
                try {
                    futures.get(i).get();
                    ok++;
                } catch (ExecutionException e) {
                    failed++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        System.out.printf("downloads on %-31s: ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, elapsedMs(start));
        if (ok != 489 || failed != 10 || timedOut != 1) {
            throw new AssertionError("unexpected download summary for " + label);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
