package vn.giapha.marital;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.giapha.common.BusinessRuleViolationException;
import vn.giapha.member.FamilyMember;
import vn.giapha.member.FamilyMemberRepository;
import vn.giapha.member.Gender;

import java.util.HashSet;
import java.util.Set;

/**
 * Kiem tra tinh hop le cua quan he cha/me - con.
 * Dung chung cho API them con ({@link MaritalRelationService#createChildWithParents}) va API tao/sua thanh vien
 * de khong co duong nao "lach" duoc quy tac.
 */
@Component
@RequiredArgsConstructor
public class ParentageValidator {
    private final MaritalRelationRepository maritalRelationRepository;
    private final FamilyMemberRepository familyMemberRepository;

    /**
     * @param child  thanh vien dang duoc gan cha me (null neu dang tao moi)
     * @param father cha (co the null)
     * @param mother me (co the null)
     */
    public void validate(FamilyMember child, FamilyMember father, FamilyMember mother) {
        if (father == null && mother == null) {
            return; // Thuy to / nguoi chua ro cha me
        }
        validateGender(father, mother);
        validateBloodLine(father, mother);
        validateParentsMarried(father, mother);
        if (child != null) {
            validateNoCycle(child, father, mother);
        }
    }

    private void validateGender(FamilyMember father, FamilyMember mother) {
        if (father != null && father.getGender() != Gender.MALE) {
            throw new BusinessRuleViolationException("INVALID_FATHER_GENDER",
                    "\"" + father.getFullName() + "\" là nữ, không thể chọn làm cha");
        }
        if (mother != null && mother.getGender() != Gender.FEMALE) {
            throw new BusinessRuleViolationException("INVALID_MOTHER_GENDER",
                    "\"" + mother.getFullName() + "\" là nam, không thể chọn làm mẹ");
        }
    }

    /** Con phai noi duoc vao huyet thong: it nhat mot ben cha/me thuoc dong ho */
    private void validateBloodLine(FamilyMember father, FamilyMember mother) {
        boolean anyBloodLine = (father != null && father.isBloodLine()) || (mother != null && mother.isBloodLine());
        if (!anyBloodLine) {
            throw new BusinessRuleViolationException("PARENT_NOT_IN_BLOOD_LINE",
                    "Ít nhất cha hoặc mẹ phải thuộc huyết thống dòng họ (không thể thêm con chỉ từ con dâu / con rể)");
        }
    }

    /** Neu chon ca cha va me thi hai nguoi phai co quan he hon nhan (bat ke trang thai) */
    private void validateParentsMarried(FamilyMember father, FamilyMember mother) {
        if (father == null || mother == null) {
            return;
        }
        if (!maritalRelationRepository.existsByHusbandIdAndWifeId(father.getId(), mother.getId())) {
            // Con theo ho me (me thuoc dong ho, cha la con re) -> dien dat theo phia me
            String msg = mother.isBloodLine() && !father.isBloodLine()
                    ? "\"" + father.getFullName() + "\" không nằm trong danh sách các đời chồng của \"" + mother.getFullName() + "\""
                    : "\"" + mother.getFullName() + "\" không nằm trong danh sách các đời vợ của \"" + father.getFullName() + "\"";
            throw new BusinessRuleViolationException("PARENTS_NOT_MARRIED",
                    msg + ". Hãy thêm quan hệ hôn nhân trước khi gắn con.");
        }
    }

    /** Khong cho chon chinh minh hoac con chau cua minh lam cha/me (tranh vong lap trong cay) */
    private void validateNoCycle(FamilyMember child, FamilyMember father, FamilyMember mother) {
        Set<Long> descendants = new HashSet<>(familyMemberRepository.findDescendantIds(child.getId()));
        if ((father != null && descendants.contains(father.getId())) || (mother != null && descendants.contains(mother.getId()))) {
            throw new BusinessRuleViolationException("PARENTAGE_CYCLE",
                    "Không thể chọn chính mình hoặc con cháu của mình làm cha/mẹ");
        }
    }
}
