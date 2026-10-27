package com.fashion.store.modules.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByUserUserIdAndProductProductIdAndOrderId(UUID userId, UUID productId, UUID orderId);

    @Query("SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.product " +
            "LEFT JOIN FETCH r.variant " +
            "LEFT JOIN FETCH r.user " +
            "WHERE r.reviewId = :reviewId")
    Optional<Review> findWithDetailsByReviewId(@Param("reviewId") UUID reviewId);

    @Query(value = "SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.product " +
            "LEFT JOIN FETCH r.variant " +
            "LEFT JOIN FETCH r.user " +
            "WHERE r.product.productId = :productId AND r.status = :status",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.product.productId = :productId AND r.status = :status")
    Page<Review> findByProductProductIdAndStatus(
            @Param("productId") UUID productId,
            @Param("status") ReviewStatus status,
            Pageable pageable
    );

    @Query(value = "SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.product " +
            "LEFT JOIN FETCH r.variant " +
            "LEFT JOIN FETCH r.user " +
            "WHERE r.product.productId = :productId AND r.status = :status AND r.rating = :rating",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.product.productId = :productId AND r.status = :status AND r.rating = :rating")
    Page<Review> findByProductProductIdAndStatusAndRating(
            @Param("productId") UUID productId,
            @Param("status") ReviewStatus status,
            @Param("rating") Short rating,
            Pageable pageable
    );

    @Query(value = "SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.product " +
            "LEFT JOIN FETCH r.variant " +
            "LEFT JOIN FETCH r.user " +
            "WHERE r.user.userId = :userId ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.user.userId = :userId")
    Page<Review> findByUserUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    long countByProductProductIdAndStatus(UUID productId, ReviewStatus status);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.productId = :productId AND r.status = :status")
    Double calculateAverageRating(@Param("productId") UUID productId, @Param("status") ReviewStatus status);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.productId = :productId AND r.status = :status GROUP BY r.rating")
    List<Object[]> countRatingsByProduct(@Param("productId") UUID productId, @Param("status") ReviewStatus status);

    @Query("SELECT r.fitRating, COUNT(r) FROM Review r WHERE r.product.productId = :productId AND r.status = :status GROUP BY r.fitRating")
    List<Object[]> countFitRatingsByProduct(@Param("productId") UUID productId, @Param("status") ReviewStatus status);
}
