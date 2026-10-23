package edu.itc.se.lab01.challenge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public final class WordCountData {
    private WordCountData() {
    }

    public static void generate(Path directory, int fileCount, int wordsPerFile)
            throws IOException {
        Files.createDirectories(directory);
        String[] vocabulary = new String[2_000];
        for (int i = 0; i < vocabulary.length; i++) {
            vocabulary[i] = "word" + letters(i);
        }
        Random random = new Random(25);
        for (int file = 0; file < fileCount; file++) {
            Path path = directory.resolve("words-" + file + ".txt");
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                for (int word = 0; word < wordsPerFile; word++) {
                    if (word > 0) {
                        writer.write(' ');
                    }
                    writer.write(vocabulary[random.nextInt(vocabulary.length)]);
                }
            }

            public static void main(String[] args) throws IOException {
                if (args.length < 1 || args.length > 3) {
                    throw new IllegalArgumentException(
                            "Usage: WordCountData <dir> [file-count] [words-per-file]");
                }
                Path directory = Path.of(args[0]);
                int fileCount = args.length > 1 ? Integer.parseInt(args[1]) : 200;
                int wordsPerFile = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;
                generate(directory, fileCount, wordsPerFile);
            }

            private static String letters(int value) {
                StringBuilder result = new StringBuilder();
                do {
                    result.append((char) ('a' + value % 26));
                    value = value / 26 - 1;
                } while (value >= 0);
                return result.reverse().toString();
            }
        }
    }
}
