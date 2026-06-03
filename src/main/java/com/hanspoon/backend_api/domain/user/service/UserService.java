package com.hanspoon.backend_api.domain.user.service;

import com.hanspoon.backend_api.domain.user.dto.UpdateUserRequest;
import com.hanspoon.backend_api.domain.user.dto.UserMeResponse;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserService(UserRepository userRepository, UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserMeResponse.of(user, userProfileRepository.existsByUserId(userId));
    }

    @Transactional
    public UserMeResponse updateMe(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeLanguage(request.languageCode());
        user.changeNickname(request.nickname());
        return UserMeResponse.of(user, userProfileRepository.existsByUserId(userId));
    }

    @Transactional
    public void deleteMe(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // FK ON DELETE CASCADE: auth_identities/sessions/profiles/allergies 까지 함께 삭제된다.
        userRepository.deleteById(userId);
    }
}
