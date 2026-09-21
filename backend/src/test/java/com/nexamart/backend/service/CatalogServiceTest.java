package com.nexamart.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexamart.backend.domain.Category;
import com.nexamart.backend.domain.Product;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.CategoryRepository;
import com.nexamart.backend.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
  @Mock CategoryRepository categories;
  @Mock ProductRepository products;
  @Mock MappingService mapper;

  private CatalogService service;

  @BeforeEach
  void setUp() {
    service = new CatalogService(categories, products, mapper);
  }

  @Test
  void customerProductsUseTheActiveCatalogQuery() {
    when(products.customerSearch(eq(null), eq(null), any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    service.customerProducts(0, 100, null, null);

    verify(products).customerSearch(eq(null), eq(null), any(Pageable.class));
  }

  @Test
  void inactiveProductIsNotAvailableFromCustomerDetail() {
    Product product = new Product();
    product.setAvailable(false);
    when(products.findById(10L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> service.customerProductDetail(10L))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void adminCreatePersistsCategoryImageAndDiscountedProduct() {
    Category category = new Category();
    category.setName("Snacks");
    when(categories.findById(3L)).thenReturn(Optional.of(category));
    when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.createProduct(Map.of(
        "name", "Samosa",
        "categoryId", "3",
        "price", "20",
        "discountPercent", "10",
        "stock", "5",
        "imageUrl", "https://cdn.example.com/samosa.jpg"
    ));

    ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
    verify(products).save(product.capture());
    assertThat(product.getValue().getName()).isEqualTo("Samosa");
    assertThat(product.getValue().getCategory()).isSameAs(category);
    assertThat(product.getValue().getDiscount()).isEqualByComparingTo(new BigDecimal("2"));
    assertThat(product.getValue().getImageUrl())
        .isEqualTo("https://cdn.example.com/samosa.jpg");
  }
}
