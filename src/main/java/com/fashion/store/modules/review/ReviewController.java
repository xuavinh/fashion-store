package com.fashion.store.modules.review;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // Viết đánh giá sản phẩm (yêu cầu đã đăng nhập và là Verified Purchase)
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDTO.ReviewResponse>> createReview(
            @Valid @RequestBody ReviewDTO.CreateReviewRequest request
    ) {
        UUID userId = getCurrentUserId();
        ReviewDTO.ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đánh giá sản phẩm thành công", response));
    }

    // Lấy danh sách đánh giá của sản phẩm
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Page<ReviewDTO.ReviewResponse>>> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(required = false) Short rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ReviewDTO.ReviewResponse> response = reviewService.getProductReviews(productId, rating, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công", response));
    }

    // Lấy tóm tắt đánh giá & phân tích độ vừa vặn
    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ApiResponse<ReviewDTO.ReviewSummaryResponse>> getProductReviewSummary(
            @PathVariable UUID productId
    ) {
        ReviewDTO.ReviewSummaryResponse response = reviewService.getProductReviewSummary(productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tổng hợp đánh giá thành công", response));
    }

    // Đánh dấu đánh giá hữu ích
    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<ApiResponse<Integer>> voteHelpful(@PathVariable UUID reviewId) {
        int helpfulCount = reviewService.voteHelpful(reviewId);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận phản hồi hữu ích", helpfulCount));
    }

    // Lịch sử các đánh giá của người dùng hiện tại
    @GetMapping("/my-reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ReviewDTO.ReviewResponse>>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReviewDTO.ReviewResponse> response = reviewService.getMyReviews(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá của bạn thành công", response));
    }

    // Kiểm duyệt / Cập nhật trạng thái đánh giá
    @PatchMapping("/{reviewId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ReviewDTO.ReviewResponse>> updateReviewStatus(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewDTO.UpdateReviewStatusRequest request
    ) {
        ReviewDTO.ReviewResponse response = reviewService.updateReviewStatus(reviewId, request.status());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đánh giá thành công", response));
    }

    // Xóa đánh giá (Người dùng sở hữu hoặc Admin)
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID reviewId) {
        UUID userId = getCurrentUserId();
        boolean isAdmin = isUserAdmin();
        reviewService.deleteReview(reviewId, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Xóa đánh giá thành công", null));
    }

    private UUID getCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"));
    }

    private boolean isUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
