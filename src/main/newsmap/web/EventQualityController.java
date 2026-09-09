package main.newsmap.web;

import main.newsmap.web.EventQualityStore.FeedbackDTO;
import main.newsmap.web.EventQualityStore.FeedbackRequest;
import main.newsmap.web.EventQualityStore.QualitySummaryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/event-quality")
public class EventQualityController {
    private final EventQualityStore quality;

    public EventQualityController(EventQualityStore quality) {
        this.quality = quality;
    }

    @PostMapping("/feedback")
    public FeedbackDTO feedback(@RequestBody FeedbackRequest request, Principal principal) {
        if (principal == null) throw new IllegalStateException("Authentication is required");
        return quality.record(principal.getName(), request);
    }

    @GetMapping("/summary")
    public QualitySummaryDTO summary() {
        return quality.summary();
    }
}
