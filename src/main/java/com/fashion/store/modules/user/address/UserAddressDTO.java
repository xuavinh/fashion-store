package com.fashion.store.modules.user.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserAddressDTO {

    public record CreateAddressRequest(
            @NotBlank(message = "Tên người nhận không được để trống")
            @Size(min = 2, max = 100, message = "Tên người nhận phải từ 2 đến 100 ký tự")
            String recipientName,

            @NotBlank(message = "Số điện thoại không được để trống")
            @Pattern(
                    regexp = "^(|(0|\\+84)[35789]\\d{8})$",
                    message = "Số điện thoại nhận hàng không đúng định dạng"
            )
            String phoneNumber,

            @NotBlank(message = "Địa chỉ chi tiết (số nhà, tên đường) không được để trống")
            @Size(max = 255, message = "Địa chỉ chi tiết không được quá 255 ký tự")
            String streetAddress,

            String province,
            String district,
            String ward,
            AddressType addressType,
            Boolean isDefault
    ) {}

    public record UpdateAddressRequest(
            String recipientName,
            @Pattern(
                    regexp = "^(|(0|\\+84)[35789]\\d{8})$",
                    message = "Số điện thoại nhận hàng không đúng định dạng"
            )
            String phoneNumber,
            String streetAddress,
            String province,
            String district,
            String ward,
            AddressType addressType,
            Boolean isDefault
    ) {}

    public record AddressResponse(
            UUID addressId,
            String recipientName,
            String phoneNumber,
            String streetAddress,
            String province,
            String district,
            String ward,
            String fullAddress,
            AddressType addressType,
            Boolean isDefault,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
