package main.newsmap.web;

import storage.Article;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import static global.Constants.ARTICLE_DIR;

/**
 * REST controller for article data.
 * Reads crawled articles from the data/articles/ directory (JSONL files).
 * Does NOT trigger crawling — it only serves previously crawled data.
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping
    public List<Article> getArticles(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        List<Article> articles = loadArticlesFromDisk();

        if (source != null && !source.isBlank()) {
            articles = articles.stream()
                    .filter(a -> a.source().toLowerCase().contains(source.toLowerCase()))
                    .toList();
        }

        return articles.stream().limit(limit).toList();
    }

    /**
     * Reads all .jsonl files from the article output directory.
     * Each line in a .jsonl file is one Article JSON object.
     */
    private List<Article> loadArticlesFromDisk() {
        List<Article> result = new ArrayList<>();
        Path dir = Path.of(ARTICLE_DIR);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return result;
        }

        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".jsonl")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    try {
                        Article article = MAPPER.readValue(line, Article.class);
                        result.add(article);
                    } catch (Exception e) {
                        System.err.println("[ArticleController] Failed to parse line in " + file.getFileName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ArticleController] Failed to read article directory: " + e.getMessage());
        }

        // Also read individual .json files
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json") && !f.toString().endsWith(".jsonl")).toList()) {
                try {
                    Article article = MAPPER.readValue(file.toFile(), Article.class);
                    result.add(article);
                } catch (Exception e) {
                    System.err.println("[ArticleController] Failed to parse " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[ArticleController] Failed to read article directory: " + e.getMessage());
        }

        return result;
    }
}
