package vn.giapha.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.giapha.member.ChildOrderDtos.NextBirthOrderResponse;
import vn.giapha.member.ChildOrderDtos.ReorderChildrenRequest;
import vn.giapha.member.ChildOrderDtos.SiblingDto;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/members/{parentId}")
@RequiredArgsConstructor
@Tag(name = "Thứ tự con cái", description = "Gợi ý, xem và đổi thứ tự sinh (birth_order) của các con")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Chưa đăng nhập", content = @Content),
        @ApiResponse(responseCode = "403", description = "Không có quyền", content = @Content),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy thành viên trong dòng họ", content = @Content)
})
public class ChildOrderController {
    private final ChildOrderService childOrderService;

    @GetMapping("/next-birth-order")
    @Operation(summary = "Gợi ý thứ tự cho người con tiếp theo",
            description = "Trả về thứ tự lớn nhất hiện tại + 1 trong nhóm anh chị em và danh sách các con hiện có. "
                    + "Nhóm = toàn bộ con của người cha thuộc huyết thống (mọi đời vợ); nếu cha là con rể thì theo người mẹ.")
    public NextBirthOrderResponse nextBirthOrder(
            @PathVariable Long familyId,
            @Parameter(description = "Cha hoặc mẹ của người con sắp thêm") @PathVariable Long parentId,
            @Parameter(description = "Người còn lại (vợ/chồng), tùy chọn") @RequestParam(required = false) Long spouseId) {
        return childOrderService.nextBirthOrder(familyId, parentId, spouseId);
    }

    @GetMapping("/children")
    @Operation(summary = "Danh sách con, đã sắp xếp",
            description = "Sắp theo quy ước của dòng họ: BIRTH_ORDER (con cả → con út) hoặc SONS_FIRST (con trai trước).")
    public List<SiblingDto> children(@PathVariable Long familyId, @PathVariable Long parentId) {
        return childOrderService.childrenOf(familyId, parentId);
    }

    @PutMapping("/children/order")
    @Operation(summary = "Đổi thứ tự các con (kéo thả)",
            description = "Gửi toàn bộ id con theo thứ tự mới, phần tử đầu là con cả. Hệ thống đánh số lại 1..n. "
                    + "Quyền: Ban quản trị, hoặc Trưởng chi trong nhánh của mình.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thứ tự mới"),
            @ApiResponse(responseCode = "422", description = "Danh sách thiếu / thừa / trùng con (CHILD_ORDER_MISMATCH)",
                    content = @Content)
    })
    public List<SiblingDto> reorder(@PathVariable Long familyId, @PathVariable Long parentId,
                                    @Valid @RequestBody ReorderChildrenRequest req) {
        return childOrderService.reorderChildren(familyId, parentId, req.childIds());
    }
}
