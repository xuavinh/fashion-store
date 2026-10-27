package com.fashion.store.modules.review;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.order.Order;
import com.fashion.store.modules.order.OrderItem;
import com.fashion.store.modules.order.OrderRepository;
import com.fashion.store.modules.order.OrderStatus;
import com.fashion.store.modules.product.Product;
import com.fashion.store.modules.product.ProductRepository;
import com.fashion.store.modules.product.ProductVariant;
import com.fashion.store.modules.product.ProductVariantRepository;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    // Tạo đánh giá sản phẩm
    @Transactional
    public ReviewDTO.ReviewResponse createReview(UUID userId, ReviewDTO.CreateReviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + request.productId()));

        // Kiểm tra điều kiện Verified Purchase
        List<Order> deliveredOrders = orderRepository.findDeliveredOrdersForProduct(
                userId,
                OrderStatus.DELIVERED,
                request.productId()
        );

        if (deliveredOrders.isEmpty()) {
            throw new BadRequestException("Bạn chỉ có thể đánh giá sản phẩm sau khi đơn hàng đã được giao thành công (Verified Purchase).");
        }

        Order targetOrder;
        if (request.orderId() != null) {
            targetOrder = deliveredOrders.stream()
                    .filter(o -> o.getOrderId().equals(request.orderId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy đơn hàng đã giao hợp lệ với mã: " + request.orderId()));

            if (reviewRepository.existsByUserUserIdAndProductProductIdAndOrderId(userId, request.productId(), request.orderId())) {
                throw new BadRequestException("Bạn đã đánh giá sản phẩm này cho đơn hàng trên rồi.");
            }
        } else {
            // Tự động tìm đơn hàng đã giao gần nhất chưa được đánh giá
            targetOrder = deliveredOrders.stream()
                    .filter(o -> !reviewRepository.existsByUserUserIdAndProductProductIdAndOrderId(userId, request.productId(), o.getOrderId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Tất cả đơn hàng chứa sản phẩm này của bạn đều đã được đánh giá."));
        }

        // Xác định phân loại sản phẩm
        ProductVariant variant = null;
        if (request.variantId() != null) {
            variant = productVariantRepository.findById(request.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân loại sản phẩm: " + request.variantId()));
            if (!variant.getProduct().getProductId().equals(product.getProductId())) {
                throw new BadRequestException("Phân loại không thuộc về sản phẩm này");
            }
        } else if (targetOrder.getItems() != null) {
            for (OrderItem item : targetOrder.getItems()) {
                if (item.getVariant() != null && item.getVariant().getProduct().getProductId().equals(product.getProductId())) {
                    variant = item.getVariant();
                    break;
                }
            }
        }

        // Serialize images thành JSON string
        String imagesJson = null;
        if (request.images() != null && !request.images().isEmpty()) {
            try {
                imagesJson = objectMapper.writeValueAsString(request.images());
            } catch (JacksonException e) {
                log.error("Lỗi khi chuyển đổi ảnh đánh giá thành JSON", e);
                imagesJson = "[]";
            }
        }

        Review review = Review.builder()
                .product(product)
                .variant(variant)
                .user(user)
                .orderId(targetOrder.getOrderId())
                .rating(request.rating())
                .fitRating(request.fitRating() != null ? request.fitRating() : FitRating.TRUE_TO_SIZE)
                .qualityRating(request.qualityRating())
                .body(request.body())
                .images(imagesJson)
                .helpfulCount(0)
                .reviewerSize(request.reviewerSize())
                .reviewerHeight(request.reviewerHeight())
                .status(ReviewStatus.APPROVED) // Đã xác thực mua hàng nên duyệt tự động
                .build();

        Review savedReview = reviewRepository.save(review);

        // Cập nhật lại avg_rating của sản phẩm
        updateProductAvgRating(product);

        return toResponse(savedReview);
    }

    // Lấy danh sách đánh giá của sản phẩm (chỉ lấy review APPROVED)
    @Transactional(readOnly = true)
    public Page<ReviewDTO.ReviewResponse> getProductReviews(UUID productId, Short rating, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId);
        }

        Page<Review> reviewsPage;
        if (rating != null) {
            reviewsPage = reviewRepository.findByProductProductIdAndStatusAndRating(
                    productId,
                    ReviewStatus.APPROVED,
                    rating,
                    pageable
            );
        } else {
            reviewsPage = reviewRepository.findByProductProductIdAndStatus(
                    productId,
                    ReviewStatus.APPROVED,
                    pageable
            );
        }

        return reviewsPage.map(this::toResponse);
    }

    // Lấy thống kê đánh giá và phân tích độ vừa vặn của sản phẩm
    @Transactional(readOnly = true)
    public ReviewDTO.ReviewSummaryResponse getProductReviewSummary(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId);
        }

        long totalReviews = reviewRepository.countByProductProductIdAndStatus(productId, ReviewStatus.APPROVED);

        Double avgRatingDouble = reviewRepository.calculateAverageRating(productId, ReviewStatus.APPROVED);
        BigDecimal avgRating = avgRatingDouble != null
                ? BigDecimal.valueOf(avgRatingDouble).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Phân bổ sao (1-5)
        Map<Short, Long> ratingCounts = new LinkedHashMap<>();
        for (short i = 5; i >= 1; i--) {
            ratingCounts.put(i, 0L);
        }
        List<Object[]> ratingsResult = reviewRepository.countRatingsByProduct(productId, ReviewStatus.APPROVED);
        for (Object[] row : ratingsResult) {
            Short r = ((Number) row[0]).shortValue();
            Long count = ((Number) row[1]).longValue();
            ratingCounts.put(r, count);
        }

        // Thống kê độ vừa vặn
        long tooSmallCount = 0;
        long trueToSizeCount = 0;
        long tooLargeCount = 0;

        List<Object[]> fitResult = reviewRepository.countFitRatingsByProduct(productId, ReviewStatus.APPROVED);
        for (Object[] row : fitResult) {
            FitRating fr = (FitRating) row[0];
            Long count = ((Number) row[1]).longValue();
            if (fr == FitRating.TOO_SMALL) {
                tooSmallCount = count;
            } else if (fr == FitRating.TRUE_TO_SIZE) {
                trueToSizeCount = count;
            } else if (fr == FitRating.TOO_LARGE) {
                tooLargeCount = count;
            }
        }

        double trueToSizePercentage = totalReviews > 0
                ? BigDecimal.valueOf((double) trueToSizeCount / totalReviews * 100.0)
                .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        ReviewDTO.FitSummary fitSummary = new ReviewDTO.FitSummary(
                tooSmallCount,
                trueToSizeCount,
                tooLargeCount,
                trueToSizePercentage
        );

        return new ReviewDTO.ReviewSummaryResponse(
                avgRating,
                totalReviews,
                ratingCounts,
                fitSummary
        );
    }

    // Bấm hữu ích
    @Transactional
    public int voteHelpful(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá: " + reviewId));
        review.setHelpfulCount(review.getHelpfulCount() + 1);
        reviewRepository.save(review);
        return review.getHelpfulCount();
    }

    // Lấy lịch sử đánh giá của người dùng hiện tại
    @Transactional(readOnly = true)
    public Page<ReviewDTO.ReviewResponse> getMyReviews(UUID userId, Pageable pageable) {
        return reviewRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    // Cập nhật trạng thái đánh giá (Dành cho Quản trị viên / Kiểm duyệt)
    @Transactional
    public ReviewDTO.ReviewResponse updateReviewStatus(UUID reviewId, ReviewStatus status) {
        Review review = reviewRepository.findWithDetailsByReviewId(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá: " + reviewId));

        review.setStatus(status);
        Review saved = reviewRepository.save(review);

        // Cập nhật lại avg_rating của sản phẩm
        updateProductAvgRating(saved.getProduct());

        return toResponse(saved);
    }

    // Xóa đánh giá (Người viết hoặc Admin)
    @Transactional
    public void deleteReview(UUID reviewId, UUID currentUserId, boolean isAdmin) {
        Review review = reviewRepository.findWithDetailsByReviewId(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá: " + reviewId));

        if (!isAdmin && !review.getUser().getUserId().equals(currentUserId)) {
            throw new BadRequestException("Bạn không có quyền xóa đánh giá này");
        }

        Product product = review.getProduct();
        reviewRepository.delete(review);

        // Cập nhật lại avg_rating của sản phẩm
        updateProductAvgRating(product);
    }

    // Tính toán lại điểm đánh giá trung bình của sản phẩm
    public void updateProductAvgRating(Product product) {
        Double avg = reviewRepository.calculateAverageRating(product.getProductId(), ReviewStatus.APPROVED);
        BigDecimal newAvgRating = avg != null
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        product.setAvgRating(newAvgRating);
        productRepository.save(product);
    }

    public ReviewDTO.ReviewResponse toResponse(Review review) {
        List<String> images = Collections.emptyList();
        if (review.getImages() != null && !review.getImages().isBlank()) {
            try {
                images = objectMapper.readValue(review.getImages(), new TypeReference<List<String>>() {});
            } catch (JacksonException e) {
                log.warn("Không thể parse danh sách ảnh đánh giá: {}", review.getImages());
            }
        }

        String variantDesc = null;
        if (review.getVariant() != null) {
            variantDesc = review.getVariant().getColor() + " / " + review.getVariant().getSize();
        }

        return new ReviewDTO.ReviewResponse(
                review.getReviewId(),
                review.getProduct().getProductId(),
                review.getProduct().getName(),
                review.getVariant() != null ? review.getVariant().getVariantId() : null,
                variantDesc,
                review.getOrderId(),
                review.getUser().getUserId(),
                review.getUser().getFullName(),
                null,
                review.getRating(),
                review.getFitRating(),
                review.getQualityRating(),
                review.getBody(),
                images,
                review.getHelpfulCount(),
                review.getReviewerSize(),
                review.getReviewerHeight(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}
