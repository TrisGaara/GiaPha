package vn.giapha.marital;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaritalRelationRepository extends JpaRepository<MaritalRelation, Long> {

    List<MaritalRelation> findByFamilyId(Long familyId);

    Optional<MaritalRelation> findByIdAndFamilyId(Long id, Long familyId);

    /** Moi quan he hon nhan cua mot nguoi (ca vai tro chong lan vo), sap theo thu tu ket hon */
    @Query("""
            select r from MaritalRelation r
            where r.husbandId = :memberId or r.wifeId = :memberId
            order by r.marriageOrder asc, r.id asc
            """)
    List<MaritalRelation> findAllOfMember(@Param("memberId") Long memberId);

    List<MaritalRelation> findByHusbandIdInOrWifeIdIn(Collection<Long> husbandIds, Collection<Long> wifeIds);

    boolean existsByHusbandIdAndWifeId(Long husbandId, Long wifeId);

    /** Nguoi vo dang co mot cuoc hon nhan ACTIVE khac (tru ban ghi excludeId) hay khong */
    @Query("""
            select count(r) > 0 from MaritalRelation r
            where r.wifeId = :wifeId and r.status = vn.giapha.marital.MarriageStatus.ACTIVE
              and (:excludeId is null or r.id <> :excludeId)
            """)
    boolean wifeHasOtherActiveMarriage(@Param("wifeId") Long wifeId, @Param("excludeId") Long excludeId);

    void deleteByHusbandIdOrWifeId(Long husbandId, Long wifeId);
}
