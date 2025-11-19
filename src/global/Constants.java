package global;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Constants {

    private Constants() {}

    // default user agent name to offer to the website currently accessing
    public static final String USER_AGENT = "NewsMapGathererBot/1.0 (contact: your.email@domain)";

    // default location to fetch the website locations from
    public static final String WEBSITE_CONFIG_PATH = "configs/newsConfigs";

    /* This section includes the print prefixes for some commonly used print operations */
    public static final String CRAWLER_PRINT_PREFIX = "[CRAWLER]";

    public static final String SITE_CONFIG_PREFIX = "[SITE_CONFIG]";

    public static final String ARTICLE_DIR = "data/articles/";

    // enum for predefined and supported file formats

    public enum FileFormat {
        JSON,JSONL,PARQUET;
        public static String getExtensionFromFormat(FileFormat format) {
            return switch (format) {
                case JSON -> ".json";
                case JSONL -> ".jsonl";
                case PARQUET -> ".parquet";
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };
        }
    }

    // FORMATTERS
    //timestamp formatter for file name
    public static final DateTimeFormatter batchFileTimeStampFormatter = DateTimeFormatter.ofPattern("yyyy'Y'MM'M'dd'D'_HH'h'mm'm'ss's'",Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    //string timestamp formatter for storage as string
    public static final DateTimeFormatter timeStampFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
}
