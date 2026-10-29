package com.fashion.store.modules.promotion;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VoucherDTO {

    public record CartItemTargetInfo(
            UUID productId,
            UUID categoryId,
            BigDecimal subtotal
    ) {}

    public record CreateVoucherRequest(
            @NotBlank(message = "Mã voucher không được để trống")
            @Size(min = 3, max = 50, message = "Mã voucher phải từ 3 đến 50 ký tự")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã voucher chỉ được chứa chữ cái, số, dấu gạch ngang (-) và dấu gạch dưới (_)")
            String code,

            @NotBlank(message = "Tên voucher không được để trống")
            @Size(max = 150, message = "Tên voucher không được quá 150 ký tự")
            String name,

            String description,

            @NotNull(message = "Loại giảm giá không được để trống (PERCENTAGE, FIXED_AMOUNT)")
            DiscountType discountType,

            @NotNull(message = "Giá trị giảm không được để trống")
            @DecimalMin(value = "0.01", message = "Giá trị giảm phải lớn hơn 0")
            BigDecimal discountValue,

            @DecimalMin(value = "0.01", message = "Mức giảm tối đa phải lớn hơn 0")
            BigDecimal maxDiscountAmount,

            @DecimalMin(value = "0.00", message = "Giá trị đơn tối thiểu không được âm")
            BigDecimal minOrderValue,

            @NotNull(message = "Tổng lượt sử dụng không được để trống")
            @Min(value = 1, message = "Tổng lượt sử dụng tối thiểu là 1")
            Integer usageLimit,

            @NotNull(message = "Lượt sử dụng mỗi người không được để trống")
            @Min(value = 1, message = "Lượt sử dụng mỗi người tối thiểu là 1")
            Integer usageLimitPerUser,

            @NotNull(message = "Thời gian bắt đầu không được để trống")
            OffsetDateTime startDate,

            @NotNull(message = "Thời gian kết thúc không được để trống")
            OffsetDateTime endDate,

            Boolean isActive,

            VoucherTargetType targetType,
            Set<UUID> applicableProductIds,
            Set<UUID> applicableCategoryIds
    ) {}

    public record UpdateVoucherRequest(
            @Size(max = 150, message = "Tên voucher không được quá 150 ký tự")
            String name,

            String description,

            @Min(value = 1, message = "Tổng lượt sử dụng tối thiểu là 1")
            Integer usageLimit,

            @Min(value = 1, message = "Lượt sử dụng mỗi người tối thiểu là 1")
            Integer usageLimitPerUser,

            OffsetDateTime startDate,

            OffsetDateTime endDate,

            Boolean isActive,

            VoucherTargetType targetType,
            Set<UUID> applicableProductIds,
            Set<UUID> applicableCategoryIds
    ) {}

    public record ValidateVoucherRequest(
            @NotBlank(message = "Mã voucher không được để trống")
            String code,

            @NotNull(message = "Giá trị tạm tính giỏ hàng không được để trống")
            @DecimalMin(value = "0.00", message = "Giá trị tạm tính không được âm")
            BigDecimal subtotal,

            List<CartItemTargetInfo> items
    ) {}

    @Builder
    public record VoucherValidationResponse(
            String code,
            boolean isValid,
            String message,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount,
            BigDecimal finalSubtotal,
            BigDecimal eligibleSubtotal
    ) {}

    @Builder
    public record VoucherResponse(
            UUID voucherId,
            String code,
            String name,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            BigDecimal minOrderValue,
            Integer usageLimit,
            Integer usageLimitPerUser,
            Integer usedCount,
            Integer remainingCount,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            Boolean isActive,
            VoucherTargetType targetType,
            Set<UUID> applicableProductIds,
            Set<UUID> applicableCategoryIds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    @Builder
    public record VoucherSummaryResponse(
            UUID voucherId,
            String code,
            String name,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            BigDecimal minOrderValue,
            Integer remainingUserUses,
            OffsetDateTime endDate,
            VoucherTargetType targetType,
            boolean canApply,
            String applyReason
    ) {}
}
