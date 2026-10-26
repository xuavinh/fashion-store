package com.fashion.store.modules.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductProductId(UUID productId);

    Optional<ProductVariant> findBySkuCode(String skuCode);

    boolean existsBySkuCode(String skuCode);
}
