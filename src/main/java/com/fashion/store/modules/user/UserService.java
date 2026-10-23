package com.fashion.store.modules.user;

import com.fashion.store.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    public UserDTO.UserResponse getUserProfile(UUID userId) {
        User user = getUserById(userId);
        return toResponse(user);
    }

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        User user = getUserById(userId);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void activateUser(UUID userId) {
        User user = getUserById(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void banUser(UUID userId) {
        User user = getUserById(userId);
        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
    }

    public UserDTO.UserResponse toResponse(User user) {
        UserDTO.UserStyleProfileResponse styleProfileResponse = null;
        if (user.getStyleProfile() != null) {
            UserStyleProfile sp = user.getStyleProfile();
            styleProfileResponse = new UserDTO.UserStyleProfileResponse(
                    sp.getPreferredStyles(),
                    sp.getPreferredColors(),
                    sp.getBodyMeasurements(),
                    sp.getDefaultSize(),
                    sp.getAgeGroup(),
                    sp.getGenderPref()
            );
        }

        return new UserDTO.UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                styleProfileResponse,
                user.getCreatedAt()
        );
    }
}
