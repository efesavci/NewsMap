package analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import main.newsmap.web.dto.HotspotDTO.ArticleDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adds product-facing location and category fields to the persistent event projection. */
public final class EventLocationEnricher {
    private static final Path EVENTS_FILE = Path.of("embeddings-service/outputs/events.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventLocationEnricher() {}

    public static void enrichAndSave() {
        if (!Files.exists(EVENTS_FILE)) return;

        try {
            ObjectNode feed = (ObjectNode) MAPPER.readTree(EVENTS_FILE.toFile());
            ArrayNode events = (ArrayNode) feed.path("events");
            int resolved = 0;

            for (JsonNode node : events) {
                ObjectNode event = (ObjectNode) node;
                List<ArticleDTO> articles = articlesFrom(event.path("articles"));
                GeoTagger.Location location = GeoTagger.guessLocation(articles);
                event.put("lat", location.lat());
                event.put("lon", location.lon());
                event.put("location", location.name());
                event.put("locationConfidence", location.confidence());
                event.put("locationMethod", location.method());
                event.put("category", categorize(event, articles));
                if (!"unresolved".equals(location.method())) resolved++;
            }

            feed.put("locationGeneratedAt", Instant.now().toString());
            Path temporary = EVENTS_FILE.resolveSibling(EVENTS_FILE.getFileName() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), feed);
            try {
                Files.move(temporary, EVENTS_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, EVENTS_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.printf("[EventLocationEnricher] Resolved %d/%d persistent events.%n",
                    resolved, events.size());
        } catch (Exception e) {
            throw new IllegalStateException("Could not enrich persistent events", e);
        }
    }

    private static List<ArticleDTO> articlesFrom(JsonNode articlesNode) {
        List<ArticleDTO> articles = new ArrayList<>();
        for (JsonNode article : articlesNode) {
            long timestamp;
            try {
                timestamp = Instant.parse(article.path("publishTime").asText()).toEpochMilli();
            } catch (Exception ignored) {
                timestamp = 0;
            }
            articles.add(new ArticleDTO(
                    article.path("title").asText(),
                    article.path("source").asText(),
                    article.path("url").asText(null),
                    timestamp
            ));
        }
        return articles;
    }

    private static String categorize(JsonNode event, List<ArticleDTO> articles) {
        StringBuilder text = new StringBuilder(event.path("title").asText());
        articles.forEach(article -> text.append(' ').append(article.title()));
        String value = text.toString().toLowerCase(Locale.ROOT);

        if (contains(value, "war", "military", "missile", "airstrike", "attack", "ceasefire",
                "gaza", "ukraine", "iran", "israel", "army", "troops")) return "WAR";
        if (contains(value, "election", "president", "government", "parliament", "congress",
                "senate", "minister", "trump", "court", "law", "sanction")) return "POLITICS";
        if (contains(value, "ai", "artificial intelligence", "technology", "software", "cyber",
                "chip", "spacex", "nasa", "robot")) return "TECHNOLOGY";
        if (contains(value, "market", "company", "business", "economy", "trade", "bank",
                "tariff", "stock", "oil", "finance")) return "BUSINESS";
        if (contains(value, "health", "hospital", "disease", "virus", "vaccine", "medical",
                "cancer", "drug", "patient")) return "HEALTH";
        return "OTHER";
    }

    private static boolean contains(String text, String... terms) {
        for (String term : terms) {
            if (term.length() <= 3) {
                if (text.matches(".*\\b" + java.util.regex.Pattern.quote(term) + "\\b.*")) return true;
            } else if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
