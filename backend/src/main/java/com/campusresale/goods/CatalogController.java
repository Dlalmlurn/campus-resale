package com.campusresale.goods;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogRepository catalogRepository;

    public CatalogController(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    @GetMapping("/categories")
    public List<CategorySummary> categories() {
        return catalogRepository.enabledCategories();
    }

    @GetMapping("/tags")
    public List<TagSummary> tags() {
        return catalogRepository.enabledTags();
    }

    @GetMapping("/campus-places")
    public List<CampusPlaceSummary> campusPlaces() {
        return catalogRepository.enabledCampusPlaces();
    }
}
