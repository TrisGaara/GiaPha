package vn.giapha.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long>, JpaSpecificationExecutor<FamilyMember> {

    List<FamilyMember> findByFamilyIdOrderByGenerationAscBirthOrderAscIdAsc(Long familyId);

    Optional<FamilyMember> findByIdAndFamilyId(Long id, Long familyId);

    List<FamilyMember> findByFamilyIdAndUserId(Long familyId, Long userId);

    List<FamilyMember> findByFamilyIdAndAliveFalseAndDeathLunarDayNotNull(Long familyId);

    long countByFamilyId(Long familyId);

    boolean existsByFatherIdOrMotherId(Long fatherId, Long motherId);

    /** Con chung cua mot cap vo chong, sap theo thu tu sinh (Spring Data method) */
    List<FamilyMember> findByFatherIdAndMotherIdOrderByBirthOrderAscIdAsc(Long fatherId, Long motherId);

    boolean existsByFatherIdAndMotherId(Long fatherId, Long motherId);

    /**
     * Tat ca con cua mot nguoi (theo cha hoac theo me), sap theo thu tu sinh.
     * Dung chi muc idx_family_members_father_birth_order / idx_family_members_mother_birth_order.
     */
    @Query("""
            select c from FamilyMember c
            where c.fatherId = :parentId or c.motherId = :parentId
            order by c.birthOrder asc, c.birthDateSolar asc nulls last, c.id asc
            """)
    List<FamilyMember> findChildrenOfParent(@Param("parentId") Long parentId);

    /**
     * Ban PostgreSQL thuan: con cua mot cap cha - me (me co the NULL = khong ro me), sap theo thu tu sinh.
     * {@code IS NOT DISTINCT FROM} so sanh duoc ca gia tri NULL.
     */
    @Query(value = """
            SELECT * FROM family_members
            WHERE father_id IS NOT DISTINCT FROM CAST(:fatherId AS BIGINT)
              AND mother_id IS NOT DISTINCT FROM CAST(:motherId AS BIGINT)
            ORDER BY birth_order ASC, birth_date_solar ASC NULLS LAST, id ASC
            """, nativeQuery = true)
    List<FamilyMember> findChildrenOfCouple(@Param("fatherId") Long fatherId, @Param("motherId") Long motherId);

    /** Kiem tra anh chi em ruot / cung cha khac me / cung me khac cha */
    @Query("""
            select count(a) > 0 from FamilyMember a, FamilyMember b
            where a.id = :x and b.id = :y
              and ((a.fatherId is not null and a.fatherId = b.fatherId)
                or (a.motherId is not null and a.motherId = b.motherId))
            """)
    boolean areSiblings(@Param("x") Long x, @Param("y") Long y);

    /** Toan bo con chau (ke ca chinh nguoi do) - dung CTE de quy cua PostgreSQL */
    @Query(value = """
            WITH RECURSIVE tree AS (
                SELECT id FROM family_members WHERE id = :rootId
                UNION
                SELECT m.id FROM family_members m JOIN tree t ON m.father_id = t.id OR m.mother_id = t.id
            )
            SELECT id FROM tree
            """, nativeQuery = true)
    List<Long> findDescendantIds(@Param("rootId") Long rootId);

    /** Toan bo to tien (ke ca chinh nguoi do) */
    @Query(value = """
            WITH RECURSIVE anc AS (
                SELECT id, father_id, mother_id FROM family_members WHERE id = :memberId
                UNION
                SELECT m.id, m.father_id, m.mother_id FROM family_members m
                JOIN anc a ON m.id = a.father_id OR m.id = a.mother_id
            )
            SELECT id FROM anc
            """, nativeQuery = true)
    List<Long> findAncestorIds(@Param("memberId") Long memberId);
}
