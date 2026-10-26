package com.fashion.store.modules.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    @Query("SELECT img FROM ProductImage img WHERE img.product.productId = :productId ORDER BY img.isPrimary DESC, img.sortOrder ASC, img.createdAt ASC")
    List<ProductImage> findByProductIdOrdered(@Param("productId") UUID productId);

    @Query("SELECT img FROM ProductImage img WHERE img.product.productId = :productId AND (img.variant.variantId = :variantId OR img.variant IS NULL) ORDER BY img.isPrimary DESC, img.sortOrder ASC, img.createdAt ASC")
    List<ProductImage> findByProductIdAndVariantIdOrdered(@Param("productId") UUID productId, @Param("variantId") UUID variantId);

    Optional<ProductImage> findByProductProductIdAndIsPrimaryTrue(UUID productId);

    @Query("SELECT COALESCE(MAX(img.sortOrder), -1) FROM ProductImage img WHERE img.product.productId = :productId")
    Integer findMaxSortOrderByProductId(@Param("productId") UUID productId);

    long countByProductProductId(UUID productId);

    @Modifying
    @Query("UPDATE ProductImage img SET img.sortOrder = img.sortOrder + 1 WHERE img.product.productId = :productId AND img.sortOrder >= :fromOrder")
    void shiftSortOrderFrom(@Param("productId") UUID productId, @Param("fromOrder") int fromOrder);


    @Modifying
    @Query("UPDATE ProductImage img SET img.isPrimary = false WHERE img.product.productId = :productId")
    void resetPrimaryImageForProduct(@Param("productId") UUID productId);
}
