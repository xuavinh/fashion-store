package com.fashion.store.modules.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserUserId(UUID userId);

    @Query("SELECT DISTINCT c FROM Cart c " +
            "LEFT JOIN FETCH c.items i " +
            "LEFT JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product p " +
            "WHERE c.user.userId = :userId")
    Optional<Cart> findWithItemsByUserId(@Param("userId") UUID userId);
}
