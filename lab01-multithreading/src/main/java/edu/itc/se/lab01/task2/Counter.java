package edu.itc.se.lab01.task2;

interface Counter {
    void increment();
    long get();
}

final class UnsafeCounter implements Counter {
    private long count;

    @Override
    public void increment() {
        count++;
    }

    @Override
    public long get() {
        return count;
    }
}

final class SynchronizedCounter implements Counter {
    private long count;

    @Override
    public synchronized void increment() {
        count++;
    }

    @Override
    public synchronized long get() {
        return count;
    }
}

final class LockCounter implements Counter {
    private final java.util.concurrent.locks.ReentrantLock lock =
            new java.util.concurrent.locks.ReentrantLock();
    private long count;

    @Override
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long get() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}

final class AtomicCounter implements Counter {
    private final java.util.concurrent.atomic.AtomicLong count =
            new java.util.concurrent.atomic.AtomicLong();

    @Override
    public void increment() {
        count.incrementAndGet();
    }

    @Override
    public long get() {
        return count.get();
    }
}

final class LongAdderCounter implements Counter {
    // LongAdder spreads contended updates across cells and combines them in sum().
    private final java.util.concurrent.atomic.LongAdder count =
            new java.util.concurrent.atomic.LongAdder();

    @Override
    public void increment() {
        count.increment();
    }

    @Override
    public long get() {
        return count.sum();
    }
}
