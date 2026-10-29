package com.fashion.store.modules.promotion;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.order.Order;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final UserRepository userRepository;

    // Tạo mới Voucher
    @Transactional
    public VoucherDTO.VoucherResponse createVoucher(VoucherDTO.CreateVoucherRequest request) {
        String normalizedCode = request.code().trim().toUpperCase();

        if (voucherRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BadRequestException("Mã voucher '" + normalizedCode + "' đã tồn tại trong hệ thống");
        }

        if (!request.endDate().isAfter(request.startDate())) {
            throw new BadRequestException("Thời gian kết thúc phải diễn ra sau thời gian bắt đầu");
        }

        if (request.discountType() == DiscountType.PERCENTAGE) {
            if (request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Phần trăm giảm giá không được vượt quá 100%");
            }
        }

        VoucherTargetType targetType = resolveAndValidateTargetType(
                request.targetType(),
                request.applicableProductIds(),
                request.applicableCategoryIds()
        );

        Voucher voucher = Voucher.builder()
                .code(normalizedCode)
                .name(request.name().trim())
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .maxDiscountAmount(request.maxDiscountAmount())
                .minOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO)
                .usageLimit(request.usageLimit())
                .usageLimitPerUser(request.usageLimitPerUser())
                .usedCount(0)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .targetType(targetType)
                .applicableProductIds(request.applicableProductIds() != null ? new HashSet<>(request.applicableProductIds()) : new HashSet<>())
                .applicableCategoryIds(request.applicableCategoryIds() != null ? new HashSet<>(request.applicableCategoryIds()) : new HashSet<>())
                .build();

        Voucher saved = voucherRepository.save(voucher);
        log.info("Tạo voucher thành công: code={}, id={}, targetType={}", saved.getCode(), saved.getVoucherId(), saved.getTargetType());
        return toResponse(saved);
    }

    // Cập nhật Voucher
    @Transactional
    public VoucherDTO.VoucherResponse updateVoucher(UUID voucherId, VoucherDTO.UpdateVoucherRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher: " + voucherId));

        OffsetDateTime start = request.startDate() != null ? request.startDate() : voucher.getStartDate();
        OffsetDateTime end = request.endDate() != null ? request.endDate() : voucher.getEndDate();

        if (!end.isAfter(start)) {
            throw new BadRequestException("Thời gian kết thúc phải diễn ra sau thời gian bắt đầu");
        }

        if (request.name() != null && !request.name().isBlank()) {
            voucher.setName(request.name().trim());
        }
        if (request.description() != null) {
            voucher.setDescription(request.description());
        }
        if (request.usageLimit() != null) {
            if (request.usageLimit() < voucher.getUsedCount()) {
                throw new BadRequestException("Tổng lượt sử dụng mới (" + request.usageLimit() + ") không được nhỏ hơn số lượt đã dùng (" + voucher.getUsedCount() + ")");
            }
            voucher.setUsageLimit(request.usageLimit());
        }
        if (request.usageLimitPerUser() != null) {
            voucher.setUsageLimitPerUser(request.usageLimitPerUser());
        }
        if (request.startDate() != null) {
            voucher.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            voucher.setEndDate(request.endDate());
        }
        if (request.isActive() != null) {
            voucher.setIsActive(request.isActive());
        }
        if (request.targetType() != null) {
            voucher.setTargetType(request.targetType());
        }
        if (request.applicableProductIds() != null) {
            voucher.getApplicableProductIds().clear();
            voucher.getApplicableProductIds().addAll(request.applicableProductIds());
        }
        if (request.applicableCategoryIds() != null) {
            voucher.getApplicableCategoryIds().clear();
            voucher.getApplicableCategoryIds().addAll(request.applicableCategoryIds());
        }

        Voucher updated = voucherRepository.save(voucher);
        log.info("Cập nhật voucher thành công: code={}, id={}", updated.getCode(), updated.getVoucherId());
        return toResponse(updated);
    }

    // Bật/Tắt trạng thái hoạt động của Voucher
    @Transactional
    public VoucherDTO.VoucherResponse toggleVoucherStatus(UUID voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher: " + voucherId));

        voucher.setIsActive(!voucher.getIsActive());
        Voucher updated = voucherRepository.save(voucher);
        log.info("Đổi trạng thái voucher: code={}, isActive={}", updated.getCode(), updated.getIsActive());
        return toResponse(updated);
    }

    /**
     Xóa Voucher:
         Nếu voucher đã từng được dùng -> Soft delete
         Nếu chưa dùng -> Hard delete
     */
    @Transactional
    public void deleteVoucher(UUID voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher: " + voucherId));

        if (voucher.getUsedCount() > 0) {
            voucher.setIsActive(false);
            voucherRepository.save(voucher);
            log.info("Voucher code={} đã có lượt sử dụng, chuyển trạng thái sang ngưng hoạt động thay vì xóa cứng", voucher.getCode());
        } else {
            voucherRepository.delete(voucher);
            log.info("Đã xóa hoàn toàn voucher code={}, id={}", voucher.getCode(), voucherId);
        }
    }

    // Kiểm tra thử Voucher trước khi đặt hàng
    @Transactional(readOnly = true)
    public VoucherDTO.VoucherValidationResponse validateVoucher(UUID userId, VoucherDTO.ValidateVoucherRequest request) {
        String normalizedCode = request.code().trim().toUpperCase();

        Voucher voucher = voucherRepository.findByCodeIgnoreCase(normalizedCode)
                .orElse(null);

        if (voucher == null) {
            return VoucherDTO.VoucherValidationResponse.builder()
                    .code(normalizedCode)
                    .isValid(false)
                    .message("Mã voucher không tồn tại")
                    .discountAmount(BigDecimal.ZERO)
                    .finalSubtotal(request.subtotal())
                    .build();
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (Boolean.FALSE.equals(voucher.getIsActive())) {
            return invalidValidationResponse(voucher, "Voucher hiện đang tạm khóa hoặc ngưng áp dụng", request.subtotal());
        }
        if (now.isBefore(voucher.getStartDate())) {
            return invalidValidationResponse(voucher, "Voucher chưa đến thời gian áp dụng", request.subtotal());
        }
        if (now.isAfter(voucher.getEndDate())) {
            return invalidValidationResponse(voucher, "Voucher đã hết hạn sử dụng", request.subtotal());
        }
        if (voucher.getUsedCount() >= voucher.getUsageLimit()) {
            return invalidValidationResponse(voucher, "Voucher đã hết tổng số lượt sử dụng", request.subtotal());
        }

        BigDecimal eligibleSubtotal = request.subtotal();

        // Kiểm tra phạm vi áp dụng
        if (voucher.getTargetType() == VoucherTargetType.SPECIFIC_PRODUCTS) {
            if (request.items() != null && !request.items().isEmpty()) {
                eligibleSubtotal = request.items().stream()
                        .filter(i -> voucher.getApplicableProductIds().contains(i.productId()))
                        .map(VoucherDTO.CartItemTargetInfo::subtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
                return invalidValidationResponse(voucher, "Mã voucher chỉ áp dụng cho một số sản phẩm chỉ định và không có sản phẩm nào trong giỏ hàng thỏa mãn", request.subtotal());
            }
        } else if (voucher.getTargetType() == VoucherTargetType.SPECIFIC_CATEGORIES) {
            if (request.items() != null && !request.items().isEmpty()) {
                eligibleSubtotal = request.items().stream()
                        .filter(i -> i.categoryId() != null && voucher.getApplicableCategoryIds().contains(i.categoryId()))
                        .map(VoucherDTO.CartItemTargetInfo::subtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
                return invalidValidationResponse(voucher, "Mã voucher chỉ áp dụng cho một số danh mục chỉ định và không có sản phẩm nào trong giỏ hàng thỏa mãn", request.subtotal());
            }
        }

        if (eligibleSubtotal.compareTo(voucher.getMinOrderValue()) < 0) {
            return invalidValidationResponse(voucher, "Các sản phẩm thỏa điều kiện voucher chưa đạt giá trị tối thiểu " + voucher.getMinOrderValue() + " đ để dùng voucher này", request.subtotal());
        }

        int userUsedCount = voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                voucher.getVoucherId(), userId, List.of(VoucherUsageStatus.RESERVED, VoucherUsageStatus.USED)
        );
        if (userUsedCount >= voucher.getUsageLimitPerUser()) {
            return invalidValidationResponse(voucher, "Bạn đã sử dụng hết lượt cho phép của voucher này", request.subtotal());
        }

        BigDecimal discountAmount = calculateDiscountAmount(voucher, eligibleSubtotal);
        BigDecimal finalSubtotal = request.subtotal().subtract(discountAmount).max(BigDecimal.ZERO);

        return VoucherDTO.VoucherValidationResponse.builder()
                .code(voucher.getCode())
                .isValid(true)
                .message("Áp dụng mã giảm giá thành công")
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .discountAmount(discountAmount)
                .eligibleSubtotal(eligibleSubtotal)
                .finalSubtotal(finalSubtotal)
                .build();
    }

    // Áp dụng Voucher vào đơn hàng thực tế kèm kiểm tra phạm vi sản phẩm
    @Transactional
    public BigDecimal applyVoucherToOrder(UUID userId, String voucherCode, BigDecimal subtotal, List<VoucherDTO.CartItemTargetInfo> items, Order order) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return BigDecimal.ZERO;
        }

        String normalizedCode = voucherCode.trim().toUpperCase();

        // Khóa bi quan hàng Voucher để chống race condition khi nhiều request đặt hàng cùng lúc
        Voucher voucher = voucherRepository.findByCodeWithLock(normalizedCode)
                .orElseThrow(() -> new BadRequestException("Mã voucher '" + normalizedCode + "' không tồn tại"));

        OffsetDateTime now = OffsetDateTime.now();

        if (Boolean.FALSE.equals(voucher.getIsActive())) {
            throw new BadRequestException("Mã voucher '" + normalizedCode + "' hiện đang tạm khóa");
        }
        if (now.isBefore(voucher.getStartDate())) {
            throw new BadRequestException("Mã voucher '" + normalizedCode + "' chưa đến thời gian áp dụng");
        }
        if (now.isAfter(voucher.getEndDate())) {
            throw new BadRequestException("Mã voucher '" + normalizedCode + "' đã hết hạn");
        }
        if (voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new BadRequestException("Mã voucher '" + normalizedCode + "' đã hết lượt sử dụng");
        }

        BigDecimal eligibleSubtotal = subtotal;

        // Tính toán tổng tiền chỉ trên các sản phẩm hợp lệ
        if (voucher.getTargetType() == VoucherTargetType.SPECIFIC_PRODUCTS) {
            if (items != null && !items.isEmpty()) {
                eligibleSubtotal = items.stream()
                        .filter(i -> voucher.getApplicableProductIds().contains(i.productId()))
                        .map(VoucherDTO.CartItemTargetInfo::subtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Mã voucher '" + normalizedCode + "' chỉ áp dụng cho một số sản phẩm chỉ định và không có sản phẩm nào trong đơn hàng của bạn thỏa mãn");
            }
        } else if (voucher.getTargetType() == VoucherTargetType.SPECIFIC_CATEGORIES) {
            if (items != null && !items.isEmpty()) {
                eligibleSubtotal = items.stream()
                        .filter(i -> i.categoryId() != null && voucher.getApplicableCategoryIds().contains(i.categoryId()))
                        .map(VoucherDTO.CartItemTargetInfo::subtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Mã voucher '" + normalizedCode + "' chỉ áp dụng cho một số danh mục chỉ định và không có sản phẩm nào trong đơn hàng của bạn thỏa mãn");
            }
        }

        if (eligibleSubtotal.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new BadRequestException("Các sản phẩm thỏa điều kiện voucher chưa đạt giá trị tối thiểu (" + voucher.getMinOrderValue() + " đ) để áp dụng voucher này");
        }

        int userUsedCount = voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                voucher.getVoucherId(), userId, List.of(VoucherUsageStatus.RESERVED, VoucherUsageStatus.USED)
        );
        if (userUsedCount >= voucher.getUsageLimitPerUser()) {
            throw new BadRequestException("Bạn đã dùng hết số lượt cho phép (" + voucher.getUsageLimitPerUser() + " lần) của mã voucher này");
        }

        // Tính tiền giảm dựa trên eligibleSubtotal (không giảm trên toàn đơn nếu voucher chỉ áp dụng cho 1 số món)
        BigDecimal discountAmount = calculateDiscountAmount(voucher, eligibleSubtotal);

        int updatedRows = voucherRepository.incrementUsedCountIfAvailable(voucher.getVoucherId());
        if (updatedRows == 0) {
            throw new BadRequestException("Mã voucher '" + normalizedCode + "' đã hết lượt sử dụng");
        }
        voucher.setUsedCount(voucher.getUsedCount() + 1);

        User user = userRepository.getReferenceById(userId);

        // Ghi nhận lịch sử giữ chỗ voucher
        VoucherUsage usage = VoucherUsage.builder()
                .voucher(voucher)
                .user(user)
                .order(order)
                .discountAmount(discountAmount)
                .status(VoucherUsageStatus.RESERVED)
                .reservedAt(OffsetDateTime.now())
                .usedAt(null)
                .build();
        voucherUsageRepository.save(usage);

        order.setVoucherId(voucher.getVoucherId());
        order.setVoucherCode(voucher.getCode());
        order.setDiscountAmount(discountAmount);

        log.info("Áp dụng và giữ chỗ voucher thành công: code={}, discount={}, eligibleSubtotal={}, orderCode={}, user={}",
                voucher.getCode(), discountAmount, eligibleSubtotal, order.getOrderCode(), userId);

        return discountAmount;
    }

    @Transactional
    public BigDecimal applyVoucherToOrder(UUID userId, String voucherCode, BigDecimal subtotal, Order order) {
        return applyVoucherToOrder(userId, voucherCode, subtotal, Collections.emptyList(), order);
    }

    // Chốt sử dụng Voucher chính thức khi đơn hàng hoàn tất/giao thành công
    @Transactional
    public void confirmVoucherUsage(UUID orderId) {
        voucherUsageRepository.findByOrderOrderIdAndStatusIn(orderId, List.of(VoucherUsageStatus.RESERVED))
                .ifPresent(usage -> {
                    usage.setStatus(VoucherUsageStatus.USED);
                    usage.setUsedAt(OffsetDateTime.now());
                    voucherUsageRepository.save(usage);
                    log.info("Xác nhận đã sử dụng voucher chính thức: code={}, orderId={}",
                            usage.getVoucher().getCode(), orderId);
                });
    }

    // Hoàn lại lượt sử dụng Voucher khi đơn hàng bị HỦY
    @Transactional
    public void rollbackVoucherUsage(UUID orderId) {
        voucherUsageRepository.findByOrderOrderIdAndStatusIn(orderId, List.of(VoucherUsageStatus.RESERVED, VoucherUsageStatus.USED))
                .ifPresent(usage -> {
                    int affectedRows = voucherRepository.decrementUsedCount(usage.getVoucher().getVoucherId());
                    if (affectedRows == 0) {
                        log.warn("Không thể giảm usedCount cho voucher id={} (có thể used_count đã bằng 0)",
                                usage.getVoucher().getVoucherId());
                    }

                    usage.setStatus(VoucherUsageStatus.CANCELLED);
                    usage.setCancelledAt(OffsetDateTime.now());
                    voucherUsageRepository.save(usage);

                    log.info("Đã hoàn lại lượt sử dụng voucher code={} cho đơn hàng id={}",
                            usage.getVoucher().getCode(), orderId);
                });
    }

    // Lấy danh sách Voucher khả dụng cho khách hàng
    @Transactional(readOnly = true)
    public List<VoucherDTO.VoucherSummaryResponse> getAvailableVouchers(UUID userId, BigDecimal subtotal) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Voucher> activeVouchers = voucherRepository.findActiveVouchers(now);
        List<VoucherDTO.VoucherSummaryResponse> results = new ArrayList<>();

        for (Voucher v : activeVouchers) {
            int usedByUser = voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                    v.getVoucherId(), userId, List.of(VoucherUsageStatus.RESERVED, VoucherUsageStatus.USED)
            );
            int remainingUserUses = Math.max(0, v.getUsageLimitPerUser() - usedByUser);

            boolean canApply = true;
            String applyReason = "Khả dụng";

            if (remainingUserUses == 0) {
                canApply = false;
                applyReason = "Bạn đã dùng hết lượt";
            } else if (subtotal != null && subtotal.compareTo(v.getMinOrderValue()) < 0) {
                canApply = false;
                applyReason = "Chưa đạt đơn tối thiểu " + v.getMinOrderValue() + " đ";
            }

            results.add(VoucherDTO.VoucherSummaryResponse.builder()
                    .voucherId(v.getVoucherId())
                    .code(v.getCode())
                    .name(v.getName())
                    .description(v.getDescription())
                    .discountType(v.getDiscountType())
                    .discountValue(v.getDiscountValue())
                    .maxDiscountAmount(v.getMaxDiscountAmount())
                    .minOrderValue(v.getMinOrderValue())
                    .remainingUserUses(remainingUserUses)
                    .endDate(v.getEndDate())
                    .targetType(v.getTargetType())
                    .canApply(canApply)
                    .applyReason(applyReason)
                    .build());
        }

        return results;
    }

    // Danh sách tất cả Voucher (Admin quản lý)
    @Transactional(readOnly = true)
    public Page<VoucherDTO.VoucherResponse> getAllVouchers(Pageable pageable) {
        return voucherRepository.findAll(pageable).map(this::toResponse);
    }

    // Xem chi tiết Voucher theo ID
    @Transactional(readOnly = true)
    public VoucherDTO.VoucherResponse getVoucherById(UUID voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher: " + voucherId));
        return toResponse(voucher);
    }

    // Tính toán số tiền giảm giá chính xác
    private BigDecimal calculateDiscountAmount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            BigDecimal discount = subtotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                return voucher.getMaxDiscountAmount();
            }
            return discount.min(subtotal);
        } else {
            return voucher.getDiscountValue().min(subtotal);
        }
    }

    private VoucherDTO.VoucherValidationResponse invalidValidationResponse(Voucher voucher, String message, BigDecimal subtotal) {
        return VoucherDTO.VoucherValidationResponse.builder()
                .code(voucher.getCode())
                .isValid(false)
                .message(message)
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .discountAmount(BigDecimal.ZERO)
                .finalSubtotal(subtotal)
                .eligibleSubtotal(BigDecimal.ZERO)
                .build();
    }

    private VoucherTargetType resolveAndValidateTargetType(
            VoucherTargetType targetType,
            Set<UUID> applicableProductIds,
            Set<UUID> applicableCategoryIds
    ) {
        VoucherTargetType resolved = targetType != null ? targetType : VoucherTargetType.ALL;
        if (resolved == VoucherTargetType.SPECIFIC_PRODUCTS && (applicableProductIds == null || applicableProductIds.isEmpty())) {
            throw new BadRequestException("Phải chọn ít nhất một sản phẩm áp dụng cho loại voucher này");
        }
        if (resolved == VoucherTargetType.SPECIFIC_CATEGORIES && (applicableCategoryIds == null || applicableCategoryIds.isEmpty())) {
            throw new BadRequestException("Phải chọn ít nhất một danh mục áp dụng cho loại voucher này");
        }
        return resolved;
    }

    private VoucherDTO.VoucherResponse toResponse(Voucher v) {
        int remaining = Math.max(0, v.getUsageLimit() - v.getUsedCount());
        return VoucherDTO.VoucherResponse.builder()
                .voucherId(v.getVoucherId())
                .code(v.getCode())
                .name(v.getName())
                .description(v.getDescription())
                .discountType(v.getDiscountType())
                .discountValue(v.getDiscountValue())
                .maxDiscountAmount(v.getMaxDiscountAmount())
                .minOrderValue(v.getMinOrderValue())
                .usageLimit(v.getUsageLimit())
                .usageLimitPerUser(v.getUsageLimitPerUser())
                .usedCount(v.getUsedCount())
                .remainingCount(remaining)
                .startDate(v.getStartDate())
                .endDate(v.getEndDate())
                .isActive(v.getIsActive())
                .targetType(v.getTargetType())
                .applicableProductIds(v.getApplicableProductIds())
                .applicableCategoryIds(v.getApplicableCategoryIds())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
