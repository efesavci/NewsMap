package main.newsmap.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Readiness of the web service; pipeline success is tracked separately in its run history. */
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() { return Map.of("status", "UP"); }
}
