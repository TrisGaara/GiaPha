package vn.giapha.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByFamilyIdOrderByCreatedAtDesc(Long familyId);

    List<Event> findByFamilyId(Long familyId);

    Optional<Event> findByIdAndFamilyId(Long id, Long familyId);
}
