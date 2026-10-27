package com.fashion.store.modules.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.variant v " +
            "WHERE o.orderId = :orderId")
    Optional<Order> findWithItemsByOrderId(@Param("orderId") UUID orderId);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.variant v " +
            "WHERE o.orderCode = :orderCode")
    Optional<Order> findWithItemsByOrderCode(@Param("orderCode") String orderCode);

    Page<Order> findByUserUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserUserIdAndStatusAndPaymentMethod(UUID userId, OrderStatus status, PaymentMethod paymentMethod);

    @Query("SELECT o FROM Order o WHERE " +
            "(:status IS NULL OR o.status = :status) AND " +
            "(:search IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(o.shippingName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(o.shippingPhone) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> findAllFiltered(
            @Param("status") OrderStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
