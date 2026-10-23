package com.fashion.store.modules.auth;

import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.modules.user.UserDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDTO.AuthResponse>> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        AuthDTO.TokenResult result = authService.register(request);
        ResponseCookie cookie = authService.createRefreshTokenCookie(result.refreshToken());

        AuthDTO.AuthResponse body = AuthDTO.AuthResponse.of(result.accessToken(), result.expiresInMs(), result.user());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Đăng ký tài khoản thành công", body));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDTO.AuthResponse>> login(@Valid @RequestBody AuthDTO.LoginRequest request) {
        AuthDTO.TokenResult result = authService.login(request);
        ResponseCookie cookie = authService.createRefreshTokenCookie(result.refreshToken());

        AuthDTO.AuthResponse body = AuthDTO.AuthResponse.of(result.accessToken(), result.expiresInMs(), result.user());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Đăng nhập thành công", body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthDTO.AuthResponse>> refresh(
            @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String cookieRefreshToken,
            HttpServletRequest request
    ) {
        String token = cookieRefreshToken;
        if (token == null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthService.REFRESH_COOKIE_NAME.equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        AuthDTO.TokenResult result = authService.refresh(token);
        ResponseCookie cookie = authService.createRefreshTokenCookie(result.refreshToken());

        AuthDTO.AuthResponse body = AuthDTO.AuthResponse.of(result.accessToken(), result.expiresInMs(), result.user());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Làm mới Access Token thành công", body));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        ResponseCookie clearCookie = authService.createClearRefreshCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success("Đăng xuất thành công", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO.UserResponse>> getCurrentUser() {
        UserDTO.UserResponse user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
