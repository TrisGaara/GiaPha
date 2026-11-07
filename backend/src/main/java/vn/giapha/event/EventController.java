package vn.giapha.event;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.giapha.event.EventDtos.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    @GetMapping
    public List<EventDto> list(@PathVariable Long familyId) {
        return eventService.list(familyId);
    }

    @PostMapping
    public EventDto create(@PathVariable Long familyId, @Valid @RequestBody EventRequest req) {
        return eventService.create(familyId, req);
    }

    @PutMapping("/{id}")
    public EventDto update(@PathVariable Long familyId, @PathVariable Long id, @Valid @RequestBody EventRequest req) {
        return eventService.update(familyId, id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long familyId, @PathVariable Long id) {
        eventService.delete(familyId, id);
    }

    /** Su kien sap toi trong N ngay (kem ngay gio tu dong tu ho so nguoi da mat) */
    @GetMapping("/upcoming")
    public List<Occurrence> upcoming(@PathVariable Long familyId, @RequestParam(defaultValue = "30") int days) {
        LocalDate today = eventService.today();
        return eventService.occurrences(familyId, today, today.plusDays(Math.min(Math.max(days, 1), 366)), true);
    }

    @GetMapping("/occurrences")
    public List<Occurrence> occurrences(@PathVariable Long familyId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "true") boolean includeDeathAnniversaries) {
        return eventService.occurrences(familyId, from, to, includeDeathAnniversaries);
    }
}
