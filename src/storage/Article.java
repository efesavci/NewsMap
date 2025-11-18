package storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import global.Constants.*;

import static global.Constants.ARTICLE_DIR;

public record Article(
        String id,
        String url,
        String title,
        String body,
        String source,
        String publishTime,
        String crawledAt)  {

    public static final File OUT_DIR = new File(ARTICLE_DIR);
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectWriter PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter();
    public static final ObjectWriter JSONL_WRITER = MAPPER.writer()
            .without(SerializationFeature.INDENT_OUTPUT);

    static {
        if (!OUT_DIR.exists()) {
            OUT_DIR.mkdirs();
        }
    }

    /**
     * Writes the article as a standalone formatted JSON file.
     * Errors are logged here because this is a simple helper method.
     */
    public void saveAsSingleJSON() {
        File outFile = new File(OUT_DIR, id + ".json");
        try {
            PRETTY_WRITER.writeValue(outFile, this);
        } catch (IOException e) {
            System.err.printf(
                    "Could not write article '%s' to %s: %s%n",
                    id, outFile.getAbsolutePath(), e.getMessage()
            );
        }
    }

    /**
     * Appends this article as a single-line JSON object to a batch writer.
     * This method does NOT catch exceptions — the Crawler is responsible
     * for handling I/O failures.
     */
    public void appendToJsonBatch(Writer writer) throws IOException {
        writer.write(JSONL_WRITER.writeValueAsString(this));
        writer.write(System.lineSeparator()); // platform-independent newline
        // No flush here — Crawler manages flushing for performance.
    }

    @Override
    public String toString() {
        return String.format("(%s) %s - %s", id, source, title);
    }
}
