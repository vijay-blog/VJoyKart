package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.ActionRequest;
import com.nexamart.backend.api.ApiModels.CategoryResponse;
import com.nexamart.backend.api.ApiModels.PageResponse;
import com.nexamart.backend.api.ApiModels.ProductResponse;
import com.nexamart.backend.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/catalog")
public class AdminCatalogController {
  private final CatalogService catalogService;

  public AdminCatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/categories")
  PageResponse<CategoryResponse> categories(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active
  ) {
    return catalogService.categories(page, pageSize, search, active);
  }

  @GetMapping("/categories/options")
  List<CatalogService.CategoryOption> categoryOptions() {
    return catalogService.categoryOptions();
  }

  @GetMapping("/categories/{id}")
  CategoryResponse category(@PathVariable Long id) {
    return catalogService.categoryDetail(id);
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  CategoryResponse createCategory(@RequestBody Map<String, Object> body) {
    return catalogService.createCategory(body);
  }

  @PatchMapping("/categories/{id}")
  CategoryResponse updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return catalogService.updateCategory(id, body);
  }

  @PatchMapping("/categories/{id}/actions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void categoryAction(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
    catalogService.categoryAction(id, request);
  }

  @GetMapping("/products")
  PageResponse<ProductResponse> products(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String sort
  ) {
    return catalogService.products(page, pageSize, search, active, categoryId, sort);
  }

  @GetMapping("/products/{id}")
  ProductResponse product(@PathVariable Long id) {
    return catalogService.productDetail(id);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  ProductResponse createProduct(@RequestBody Map<String, Object> body) {
    return catalogService.createProduct(body);
  }

  @PatchMapping("/products/{id}")
  ProductResponse updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return catalogService.updateProduct(id, body);
  }


  @PostMapping("/products/{id}/image")
  ProductResponse uploadProductImage(
      @PathVariable Long id,
      @RequestPart("image") MultipartFile image
  ) {
    return catalogService.uploadProductImage(id, image);
  }

  @PatchMapping("/products/{id}/actions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void productAction(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
    catalogService.productAction(id, request);
  }
}
