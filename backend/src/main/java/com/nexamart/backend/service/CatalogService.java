package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.ActionRequest;
import com.nexamart.backend.api.ApiModels.CategoryResponse;
import com.nexamart.backend.api.ApiModels.PageResponse;
import com.nexamart.backend.api.ApiModels.ProductResponse;
import com.nexamart.backend.domain.Category;
import com.nexamart.backend.domain.Product;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.CategoryRepository;
import com.nexamart.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final CategoryRepository categories;
  private final ProductRepository products;
  private final MappingService mapper;

  public CatalogService(
      CategoryRepository categories,
      ProductRepository products,
      MappingService mapper
  ) {
    this.categories = categories;
    this.products = products;
    this.mapper = mapper;
  }

  public PageResponse<CategoryResponse> customerCategories(int page, int size, String search) {
    Page<Category> result = categories.search(
        clean(search),
        true,
        pageRequest(page, size, Sort.by("sortOrder").ascending().and(Sort.by("name")))
    );
    return categoryPage(result, true);
  }

  public PageResponse<ProductResponse> customerProducts(
      int page,
      int size,
      String search,
      Long categoryId
  ) {
    Page<Product> result = products.customerSearch(
        clean(search),
        categoryId,
        pageRequest(page, size, Sort.by("name"))
    );
    return productPage(result);
  }

  public ProductResponse customerProductDetail(Long id) {
    Product product = product(id);
    if (!product.isAvailable() || !product.getCategory().isActive()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Product not found.");
    }
    return mapper.product(product);
  }

  public PageResponse<CategoryResponse> categories(
      int page,
      int size,
      String search,
      Boolean active
  ) {
    Page<Category> result = categories.search(
        clean(search),
        active,
        pageRequest(page, size, Sort.by("sortOrder").ascending().and(Sort.by("name")))
    );
    return categoryPage(result, false);
  }

  public PageResponse<ProductResponse> products(
      int page,
      int size,
      String search,
      Boolean active,
      Long categoryId,
      String sort
  ) {
    Page<Product> result = products.search(
        clean(search),
        active,
        categoryId,
        pageRequest(page, size, productSort(sort))
    );
    return productPage(result);
  }

  public CategoryResponse categoryDetail(Long id) {
    Category category = category(id);
    return mapper.category(category, productCount(category.getId(), false));
  }

  @Transactional
  public CategoryResponse createCategory(Map<String, Object> body) {
    String name = requiredText(body, "name", "Category name is required.");
    if (categories.findByNameIgnoreCase(name).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "Category already exists.");
    }
    Category category = new Category();
    applyCategory(category, body, true);
    return mapper.category(categories.save(category), 0);
  }

  @Transactional
  public CategoryResponse updateCategory(Long id, Map<String, Object> body) {
    Category category = category(id);
    if (body.containsKey("name")) {
      String name = requiredText(body, "name", "Category name is required.");
      categories.findByNameIgnoreCase(name)
          .filter(existing -> !existing.getId().equals(id))
          .ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "Category already exists.");
          });
    }
    applyCategory(category, body, false);
    category.touch();
    Category saved = categories.save(category);
    return mapper.category(saved, productCount(saved.getId(), false));
  }

  @Transactional
  public void categoryAction(Long id, ActionRequest request) {
    Category category = category(id);
    switch (request.action().toUpperCase()) {
      case "ACTIVATE" -> category.setActive(true);
      case "DEACTIVATE" -> category.setActive(false);
      case "DELETE" -> {
        if (productCount(id, false) > 0) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "Category has products. Deactivate it instead."
          );
        }
        categories.delete(category);
        return;
      }
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported category action.");
    }
    category.touch();
    categories.save(category);
  }

  public ProductResponse productDetail(Long id) {
    return mapper.product(product(id));
  }

  @Transactional
  public ProductResponse createProduct(Map<String, Object> body) {
    Product product = new Product();
    applyProduct(product, body, true);
    return mapper.product(products.save(product));
  }

  @Transactional
  public ProductResponse updateProduct(Long id, Map<String, Object> body) {
    Product product = product(id);
    applyProduct(product, body, false);
    product.touch();
    return mapper.product(products.save(product));
  }

  @Transactional
  public void productAction(Long id, ActionRequest request) {
    Product product = product(id);
    switch (request.action().toUpperCase()) {
      case "ACTIVATE" -> product.setAvailable(true);
      case "DEACTIVATE" -> product.setAvailable(false);
      case "DELETE" -> product.setAvailable(false);
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported product action.");
    }
    product.touch();
    products.save(product);
  }

  public List<CategoryOption> categoryOptions() {
    return categories.search(
            null,
            true,
            Pageable.unpaged(Sort.by("sortOrder").ascending().and(Sort.by("name")))
        ).stream()
        .map(category -> new CategoryOption(category.getId().toString(), category.getName()))
        .toList();
  }

  private void applyCategory(Category category, Map<String, Object> body, boolean creating) {
    if (creating || body.containsKey("name")) {
      category.setName(requiredText(body, "name", "Category name is required."));
    }
    if (body.containsKey("description")) category.setDescription(optionalText(body.get("description")));
    if (body.containsKey("imageUrl")) category.setImageUrl(optionalText(body.get("imageUrl")));
    if (body.containsKey("sortOrder")) category.setSortOrder(nonNegativeInt(body.get("sortOrder"), "sortOrder"));
    if (body.containsKey("active")) category.setActive(booleanValue(body.get("active"), "active"));
  }

  private void applyProduct(Product product, Map<String, Object> body, boolean creating) {
    if (creating || body.containsKey("name")) {
      product.setName(requiredText(body, "name", "Product name is required."));
    }
    if (creating || body.containsKey("categoryId")) {
      Object categoryId = body.get("categoryId");
      if (categoryId == null || categoryId.toString().isBlank()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Category is required.");
      }
      try {
        product.setCategory(category(Long.valueOf(categoryId.toString())));
      } catch (NumberFormatException exception) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid category ID.");
      }
    }
    if (creating || body.containsKey("price")) {
      product.setPrice(nonNegativeDecimal(body.get("price"), "price"));
    }
    if (body.containsKey("discountPercent")) {
      BigDecimal percentage = nonNegativeDecimal(body.get("discountPercent"), "discountPercent");
      if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "discountPercent must be between 0 and 100."
        );
      }
      product.setDiscount(
          product.getPrice().multiply(percentage).divide(BigDecimal.valueOf(100))
      );
    } else if (body.containsKey("discount")) {
      product.setDiscount(nonNegativeDecimal(body.get("discount"), "discount"));
    }
    if (body.containsKey("description")) product.setDescription(optionalText(body.get("description")));
    if (body.containsKey("sku")) product.setSku(optionalText(body.get("sku")));
    if (body.containsKey("unit")) product.setUnit(optionalText(body.get("unit")));
    if (body.containsKey("stock")) product.setStock(nonNegativeInt(body.get("stock"), "stock"));
    if (body.containsKey("available")) {
      product.setAvailable(booleanValue(body.get("available"), "available"));
    }
    if (body.containsKey("imageUrl")) product.setImageUrl(optionalText(body.get("imageUrl")));
    if (product.getDiscount() != null && product.getDiscount().compareTo(product.getPrice()) > 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Discount cannot exceed product price.");
    }
  }

  private PageResponse<CategoryResponse> categoryPage(Page<Category> page, boolean activeProductsOnly) {
    List<CategoryResponse> content = page.getContent().stream()
        .map(category -> mapper.category(
            category,
            productCount(category.getId(), activeProductsOnly)
        ))
        .toList();
    return new PageResponse<>(
        content,
        page.getNumber(),
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext()
    );
  }

  private PageResponse<ProductResponse> productPage(Page<Product> page) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper::product).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.hasNext()
    );
  }

  private int productCount(Long categoryId, boolean activeOnly) {
    return (int) products.search(
        null,
        activeOnly ? true : null,
        categoryId,
        Pageable.unpaged()
    ).getTotalElements();
  }

  private Category category(Long id) {
    return categories.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
  }

  private Product product(Long id) {
    return products.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
  }

  private PageRequest pageRequest(int page, int size, Sort sort) {
    return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sort);
  }

  private Sort productSort(String value) {
    if (value == null) return Sort.by(Sort.Direction.DESC, "createdAt");
    return switch (value.toUpperCase()) {
      case "NAME_A_Z" -> Sort.by("name");
      case "PRICE_LOW_TO_HIGH" -> Sort.by("price");
      case "PRICE_HIGH_TO_LOW" -> Sort.by(Sort.Direction.DESC, "price");
      default -> Sort.by(Sort.Direction.DESC, "createdAt");
    };
  }

  private String clean(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private String requiredText(Map<String, Object> body, String key, String message) {
    String value = optionalText(body.get(key));
    if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, message);
    return value;
  }

  private String optionalText(Object value) {
    if (value == null || value.toString().isBlank()) return null;
    return value.toString().trim();
  }

  private BigDecimal nonNegativeDecimal(Object value, String field) {
    try {
      BigDecimal parsed = new BigDecimal(value == null ? "" : value.toString());
      if (parsed.signum() < 0) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be a non-negative number.");
    }
  }

  private int nonNegativeInt(Object value, String field) {
    try {
      int parsed = Integer.parseInt(value == null ? "" : value.toString());
      if (parsed < 0) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be a non-negative whole number.");
    }
  }

  private boolean booleanValue(Object value, String field) {
    if (value instanceof Boolean booleanValue) return booleanValue;
    if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
    if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
    throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be true or false.");
  }

  public record CategoryOption(String categoryId, String name) {}
}
