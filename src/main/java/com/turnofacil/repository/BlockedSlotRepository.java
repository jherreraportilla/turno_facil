package com.turnofacil.repository;

import com.turnofacil.model.BlockedSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, Long> {

    List<BlockedSlot> findByBusinessIdOrderByStartDateAsc(Long businessId);

    List<BlockedSlot> findByBusinessIdAndStartDateGreaterThanEqualOrderByStartDateAsc(Long businessId, LocalDate date);

    @Query("SELECT b FROM BlockedSlot b WHERE b.business.id = :businessId " +
           "AND b.startDate <= :date AND b.endDate >= :date")
    List<BlockedSlot> findBlocksForDate(@Param("businessId") Long businessId,
                                        @Param("date") LocalDate date);

    @Query("SELECT b FROM BlockedSlot b WHERE b.business.id = :businessId " +
           "AND b.startDate <= :date AND b.endDate >= :date " +
           "AND (b.allDay = true OR (b.startTime <= :time AND b.endTime > :time))")
    List<BlockedSlot> findConflictingBlocks(@Param("businessId") Long businessId,
                                            @Param("date") LocalDate date,
                                            @Param("time") LocalTime time);

    @Query("SELECT b FROM BlockedSlot b WHERE b.business.id = :businessId " +
           "AND ((b.startDate <= :endDate AND b.endDate >= :startDate))")
    List<BlockedSlot> findBlocksInDateRange(@Param("businessId") Long businessId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
}
