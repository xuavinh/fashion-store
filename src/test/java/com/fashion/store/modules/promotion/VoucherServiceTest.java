package com.fashion.store.modules.promotion;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.order.Order;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    @Mock
    private UserRepository userRepository;

    private VoucherService voucherService;

    private UUID userId;
    private User sampleUser;
    private Voucher samplePercentVoucher;
    private Voucher sampleFixedVoucher;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherService(voucherRepository, voucherUsageRepository, userRepository);

        userId = UUID.randomUUID();
        sampleUser = User.builder().userId(userId).email("customer@fashion.vn").build();

        samplePercentVoucher = Voucher.builder()
                .voucherId(UUID.randomUUID())
                .code("SUMMER20")
                .name("Giảm 20% đơn hè")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .maxDiscountAmount(BigDecimal.valueOf(50000))
                .minOrderValue(BigDecimal.valueOf(200000))
                .usageLimit(100)
                .usageLimitPerUser(1)
                .usedCount(0)
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(10))
                .isActive(true)
                .build();

        sampleFixedVoucher = Voucher.builder()
                .voucherId(UUID.randomUUID())
                .code("GIAM50K")
                .name("Giảm 50.000đ")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(50000))
                .maxDiscountAmount(null)
                .minOrderValue(BigDecimal.valueOf(150000))
                .usageLimit(50)
                .usageLimitPerUser(2)
                .usedCount(5)
                .startDate(OffsetDateTime.now().minusDays(2))
                .endDate(OffsetDateTime.now().plusDays(5))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Tạo voucher % giảm giá thành công")
    void createVoucher_Success() {
        VoucherDTO.CreateVoucherRequest request = new VoucherDTO.CreateVoucherRequest(
                "flashsale", "Flash Sale 15%", "Mô tả",
                DiscountType.PERCENTAGE, BigDecimal.valueOf(15), BigDecimal.valueOf(100000),
                BigDecimal.valueOf(300000), 200, 1,
                OffsetDateTime.now(), OffsetDateTime.now().plusDays(3), true,
                VoucherTargetType.ALL, null, null
        );

        when(voucherRepository.existsByCodeIgnoreCase("FLASHSALE")).thenReturn(false);
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(i -> {
            Voucher v = i.getArgument(0);
            v.setVoucherId(UUID.randomUUID());
            return v;
        });

        VoucherDTO.VoucherResponse response = voucherService.createVoucher(request);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("FLASHSALE");
        assertThat(response.discountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(response.discountValue()).isEqualByComparingTo(BigDecimal.valueOf(15));
        verify(voucherRepository).save(any(Voucher.class));
    }

    @Test
    @DisplayName("Tạo voucher thất bại khi trùng mã code")
    void createVoucher_DuplicateCode_ThrowsBadRequest() {
        VoucherDTO.CreateVoucherRequest request = new VoucherDTO.CreateVoucherRequest(
                "SUMMER20", "Trùng mã", null,
                DiscountType.PERCENTAGE, BigDecimal.valueOf(20), null,
                BigDecimal.ZERO, 100, 1,
                OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), true,
                VoucherTargetType.ALL, null, null
        );

        when(voucherRepository.existsByCodeIgnoreCase("SUMMER20")).thenReturn(true);

        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã tồn tại");

        verify(voucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo voucher thất bại khi ngày kết thúc trước ngày bắt đầu")
    void createVoucher_InvalidDates_ThrowsBadRequest() {
        VoucherDTO.CreateVoucherRequest request = new VoucherDTO.CreateVoucherRequest(
                "VOUCHER_SAI", "Sai ngày", null,
                DiscountType.PERCENTAGE, BigDecimal.valueOf(20), null,
                BigDecimal.ZERO, 100, 1,
                OffsetDateTime.now().plusDays(5), OffsetDateTime.now().plusDays(2), true,
                VoucherTargetType.ALL, null, null
        );

        when(voucherRepository.existsByCodeIgnoreCase("VOUCHER_SAI")).thenReturn(false);

        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Thời gian kết thúc");
    }

    @Test
    @DisplayName("Tạo voucher thất bại khi % giảm giá vượt quá 100%")
    void createVoucher_PercentageOver100_ThrowsBadRequest() {
        VoucherDTO.CreateVoucherRequest request = new VoucherDTO.CreateVoucherRequest(
                "GIAM150", "Giảm 150%", null,
                DiscountType.PERCENTAGE, BigDecimal.valueOf(150), null,
                BigDecimal.ZERO, 100, 1,
                OffsetDateTime.now(), OffsetDateTime.now().plusDays(2), true,
                VoucherTargetType.ALL, null, null
        );

        when(voucherRepository.existsByCodeIgnoreCase("GIAM150")).thenReturn(false);

        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100%");
    }

    @Test
    @DisplayName("Kiểm tra thử voucher thành công và áp dụng mức giảm tối đa")
    void validateVoucher_Success_CappedAtMaxDiscount() {
        VoucherDTO.ValidateVoucherRequest request = new VoucherDTO.ValidateVoucherRequest(
                "SUMMER20", BigDecimal.valueOf(500000), null // 20% của 500k là 100k, nhưng chặn tối đa 50k
        );

        when(voucherRepository.findByCodeIgnoreCase("SUMMER20")).thenReturn(Optional.of(samplePercentVoucher));
        when(voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                eq(samplePercentVoucher.getVoucherId()), eq(userId), any()
        )).thenReturn(0);

        VoucherDTO.VoucherValidationResponse response = voucherService.validateVoucher(userId, request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000)); // Bị chặn ở 50k
        assertThat(response.finalSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(450000));
    }

    @Test
    @DisplayName("Kiểm tra thử voucher thất bại khi chưa đạt đơn hàng tối thiểu")
    void validateVoucher_MinOrderValueNotMet_ReturnsInvalid() {
        VoucherDTO.ValidateVoucherRequest request = new VoucherDTO.ValidateVoucherRequest(
                "SUMMER20", BigDecimal.valueOf(100000), null // Đơn tối thiểu là 200k
        );

        when(voucherRepository.findByCodeIgnoreCase("SUMMER20")).thenReturn(Optional.of(samplePercentVoucher));

        VoucherDTO.VoucherValidationResponse response = voucherService.validateVoucher(userId, request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.message()).contains("chưa đạt giá trị tối thiểu");
        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Áp dụng voucher thực tế vào đơn hàng thành công với Pessimistic Lock")
    void applyVoucherToOrder_Success_WithPessimisticLock() {
        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .orderCode("ORD-20261028-TEST01")
                .build();

        BigDecimal subtotal = BigDecimal.valueOf(300000);

        when(voucherRepository.findByCodeWithLock("SUMMER20")).thenReturn(Optional.of(samplePercentVoucher));
        when(voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                eq(samplePercentVoucher.getVoucherId()), eq(userId), any()
        )).thenReturn(0);
        when(voucherRepository.incrementUsedCountIfAvailable(samplePercentVoucher.getVoucherId())).thenReturn(1);
        when(userRepository.getReferenceById(userId)).thenReturn(sampleUser);

        BigDecimal discount = voucherService.applyVoucherToOrder(userId, "summer20", subtotal, order);

        // 20% của 300k là 60k, nhưng max discount là 50k
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(samplePercentVoucher.getUsedCount()).isEqualTo(1); // Đã tăng usedCount
        assertThat(order.getVoucherCode()).isEqualTo("SUMMER20");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        verify(voucherRepository).findByCodeWithLock("SUMMER20");
        verify(voucherRepository).incrementUsedCountIfAvailable(samplePercentVoucher.getVoucherId());
        verify(voucherUsageRepository).save(any(VoucherUsage.class));
    }

    @Test
    @DisplayName("Áp dụng voucher thất bại khi người dùng đã dùng hết số lượt cho phép")
    void applyVoucherToOrder_UserLimitExceeded_ThrowsBadRequest() {
        Order order = Order.builder().orderCode("ORD-TEST").build();

        when(voucherRepository.findByCodeWithLock("SUMMER20")).thenReturn(Optional.of(samplePercentVoucher));
        when(voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                eq(samplePercentVoucher.getVoucherId()), eq(userId), any()
        )).thenReturn(1); // User đã dùng 1 lần, limitPerUser = 1

        assertThatThrownBy(() -> voucherService.applyVoucherToOrder(userId, "SUMMER20", BigDecimal.valueOf(300000), order))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã dùng hết số lượt cho phép");

        verify(voucherUsageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Áp dụng voucher thất bại khi voucher đã hết tổng lượt sử dụng trên hệ thống")
    void applyVoucherToOrder_TotalUsageLimitReached_ThrowsBadRequest() {
        Order order = Order.builder().orderCode("ORD-TEST").build();
        samplePercentVoucher.setUsedCount(100); // 100/100

        when(voucherRepository.findByCodeWithLock("SUMMER20")).thenReturn(Optional.of(samplePercentVoucher));

        assertThatThrownBy(() -> voucherService.applyVoucherToOrder(userId, "SUMMER20", BigDecimal.valueOf(300000), order))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã hết lượt sử dụng");
    }

    @Test
    @DisplayName("Hoàn lại lượt sử dụng voucher khi đơn hàng bị hủy")
    void rollbackVoucherUsage_Success() {
        UUID orderId = UUID.randomUUID();

        VoucherUsage usage = VoucherUsage.builder()
                .usageId(UUID.randomUUID())
                .voucher(samplePercentVoucher)
                .order(Order.builder().orderId(orderId).build())
                .status(VoucherUsageStatus.RESERVED)
                .build();

        when(voucherUsageRepository.findByOrderOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(usage));

        voucherService.rollbackVoucherUsage(orderId);

        assertThat(usage.getStatus()).isEqualTo(VoucherUsageStatus.CANCELLED);
        assertThat(usage.getCancelledAt()).isNotNull();

        verify(voucherRepository).decrementUsedCount(samplePercentVoucher.getVoucherId());
        verify(voucherUsageRepository).save(usage);
    }

    @Test
    @DisplayName("Chốt sử dụng voucher khi đơn hàng được giao thành công")
    void confirmVoucherUsage_Success() {
        UUID orderId = UUID.randomUUID();

        VoucherUsage usage = VoucherUsage.builder()
                .usageId(UUID.randomUUID())
                .voucher(samplePercentVoucher)
                .order(Order.builder().orderId(orderId).build())
                .status(VoucherUsageStatus.RESERVED)
                .build();

        when(voucherUsageRepository.findByOrderOrderIdAndStatusIn(eq(orderId), any()))
                .thenReturn(Optional.of(usage));

        voucherService.confirmVoucherUsage(orderId);

        assertThat(usage.getStatus()).isEqualTo(VoucherUsageStatus.USED);
        assertThat(usage.getUsedAt()).isNotNull();
        verify(voucherUsageRepository).save(usage);
    }

    @Test
    @DisplayName("Xóa voucher đã có lượt sử dụng -> Tự động chuyển sang Soft Delete")
    void deleteVoucher_AlreadyUsed_PerformsSoftDelete() {
        UUID voucherId = sampleFixedVoucher.getVoucherId();
        sampleFixedVoucher.setUsedCount(5);

        when(voucherRepository.findById(voucherId)).thenReturn(Optional.of(sampleFixedVoucher));

        voucherService.deleteVoucher(voucherId);

        assertThat(sampleFixedVoucher.getIsActive()).isFalse();
        verify(voucherRepository).save(sampleFixedVoucher);
        verify(voucherRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Tạo voucher giới hạn mặt hàng thất bại khi không cung cấp danh sách sản phẩm")
    void createVoucher_SpecificProductsWithoutList_ThrowsBadRequest() {
        VoucherDTO.CreateVoucherRequest request = new VoucherDTO.CreateVoucherRequest(
                "SPECIFIC1", "Chỉ áp dụng vài món", null,
                DiscountType.PERCENTAGE, BigDecimal.valueOf(10), null,
                BigDecimal.ZERO, 100, 1,
                OffsetDateTime.now(), OffsetDateTime.now().plusDays(2), true,
                VoucherTargetType.SPECIFIC_PRODUCTS, Collections.emptySet(), null
        );

        when(voucherRepository.existsByCodeIgnoreCase("SPECIFIC1")).thenReturn(false);

        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phải chọn ít nhất một sản phẩm");
    }

    @Test
    @DisplayName("Kiểm tra thử voucher theo sản phẩm cụ thể: Chỉ tính giảm trên các món thỏa điều kiện")
    void validateVoucher_SpecificProducts_CalculatesOnlyOnEligibleItems() {
        UUID eligibleProductId = UUID.randomUUID();
        UUID otherProductId = UUID.randomUUID();

        Voucher targetVoucher = Voucher.builder()
                .voucherId(UUID.randomUUID())
                .code("TARGETPROD")
                .name("Chỉ giảm trên sản phẩm A")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20)) // Giảm 20%
                .maxDiscountAmount(BigDecimal.valueOf(100000))
                .minOrderValue(BigDecimal.valueOf(100000))
                .usageLimit(100)
                .usageLimitPerUser(1)
                .usedCount(0)
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(10))
                .isActive(true)
                .targetType(VoucherTargetType.SPECIFIC_PRODUCTS)
                .applicableProductIds(Set.of(eligibleProductId))
                .build();

        // Giỏ hàng gồm: Món thỏa mãn (200k), món không thỏa mãn (300k). Tổng giỏ hàng = 500k.
        List<VoucherDTO.CartItemTargetInfo> items = List.of(
                new VoucherDTO.CartItemTargetInfo(eligibleProductId, null, BigDecimal.valueOf(200000)),
                new VoucherDTO.CartItemTargetInfo(otherProductId, null, BigDecimal.valueOf(300000))
        );
        VoucherDTO.ValidateVoucherRequest request = new VoucherDTO.ValidateVoucherRequest(
                "TARGETPROD", BigDecimal.valueOf(500000), items
        );

        when(voucherRepository.findByCodeIgnoreCase("TARGETPROD")).thenReturn(Optional.of(targetVoucher));
        when(voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                eq(targetVoucher.getVoucherId()), eq(userId), any()
        )).thenReturn(0);

        VoucherDTO.VoucherValidationResponse response = voucherService.validateVoucher(userId, request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.eligibleSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(200000));
        // Giảm 20% của 200k = 40k (không phải 20% của 500k = 100k)
        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(40000));
        assertThat(response.finalSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(460000));
    }

    @Test
    @DisplayName("Kiểm tra thử voucher theo sản phẩm: Thất bại khi giỏ hàng không có sản phẩm nào thỏa mãn")
    void validateVoucher_SpecificProducts_NoEligibleItems_ReturnsInvalid() {
        UUID eligibleProductId = UUID.randomUUID();
        UUID otherProductId = UUID.randomUUID();

        Voucher targetVoucher = Voucher.builder()
                .voucherId(UUID.randomUUID())
                .code("TARGETPROD")
                .targetType(VoucherTargetType.SPECIFIC_PRODUCTS)
                .applicableProductIds(Set.of(eligibleProductId))
                .minOrderValue(BigDecimal.valueOf(50000))
                .usageLimit(100)
                .usageLimitPerUser(1)
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(10))
                .isActive(true)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(20000))
                .build();

        List<VoucherDTO.CartItemTargetInfo> items = List.of(
                new VoucherDTO.CartItemTargetInfo(otherProductId, null, BigDecimal.valueOf(300000))
        );
        VoucherDTO.ValidateVoucherRequest request = new VoucherDTO.ValidateVoucherRequest(
                "TARGETPROD", BigDecimal.valueOf(300000), items
        );

        when(voucherRepository.findByCodeIgnoreCase("TARGETPROD")).thenReturn(Optional.of(targetVoucher));

        VoucherDTO.VoucherValidationResponse response = voucherService.validateVoucher(userId, request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.message()).containsIgnoringCase("không có sản phẩm nào trong giỏ hàng thỏa mãn");
        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Áp dụng voucher theo danh mục: Chỉ tính giảm trên sản phẩm thuộc danh mục và chốt giữ chỗ")
    void applyVoucherToOrder_SpecificCategories_Success() {
        UUID eligibleCatId = UUID.randomUUID();
        UUID otherCatId = UUID.randomUUID();

        Voucher catVoucher = Voucher.builder()
                .voucherId(UUID.randomUUID())
                .code("CATFASHION")
                .targetType(VoucherTargetType.SPECIFIC_CATEGORIES)
                .applicableCategoryIds(Set.of(eligibleCatId))
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10)) // 10%
                .minOrderValue(BigDecimal.valueOf(100000))
                .usageLimit(50)
                .usageLimitPerUser(1)
                .usedCount(0)
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(5))
                .isActive(true)
                .build();

        Order order = Order.builder().orderId(UUID.randomUUID()).orderCode("ORD-CAT-01").build();
        List<VoucherDTO.CartItemTargetInfo> targetItems = List.of(
                new VoucherDTO.CartItemTargetInfo(UUID.randomUUID(), eligibleCatId, BigDecimal.valueOf(300000)),
                new VoucherDTO.CartItemTargetInfo(UUID.randomUUID(), otherCatId, BigDecimal.valueOf(500000))
        );

        when(voucherRepository.findByCodeWithLock("CATFASHION")).thenReturn(Optional.of(catVoucher));
        when(voucherUsageRepository.countByVoucherVoucherIdAndUserUserIdAndStatusIn(
                eq(catVoucher.getVoucherId()), eq(userId), any()
        )).thenReturn(0);
        when(voucherRepository.incrementUsedCountIfAvailable(catVoucher.getVoucherId())).thenReturn(1);
        when(userRepository.getReferenceById(userId)).thenReturn(sampleUser);

        BigDecimal discount = voucherService.applyVoucherToOrder(userId, "CATFASHION", BigDecimal.valueOf(800000), targetItems, order);

        // 10% của 300k (món thỏa danh mục) = 30k
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(order.getVoucherCode()).isEqualTo("CATFASHION");
        verify(voucherUsageRepository).save(any(VoucherUsage.class));
    }

    @Test
    @DisplayName("Áp dụng voucher thất bại khi tổng tiền các món thỏa điều kiện chưa đạt minOrderValue")
    void applyVoucherToOrder_SpecificProducts_EligibleSubtotalBelowMinOrder_ThrowsBadRequest() {
        UUID eligibleProductId = UUID.randomUUID();
        UUID otherProductId = UUID.randomUUID();

        Voucher targetVoucher = Voucher.builder()
                .voucherId(UUID.randomUUID())
                .code("TARGETMIN")
                .targetType(VoucherTargetType.SPECIFIC_PRODUCTS)
                .applicableProductIds(Set.of(eligibleProductId))
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(30000))
                .minOrderValue(BigDecimal.valueOf(200000)) // Cần tối thiểu 200k món thỏa mãn
                .usageLimit(50)
                .usageLimitPerUser(1)
                .usedCount(0)
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(5))
                .isActive(true)
                .build();

        Order order = Order.builder().orderId(UUID.randomUUID()).orderCode("ORD-MIN-01").build();
        // Món thỏa mãn chỉ 150k < 200k, dù tổng cả đơn là 650k
        List<VoucherDTO.CartItemTargetInfo> targetItems = List.of(
                new VoucherDTO.CartItemTargetInfo(eligibleProductId, null, BigDecimal.valueOf(150000)),
                new VoucherDTO.CartItemTargetInfo(otherProductId, null, BigDecimal.valueOf(500000))
        );

        when(voucherRepository.findByCodeWithLock("TARGETMIN")).thenReturn(Optional.of(targetVoucher));

        assertThatThrownBy(() -> voucherService.applyVoucherToOrder(userId, "TARGETMIN", BigDecimal.valueOf(650000), targetItems, order))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa đạt giá trị tối thiểu");

        verify(voucherUsageRepository, never()).save(any());
    }
}
