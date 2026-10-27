package com.fashion.store.modules.user.address;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/addresses")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserAddressController {

    private final UserAddressService userAddressService;

    // Thêm địa chỉ nhận hàng mới
    @PostMapping
    public ResponseEntity<ApiResponse<UserAddressDTO.AddressResponse>> createAddress(
            @Valid @RequestBody UserAddressDTO.CreateAddressRequest request
    ) {
        UUID userId = getCurrentUserId();
        UserAddressDTO.AddressResponse response = userAddressService.createAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm địa chỉ nhận hàng thành công", response));
    }

    // Lấy danh sách sổ địa chỉ của người dùng hiện tại
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAddressDTO.AddressResponse>>> getMyAddresses() {
        UUID userId = getCurrentUserId();
        List<UserAddressDTO.AddressResponse> response = userAddressService.getMyAddresses(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa chỉ thành công", response));
    }

    // Lấy địa chỉ mặc định của người dùng
    @GetMapping("/default")
    public ResponseEntity<ApiResponse<UserAddressDTO.AddressResponse>> getDefaultAddress() {
        UUID userId = getCurrentUserId();
        UserAddressDTO.AddressResponse response = userAddressService.getDefaultAddress(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy địa chỉ mặc định thành công", response));
    }

    // Lấy chi tiết một địa chỉ
    @GetMapping("/{addressId}")
    public ResponseEntity<ApiResponse<UserAddressDTO.AddressResponse>> getAddressById(
            @PathVariable UUID addressId
    ) {
        UUID userId = getCurrentUserId();
        UserAddressDTO.AddressResponse response = userAddressService.getAddressById(userId, addressId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin địa chỉ thành công", response));
    }

    // Cập nhật thông tin địa chỉ
    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<UserAddressDTO.AddressResponse>> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody UserAddressDTO.UpdateAddressRequest request
    ) {
        UUID userId = getCurrentUserId();
        UserAddressDTO.AddressResponse response = userAddressService.updateAddress(userId, addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật địa chỉ thành công", response));
    }

    // Đặt một địa chỉ làm địa chỉ mặc định
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<UserAddressDTO.AddressResponse>> setDefaultAddress(
            @PathVariable UUID addressId
    ) {
        UUID userId = getCurrentUserId();
        UserAddressDTO.AddressResponse response = userAddressService.setDefaultAddress(userId, addressId);
        return ResponseEntity.ok(ApiResponse.success("Đã đặt làm địa chỉ mặc định", response));
    }

    // Xóa một địa chỉ
    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable UUID addressId
    ) {
        UUID userId = getCurrentUserId();
        userAddressService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(ApiResponse.success("Xóa địa chỉ thành công", null));
    }

    private UUID getCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"));
    }
}
