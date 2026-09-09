package analysis;

import org.json.JSONObject;
import org.json.JSONTokener;
import main.newsmap.web.dto.HotspotDTO.ArticleDTO;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeoTagger {

    public record Location(double lat, double lon, String name, double confidence, String method) {}

    private static final double LOCAL_CONFIDENCE_THRESHOLD = 0.70;

    private static final String CACHE_FILE = "data/geocache.json";
    private static final String CACHE_VERSION = "v6";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Uses free local place matching first and calls Gemini only for ambiguous clusters.
     */
    public static Location guessLocation(List<ArticleDTO> articles) {
        if (articles == null || articles.isEmpty()) {
            return new Location(0, 0, "Unknown", 0, "unresolved");
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null) {
            apiKey = System.getenv("GOOGLE_API_KEY");
        }

        // 1. Generate a stable cache key based on the top 3 article titles
        String cacheKey = generateCacheKey(articles);
        
        // 2. Try to retrieve from local cache
        Location cachedLoc = getFromCache(cacheKey);
        // An unresolved result is not permanent: retry it when a key is added later.
        if (cachedLoc != null && !("unresolved".equals(cachedLoc.method()) && hasText(apiKey))) {
            return cachedLoc;
        }

        // 3. Resolve locally first. Most repeated, explicit place mentions need no API call.
        Location local = guessLocationHeuristic(articles);
        if (local.confidence() >= LOCAL_CONFIDENCE_THRESHOLD) {
            saveToCache(cacheKey, local);
            return local;
        }

        // 4. Ask Gemini only when local evidence is missing or ambiguous.
        Location loc = null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            loc = askGemini(articles, apiKey);
        }

        // 5. Preserve the best local answer when the API is unavailable or fails.
        if (loc == null) {
            loc = local.confidence() >= LOCAL_CONFIDENCE_THRESHOLD
                    ? local
                    : new Location(0, 0, "Global / Unresolved", 0, "unresolved");
        }

        // 6. Save to local cache
        if (!"unresolved".equals(loc.method())) {
            saveToCache(cacheKey, loc);
        }

        return loc;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String generateCacheKey(List<ArticleDTO> articles) {
        StringBuilder sb = new StringBuilder(CACHE_VERSION);
        // Use top 3 titles for stable hashing
        for (int i = 0; i < Math.min(articles.size(), 3); i++) {
            sb.append(articles.get(i).title().trim().toLowerCase());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    private static Location getFromCache(String key) {
        File file = new File(CACHE_FILE);
        if (!file.exists()) return null;

        try (FileReader reader = new FileReader(file)) {
            JSONObject cache = new JSONObject(new JSONTokener(reader));
            if (cache.has(key)) {
                JSONObject obj = cache.getJSONObject(key);
                return new Location(
                        obj.getDouble("lat"),
                        obj.getDouble("lon"),
                        obj.getString("name"),
                        obj.optDouble("confidence", 0),
                        obj.optString("method", "legacy-cache")
                );
            }
        } catch (Exception e) {
            System.err.println("[GeoTagger] Error reading cache: " + e.getMessage());
        }
        return null;
    }

    private static synchronized void saveToCache(String key, Location loc) {
        File file = new File(CACHE_FILE);
        // Ensure data directory exists
        file.getParentFile().mkdirs();

        JSONObject cache = new JSONObject();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                cache = new JSONObject(new JSONTokener(reader));
            } catch (Exception ignored) {}
        }

        JSONObject entry = new JSONObject();
        entry.put("lat", loc.lat());
        entry.put("lon", loc.lon());
        entry.put("name", loc.name());
        entry.put("confidence", loc.confidence());
        entry.put("method", loc.method());
        cache.put(key, entry);

        try (FileWriter writer = new FileWriter(file)) {
            cache.write(writer, 2, 0);
        } catch (IOException e) {
            System.err.println("[GeoTagger] Error writing cache: " + e.getMessage());
        }
    }

    private static Location askGemini(List<ArticleDTO> articles, String apiKey) {
        try {
            StringBuilder articlesText = new StringBuilder();
            for (ArticleDTO a : articles.subList(0, Math.min(articles.size(), 8))) {
                articlesText.append("- ").append(a.title()).append(" (Source: ").append(a.source()).append(")\n");
            }

            String prompt = "You are a news geotagging agent. Identify the single most specific geographical location (city, state/region, country) representing this news story cluster based on the article titles. Return ONLY a valid JSON object. Do NOT wrap in markdown formatting or ```json code blocks.\n" +
                    "Format:\n" +
                    "{\"name\": \"City, Country\", \"lat\": latitude_float, \"lon\": longitude_float}\n\n" +
                    "Articles:\n" +
                    articlesText.toString();

            JSONObject payload = new JSONObject();
            JSONObject content = new JSONObject();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            content.put("parts", new JSONObject[]{part});
            payload.put("contents", new JSONObject[]{content});

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("temperature", 0.1);
            payload.put("generationConfig", generationConfig);

            String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-2.5-flash-lite");
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JSONObject resObj = new JSONObject(response.body());
                String textResponse = resObj.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim();

                // Clean any markdown code blocks
                textResponse = textResponse.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
                
                // Extract only the JSON object if there's any surrounding text
                int firstBrace = textResponse.indexOf('{');
                int lastBrace = textResponse.lastIndexOf('}');
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    textResponse = textResponse.substring(firstBrace, lastBrace + 1);
                }

                JSONObject locJson = new JSONObject(textResponse);
                return new Location(
                        locJson.getDouble("lat"),
                        locJson.getDouble("lon"),
                        locJson.getString("name"),
                        0.90,
                        "gemini"
                );
            } else {
                System.err.println("[GeoTagger] Gemini API error: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[GeoTagger] Failed to call Gemini: " + e.getMessage());
        }
        return null;
    }

    private static boolean containsWord(String text, String... words) {
        for (String word : words) {
            String regex = ".*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*";
            if (text.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private static Location guessLocationHeuristic(List<ArticleDTO> articles) {
        Map<PlaceCandidate, Integer> hits = new LinkedHashMap<>();
        for (ArticleDTO a : articles) {
            String title = a.title().toLowerCase();
            for (PlaceCandidate candidate : PLACE_CANDIDATES) {
                if (containsWord(title, candidate.terms())) {
                    hits.merge(candidate, 1, Integer::sum);
                }
            }
        }

        if (!hits.isEmpty()) {
            List<Map.Entry<PlaceCandidate, Integer>> ranked = hits.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<PlaceCandidate, Integer>>comparingInt(
                            entry -> weightedScore(entry.getKey(), entry.getValue())).reversed())
                    .toList();

            Map.Entry<PlaceCandidate, Integer> winner = ranked.get(0);
            int bestScore = weightedScore(winner.getKey(), winner.getValue());
            int secondScore = ranked.size() > 1
                    ? weightedScore(ranked.get(1).getKey(), ranked.get(1).getValue())
                    : 0;
            double agreement = (double) winner.getValue() / articles.size();
            double margin = (double) (bestScore - secondScore) / Math.max(bestScore, 1);
            double confidence = Math.min(0.99, agreement * 0.70 + margin * 0.30);
            Location location = winner.getKey().location();
            // A country mentioned in one headline may be an actor, team or comparison
            // rather than where the event happened. Cities remain strong explicit evidence.
            if (articles.size() == 1 && !location.name().contains(",")
                    && !hasLocationCue(articles.get(0).title(), winner.getKey().terms())) {
                confidence = Math.min(confidence, 0.60);
            }
            return new Location(location.lat(), location.lon(), location.name(), confidence, "local");
        }

        return new Location(0, 0, "Global / Unresolved", 0, "unresolved");
    }

    private static boolean hasLocationCue(String title, String[] terms) {
        String normalized = title.toLowerCase();
        for (String term : terms) {
            String pattern = ".*\\b(in|at|across|near|inside|outside|throughout|off|over)\\s+" +
                    "(the\\s+)?" + java.util.regex.Pattern.quote(term) + "\\b.*";
            if (normalized.matches(pattern)) return true;
        }
        return false;
    }

    private static int weightedScore(PlaceCandidate candidate, int hits) {
        // City/region labels generally include a country and outrank a simultaneous
        // country mention without requiring another API call.
        int specificity = candidate.location().name().contains(",") ? 2 : 1;
        return hits * specificity;
    }

    private record PlaceCandidate(Location location, String[] terms) {
        PlaceCandidate(double lat, double lon, String name, String... terms) {
            this(new Location(lat, lon, name, 0, "local"), terms);
        }
    }

    private static final List<PlaceCandidate> PLACE_CANDIDATES = List.of(
            new PlaceCandidate(13.7563, 100.5018, "Bangkok, Thailand", "bangkok"),
            new PlaceCandidate(37.7749, -122.4194, "San Francisco, USA", "san francisco", "alcatraz"),
            new PlaceCandidate(41.3874, 2.1686, "Barcelona, Spain", "barcelona"),
            new PlaceCandidate(36.1408, -5.3536, "Gibraltar", "gibraltar"),
            new PlaceCandidate(31.5, 34.47, "Gaza", "gaza"),
            new PlaceCandidate(50.4501, 30.5234, "Kyiv, Ukraine", "kyiv", "kiev"),
            new PlaceCandidate(25.2854, 51.5310, "Doha, Qatar", "doha", "qatar"),
            new PlaceCandidate(48.8566, 2.3522, "Paris, France", "paris"),
            new PlaceCandidate(51.5074, -0.1278, "London, UK", "london"),
            new PlaceCandidate(52.5200, 13.4050, "Berlin, Germany", "berlin"),
            new PlaceCandidate(35.6762, 139.6503, "Tokyo, Japan", "tokyo"),
            new PlaceCandidate(28.6139, 77.2090, "New Delhi, India", "new delhi", "delhi"),
            new PlaceCandidate(35.6892, 51.3890, "Tehran, Iran", "tehran"),
            new PlaceCandidate(31.7683, 35.2137, "Jerusalem", "jerusalem"),
            new PlaceCandidate(55.7558, 37.6173, "Moscow, Russia", "moscow"),
            new PlaceCandidate(38.8951, -77.0364, "Washington D.C., USA", "washington d.c", "washington dc"),
            new PlaceCandidate(23.8859, 45.0792, "Saudi Arabia", "saudi", "riyadh"),
            new PlaceCandidate(15.5007, 32.5599, "Sudan", "sudan", "khartoum"),
            new PlaceCandidate(-4.0383, 21.7587, "DR Congo", "dr congo", "drc", "congo", "kinshasa"),
            new PlaceCandidate(15.8700, 100.9925, "Thailand", "thailand"),
            new PlaceCandidate(35.8617, 104.1954, "China", "china", "chinese"),
            new PlaceCandidate(12.8797, 121.7740, "Philippines", "philippines"),
            new PlaceCandidate(20.5937, 78.9629, "India", "india", "indian"),
            new PlaceCandidate(32.4279, 53.6880, "Iran", "iran", "iranian", "hormuz"),
            new PlaceCandidate(31.0461, 34.8516, "Israel / Palestine", "israel", "israeli", "palestine", "palestinian"),
            new PlaceCandidate(48.3794, 31.1656, "Ukraine", "ukraine", "ukrainian"),
            new PlaceCandidate(61.5240, 105.3188, "Russia", "russia", "russian"),
            new PlaceCandidate(46.2276, 2.2137, "France", "france", "french"),
            new PlaceCandidate(55.3781, -3.4360, "United Kingdom", "uk", "britain", "british", "england", "scotland"),
            new PlaceCandidate(40.4637, -3.7492, "Spain", "spain", "spanish"),
            new PlaceCandidate(41.8719, 12.5674, "Italy", "italy", "italian"),
            new PlaceCandidate(51.1657, 10.4515, "Germany", "germany", "german"),
            new PlaceCandidate(23.6345, -102.5528, "Mexico", "mexico", "mexican"),
            new PlaceCandidate(56.1304, -106.3468, "Canada", "canada", "canadian"),
            new PlaceCandidate(-25.2744, 133.7751, "Australia", "australia", "australian"),
            new PlaceCandidate(9.0820, 8.6753, "Nigeria", "nigeria", "nigerian"),
            new PlaceCandidate(36.2048, 138.2529, "Japan", "japan", "japanese"),
            new PlaceCandidate(39.8283, -98.5795, "United States", "us", "usa", "united states", "american", "trump", "white house", "congress", "senate"),
            new PlaceCandidate(50.8503, 4.3517, "Europe", "eu", "europe", "european")
    );
}
