package com.fashion.store.modules.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, UUID> {

    int countByVoucherVoucherIdAndUserUserIdAndStatusIn(UUID voucherId, UUID userId, Collection<VoucherUsageStatus> statuses);

    Optional<VoucherUsage> findByOrderOrderId(UUID orderId);

    Optional<VoucherUsage> findByOrderOrderIdAndStatusIn(UUID orderId, Collection<VoucherUsageStatus> statuses);
}
