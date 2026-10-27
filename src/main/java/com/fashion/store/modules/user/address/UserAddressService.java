package com.fashion.store.modules.user.address;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressService {

    private static final int MAX_ADDRESSES_PER_USER = 10;

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserAddressDTO.AddressResponse createAddress(UUID userId, UserAddressDTO.CreateAddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

        long existingCount = userAddressRepository.countByUserUserId(userId);
        if (existingCount >= MAX_ADDRESSES_PER_USER) {
            throw new BadRequestException("Bạn chỉ có thể lưu tối đa " + MAX_ADDRESSES_PER_USER + " địa chỉ nhận hàng.");
        }

        boolean makeDefault = existingCount == 0 || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            userAddressRepository.clearDefaultAddresses(userId);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .recipientName(request.recipientName().trim())
                .phoneNumber(request.phoneNumber().trim())
                .streetAddress(request.streetAddress().trim())
                .province(request.province() != null ? request.province().trim() : null)
                .district(request.district() != null ? request.district().trim() : null)
                .ward(request.ward() != null ? request.ward().trim() : null)
                .addressType(request.addressType() != null ? request.addressType() : AddressType.HOME)
                .isDefault(makeDefault)
                .build();

        UserAddress saved = userAddressRepository.save(address);
        log.info("Đã tạo địa chỉ mới: addressId={}, userId={}, isDefault={}", saved.getAddressId(), userId, makeDefault);
        return toResponse(saved);
    }

    @Transactional
    public UserAddressDTO.AddressResponse updateAddress(UUID userId, UUID addressId, UserAddressDTO.UpdateAddressRequest request) {
        UserAddress address = userAddressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ: " + addressId));

        if (Boolean.TRUE.equals(request.isDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            userAddressRepository.clearDefaultAddresses(userId);
            address.setIsDefault(true);
        }

        if (request.recipientName() != null && !request.recipientName().isBlank()) {
            address.setRecipientName(request.recipientName().trim());
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            address.setPhoneNumber(request.phoneNumber().trim());
        }
        if (request.streetAddress() != null && !request.streetAddress().isBlank()) {
            address.setStreetAddress(request.streetAddress().trim());
        }
        if (request.province() != null) {
            address.setProvince(request.province().trim());
        }
        if (request.district() != null) {
            address.setDistrict(request.district().trim());
        }
        if (request.ward() != null) {
            address.setWard(request.ward().trim());
        }
        if (request.addressType() != null) {
            address.setAddressType(request.addressType());
        }

        UserAddress updated = userAddressRepository.save(address);
        return toResponse(updated);
    }

    @Transactional
    public UserAddressDTO.AddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        UserAddress address = userAddressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ: " + addressId));

        userAddressRepository.clearDefaultAddresses(userId);
        address.setIsDefault(true);
        UserAddress updated = userAddressRepository.save(address);
        return toResponse(updated);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        UserAddress address = userAddressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ: " + addressId));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        userAddressRepository.delete(address);

        if (wasDefault) {
            // Tự động gán địa chỉ còn lại đầu tiên làm mặc định mới
            List<UserAddress> remaining = userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                UserAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                userAddressRepository.save(newDefault);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UserAddressDTO.AddressResponse> getMyAddresses(UUID userId) {
        return userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAddressDTO.AddressResponse getDefaultAddress(UUID userId) {
        return userAddressRepository.findByUserUserIdAndIsDefaultTrue(userId)
                .or(() -> userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream().findFirst())
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa có địa chỉ nhận hàng nào"));
    }

    @Transactional(readOnly = true)
    public UserAddressDTO.AddressResponse getAddressById(UUID userId, UUID addressId) {
        return userAddressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ: " + addressId));
    }

    public UserAddressDTO.AddressResponse toResponse(UserAddress address) {
        String fullAddress = Stream.of(address.getStreetAddress(), address.getWard(), address.getDistrict(), address.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));

        return new UserAddressDTO.AddressResponse(
                address.getAddressId(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getStreetAddress(),
                address.getProvince(),
                address.getDistrict(),
                address.getWard(),
                fullAddress,
                address.getAddressType(),
                address.getIsDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
