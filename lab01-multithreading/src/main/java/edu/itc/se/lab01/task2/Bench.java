package edu.itc.se.lab01.task2;

import java.util.ArrayList;
import java.util.List;

public class Bench {
    static final int THREADS = 8;
    static final int ITERATIONS = 1_000_000;

    static void run(String label, Counter counter) throws InterruptedException {
        long start = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    counter.increment();
                }
            }, "inc-" + t);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long expected = (long) THREADS * ITERATIONS;
        long actual = counter.get();
        System.out.printf("%-22s value=%,d  correct=%-5b  %d ms%n",
                label, actual, actual == expected, elapsedMs);
        if (!(counter instanceof UnsafeCounter) && actual != expected) {
            throw new AssertionError(label + " lost updates: expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Example unsafe result: 3,412,907; lost-update totals vary by run.
        for (int attempt = 1; attempt <= 3; attempt++) {
            run("UnsafeCounter run " + attempt, new UnsafeCounter());
        }
        run("SynchronizedCounter", new SynchronizedCounter());
        run("LockCounter", new LockCounter());
        run("AtomicCounter", new AtomicCounter());
        run("LongAdderCounter", new LongAdderCounter());
    }
}
