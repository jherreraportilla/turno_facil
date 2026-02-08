package com.turnofacil.repository;

import com.turnofacil.model.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    List<Testimonial> findByBusinessIdAndActiveTrueOrderByDisplayOrderAsc(Long businessId);

    List<Testimonial> findByBusinessIdOrderByDisplayOrderAsc(Long businessId);

    int countByBusinessId(Long businessId);
}
