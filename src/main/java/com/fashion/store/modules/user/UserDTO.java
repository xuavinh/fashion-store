package com.fashion.store.modules.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserDTO {

    public record UpdateProfileRequest(
            @NotBlank(message = "Họ tên không được để trống")
            @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
            String fullName,

            @Pattern(
                    regexp = "^(|(0|\\+84)[35789]\\d{8})$",
                    message = "Số điện thoại không đúng định dạng (ví dụ: 0912345678 hoặc +84912345678)"
            )
            String phone
    ) {}

    public record UpdateStyleProfileRequest(
            List<String> preferredStyles,
            List<String> preferredColors,
            Map<String, Object> bodyMeasurements,
            Map<String, String> defaultSize,
            AgeGroup ageGroup,
            String genderPref
    ) {}

    public record UserResponse(
            UUID userId,
            String email,
            String fullName,
            String phone,
            Role role,
            UserStatus status,
            UserStyleProfileResponse styleProfile,
            OffsetDateTime createdAt
    ) {}

    public record UserStyleProfileResponse(
            List<String> preferredStyles,
            List<String> preferredColors,
            Map<String, Object> bodyMeasurements,
            Map<String, String> defaultSize,
            AgeGroup ageGroup,
            String genderPref
    ) {}
}
