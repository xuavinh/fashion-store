package com.fashion.store.modules.review;

import com.fashion.store.common.exception.BadRequestException;
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
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    private ReviewService reviewService;

    private UUID userId;
    private UUID productId;
    private UUID variantId;
    private UUID orderId;
    private UUID reviewId;

    private User sampleUser;
    private Product sampleProduct;
    private ProductVariant sampleVariant;
    private Order sampleOrder;
    private OrderItem sampleOrderItem;
    private Review sampleReview;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository,
                productRepository,
                productVariantRepository,
                userRepository,
                orderRepository,
                new ObjectMapper()
        );

        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        sampleUser = User.builder()
                .userId(userId)
                .email("buyer@gmail.com")
                .fullName("Nguyen Van A")
                .build();

        sampleProduct = Product.builder()
                .name("Áo Sơ Mi Oxford")
                .slug("ao-so-mi-oxford")
                .avgRating(BigDecimal.ZERO)
                .build();
        sampleProduct.setProductId(productId);

        sampleVariant = ProductVariant.builder()
                .product(sampleProduct)
                .color("Trắng")
                .size("L")
                .skuCode("SMI-WHT-L")
                .price(BigDecimal.valueOf(350000))
                .stockQty(50)
                .build();
        sampleVariant.setVariantId(variantId);

        sampleOrderItem = OrderItem.builder()
                .itemId(UUID.randomUUID())
                .variant(sampleVariant)
                .productName("Áo Sơ Mi Oxford")
                .color("Trắng")
                .size("L")
                .skuCode("SMI-WHT-L")
                .price(BigDecimal.valueOf(350000))
                .quantity(1)
                .subtotal(BigDecimal.valueOf(350000))
                .build();

        sampleOrder = Order.builder()
                .orderId(orderId)
                .user(sampleUser)
                .orderCode("ORD-20261027-123456")
                .status(OrderStatus.DELIVERED)
                .items(new ArrayList<>(List.of(sampleOrderItem)))
                .build();
        sampleOrderItem.setOrder(sampleOrder);

        sampleReview = Review.builder()
                .reviewId(reviewId)
                .product(sampleProduct)
                .variant(sampleVariant)
                .user(sampleUser)
                .orderId(orderId)
                .rating((short) 5)
                .fitRating(FitRating.TRUE_TO_SIZE)
                .qualityRating((short) 5)
                .body("Áo rất đẹp, vải mát vừa vặn!")
                .images("[\"https://cdn.fashion.com/img1.jpg\"]")
                .helpfulCount(3)
                .reviewerSize("L")
                .reviewerHeight((short) 175)
                .status(ReviewStatus.APPROVED)
                .build();
        sampleReview.setCreatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Đánh giá sản phẩm thành công khi đã mua hàng và đơn hàng DELIVERED")
    void createReview_VerifiedPurchase_Success() {
        ReviewDTO.CreateReviewRequest request = new ReviewDTO.CreateReviewRequest(
                productId,
                orderId,
                variantId,
                (short) 5,
                FitRating.TRUE_TO_SIZE,
                (short) 5,
                "Áo rất đẹp!",
                List.of("https://cdn.fashion.com/img1.jpg"),
                "L",
                (short) 175
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findDeliveredOrdersForProduct(userId, OrderStatus.DELIVERED, productId))
                .thenReturn(List.of(sampleOrder));
        when(reviewRepository.existsByUserUserIdAndProductProductIdAndOrderId(userId, productId, orderId))
                .thenReturn(false);
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(sampleVariant));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setReviewId(reviewId);
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });
        when(reviewRepository.calculateAverageRating(productId, ReviewStatus.APPROVED)).thenReturn(5.0);

        ReviewDTO.ReviewResponse response = reviewService.createReview(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.rating()).isEqualTo((short) 5);
        assertThat(response.fitRating()).isEqualTo(FitRating.TRUE_TO_SIZE);
        assertThat(response.reviewerName()).isEqualTo("Nguyen Van A");
        assertThat(response.images()).containsExactly("https://cdn.fashion.com/img1.jpg");

        // Verify product avgRating was recalculated and saved
        verify(productRepository).save(sampleProduct);
        assertThat(sampleProduct.getAvgRating()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("Đánh giá thất bại khi người dùng chưa từng mua hàng có trạng thái DELIVERED")
    void createReview_NotVerifiedPurchase_ThrowsBadRequest() {
        ReviewDTO.CreateReviewRequest request = new ReviewDTO.CreateReviewRequest(
                productId,
                null,
                null,
                (short) 5,
                FitRating.TRUE_TO_SIZE,
                null,
                "Chưa mua nhưng muốn đánh giá",
                null,
                null,
                null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findDeliveredOrdersForProduct(userId, OrderStatus.DELIVERED, productId))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Verified Purchase");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đánh giá thất bại khi đơn hàng đã được đánh giá trước đó (tránh duplicate)")
    void createReview_AlreadyReviewed_ThrowsBadRequest() {
        ReviewDTO.CreateReviewRequest request = new ReviewDTO.CreateReviewRequest(
                productId,
                orderId,
                variantId,
                (short) 5,
                FitRating.TRUE_TO_SIZE,
                null,
                "Đánh giá lần 2",
                null,
                null,
                null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findDeliveredOrdersForProduct(userId, OrderStatus.DELIVERED, productId))
                .thenReturn(List.of(sampleOrder));
        when(reviewRepository.existsByUserUserIdAndProductProductIdAndOrderId(userId, productId, orderId))
                .thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã đánh giá sản phẩm này cho đơn hàng trên rồi");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy danh sách đánh giá theo sản phẩm và lọc theo số sao thành công")
    void getProductReviews_FilterByRating_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Review> page = new PageImpl<>(List.of(sampleReview), pageable, 1);

        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.findByProductProductIdAndStatusAndRating(productId, ReviewStatus.APPROVED, (short) 5, pageable))
                .thenReturn(page);

        Page<ReviewDTO.ReviewResponse> result = reviewService.getProductReviews(productId, (short) 5, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().rating()).isEqualTo((short) 5);
    }

    @Test
    @DisplayName("Lấy thống kê đánh giá và phân tích độ vừa vặn chính xác")
    void getProductReviewSummary_Success() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.countByProductProductIdAndStatus(productId, ReviewStatus.APPROVED)).thenReturn(4L);
        when(reviewRepository.calculateAverageRating(productId, ReviewStatus.APPROVED)).thenReturn(4.75);

        List<Object[]> ratingCounts = List.of(
                new Object[]{(short) 5, 3L},
                new Object[]{(short) 4, 1L}
        );
        when(reviewRepository.countRatingsByProduct(productId, ReviewStatus.APPROVED)).thenReturn(ratingCounts);

        List<Object[]> fitCounts = List.of(
                new Object[]{FitRating.TRUE_TO_SIZE, 3L},
                new Object[]{FitRating.TOO_SMALL, 1L}
        );
        when(reviewRepository.countFitRatingsByProduct(productId, ReviewStatus.APPROVED)).thenReturn(fitCounts);

        ReviewDTO.ReviewSummaryResponse summary = reviewService.getProductReviewSummary(productId);

        assertThat(summary.totalReviews()).isEqualTo(4L);
        assertThat(summary.avgRating()).isEqualTo(new BigDecimal("4.75"));
        assertThat(summary.ratingCounts().get((short) 5)).isEqualTo(3L);
        assertThat(summary.ratingCounts().get((short) 4)).isEqualTo(1L);
        assertThat(summary.fitSummary().trueToSizeCount()).isEqualTo(3L);
        assertThat(summary.fitSummary().tooSmallCount()).isEqualTo(1L);
        assertThat(summary.fitSummary().trueToSizePercentage()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("Bấm hữu ích (Vote Helpful) tăng lượt thành công")
    void voteHelpful_Success() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(sampleReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int newCount = reviewService.voteHelpful(reviewId);

        assertThat(newCount).isEqualTo(4);
        assertThat(sampleReview.getHelpfulCount()).isEqualTo(4);
        verify(reviewRepository).save(sampleReview);
    }

    @Test
    @DisplayName("Chủ bài đánh giá xóa đánh giá thành công và cập nhật lại điểm trung bình")
    void deleteReview_ByOwner_Success() {
        when(reviewRepository.findWithDetailsByReviewId(reviewId)).thenReturn(Optional.of(sampleReview));
        when(reviewRepository.calculateAverageRating(productId, ReviewStatus.APPROVED)).thenReturn(null);

        reviewService.deleteReview(reviewId, userId, false);

        verify(reviewRepository).delete(sampleReview);
        verify(productRepository).save(sampleProduct);
        assertThat(sampleProduct.getAvgRating()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Người dùng khác cố tình xóa đánh giá thì bị từ chối")
    void deleteReview_ByUnauthorizedUser_ThrowsBadRequest() {
        UUID otherUserId = UUID.randomUUID();
        when(reviewRepository.findWithDetailsByReviewId(reviewId)).thenReturn(Optional.of(sampleReview));

        assertThatThrownBy(() -> reviewService.deleteReview(reviewId, otherUserId, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn không có quyền xóa đánh giá này");

        verify(reviewRepository, never()).delete(any());
    }
}
