package com.fashion.store.modules.auth;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ConflictException;
import com.fashion.store.common.security.JwtService;
import com.fashion.store.common.security.SecurityUtils;
import com.fashion.store.modules.user.Role;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserDTO;
import com.fashion.store.modules.user.UserRepository;
import com.fashion.store.modules.user.UserService;
import com.fashion.store.modules.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthDTO.TokenResult register(AuthDTO.RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email '" + normalizedEmail + "' đã được đăng ký trong hệ thống");
        }

        User user = User.builder()
                .userId(UUID.randomUUID())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        return new AuthDTO.TokenResult(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration(),
                userService.toResponse(savedUser)
        );
    }

    public AuthDTO.TokenResult login(AuthDTO.LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (AuthenticationException e) {
            log.warn("Đăng nhập thất bại cho email {}: {}", normalizedEmail, e.getMessage());
            throw new BadRequestException("Email hoặc mật khẩu không chính xác");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Email hoặc mật khẩu không chính xác"));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BadRequestException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ bộ phận hỗ trợ.");
        }

        // tam thoi
        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthDTO.TokenResult(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration(),
                userService.toResponse(user)
        );
    }

    public AuthDTO.TokenResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token không tồn tại trong yêu cầu");
        }

        try {
            String email = jwtService.extractUsername(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadRequestException("Người dùng không tồn tại"));

            if (user.getStatus() == UserStatus.BANNED) {
                throw new BadRequestException("Tài khoản đã bị khóa");
            }

            // Sinh access token mới và rotate refresh token
            String newAccessToken = jwtService.generateAccessToken(user);
            String newRefreshToken = jwtService.generateRefreshToken(user);

            return new AuthDTO.TokenResult(
                    newAccessToken,
                    newRefreshToken,
                    jwtService.getAccessTokenExpiration(),
                    userService.toResponse(user)
            );
        } catch (Exception e) {
            log.warn("Lỗi khi giải mã refresh token: {}", e.getMessage());
            throw new BadRequestException("Refresh token không hợp lệ hoặc đã hết hạn");
        }
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false) // Đặt true nếu chạy HTTPS trên production
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(jwtService.getRefreshTokenExpiration() / 1000)
                .build();
    }

    public ResponseCookie createClearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    public UserDTO.UserResponse getCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"));

        return userService.getUserProfile(userId);
    }
}
