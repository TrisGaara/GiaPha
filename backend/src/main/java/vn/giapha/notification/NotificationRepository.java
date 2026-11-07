package vn.giapha.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndSeenFalse(Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    @Modifying
    @Query("update Notification n set n.seen = true where n.userId = :userId and n.seen = false")
    int markAllSeen(@Param("userId") Long userId);
}
