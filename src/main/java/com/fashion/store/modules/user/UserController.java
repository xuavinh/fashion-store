package com.fashion.store.modules.user;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO.UserResponse>> getCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên làm việc đã hết hạn"));
        return ResponseEntity.ok(ApiResponse.success(userService.getUserProfile(userId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<UserDTO.UserResponse>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserProfile(id)));
    }
}
