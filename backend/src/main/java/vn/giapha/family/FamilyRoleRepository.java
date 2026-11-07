package vn.giapha.family;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyRoleRepository extends JpaRepository<FamilyRole, Long> {
    Optional<FamilyRole> findByFamilyIdAndUserId(Long familyId, Long userId);

    List<FamilyRole> findByUserId(Long userId);

    List<FamilyRole> findByFamilyId(Long familyId);

    List<FamilyRole> findByFamilyIdAndRoleIn(Long familyId, List<FamilyRoleType> roles);

    long countByFamilyId(Long familyId);
}
