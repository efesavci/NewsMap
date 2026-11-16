package main.newsmap.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ArticleBatchQueueTest {
    @TempDir Path root;

    @Test void failureAndRestartKeepInputAndEmbeddingCheckpoint() throws Exception {
        Path spool = root.resolve("articles"), batches = root.resolve("batches");
        Files.createDirectories(spool);
        Files.writeString(spool.resolve("source.jsonl"), "{\"id\":\"first\",\"body\":\"report\"}\n");
        var queue = new ArticleBatchQueue(spool, batches);
        queue.capture();
        var batch = queue.pending().getFirst();
        Files.writeString(batch.embeddings(), "completed embedding checkpoint");
        // A failed matching process never acknowledges the batch. A restarted worker
        // must find the same input and checkpoint, including with no fresh articles.
        var restarted = new ArticleBatchQueue(spool, batches);
        restarted.capture();
        assertEquals(1, restarted.pending().size());
        assertEquals(batch.directory(), restarted.pending().getFirst().directory());
        assertTrue(Files.exists(batch.input().resolve("source.jsonl")));
        assertEquals("completed embedding checkpoint", Files.readString(batch.embeddings()));
        restarted.complete(batch);
        assertTrue(restarted.pending().isEmpty());
    }

    @Test void newCrawlDoesNotOverwritePendingInput() throws Exception {
        Path spool = root.resolve("articles"), batches = root.resolve("batches");
        Files.createDirectories(spool);
        var queue = new ArticleBatchQueue(spool, batches);
        Files.writeString(spool.resolve("source.jsonl"), "old\n");
        queue.capture();
        var first = queue.pending().getFirst();
        Files.writeString(spool.resolve("source.jsonl"), "new\n");
        queue.capture();
        assertEquals(2, queue.pending().size());
        assertEquals("old\n", Files.readString(first.input().resolve("source.jsonl")));
        queue.complete(first);
        assertEquals("new\n", Files.readString(queue.pending().getFirst().input().resolve("source.jsonl")));
    }

    @Test void resumesPartiallyCapturedBatchAndIgnoresEmptyCrawl() throws Exception {
        Path spool = root.resolve("articles"), batches = root.resolve("batches");
        Files.createDirectories(spool);
        Path partial = batches.resolve("interrupted/input");
        Files.createDirectories(partial);
        Files.writeString(partial.resolve("a.jsonl"), "old\n");
        Files.writeString(spool.resolve("empty.jsonl"), "");
        var queue = new ArticleBatchQueue(spool, batches);
        queue.capture();
        assertEquals(1, queue.pending().size());
        queue.complete(queue.pending().getFirst());
        assertTrue(queue.pending().isEmpty());
    }
}
