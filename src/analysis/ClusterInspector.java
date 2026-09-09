package analysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import main.newsmap.web.dto.HotspotDTO;
import main.newsmap.web.dto.HotspotDTO.ArticleDTO;
import main.newsmap.model.HotspotCategory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClusterInspector {

    private static final String CLUSTERS_FILE = "embeddings-service/outputs/clusters.csv";
    private static final String HOTSPOTS_FILE = "data/hotspots.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter STORED_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    /**
     * Reads the clusters.csv file and groups articles by their cluster_label.
     * Only valid clusters (label != -1) are returned.
     */
    public static Map<Integer, List<ArticleDTO>> getClusteredArticles() {
        Map<Integer, List<ArticleDTO>> clusters = new HashMap<>();
        Path path = Path.of(CLUSTERS_FILE);

        if (!Files.exists(path)) {
            System.err.println("[ClusterInspector] " + CLUSTERS_FILE + " not found. Run the pipeline first.");
            return clusters;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String headerLine = br.readLine();
            if (headerLine == null) return clusters;

            String[] headers = headerLine.split(",");
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colMap.put(headers[i].trim(), i);
            }

            String line;
            while ((line = br.readLine()) != null) {
                // simple CSV split, assuming no commas in title for this basic prototype.
                // a more robust CSV parser (like opencsv) could be used if titles contain commas.
                // To be safe against commas in the title, we'll split with a limit or regex,
                // but for now, since pandas uses standard CSV, we'll use a regex that handles basic quoting.
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                int labelCol = colMap.getOrDefault("cluster_label", -1);
                int titleCol = colMap.getOrDefault("title", -1);
                int sourceCol = colMap.getOrDefault("source", -1);
                int urlCol = colMap.getOrDefault("url", -1);
                int publishTimeCol = colMap.getOrDefault("publishTime", -1);

                if (labelCol == -1 || titleCol == -1 || sourceCol == -1 || urlCol == -1) continue;

                int clusterLabel = Integer.parseInt(cols[labelCol].trim());
                if (clusterLabel == -1) continue; // skip noise

                String title = cols[titleCol].replaceAll("^\"|\"$", "").trim();
                String source = cols[sourceCol].replaceAll("^\"|\"$", "").trim();
                String url = cols[urlCol].replaceAll("^\"|\"$", "").trim();

                long timestamp = System.currentTimeMillis(); // fallback
                if (publishTimeCol != -1 && !cols[publishTimeCol].trim().isEmpty()) {
                    timestamp = parseTimestamp(cols[publishTimeCol].replaceAll("^\"|\"$", "").trim(), timestamp);
                }

                ArticleDTO article = new ArticleDTO(title, source, url, timestamp);

                clusters.computeIfAbsent(clusterLabel, k -> new ArrayList<>()).add(article);
            }
        } catch (Exception e) {
            System.err.println("[ClusterInspector] Error reading clusters: " + e.getMessage());
            e.printStackTrace();
        }

        return clusters;
    }

    private static long parseTimestamp(String value, long fallback) {
        try {
            if (value.matches("\\d{13}")) return Long.parseLong(value);
            if (value.matches("\\d{10}")) return Long.parseLong(value) * 1000L;
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (Exception ignored) {
                return LocalDateTime.parse(value, STORED_TIMESTAMP).toInstant(ZoneOffset.UTC).toEpochMilli();
            }
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Precomputes hotspots by running the AI Geotagger dynamically and saves them to a JSON file.
     */
    public static synchronized void precomputeAndSaveHotspots() {
        System.out.println("[ClusterInspector] Precomputing hotspots in the background...");
        List<HotspotDTO> hotspots = generateHotspotsDynamic();
        try {
            File file = new File(HOTSPOTS_FILE);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            mapper.writeValue(file, hotspots);
            System.out.println("[ClusterInspector] Precomputed hotspots successfully saved to " + HOTSPOTS_FILE);
        } catch (IOException e) {
            System.err.println("[ClusterInspector] Error saving precomputed hotspots: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Serves precomputed hotspots if the JSON file exists, otherwise falls back to generating dynamically.
     */
    public static List<HotspotDTO> generateHotspots() {
        File file = new File(HOTSPOTS_FILE);
        if (file.exists()) {
            try {
                return Arrays.asList(mapper.readValue(file, HotspotDTO[].class));
            } catch (IOException e) {
                System.err.println("[ClusterInspector] Error reading precomputed hotspots: " + e.getMessage());
            }
        }
        System.out.println("[ClusterInspector] Precomputed file " + HOTSPOTS_FILE + " not found. Falling back to dynamic generation.");
        return generateHotspotsDynamic();
    }

    /**
     * Converts raw clusters into HotspotDTO objects using GeoTagger dynamically.
     */
    public static List<HotspotDTO> generateHotspotsDynamic() {
        Map<Integer, List<ArticleDTO>> clusters = getClusteredArticles();
        List<HotspotDTO> hotspots = new ArrayList<>();

        for (Map.Entry<Integer, List<ArticleDTO>> entry : clusters.entrySet()) {
            List<ArticleDTO> articles = entry.getValue();
            if (articles.isEmpty()) continue;

            // Generate location using GeoTagger based on the first article (or all articles)
            GeoTagger.Location loc = GeoTagger.guessLocation(articles);

            String category = guessCategory(articles).name();

            hotspots.add(new HotspotDTO(
                    loc.lat(),
                    loc.lon(),
                    category,
                    loc.name(),
                    loc.confidence(),
                    loc.method(),
                    articles
            ));
        }

        return hotspots;
    }

    private static HotspotCategory guessCategory(List<ArticleDTO> articles) {
        String text = articles.stream()
                .map(ArticleDTO::title)
                .map(String::toLowerCase)
                .reduce("", (left, right) -> left + " " + right);

        Map<HotspotCategory, List<String>> keywords = Map.of(
                HotspotCategory.TECHNOLOGY, List.of("ai", "technology", "tech", "software", "computer", "chip", "spacex", "satellite", "internet"),
                HotspotCategory.BUSINESS, List.of("business", "economy", "economic", "market", "trade", "stock", "company", "debt", "bank", "price", "jobs"),
                HotspotCategory.HEALTH, List.of("health", "medical", "hospital", "disease", "virus", "outbreak", "ebola", "covid", "vaccine", "polio"),
                HotspotCategory.WAR, List.of("war", "missile", "military", "airstrike", "weapon", "troops", "ceasefire", "armed conflict", "invasion"),
                HotspotCategory.POLITICS, List.of("politics", "election", "parliament", "president", "minister", "government", "congress", "senate", "law", "bill")
        );

        HotspotCategory best = HotspotCategory.OTHER;
        int bestScore = 0;
        for (Map.Entry<HotspotCategory, List<String>> entry : keywords.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                var matcher = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(keyword) + "\\b").matcher(text);
                while (matcher.find()) score++;
            }
            if (score > bestScore) {
                best = entry.getKey();
                bestScore = score;
            }
        }
        return best;
    }
}
