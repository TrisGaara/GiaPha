package vn.giapha.join;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {
    List<JoinRequest> findByFamilyIdAndStatusOrderByCreatedAtDesc(Long familyId, String status);

    List<JoinRequest> findByFamilyIdOrderByCreatedAtDesc(Long familyId);

    List<JoinRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByFamilyIdAndUserIdAndStatus(Long familyId, Long userId, String status);
}
