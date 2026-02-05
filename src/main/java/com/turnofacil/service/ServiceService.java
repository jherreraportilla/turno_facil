package com.turnofacil.service;

import com.turnofacil.dto.ServiceDto;
import com.turnofacil.exception.AccessDeniedException;
import com.turnofacil.exception.ResourceNotFoundException;
import com.turnofacil.model.Service;
import com.turnofacil.model.User;
import com.turnofacil.repository.ServiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final PlanLimitsService planLimitsService;

    public ServiceService(ServiceRepository serviceRepository,
                          PlanLimitsService planLimitsService) {
        this.serviceRepository = serviceRepository;
        this.planLimitsService = planLimitsService;
    }

    @Transactional(readOnly = true)
    public List<Service> getServicesByBusiness(Long businessId) {
        return serviceRepository.findByBusinessIdOrderByDisplayOrderAsc(businessId);
    }

    @Transactional(readOnly = true)
    public List<Service> getActiveServicesByBusiness(Long businessId) {
        return serviceRepository.findByBusinessIdAndActiveOrderByDisplayOrderAsc(businessId, true);
    }

    @Transactional(readOnly = true)
    public Service getServiceById(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio", id));
    }

    @Transactional
    public Service createService(User business, ServiceDto dto) {
        if (!planLimitsService.canCreateService(business.getId())) {
            throw new com.turnofacil.exception.PlanLimitExceededException(
                    "Has alcanzado el límite de 3 servicios del plan gratuito. Actualiza tu plan para servicios ilimitados.");
        }
        int count = serviceRepository.countByBusinessId(business.getId());

        Service service = new Service();
        service.setBusiness(business);
        service.setName(dto.name());
        service.setDescription(dto.description());
        service.setDurationMinutes(dto.durationMinutes() != null ? dto.durationMinutes() : 30);
        service.setPrice(dto.price());
        service.setColor(dto.color() != null ? dto.color() : "#6366F1");
        service.setIcon(dto.icon() != null ? dto.icon() : "bi-calendar-check");
        service.setActive(dto.active());
        service.setDisplayOrder(dto.displayOrder() != null ? dto.displayOrder() : count);

        return serviceRepository.save(service);
    }

    @Transactional
    public Service updateService(Long id, User business, ServiceDto dto) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio", id));

        if (!service.getBusiness().getId().equals(business.getId())) {
            throw new AccessDeniedException("Servicio", id);
        }

        service.setName(dto.name());
        service.setDescription(dto.description());
        service.setDurationMinutes(dto.durationMinutes());
        service.setPrice(dto.price());
        service.setColor(dto.color());
        service.setIcon(dto.icon());
        service.setActive(dto.active());
        service.setDisplayOrder(dto.displayOrder());

        return serviceRepository.save(service);
    }

    @Transactional
    public void deleteService(Long id, User business) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio", id));

        if (!service.getBusiness().getId().equals(business.getId())) {
            throw new AccessDeniedException("Servicio", id);
        }

        serviceRepository.delete(service);
    }

    @Transactional
    public void toggleActive(Long id, User business) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio", id));

        if (!service.getBusiness().getId().equals(business.getId())) {
            throw new AccessDeniedException("Servicio", id);
        }

        service.setActive(!service.isActive());
        serviceRepository.save(service);
    }

    @Transactional
    public void createDefaultServices(User business) {
        // Servicio 1: Corte de cabello
        Service service1 = new Service();
        service1.setBusiness(business);
        service1.setName("Corte de cabello");
        service1.setDescription("Corte de cabello clasico o moderno");
        service1.setDurationMinutes(30);
        service1.setPrice(new BigDecimal("25.00"));
        service1.setColor("#6366F1");
        service1.setIcon("bi-scissors");
        service1.setActive(true);
        service1.setDisplayOrder(0);
        serviceRepository.save(service1);

        // Servicio 2: Corte + Barba
        Service service2 = new Service();
        service2.setBusiness(business);
        service2.setName("Corte + Barba");
        service2.setDescription("Corte de cabello y arreglo de barba completo");
        service2.setDurationMinutes(60);
        service2.setPrice(new BigDecimal("40.00"));
        service2.setColor("#8B5CF6");
        service2.setIcon("bi-person-badge");
        service2.setActive(true);
        service2.setDisplayOrder(1);
        serviceRepository.save(service2);

        // Servicio 3: Tratamiento completo
        Service service3 = new Service();
        service3.setBusiness(business);
        service3.setName("Tratamiento completo");
        service3.setDescription("Corte, barba, lavado y tratamiento capilar");
        service3.setDurationMinutes(90);
        service3.setPrice(new BigDecimal("70.00"));
        service3.setColor("#EC4899");
        service3.setIcon("bi-stars");
        service3.setActive(true);
        service3.setDisplayOrder(2);
        serviceRepository.save(service3);
    }
}
