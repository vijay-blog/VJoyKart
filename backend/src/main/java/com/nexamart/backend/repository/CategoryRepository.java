package com.nexamart.backend.repository;

import com.nexamart.backend.domain.Category;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
  Optional<Category> findByNameIgnoreCase(String name);

  @Query("""
      select c from Category c
      where (:search is null or lower(c.name) like lower(concat('%', :search, '%')))
        and (:active is null or c.active = :active)
      """)
  Page<Category> search(
      @Param("search") String search,
      @Param("active") Boolean active,
      Pageable pageable
  );
}
