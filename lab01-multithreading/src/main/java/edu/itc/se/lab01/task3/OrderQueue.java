package edu.itc.se.lab01.task3;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

record Order(int id, String item, int qty) {
    static Order poisonPill() {
        return new Order(-1, "", 0);
    }
}

interface OrderQueue<T> {
    void put(T item) throws InterruptedException;
    T take() throws InterruptedException;
}

final class BoundedBuffer<T> implements OrderQueue<T> {
    private final java.util.ArrayDeque<T> items = new java.util.ArrayDeque<>();
    private final int capacity;

    BoundedBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) {
            wait();
        }
        items.addLast(item);
        assert items.size() <= capacity;
        notifyAll();
    }

    @Override
    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        T item = items.removeFirst();
        assert items.size() >= 0;
        notifyAll();
        return item;
    }

    synchronized int size() {
        return items.size();
    }
}

final class BlockingOrderQueue<T> implements OrderQueue<T> {
    private final BlockingQueue<T> queue;

    BlockingOrderQueue(int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void put(T item) throws InterruptedException {
        queue.put(item);
    }

    @Override
    public T take() throws InterruptedException {
        return queue.take();
    }
}
