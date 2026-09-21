package com.nexamart.backend.repository;

import com.nexamart.backend.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
  @Query("""
      select p from Product p
      where (:q is null
        or lower(p.name) like lower(concat('%', :q, '%'))
        or lower(coalesce(p.description, '')) like lower(concat('%', :q, '%'))
        or lower(coalesce(p.sku, '')) like lower(concat('%', :q, '%')))
        and (:active is null or p.available = :active)
        and (:categoryId is null or p.category.id = :categoryId)
      """)
  Page<Product> search(
      @Param("q") String query,
      @Param("active") Boolean active,
      @Param("categoryId") Long categoryId,
      Pageable pageable
  );

  @Query("""
      select p from Product p
      where p.available = true and p.category.active = true
        and (:q is null
          or lower(p.name) like lower(concat('%', :q, '%'))
          or lower(coalesce(p.description, '')) like lower(concat('%', :q, '%')))
        and (:categoryId is null or p.category.id = :categoryId)
      """)
  Page<Product> customerSearch(
      @Param("q") String query,
      @Param("categoryId") Long categoryId,
      Pageable pageable
  );
}
