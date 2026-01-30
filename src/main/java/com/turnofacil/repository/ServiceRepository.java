package com.turnofacil.repository;

import com.turnofacil.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByBusinessIdOrderByDisplayOrderAsc(Long businessId);

    List<Service> findByBusinessIdAndActiveOrderByDisplayOrderAsc(Long businessId, boolean active);

    int countByBusinessId(Long businessId);
}
