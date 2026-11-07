package vn.giapha.join;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.giapha.join.JoinService.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JoinController {
    private final JoinService joinService;

    @PostMapping("/join-requests")
    public JoinRequestDto create(@Valid @RequestBody CreateJoinRequest req) {
        return joinService.create(req);
    }

    @GetMapping("/join-requests/mine")
    public List<JoinRequestDto> mine() {
        return joinService.mine();
    }

    @GetMapping("/families/{familyId}/join-requests")
    public List<JoinRequestDto> list(@PathVariable Long familyId, @RequestParam(required = false) String status) {
        return joinService.listForFamily(familyId, status);
    }

    @PostMapping("/families/{familyId}/join-requests/{id}/approve")
    public void approve(@PathVariable Long familyId, @PathVariable Long id, @Valid @RequestBody ApproveRequest req) {
        joinService.approve(familyId, id, req);
    }

    @PostMapping("/families/{familyId}/join-requests/{id}/reject")
    public void reject(@PathVariable Long familyId, @PathVariable Long id) {
        joinService.reject(familyId, id);
    }
}
