package main.newsmap.web.dto;

import java.util.List;

/**
 * Data transfer object for hotspot data served via the REST API.
 * Mirrors the structure of the JavaFX Hotspot record but without any UI dependencies.
 */
public record HotspotDTO(
        double lat,
        double lon,
        String category,
        String location,
        double locationConfidence,
        String locationMethod,
        List<ArticleDTO> articles
) {

    public record ArticleDTO(
            String title,
            String source,
            String url,
            long timestamp
    ) {}
}
