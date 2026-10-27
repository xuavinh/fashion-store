package com.fashion.store.modules.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByCartCartIdAndVariantVariantId(UUID cartId, UUID variantId);

    void deleteByCartCartId(UUID cartId);
}
