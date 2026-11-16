package main.newsmap.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/saved-events")
public class SavedEventController {
    public record ArchiveRequest(boolean archived) {}
    private final SavedEventStore store;

    public SavedEventController(SavedEventStore store) { this.store = store; }

    @GetMapping
    public List<SavedEventStore.SavedEvent> list(@PathVariable String userId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        return store.list(userId);
    }

    @PutMapping("/{eventId}")
    public List<SavedEventStore.SavedEvent> save(@PathVariable String userId, @PathVariable String eventId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        // Resolve on the server: clients cannot forge saved reporting or external links.
        var event = EventController.readFeed().events().stream().filter(item -> item.id().equals(eventId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        store.save(userId, event);
        return store.list(userId);
    }

    @PatchMapping("/{eventId}")
    public List<SavedEventStore.SavedEvent> archive(@PathVariable String userId, @PathVariable String eventId,
                                                 @RequestBody ArchiveRequest request, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        if (!store.archive(userId, eventId, request.archived()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved event not found");
        return store.list(userId);
    }

    @DeleteMapping("/{eventId}")
    public List<SavedEventStore.SavedEvent> remove(@PathVariable String userId, @PathVariable String eventId, Principal principal) {
        AccountAccess.requireOwner(principal, userId);
        store.remove(userId, eventId);
        return store.list(userId);
    }
}
