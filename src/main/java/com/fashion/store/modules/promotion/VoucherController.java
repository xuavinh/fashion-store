package com.fashion.store.modules.promotion;

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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    // Tạo Voucher mới
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO.VoucherResponse>> createVoucher(
            @Valid @RequestBody VoucherDTO.CreateVoucherRequest request
    ) {
        VoucherDTO.VoucherResponse response = voucherService.createVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo mã voucher thành công", response));
    }

    // Cập nhật Voucher
    @PutMapping("/{voucherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO.VoucherResponse>> updateVoucher(
            @PathVariable UUID voucherId,
            @Valid @RequestBody VoucherDTO.UpdateVoucherRequest request
    ) {
        VoucherDTO.VoucherResponse response = voucherService.updateVoucher(voucherId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật mã voucher thành công", response));
    }

    // Bật / Tắt trạng thái kích hoạt của Voucher
    @PatchMapping("/{voucherId}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO.VoucherResponse>> toggleVoucherStatus(
            @PathVariable UUID voucherId
    ) {
        VoucherDTO.VoucherResponse response = voucherService.toggleVoucherStatus(voucherId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái voucher thành công", response));
    }

    // Xóa Voucher
    @DeleteMapping("/{voucherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVoucher(@PathVariable UUID voucherId) {
        voucherService.deleteVoucher(voucherId);
        return ResponseEntity.ok(ApiResponse.success("Xóa voucher thành công", null));
    }

    // Danh sách tất cả Voucher
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<VoucherDTO.VoucherResponse>>> getAllVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<VoucherDTO.VoucherResponse> response = voucherService.getAllVouchers(pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách voucher thành công", response));
    }

    // Chi tiết một Voucher theo ID
    @GetMapping("/{voucherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDTO.VoucherResponse>> getVoucherById(
            @PathVariable UUID voucherId
    ) {
        VoucherDTO.VoucherResponse response = voucherService.getVoucherById(voucherId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin voucher thành công", response));
    }

    // Kiểm tra thử Voucher cho giỏ hàng
    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VoucherDTO.VoucherValidationResponse>> validateVoucher(
            @Valid @RequestBody VoucherDTO.ValidateVoucherRequest request
    ) {
        UUID userId = getCurrentUserId();
        VoucherDTO.VoucherValidationResponse response = voucherService.validateVoucher(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra mã giảm giá thành công", response));
    }

    // Danh sách Voucher đang có hiệu lực và khả dụng cho khách hàng
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<VoucherDTO.VoucherSummaryResponse>>> getAvailableVouchers(
            @RequestParam(required = false) BigDecimal subtotal
    ) {
        UUID userId = getCurrentUserId();
        List<VoucherDTO.VoucherSummaryResponse> response = voucherService.getAvailableVouchers(userId, subtotal);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách mã giảm giá khả dụng thành công", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"));
    }
}
