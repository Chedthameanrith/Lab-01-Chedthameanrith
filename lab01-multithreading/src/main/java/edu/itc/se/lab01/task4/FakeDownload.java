package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.concurrent.Callable;

final class FakeDownload implements Callable<String> {
    private final int id;

    FakeDownload(int id) {
        this.id = id;
    }

    @Override
    public String call() throws Exception {
        if (id != 1) {
            if (id % 50 == 0) {
                throw new IOException("boom");
            }
            sleep(200);
        } else {
            sleep(5_000);
        }
        String url = "https://example.test/download/" + id;
        return url + " -> " + url.length();
    }

    private static void sleep(long millis) throws InterruptedException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
