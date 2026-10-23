package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.ThreadLocalRandom;

public class Bank {
    static final class Account {
        final int id;
        private long balance;

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        long balance() {
            return balance;
        }

        void withdraw(long amount) {
            balance -= amount;
        }

        void deposit(long amount) {
            balance += amount;
        }
    }

    static void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length == 0 ? "--deadlock" : args[0];
        switch (mode) {
            case "--ordered" -> OrderedBank.run(8, 10_000);
            case "--try-lock" -> TryLockBank.runFixedWork(8, 100_000);
            case "--graceful" -> TryLockBank.runUntilInterrupted(8, 2_000);
            case "--deadlock" -> reproduceDeadlock();
            default -> throw new IllegalArgumentException(
                    "Usage: Bank [--deadlock|--ordered|--try-lock|--graceful]");
        }
    }

    private static void reproduceDeadlock() {
        Account[] accounts = accounts();
        Thread watchdog = new Thread(() -> {
            while (true) {
                long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
                if (deadlocked != null) {
                    ThreadInfo[] infos = ManagementFactory.getThreadMXBean()
                            .getThreadInfo(deadlocked);
                    System.err.print("[watchdog] DEADLOCK detected between: ");
                    for (int i = 0; i < infos.length; i++) {
                        System.err.print((i == 0 ? "" : ", ") + infos[i].getThreadName());
                    }
                    System.err.println();
                    return;
                }
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "deadlock-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        Thread[] workers = new Thread[8];
        for (int worker = 0; worker < workers.length; worker++) {
            workers[worker] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account from = accounts[random.nextInt(accounts.length)];
                    Account to = accounts[random.nextInt(accounts.length)];
                    if (from != to) {
                        transfer(from, to, random.nextInt(1, 50));
                    }
                }
            }, "transfer-" + worker);
            workers[worker].start();
        }
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("unexpectedly completed without deadlock");
    }

    static Account[] accounts() {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1_000);
        }
        return accounts;
    }

    static long total(Account[] accounts) {
        return totalLocked(accounts, 0);
    }

    private static long totalLocked(Account[] accounts, int index) {
        if (index == accounts.length) {
            long total = 0;
            for (Account account : accounts) {
                total += account.balance();
            }
            return total;
        }
        synchronized (accounts[index]) {
            return totalLocked(accounts, index + 1);
        }
    }
}
