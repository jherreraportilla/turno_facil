package com.turnofacil.service;

import com.turnofacil.dto.BlockedSlotDto;
import com.turnofacil.exception.AccessDeniedException;
import com.turnofacil.exception.ResourceNotFoundException;
import com.turnofacil.model.BlockedSlot;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.BlockedSlotType;
import com.turnofacil.repository.BlockedSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class BlockedSlotService {

    private final BlockedSlotRepository blockedSlotRepository;

    public BlockedSlotService(BlockedSlotRepository blockedSlotRepository) {
        this.blockedSlotRepository = blockedSlotRepository;
    }

    @Transactional(readOnly = true)
    public List<BlockedSlot> getBlockedSlotsByBusiness(Long businessId) {
        return blockedSlotRepository.findByBusinessIdOrderByStartDateAsc(businessId);
    }

    @Transactional(readOnly = true)
    public List<BlockedSlot> getFutureBlockedSlots(Long businessId) {
        // Devuelve bloqueos que aun no han terminado (endDate >= hoy)
        // Esto incluye bloqueos que empezaron antes pero siguen activos
        return blockedSlotRepository.findByBusinessIdAndEndDateGreaterThanEqualOrderByStartDateAsc(
                businessId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public BlockedSlot getBlockedSlotById(Long id) {
        return blockedSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo", id));
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(Long businessId, LocalDate date, LocalTime time) {
        List<BlockedSlot> conflicts = blockedSlotRepository.findConflictingBlocks(businessId, date, time);
        return !conflicts.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<BlockedSlot> getBlocksForDate(Long businessId, LocalDate date) {
        return blockedSlotRepository.findBlocksForDate(businessId, date);
    }

    @Transactional(readOnly = true)
    public List<BlockedSlot> getBlocksInRange(Long businessId, LocalDate startDate, LocalDate endDate) {
        return blockedSlotRepository.findBlocksInDateRange(businessId, startDate, endDate);
    }

    @Transactional
    public BlockedSlot createBlockedSlot(User business, BlockedSlotDto dto) {
        BlockedSlot blockedSlot = new BlockedSlot();
        blockedSlot.setBusiness(business);
        blockedSlot.setTitle(dto.title());
        blockedSlot.setStartDate(dto.startDate());
        blockedSlot.setEndDate(dto.endDate() != null ? dto.endDate() : dto.startDate());
        blockedSlot.setStartTime(dto.startTime());
        blockedSlot.setEndTime(dto.endTime());
        blockedSlot.setAllDay(dto.allDay());
        blockedSlot.setType(dto.type() != null ? dto.type() : BlockedSlotType.CUSTOM);
        blockedSlot.setNotes(dto.notes());

        return blockedSlotRepository.save(blockedSlot);
    }

    @Transactional
    public BlockedSlot updateBlockedSlot(Long id, User business, BlockedSlotDto dto) {
        BlockedSlot blockedSlot = blockedSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo", id));

        if (!blockedSlot.getBusiness().getId().equals(business.getId())) {
            throw new AccessDeniedException("Bloqueo", id);
        }

        blockedSlot.setTitle(dto.title());
        blockedSlot.setStartDate(dto.startDate());
        blockedSlot.setEndDate(dto.endDate() != null ? dto.endDate() : dto.startDate());
        blockedSlot.setStartTime(dto.startTime());
        blockedSlot.setEndTime(dto.endTime());
        blockedSlot.setAllDay(dto.allDay());
        blockedSlot.setType(dto.type() != null ? dto.type() : BlockedSlotType.CUSTOM);
        blockedSlot.setNotes(dto.notes());

        return blockedSlotRepository.save(blockedSlot);
    }

    @Transactional
    public void deleteBlockedSlot(Long id, User business) {
        BlockedSlot blockedSlot = blockedSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo", id));

        if (!blockedSlot.getBusiness().getId().equals(business.getId())) {
            throw new AccessDeniedException("Bloqueo", id);
        }

        blockedSlotRepository.delete(blockedSlot);
    }
}
