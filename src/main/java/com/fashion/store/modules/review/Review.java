package com.fashion.store.modules.review;

import com.fashion.store.common.domain.BaseEntity;
import com.fashion.store.modules.product.Product;
import com.fashion.store.modules.product.ProductVariant;
import com.fashion.store.modules.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    @JsonIgnore
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "fit_rating", nullable = false, length = 20)
    @Builder.Default
    private FitRating fitRating = FitRating.TRUE_TO_SIZE;

    @Column(name = "quality_rating")
    private Short qualityRating;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", columnDefinition = "jsonb")
    private String images;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    @Column(name = "reviewer_size", length = 20)
    private String reviewerSize;

    @Column(name = "reviewer_height")
    private Short reviewerHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;
}
