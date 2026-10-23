package edu.itc.se.lab01.task3;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

final class Producer implements Runnable {
    private final int firstId;
    private final int count;
    private final int consumerCount;
    private final OrderQueue<Order> queue;
    private final CountDownLatch producersRemaining;

    Producer(int firstId, int count, int consumerCount, OrderQueue<Order> queue,
             CountDownLatch producersRemaining) {
        this.firstId = firstId;
        this.count = count;
        this.consumerCount = consumerCount;
        this.queue = queue;
        this.producersRemaining = producersRemaining;
    }

    @Override
    public void run() {
        boolean interrupted = false;
        try {
            for (int i = 0; i < count && !Thread.currentThread().isInterrupted(); i++) {
                queue.put(new Order(firstId + i, "item-" + (firstId + i),
                        ThreadLocalRandom.current().nextInt(1, 6)));
                Thread.sleep(ThreadLocalRandom.current().nextInt(21));
            }
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (producersRemaining.countDown() == 0) {
                for (int i = 0; i < consumerCount; i++) {
                    boolean inserted = false;
                    while (!inserted) {
                        try {
                            queue.put(Order.poisonPill());
                            inserted = true;
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
