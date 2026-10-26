package com.fashion.store.modules.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // Tìm kiếm Full-Text Search
    @Query(value = """
            SELECT p.* FROM products p
            WHERE (:isActive IS NULL OR p.is_active = :isActive)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:brandId IS NULL OR p.brand_id = :brandId)
              AND (:gender IS NULL OR p.gender = :gender OR p.gender = 'UNISEX')
              AND (
                  p.search_vector @@ plainto_tsquery('simple', unaccent(:search)) OR
                  p.name ILIKE CONCAT('%', :search, '%')
              )
              AND (:minPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price >= :minPrice
              ))
              AND (:maxPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price <= :maxPrice
              ))
            ORDER BY ts_rank(p.search_vector, plainto_tsquery('simple', unaccent(:search))) DESC, p.created_at DESC
            """,
            countQuery = """
            SELECT count(DISTINCT p.product_id) FROM products p
            WHERE (:isActive IS NULL OR p.is_active = :isActive)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:brandId IS NULL OR p.brand_id = :brandId)
              AND (:gender IS NULL OR p.gender = :gender OR p.gender = 'UNISEX')
              AND (
                  p.search_vector @@ plainto_tsquery('simple', unaccent(:search)) OR
                  p.name ILIKE CONCAT('%', :search, '%')
              )
              AND (:minPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price >= :minPrice
              ))
              AND (:maxPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price <= :maxPrice
              ))
            """,
            nativeQuery = true)
    Page<Product> searchFullTextWithRelevance(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            @Param("gender") String gender,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );

    // Lọc danh sách sản phẩm khi KHÔNG có từ khóa search
    @Query(value = """
            SELECT p.* FROM products p
            WHERE (:isActive IS NULL OR p.is_active = :isActive)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:brandId IS NULL OR p.brand_id = :brandId)
              AND (:gender IS NULL OR p.gender = :gender OR p.gender = 'UNISEX')
              AND (:minPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price >= :minPrice
              ))
              AND (:maxPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price <= :maxPrice
              ))
            """,
            countQuery = """
            SELECT count(DISTINCT p.product_id) FROM products p
            WHERE (:isActive IS NULL OR p.is_active = :isActive)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:brandId IS NULL OR p.brand_id = :brandId)
              AND (:gender IS NULL OR p.gender = :gender OR p.gender = 'UNISEX')
              AND (:minPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price >= :minPrice
              ))
              AND (:maxPrice IS NULL OR EXISTS (
                  SELECT 1 FROM product_variants pv WHERE pv.product_id = p.product_id AND pv.price <= :maxPrice
              ))
            """,
            nativeQuery = true)
    Page<Product> findAllFiltered(
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            @Param("gender") String gender,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
