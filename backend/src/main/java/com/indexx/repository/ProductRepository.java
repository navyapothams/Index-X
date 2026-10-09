package com.indexx.repository;

import com.indexx.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(value = """
            SELECT p.* FROM products p
             WHERE (:query IS NULL OR :query = ''
                    OR to_tsvector('english', p.name) @@ plainto_tsquery('english', :query)
                    OR LOWER(p.sku) LIKE LOWER(CONCAT(:query, '%')))
               AND (:categoryId IS NULL OR p.category_id = :categoryId)
               AND (:subcategoryId IS NULL OR p.subcategory_id = :subcategoryId)
               AND (:brandId IS NULL OR p.brand_id = :brandId)
               AND (:active IS NULL OR p.active = :active)
               AND (:minPrice IS NULL OR p.selling_price >= :minPrice)
               AND (:maxPrice IS NULL OR p.selling_price <= :maxPrice)
             ORDER BY
               CASE WHEN :sortField = 'name' AND :sortDirection = 'ASC' THEN p.name END ASC,
               CASE WHEN :sortField = 'name' AND :sortDirection = 'DESC' THEN p.name END DESC,
               CASE WHEN :sortField = 'sku' AND :sortDirection = 'ASC' THEN p.sku END ASC,
               CASE WHEN :sortField = 'sku' AND :sortDirection = 'DESC' THEN p.sku END DESC,
               CASE WHEN :sortField = 'sellingPrice' AND :sortDirection = 'ASC' THEN p.selling_price END ASC,
               CASE WHEN :sortField = 'sellingPrice' AND :sortDirection = 'DESC' THEN p.selling_price END DESC,
               CASE WHEN :sortField = 'costPrice' AND :sortDirection = 'ASC' THEN p.cost_price END ASC,
               CASE WHEN :sortField = 'costPrice' AND :sortDirection = 'DESC' THEN p.cost_price END DESC,
               CASE WHEN :sortField = 'createdAt' AND :sortDirection = 'ASC' THEN p.created_at END ASC,
               CASE WHEN :sortField = 'createdAt' AND :sortDirection = 'DESC' THEN p.created_at END DESC,
               p.id ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM products p
             WHERE (:query IS NULL OR :query = ''
                    OR to_tsvector('english', p.name) @@ plainto_tsquery('english', :query)
                    OR LOWER(p.sku) LIKE LOWER(CONCAT(:query, '%')))
               AND (:categoryId IS NULL OR p.category_id = :categoryId)
               AND (:subcategoryId IS NULL OR p.subcategory_id = :subcategoryId)
               AND (:brandId IS NULL OR p.brand_id = :brandId)
               AND (:active IS NULL OR p.active = :active)
               AND (:minPrice IS NULL OR p.selling_price >= :minPrice)
               AND (:maxPrice IS NULL OR p.selling_price <= :maxPrice)
            """,
            nativeQuery = true)
    Page<Product> search(@Param("query") String query,
                         @Param("categoryId") Long categoryId,
                         @Param("subcategoryId") Long subcategoryId,
                         @Param("brandId") Long brandId,
                         @Param("active") Boolean active,
                         @Param("minPrice") java.math.BigDecimal minPrice,
                         @Param("maxPrice") java.math.BigDecimal maxPrice,
                         @Param("sortField") String sortField,
                         @Param("sortDirection") String sortDirection,
                         Pageable pageable);

    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
}
