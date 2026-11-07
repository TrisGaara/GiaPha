package vn.giapha.family;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {
    Optional<Family> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    List<Family> findByStatus(FamilyStatus status);

    List<Family> findAllByOrderByCreatedAtDesc();
}
