package vn.giapha.member;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/** Dieu kien truy van dong cho family_members (JPA Specification) */
public final class MemberSpecifications {
    private MemberSpecifications() {
    }

    public static Specification<FamilyMember> inFamily(Long familyId) {
        return (root, query, cb) -> cb.equal(root.get("familyId"), familyId);
    }

    /**
     * Nguoi dang hien tren cay, HOAC nam trong danh sach luon-hien cua nguoi xem
     * (chinh minh, nhanh minh quan ly). Tan dung chi muc mot phan idx_family_members_tree_visible.
     */
    public static Specification<FamilyMember> visibleOnTreeOrIdIn(Collection<Long> alwaysVisibleIds) {
        return (root, query, cb) -> alwaysVisibleIds == null || alwaysVisibleIds.isEmpty()
                ? cb.isTrue(root.get("visibleOnTree"))
                : cb.or(cb.isTrue(root.get("visibleOnTree")), root.get("id").in(alwaysVisibleIds));
    }
}
