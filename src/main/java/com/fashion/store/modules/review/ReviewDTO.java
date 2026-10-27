package com.fashion.store.modules.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReviewDTO {

    public record CreateReviewRequest(
            @NotNull(message = "Mã sản phẩm không được để trống")
            UUID productId,

            UUID orderId,

            UUID variantId,

            @NotNull(message = "Đánh giá số sao không được để trống")
            @Min(value = 1, message = "Đánh giá tối thiểu là 1 sao")
            @Max(value = 5, message = "Đánh giá tối đa là 5 sao")
            Short rating,

            @NotNull(message = "Đánh giá độ vừa vặn không được để trống (TOO_SMALL, TRUE_TO_SIZE, TOO_LARGE)")
            FitRating fitRating,

            @Min(value = 1, message = "Đánh giá chất lượng tối thiểu là 1")
            @Max(value = 5, message = "Đánh giá chất lượng tối đa là 5")
            Short qualityRating,

            String body,

            List<String> images,

            String reviewerSize,

            Short reviewerHeight
    ) {}

    public record UpdateReviewStatusRequest(
            @NotNull(message = "Trạng thái đánh giá không được để trống")
            ReviewStatus status
    ) {}

    public record ReviewResponse(
            UUID reviewId,
            UUID productId,
            String productName,
            UUID variantId,
            String variantDescription,
            UUID orderId,
            UUID userId,
            String reviewerName,
            String reviewerAvatar,
            Short rating,
            FitRating fitRating,
            Short qualityRating,
            String body,
            List<String> images,
            Integer helpfulCount,
            String reviewerSize,
            Short reviewerHeight,
            ReviewStatus status,
            OffsetDateTime createdAt
    ) {}

    public record FitSummary(
            long tooSmallCount,
            long trueToSizeCount,
            long tooLargeCount,
            double trueToSizePercentage
    ) {}

    public record ReviewSummaryResponse(
            BigDecimal avgRating,
            long totalReviews,
            Map<Short, Long> ratingCounts,
            FitSummary fitSummary
    ) {}
}
