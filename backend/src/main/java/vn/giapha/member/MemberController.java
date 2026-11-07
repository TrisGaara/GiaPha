package vn.giapha.member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.giapha.member.MemberDtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    /** Cay pha he: nguoi bi an khoi cay duoc loai bo / thay bang nut "an danh" voi nguoi dung thong thuong */
    @GetMapping("/tree")
    public TreeDto tree(@PathVariable Long familyId) {
        return memberService.tree(familyId);
    }

    /**
     * Cap nhat nhanh cau hinh rieng tu (truong null = giu nguyen).
     * Ban than / Truong chi / Ban quan tri doi duoc cac co hide_* va publicProfile;
     * an/hien tren cay (visibleOnTree) chi Ban quan tri va Truong chi.
     */
    @PatchMapping("/members/{id}/privacy")
    public MemberDto updatePrivacy(@PathVariable Long familyId, @PathVariable Long id,
                                  @Valid @RequestBody vn.giapha.privacy.PrivacySettings settings) {
        return memberService.updatePrivacy(familyId, id, settings);
    }

    @GetMapping("/members")
    public List<MemberDto> list(@PathVariable Long familyId) {
        return memberService.list(familyId);
    }

    @GetMapping("/members/{id}")
    public MemberDto get(@PathVariable Long familyId, @PathVariable Long id) {
        return memberService.get(familyId, id);
    }

    @GetMapping("/members/{id}/descendants")
    public List<MemberDto> descendants(@PathVariable Long familyId, @PathVariable Long id) {
        return memberService.descendants(familyId, id);
    }

    @GetMapping("/members/{id}/ancestors")
    public List<MemberDto> ancestors(@PathVariable Long familyId, @PathVariable Long id) {
        return memberService.ancestors(familyId, id);
    }

    @PostMapping("/members")
    public MemberDto create(@PathVariable Long familyId, @Valid @RequestBody MemberRequest req) {
        return memberService.create(familyId, req);
    }

    @PutMapping("/members/{id}")
    public MemberDto update(@PathVariable Long familyId, @PathVariable Long id, @Valid @RequestBody MemberRequest req) {
        return memberService.update(familyId, id, req);
    }

    @DeleteMapping("/members/{id}")
    public void delete(@PathVariable Long familyId, @PathVariable Long id) {
        memberService.delete(familyId, id);
    }

    // Quan he hon nhan & them con: xem vn.giapha.marital.MaritalRelationController
}
