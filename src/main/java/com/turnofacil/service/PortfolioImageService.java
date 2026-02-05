package com.turnofacil.service;

import com.turnofacil.exception.AccessDeniedException;
import com.turnofacil.exception.ResourceNotFoundException;
import com.turnofacil.model.BusinessConfig;
import com.turnofacil.model.PortfolioImage;
import com.turnofacil.repository.PortfolioImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioImageService {

    private final PortfolioImageRepository portfolioImageRepository;

    public PortfolioImageService(PortfolioImageRepository portfolioImageRepository) {
        this.portfolioImageRepository = portfolioImageRepository;
    }

    @Transactional(readOnly = true)
    public List<PortfolioImage> getByBusinessConfig(Long businessConfigId) {
        return portfolioImageRepository.findByBusinessConfigIdOrderByDisplayOrderAsc(businessConfigId);
    }

    @Transactional
    public PortfolioImage addImage(BusinessConfig config, String imageUrl, String caption) {
        int count = portfolioImageRepository.countByBusinessConfigId(config.getId());

        PortfolioImage image = new PortfolioImage();
        image.setBusinessConfig(config);
        image.setImageUrl(imageUrl);
        image.setCaption(caption);
        image.setDisplayOrder(count);

        return portfolioImageRepository.save(image);
    }

    @Transactional
    public PortfolioImage updateImage(Long id, BusinessConfig config, String imageUrl, String caption, Integer displayOrder) {
        PortfolioImage image = portfolioImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen", id));

        if (!image.getBusinessConfig().getId().equals(config.getId())) {
            throw new AccessDeniedException("Imagen", id);
        }

        image.setImageUrl(imageUrl);
        image.setCaption(caption);
        if (displayOrder != null) {
            image.setDisplayOrder(displayOrder);
        }

        return portfolioImageRepository.save(image);
    }

    @Transactional
    public void deleteImage(Long id, BusinessConfig config) {
        PortfolioImage image = portfolioImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen", id));

        if (!image.getBusinessConfig().getId().equals(config.getId())) {
            throw new AccessDeniedException("Imagen", id);
        }

        portfolioImageRepository.delete(image);
    }
}
