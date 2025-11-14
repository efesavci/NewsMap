package storage;
import com.fasterxml.jackson.databind.ObjectMapper;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.net.*;
import static global.Constants.*;

public record SiteConfig(
        String baseUrl,
        List<String> topicSelectors,
        List<String> articleSelectors,
        String articleTitle,
        String articleTime,
        String articleBody,
        int maxDepth,
        BaseRobotRules rules
) {

    /**
     * Loads all .json configs from the given path (or default Env.CONFIG_DIR),
     * parses their robots.txt, and returns a list of enriched SiteConfig objects.
     */
    public static List<SiteConfig> generateConfigsWithRobots(String path) {
        List<SiteConfig> configs = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        // Use default path if none provided
        if (path == null || path.isEmpty()) {
            path = WEBSITE_CONFIG_PATH;
        }

        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println(SITE_CONFIG_PREFIX +"Config directory not found: " + folder.getAbsolutePath());
            return List.of();
        }

        File[] jsonFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0){
            System.err.println(SITE_CONFIG_PREFIX + "There are no valid JSON files under " + folder.getAbsolutePath());
            return List.of();
        }

        for (File file : jsonFiles) {
            try {
                // Parse JSON into raw config
                SiteConfig baseConfig = mapper.readValue(file, SiteConfig.class);
                System.out.println(SITE_CONFIG_PREFIX + "Loaded config for: " + baseConfig.baseUrl());

                // Fetch and parse robots.txt
                String host = URI.create(baseConfig.baseUrl()).toURL().getHost();
                if(host == null) host = "unknown";
                String domain = host;
                String robotsUrl = "https://" + domain + "/robots.txt";

                URL robotsURL = URI.create(robotsUrl).toURL();
                String robotsTxt = IOUtils.toString(robotsURL, StandardCharsets.UTF_8);

                SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
                BaseRobotRules parsedRules = parser.parseContent(
                        robotsUrl,
                        robotsTxt.getBytes(StandardCharsets.UTF_8),
                        "text/plain",
                        USER_AGENT
                );
                // Build enriched SiteConfig instance
                SiteConfig enriched = new SiteConfig(
                        baseConfig.baseUrl(),
                        baseConfig.topicSelectors(),
                        baseConfig.articleSelectors(),
                        baseConfig.articleTitle(),
                        baseConfig.articleTime(),
                        baseConfig.articleBody(),
                        baseConfig.maxDepth(),
                        parsedRules
                );

                configs.add(enriched);
                System.out.println(SITE_CONFIG_PREFIX + "The config file (with robot rules) was successfully parsed for: " + baseConfig.baseUrl());

            } catch (Exception e) {
                System.err.println(SITE_CONFIG_PREFIX +"Failed to load or parse config: " + file.getName() + " (" + e.getMessage() + ")");
            }
        }

        return configs;
    }
}