package edu.itc.se.lab01.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordCountTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parallelAndSequentialCountsMatch() throws Exception {
        Path fixture = Files.createDirectory(temporaryDirectory.resolve("fixture"));
        for (int i = 0; i < 20; i++) {
            Files.writeString(fixture.resolve("file-" + i + ".txt"),
                    "Alpha beta beta\nCAFÉ alpha " + i + " words");
        }

        assertEquals(WordCount.countSequential(fixture), WordCount.countParallel(fixture, 4));
    }

    @Test
    void timeoutReturnsWithinThreeSecondsForLargeDirectory() throws Exception {
        Path fixture = Files.createDirectory(temporaryDirectory.resolve("many-files"));
        for (int i = 0; i < 5_000; i++) {
            Files.writeString(fixture.resolve("file-" + i + ".txt"), "one two three\n");
        }
        long start = System.nanoTime();
        new WordCount(fixture, 3, 4, 1, false, false).run();
        long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(3),
                "timeout run took " + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms");
    }
}
