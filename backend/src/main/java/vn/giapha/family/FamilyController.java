package vn.giapha.family;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.giapha.family.FamilyDtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/families")
@RequiredArgsConstructor
public class FamilyController {
    private final FamilyService familyService;

    @GetMapping
    public List<FamilyDto> myFamilies() {
        return familyService.myFamilies();
    }

    @PostMapping
    public FamilyDto create(@Valid @RequestBody FamilyRequest req) {
        return familyService.create(req);
    }

    @GetMapping("/lookup")
    public FamilyPreview lookup(@RequestParam String code) {
        return familyService.preview(code);
    }

    @GetMapping("/{familyId}")
    public FamilyDto get(@PathVariable Long familyId) {
        return familyService.get(familyId);
    }

    @PutMapping("/{familyId}")
    public FamilyDto update(@PathVariable Long familyId, @Valid @RequestBody FamilyRequest req) {
        return familyService.update(familyId, req);
    }

    @DeleteMapping("/{familyId}")
    public void delete(@PathVariable Long familyId) {
        familyService.delete(familyId);
    }

    @PostMapping("/{familyId}/join-code")
    public FamilyDto regenerateJoinCode(@PathVariable Long familyId) {
        return familyService.regenerateJoinCode(familyId);
    }

    @PostMapping("/{familyId}/leave")
    public void leave(@PathVariable Long familyId) {
        familyService.leave(familyId);
    }

    @GetMapping("/{familyId}/roles")
    public List<FamilyRoleDto> roles(@PathVariable Long familyId) {
        return familyService.listRoles(familyId);
    }

    @PutMapping("/{familyId}/roles/{roleId}")
    public void updateRole(@PathVariable Long familyId, @PathVariable Long roleId, @Valid @RequestBody UpdateRoleRequest req) {
        familyService.updateRole(familyId, roleId, req);
    }

    @DeleteMapping("/{familyId}/roles/{roleId}")
    public void removeRole(@PathVariable Long familyId, @PathVariable Long roleId) {
        familyService.removeRole(familyId, roleId);
    }
}
