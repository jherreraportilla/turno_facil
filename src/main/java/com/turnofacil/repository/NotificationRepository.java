package com.turnofacil.repository;

import com.turnofacil.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<Notification> findTop10ByBusinessIdOrderByCreatedAtDesc(Long businessId);

    long countByBusinessIdAndReadFalse(Long businessId);

    List<Notification> findByBusinessIdAndReadFalseOrderByCreatedAtDesc(Long businessId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.business.id = :businessId AND n.read = false")
    int markAllAsReadByBusinessId(@Param("businessId") Long businessId);
}
