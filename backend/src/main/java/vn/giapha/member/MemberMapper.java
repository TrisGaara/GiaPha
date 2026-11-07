package vn.giapha.member;

import vn.giapha.member.MemberDtos.LunarInfo;
import vn.giapha.member.MemberDtos.MemberDto;
import vn.giapha.privacy.MaskedField;
import vn.giapha.privacy.PrivacySettings;
import vn.giapha.privacy.ViewerContext;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chuyen FamilyMember -> MemberDto va CHE du lieu (data masking) theo nguoi xem.
 * Moi duong tra thanh vien ra ngoai deu phai di qua day.
 */
public final class MemberMapper {
    /** Ten hien thi cua nut an danh */
    public static final String HIDDEN_NAME = "Thành viên ẩn";

    private MemberMapper() {
    }

    /**
     * @param ctx             nguoi dang xem
     * @param birthOrderLabel danh xung theo thu tu sinh (co the null)
     * @param refVisible      cha/me co hien voi nguoi xem khong - neu khong, id cha/me bi xoa khoi DTO
     */
    public static MemberDto toDto(FamilyMember m, ViewerContext ctx, String birthOrderLabel, Predicate<Long> refVisible) {
        Set<MaskedField> mask = ctx.maskedFields(m);
        boolean canEdit = ctx.canEdit(m);
        boolean birth = !mask.contains(MaskedField.BIRTH_DATE);
        boolean death = !mask.contains(MaskedField.DEATH_DATE);
        boolean contact = !mask.contains(MaskedField.CONTACT_INFO);
        boolean bio = !mask.contains(MaskedField.BIO);
        boolean profile = !mask.contains(MaskedField.PROFILE);

        return new MemberDto(
                m.getId(), m.getFamilyId(), profile ? m.getUserId() : null,
                m.getFullName(), m.getOtherName(), m.getGender(),
                m.getGeneration(), m.getBirthOrder(), birthOrderLabel,
                ref(m.getFatherId(), refVisible), ref(m.getMotherId(), refVisible), m.isBloodLine(),
                birth ? m.getBirthDateSolar() : null,
                birth ? LunarInfo.of(m.getBirthLunarDay(), m.getBirthLunarMonth(), m.getBirthLunarYear(), m.isBirthLunarLeap()) : null,
                m.isAlive(),
                death ? m.getDeathDateSolar() : null,
                death ? LunarInfo.of(m.getDeathLunarDay(), m.getDeathLunarMonth(), m.getDeathLunarYear(), m.isDeathLunarLeap()) : null,
                death ? m.getBurialPlace() : null,
                profile ? m.getHometown() : null,
                bio ? m.getOccupation() : null,
                bio ? m.getBiography() : null,
                bio ? m.getAchievements() : null,
                profile ? m.getAvatarUrl() : null,
                contact ? m.getPhone() : null,
                contact ? m.getEmail() : null,
                contact ? m.getAddress() : null,
                canEdit ? PrivacySettings.of(m) : null,
                List.copyOf(mask),
                false,
                canEdit);
    }

    /**
     * Nut "an danh": nguoi thuoc huyet thong bi an nhung con chau van hien.
     * Chi giu nhung gi can de ve cay (gioi tinh, doi, thu tu, lien ket cha me).
     */
    public static MemberDto placeholder(FamilyMember m, Predicate<Long> refVisible) {
        return new MemberDto(
                m.getId(), m.getFamilyId(), null,
                HIDDEN_NAME, null, m.getGender(),
                m.getGeneration(), m.getBirthOrder(), null,
                ref(m.getFatherId(), refVisible), ref(m.getMotherId(), refVisible), true,
                null, null, true, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null,
                List.of(MaskedField.values()),
                true,
                false);
    }

    private static Long ref(Long id, Predicate<Long> visible) {
        return id != null && visible.test(id) ? id : null;
    }
}
