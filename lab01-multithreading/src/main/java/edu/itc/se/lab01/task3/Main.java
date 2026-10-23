package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final int ORDER_COUNT = 100;
    private static final int PRODUCER_COUNT = 2;
    private static final int CONSUMER_COUNT = 3;

    public static void main(String[] args) throws InterruptedException {
        run("BoundedBuffer", new BoundedBuffer<>(10));
        run("ArrayBlockingQueue", new BlockingOrderQueue<>(10));
    }

    private static void run(String label, OrderQueue<Order> queue) throws InterruptedException {
        long start = System.nanoTime();
        AtomicInteger processed = new AtomicInteger();
        CountDownLatch producersRemaining = new CountDownLatch(PRODUCER_COUNT);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            Thread consumer = new Thread(new Consumer("consumer-" + (i + 1), queue, processed),
                    "consumer-" + (i + 1));
            threads.add(consumer);
            consumer.start();
        }
        int perProducer = ORDER_COUNT / PRODUCER_COUNT;
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            Thread producer = new Thread(new Producer(i * perProducer + 1, perProducer,
                    CONSUMER_COUNT, queue, producersRemaining), "producer-" + (i + 1));
            threads.add(producer);
            producer.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%s: Total processed: %d (expected %d) in %d ms%n",
                label, processed.get(), ORDER_COUNT, elapsedMs);
        if (processed.get() != ORDER_COUNT) {
            throw new AssertionError(label + " processed " + processed.get()
                    + " orders instead of " + ORDER_COUNT);
        }
    }
}
