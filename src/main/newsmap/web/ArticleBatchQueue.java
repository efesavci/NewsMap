package main.newsmap.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Files remain pending until matching, location projection and notifications succeed. */
final class ArticleBatchQueue {
    record Batch(Path directory) {
        Path input() { return directory.resolve("input"); }
        Path embeddings() { return directory.resolve("embeddings.h5"); }
    }
    private final Path spool;
    private final Path root;

    ArticleBatchQueue(Path spool, Path root) { this.spool = spool; this.root = root; }

    void capture() throws IOException {
        Files.createDirectories(spool);
        Files.createDirectories(root);
        List<Path> files;
        try (var entries = Files.list(spool)) {
            files = entries.filter(path -> path.toString().endsWith(".jsonl")).sorted().toList();
        }
        // Empty crawler files contain no work; retain non-empty files even after a failed crawl.
        for (Path file : files) if (Files.size(file) == 0) Files.delete(file);
        files = files.stream().filter(Files::exists).toList();
        if (files.isEmpty()) return;
        Path input = root.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID()).resolve("input");
        Files.createDirectories(input);
        for (Path file : files) Files.move(file, input.resolve(file.getFileName()), StandardCopyOption.ATOMIC_MOVE);
    }

    List<Batch> pending() throws IOException {
        Files.createDirectories(root);
        List<Path> directories;
        try (var entries = Files.list(root)) { directories = entries.filter(Files::isDirectory).sorted().toList(); }
        var batches = new java.util.ArrayList<Batch>();
        for (Path directory : directories) {
            if (Files.exists(directory.resolve("complete"))) { remove(directory); continue; }
            Batch batch = new Batch(directory);
            if (Files.isDirectory(batch.input())) {
                try (var entries = Files.list(batch.input())) {
                    if (entries.anyMatch(path -> path.toString().endsWith(".jsonl"))) batches.add(batch);
                }
            }
        }
        return batches;
    }

    void complete(Batch batch) throws IOException {
        Files.writeString(batch.directory().resolve("complete"), "matched, enriched and notifications refreshed\n");
        remove(batch.directory());
    }

    private void remove(Path directory) throws IOException {
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
