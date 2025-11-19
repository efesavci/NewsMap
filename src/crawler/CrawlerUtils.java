package crawler;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import storage.SiteConfig;

import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static global.Constants.*;

public class CrawlerUtils {

    public static final List<DateTimeFormatter> englishFORMATS = List.of(
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    );

    public static final Pattern updateDatePattern = Pattern
            .compile("(\\d{1,2} [A-Za-z]{3,} \\d{4})");

    public static final String[] possibleTimeStamps = {
        "time", "date", "timestamp", "published", "updated", "datetime"
    };

    static boolean containsNumber(String s) {
        return s != null && s.matches(".*\\d.*");
    }

    static String extractTimeAttribute(Element el) {
        if (el == null) {
            System.out.println("date elements is null");
            return "";
        }

        // Common timestamp-related keywords in attribute names


        // First pass: attribute KEY contains a time keyword
        for (Attribute attr : el.attributes()) {
            String key = attr.getKey().toLowerCase();
            String value = attr.getValue().trim();

            for (String k : possibleTimeStamps) {
                if (key.contains(k) && containsNumber(value)) {
                    return value;
                }
            }
        }

        // Second pass: attribute VALUE looks like a timestamp even if the key doesn't match
        for (Attribute attr : el.attributes()) {
            String value = attr.getValue().trim();
            if (containsNumber(value)) {
                return value;
            }
        }

        // Third pass: fallback to element text (for cases like <span>14 Nov 2025</span>)
        String text = el.text().trim();
        if (containsNumber(text)) {
            return text;
        }

        return "";
    }

    public static Instant parseSmartTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return Instant.now();

        raw = raw.trim();

        try {
            // ---------------------------
            // 1) Epoch milliseconds
            // ---------------------------
            if (raw.matches("\\d{13}")) {
                return Instant.ofEpochMilli(Long.parseLong(raw));
            }

            // ---------------------------
            // 2) Epoch seconds
            // ---------------------------
            if (raw.matches("\\d{10}")) {
                return Instant.ofEpochSecond(Long.parseLong(raw));
            }

            // ---------------------------
            // 3) ISO 8601 (BBC, CNN, Reuters)
            // ---------------------------
            try {
                return Instant.parse(raw);
            } catch (Exception ignore) {}

            // ---------------------------
            // 4) English textual dates:
            // Formats:
            // - 14 Nov 2025
            // - Nov 14, 2025
            // - November 14, 2025
            // - 14 November 2025
            // ---------------------------
            for (DateTimeFormatter f : englishFORMATS) {
                try {
                    return f.parse(raw, java.time.LocalDate::from)
                            .atStartOfDay()
                            .toInstant(ZoneOffset.UTC);
                } catch (Exception ignore) {
                }
            }

            // ---------------------------
            // 5) Try extracting date-like substrings with regex
            // Used when raw text is embedded inside longer text
            // e.g. "Updated 14 Nov 2025 at 5:30 PM"
            // ---------------------------
            java.util.regex.Matcher m = updateDatePattern.matcher(raw);

            if (m.find()) {
                String candidate = m.group(1);
                try {
                    return englishFORMATS.getFirst().parse(candidate, LocalDate::from)
                            .atStartOfDay()
                            .toInstant(ZoneOffset.UTC);
                } catch (Exception ignore) {}
            }

            // ---------------------------
            // 6) Final fallback
            // ---------------------------
            return Instant.now();

        } catch (Exception e) {
            return Instant.now();
        }
    }


    public static void crawler_info(String msg) {
        System.out.println(CRAWLER_PRINT_PREFIX + msg);
    }

    public static void crawler_warn(String msg) {
        System.out.println(CRAWLER_PRINT_PREFIX+"[WARN]" + msg);
    }

    public static void crawler_error(String msg) {
        System.err.println(CRAWLER_PRINT_PREFIX+"[ERROR]" + msg);
    }

    static File createBatchFile(SiteConfig cfg, String extension,Instant timestamp, boolean concurrent) {
        String host = URI.create(cfg.baseUrl()).getHost();
        String domain = host.split("\\.")[host.split("\\.").length - 2];
        String name = "articles_" + batchFileTimeStampFormatter.format(timestamp);
        if (concurrent) name += "_" + domain;
        return new File(ARTICLE_DIR + name + extension);
    }

}
