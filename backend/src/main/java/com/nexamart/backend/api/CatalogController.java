package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.CategoryResponse;
import com.nexamart.backend.api.ApiModels.PageResponse;
import com.nexamart.backend.api.ApiModels.ProductResponse;
import com.nexamart.backend.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/categories")
  PageResponse<CategoryResponse> categories(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int pageSize,
      @RequestParam(required = false) String search
  ) {
    return catalogService.customerCategories(page, pageSize, search);
  }

  @GetMapping("/products")
  PageResponse<ProductResponse> products(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int pageSize,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long categoryId
  ) {
    return catalogService.customerProducts(page, pageSize, search, categoryId);
  }

  @GetMapping("/products/{id}")
  ProductResponse product(@PathVariable Long id) {
    return catalogService.customerProductDetail(id);
  }


  @GetMapping("/products/{id}/image")
  ResponseEntity<byte[]> productImage(@PathVariable Long id) {
    return catalogService.productImage(id)
        .map(image -> ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .contentType(MediaType.parseMediaType(image.getContentType()))
            .body(image.getImageData()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
