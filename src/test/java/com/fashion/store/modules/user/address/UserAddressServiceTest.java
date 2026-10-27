package com.fashion.store.modules.user.address;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private UserRepository userRepository;

    private UserAddressService userAddressService;

    private UUID userId;
    private UUID addressId;
    private User sampleUser;
    private UserAddress sampleAddress;

    @BeforeEach
    void setUp() {
        userAddressService = new UserAddressService(userAddressRepository, userRepository);

        userId = UUID.randomUUID();
        addressId = UUID.randomUUID();

        sampleUser = User.builder()
                .userId(userId)
                .email("user@gmail.com")
                .fullName("Nguyen Van A")
                .phone("0912345678")
                .build();

        sampleAddress = UserAddress.builder()
                .addressId(addressId)
                .user(sampleUser)
                .recipientName("Nguyen Van A")
                .phoneNumber("0912345678")
                .streetAddress("123 Le Loi")
                .province("TP.HCM")
                .district("Quan 1")
                .ward("Ben Nghe")
                .addressType(AddressType.HOME)
                .isDefault(true)
                .build();
    }

    @Test
    @DisplayName("Thêm địa chỉ đầu tiên -> Tự động đặt làm địa chỉ mặc định")
    void createAddress_FirstAddress_AutomaticallySetAsDefault() {
        UserAddressDTO.CreateAddressRequest request = new UserAddressDTO.CreateAddressRequest(
                "Nguyen Van A", "0912345678", "123 Le Loi", "TP.HCM", "Quan 1", "Ben Nghe",
                AddressType.HOME, false // client truyền false nhưng vì là địa chỉ đầu tiên nên tự động true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userAddressRepository.countByUserUserId(userId)).thenReturn(0L);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress a = inv.getArgument(0);
            a.setAddressId(addressId);
            return a;
        });

        UserAddressDTO.AddressResponse response = userAddressService.createAddress(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.isDefault()).isTrue();
        assertThat(response.fullAddress()).contains("123 Le Loi", "Ben Nghe", "Quan 1", "TP.HCM");
        verify(userAddressRepository).clearDefaultAddresses(userId);
        verify(userAddressRepository).save(any(UserAddress.class));
    }

    @Test
    @DisplayName("Thêm địa chỉ khi đã đạt giới hạn 10 địa chỉ -> Ném BadRequestException")
    void createAddress_MaxLimitExceeded_ThrowsBadRequest() {
        UserAddressDTO.CreateAddressRequest request = new UserAddressDTO.CreateAddressRequest(
                "Nguyen Van A", "0912345678", "123 Le Loi", null, null, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userAddressRepository.countByUserUserId(userId)).thenReturn(10L);

        assertThatThrownBy(() -> userAddressService.createAddress(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tối đa 10 địa chỉ");

        verify(userAddressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật địa chỉ thành công")
    void updateAddress_Success() {
        UserAddressDTO.UpdateAddressRequest request = new UserAddressDTO.UpdateAddressRequest(
                "Nguyen Van B", "0987654321", "456 Nguyen Hue", null, null, null, null, null
        );

        when(userAddressRepository.findByAddressIdAndUserUserId(addressId, userId))
                .thenReturn(Optional.of(sampleAddress));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddressDTO.AddressResponse response = userAddressService.updateAddress(userId, addressId, request);

        assertThat(response.recipientName()).isEqualTo("Nguyen Van B");
        assertThat(response.phoneNumber()).isEqualTo("0987654321");
        assertThat(response.streetAddress()).isEqualTo("456 Nguyen Hue");
    }

    @Test
    @DisplayName("Đặt làm địa chỉ mặc định thành công")
    void setDefaultAddress_Success() {
        sampleAddress.setIsDefault(false);
        when(userAddressRepository.findByAddressIdAndUserUserId(addressId, userId))
                .thenReturn(Optional.of(sampleAddress));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAddressDTO.AddressResponse response = userAddressService.setDefaultAddress(userId, addressId);

        assertThat(response.isDefault()).isTrue();
        verify(userAddressRepository).clearDefaultAddresses(userId);
    }

    @Test
    @DisplayName("Xóa địa chỉ mặc định -> Tự động gán địa chỉ còn lại làm mặc định mới")
    void deleteAddress_DefaultDeleted_AssignsNewDefault() {
        UUID remainingAddressId = UUID.randomUUID();
        UserAddress remainingAddress = UserAddress.builder()
                .addressId(remainingAddressId)
                .user(sampleUser)
                .recipientName("Nguyen Van C")
                .phoneNumber("0933333333")
                .streetAddress("789 Hai Ba Trung")
                .isDefault(false)
                .build();

        when(userAddressRepository.findByAddressIdAndUserUserId(addressId, userId))
                .thenReturn(Optional.of(sampleAddress));
        when(userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId))
                .thenReturn(new ArrayList<>(List.of(remainingAddress)));

        userAddressService.deleteAddress(userId, addressId);

        verify(userAddressRepository).delete(sampleAddress);
        verify(userAddressRepository).save(remainingAddress);
        assertThat(remainingAddress.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("Lấy địa chỉ mặc định thành công")
    void getDefaultAddress_Success() {
        when(userAddressRepository.findByUserUserIdAndIsDefaultTrue(userId))
                .thenReturn(Optional.of(sampleAddress));

        UserAddressDTO.AddressResponse response = userAddressService.getDefaultAddress(userId);

        assertThat(response).isNotNull();
        assertThat(response.addressId()).isEqualTo(addressId);
        assertThat(response.isDefault()).isTrue();
    }
}
