package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

final class TryLockBank {
    private static final class Account {
        private final int id;
        private final ReentrantLock lock = new ReentrantLock();
        private long balance = 1_000;

        Account(int id) {
            this.id = id;
        }
    }

    private final Account[] accounts = {
            new Account(0), new Account(1), new Account(2), new Account(3)
    };
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();

    private boolean transfer(Account from, Account to, long amount) throws InterruptedException {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        while (!Thread.currentThread().isInterrupted()) {
            if (!first.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                retries.incrementAndGet();
                backoff();
                continue;
            }
            boolean secondLocked = false;
            try {
                secondLocked = second.lock.tryLock(10, TimeUnit.MILLISECONDS);
                if (secondLocked) {
                    if (from.balance >= amount) {
                        from.balance -= amount;
                        to.balance += amount;
                    }
                    completed.incrementAndGet();
                    return true;
                }
            } finally {
                if (secondLocked) {
                    second.lock.unlock();
                }
                first.lock.unlock();
            }
            retries.incrementAndGet();
            backoff();
        }
        return false;
    }

    private static void backoff() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
    }

    private long total() {
        for (Account account : accounts) {
            account.lock.lock();
        }
        try {
            long total = 0;
            for (Account account : accounts) {
                total += account.balance;
            }
            return total;
        } finally {
            for (int i = accounts.length - 1; i >= 0; i--) {
                accounts[i].lock.unlock();
            }
        }
    }

    static void runFixedWork(int threadCount, int transfersPerThread) throws InterruptedException {
        TryLockBank bank = new TryLockBank();
        Thread[] workers = new Thread[threadCount];
        long start = System.nanoTime();
        for (int worker = 0; worker < threadCount; worker++) {
            workers[worker] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                for (int i = 0; i < transfersPerThread; i++) {
                    Account from = bank.accounts[random.nextInt(bank.accounts.length)];
                    Account to = bank.accounts[random.nextInt(bank.accounts.length)];
                    if (from != to) {
                        try {
                            bank.transfer(from, to, random.nextInt(1, 50));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }, "try-lock-transfer-" + worker);
            workers[worker].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        long total = bank.total();
        if (total != 4_000) {
            throw new AssertionError("account total changed: " + total);
        }
        System.out.printf("transfers=%d retries=%d total=%d (expected 4000) in %d ms%n",
                bank.completed.get(), bank.retries.get(), total,
                (System.nanoTime() - start) / 1_000_000);
    }

    static void runUntilInterrupted(int threadCount, long runMillis) throws InterruptedException {
        TryLockBank bank = new TryLockBank();
        Thread[] workers = new Thread[threadCount];
        for (int worker = 0; worker < threadCount; worker++) {
            workers[worker] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                while (!Thread.currentThread().isInterrupted()) {
                    Account from = bank.accounts[random.nextInt(bank.accounts.length)];
                    Account to = bank.accounts[random.nextInt(bank.accounts.length)];
                    if (from == to) {
                        continue;
                    }
                    try {
                        bank.transfer(from, to, random.nextInt(1, 50));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.printf("%s stopped%n", Thread.currentThread().getName());
            }, "transfer-" + worker);
            workers[worker].start();
        }

        Thread.sleep(runMillis);
        System.out.println("[main] interrupting " + threadCount + " workers after "
                + runMillis + " ms");
        for (Thread worker : workers) {
            worker.interrupt();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        for (Thread worker : workers) {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) {
                worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            }
        }
        boolean stopped = allStopped(workers);
        long total = bank.total();
        if (total != 4_000) {
            throw new AssertionError("account total changed: " + total);
        }
        System.out.printf("completed=%d all workers stopped=%b total=%d (expected 4000)%n",
                bank.completed.get(), stopped, total);
    }

    private static boolean allStopped(Thread[] workers) {
        for (Thread worker : workers) {
            if (worker.isAlive()) {
                return false;
            }
        }
        return true;
    }
}
