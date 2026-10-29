package com.fashion.store.modules.promotion;

import com.fashion.store.modules.order.Order;
import com.fashion.store.modules.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "voucher_usages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "usage_id", nullable = false, updatable = false)
    private UUID usageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    @JsonIgnore
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VoucherUsageStatus status = VoucherUsageStatus.RESERVED;

    @Column(name = "reserved_at", nullable = false)
    @Builder.Default
    private OffsetDateTime reservedAt = OffsetDateTime.now();

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
}
