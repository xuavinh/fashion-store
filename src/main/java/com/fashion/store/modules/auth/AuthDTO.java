package com.fashion.store.modules.auth;

import com.fashion.store.modules.user.UserDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDTO {

    public record RegisterRequest(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            String email,

            @NotBlank(message = "Mật khẩu không được để trống")
            @Size(min = 8, max = 32, message = "Mật khẩu phải từ 8 đến 32 ký tự")
            @Pattern(
                    regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!._\\-*?]).*$",
                    message = "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt (@#$%^&+=!._-*?)"
            )
            String password,

            @NotBlank(message = "Họ và tên không được để trống")
            @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
            String fullName,

            @Pattern(
                    regexp = "^(|(0|\\+84)[35789]\\d{8})$",
                    message = "Số điện thoại không đúng định dạng (ví dụ: 0912345678 hoặc +84912345678)"
            )
            String phone
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            String email,

            @NotBlank(message = "Mật khẩu không được để trống")
            String password
    ) {}

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInMs,
            UserDTO.UserResponse user
    ) {
        public static AuthResponse of(String accessToken, long expiresInMs, UserDTO.UserResponse user) {
            return new AuthResponse(accessToken, "Bearer", expiresInMs, user);
        }
    }

    public record TokenResult(
            String accessToken,
            String refreshToken,
            long expiresInMs,
            UserDTO.UserResponse user
    ) {}
}
