package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

final class OrderedBank {
    private OrderedBank() {
    }

    static void transfer(Bank.Account from, Bank.Account to, long amount) {
        Bank.Account first = from.id < to.id ? from : to;
        Bank.Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    static void run(int threadCount, int transfersPerThread) throws InterruptedException {
        Bank.Account[] accounts = Bank.accounts();
        AtomicLong completed = new AtomicLong();
        Thread[] workers = new Thread[threadCount];
        long start = System.nanoTime();
        for (int worker = 0; worker < threadCount; worker++) {
            workers[worker] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                for (int i = 0; i < transfersPerThread; i++) {
                    Bank.Account from = accounts[random.nextInt(accounts.length)];
                    Bank.Account to = accounts[random.nextInt(accounts.length)];
                    if (from != to) {
                        transfer(from, to, random.nextInt(1, 50));
                        completed.incrementAndGet();
                    }
                }
            }, "ordered-transfer-" + worker);
            workers[worker].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        long total = Bank.total(accounts);
        if (total != 4_000) {
            throw new AssertionError("account total changed: " + total);
        }
        System.out.printf("transfers=%d total=%d (expected 4000) in %d ms%n",
                completed.get(), total,
                (System.nanoTime() - start) / 1_000_000);
    }
}
