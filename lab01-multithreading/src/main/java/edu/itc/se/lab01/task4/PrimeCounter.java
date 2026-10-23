package edu.itc.se.lab01.task4;

import java.util.concurrent.Callable;

final class PrimeCounter implements Callable<Long> {
    private final long from;
    private final long to;

    PrimeCounter(long from, long to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Long call() {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long divisor = 2; divisor * divisor <= n; divisor++) {
                if (n % divisor == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) {
                count++;
            }
        }
        return count;
    }
}
