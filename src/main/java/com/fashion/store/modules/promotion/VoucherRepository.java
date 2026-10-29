package com.fashion.store.modules.promotion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE UPPER(v.code) = UPPER(:code)")
    Optional<Voucher> findByCodeWithLock(@Param("code") String code);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount + 1 WHERE v.voucherId = :voucherId AND v.usedCount < v.usageLimit")
    int incrementUsedCountIfAvailable(@Param("voucherId") UUID voucherId);

    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount - 1 WHERE v.voucherId = :voucherId AND v.usedCount > 0")
    int decrementUsedCount(@Param("voucherId") UUID voucherId);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT v FROM Voucher v WHERE v.isActive = true AND v.startDate <= :now AND v.endDate >= :now AND v.usedCount < v.usageLimit ORDER BY v.endDate ASC")
    List<Voucher> findActiveVouchers(@Param("now") OffsetDateTime now);
}
