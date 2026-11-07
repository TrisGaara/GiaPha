package vn.giapha.marital;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.giapha.marital.dto.*;
import vn.giapha.member.MemberDtos.MemberDto;

import java.net.URI;
import java.util.List;

/**
 * API Hon nhan (vo/chong) va them con.
 * <p>
 * Phan quyen (kiem tra trong service qua PermissionService, theo tung dong ho):
 * <ul>
 *     <li>Xem: moi thanh vien cua dong ho (VIEWER tro len)</li>
 *     <li>Them / sua / go: Ban quan tri (ADMIN, OWNER) hoac Truong chi trong nhanh minh quan ly</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/families/{familyId}")
@RequiredArgsConstructor
@Tag(name = "Hôn nhân & Con cái", description = "Quản lý các đời vợ/chồng và liên kết cha mẹ - con")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Chưa đăng nhập", content = @Content),
        @ApiResponse(responseCode = "403", description = "Không có quyền trong dòng họ này", content = @Content),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy dòng họ / thành viên / quan hệ", content = @Content)
})
public class MaritalRelationController {
    private final MaritalRelationService maritalRelationService;

    @GetMapping("/members/{memberId}/spouses")
    @Operation(summary = "Danh sách các đời vợ/chồng",
            description = "Trả về toàn bộ vợ/chồng của thành viên, sắp theo thứ tự kết hôn, kèm con chung của từng cặp. Quyền: VIEWER trở lên.")
    @ApiResponse(responseCode = "200", description = "Thành công")
    public List<SpouseResponseDto> getSpouses(
            @Parameter(description = "Id dòng họ") @PathVariable Long familyId,
            @Parameter(description = "Id thành viên") @PathVariable Long memberId) {
        return maritalRelationService.getSpousesOfMember(familyId, memberId);
    }

    @PostMapping("/members/{memberId}/spouses")
    @Operation(summary = "Thêm vợ/chồng",
            description = "Gắn một người có sẵn (existingSpouseId) hoặc tạo mới con dâu/con rể (newSpouse) và tạo quan hệ hôn nhân. "
                    + "Quyền: ADMIN, hoặc Trưởng chi trong nhánh của mình.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo quan hệ hôn nhân"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content),
            @ApiResponse(responseCode = "409", description = "Quan hệ đã tồn tại / trùng thứ tự kết hôn", content = @Content),
            @ApiResponse(responseCode = "422", description = "Vi phạm quy tắc: cùng giới, cả hai ngoại tộc, họ hàng gần, người vợ đang có chồng...",
                    content = @Content)
    })
    public ResponseEntity<SpouseResponseDto> addSpouse(@PathVariable Long familyId, @PathVariable Long memberId,
                                                       @Valid @RequestBody AddSpouseRequest req) {
        SpouseResponseDto created = maritalRelationService.addSpouse(familyId, memberId, req);
        return ResponseEntity
                .created(URI.create("/api/families/" + familyId + "/marital-relations/" + created.relationId()))
                .body(created);
    }

    @PutMapping("/marital-relations/{relationId}")
    @Operation(summary = "Cập nhật quan hệ hôn nhân", description = "Đổi thứ tự kết hôn, trạng thái (ACTIVE / DIVORCED / DECEASED), ghi chú.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã cập nhật"),
            @ApiResponse(responseCode = "409", description = "Trùng thứ tự kết hôn", content = @Content),
            @ApiResponse(responseCode = "422", description = "Người vợ đã có một cuộc hôn nhân ACTIVE khác", content = @Content)
    })
    public MaritalRelationDto updateRelation(@PathVariable Long familyId, @PathVariable Long relationId,
                                             @Valid @RequestBody UpdateMaritalRelationRequest req) {
        return maritalRelationService.updateRelation(familyId, relationId, req);
    }

    @DeleteMapping("/marital-relations/{relationId}")
    @Operation(summary = "Gỡ quan hệ hôn nhân",
            description = "Không cho gỡ nếu hai người đã có con chung (dùng trạng thái DIVORCED thay thế).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã gỡ"),
            @ApiResponse(responseCode = "422", description = "Hai người đã có con chung", content = @Content)
    })
    public ResponseEntity<Void> deleteRelation(
            @PathVariable Long familyId, @PathVariable Long relationId,
            @Parameter(description = "Xóa luôn con dâu/con rể nếu người đó không còn liên kết nào khác")
            @RequestParam(defaultValue = "false") boolean removeSpouseOnlyMember) {
        maritalRelationService.deleteRelation(familyId, relationId, removeSpouseOnlyMember);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/children")
    @Operation(summary = "Thêm con và liên kết cha mẹ",
            description = "Tạo thành viên mới với father_id / mother_id. Nếu chọn cả cha và mẹ thì mẹ phải là một đời vợ của cha "
                    + "(hoặc cha là một đời chồng của mẹ khi con theo họ mẹ). Đời và thứ tự con được tự tính nếu bỏ trống.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo con",
                    content = @Content(schema = @Schema(implementation = MemberDto.class))),
            @ApiResponse(responseCode = "400", description = "Thiếu cha/mẹ hoặc dữ liệu không hợp lệ", content = @Content),
            @ApiResponse(responseCode = "422", description = "Cha/mẹ không hợp lệ (sai giới tính, không phải vợ chồng, cả hai ngoại tộc)",
                    content = @Content)
    })
    public ResponseEntity<MemberDto> addChild(@PathVariable Long familyId, @Valid @RequestBody AddChildRequest req) {
        MemberDto child = maritalRelationService.createChildWithParents(familyId, req);
        return ResponseEntity
                .created(URI.create("/api/families/" + familyId + "/members/" + child.id()))
                .body(child);
    }
}
