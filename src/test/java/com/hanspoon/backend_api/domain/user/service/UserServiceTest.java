package com.hanspoon.backend_api.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.user.dto.UpdateUserRequest;
import com.hanspoon.backend_api.domain.user.dto.UserMeResponse;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getMe_returnsUserWithProfileFlag() {
        User user = User.create("me@example.com", "Me", "ko");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(true);

        UserMeResponse response = userService.getMe(user.getId());

        assertThat(response.email()).isEqualTo("me@example.com");
        assertThat(response.languageCode()).isEqualTo("ko");
        assertThat(response.hasProfile()).isTrue();
    }

    @Test
    void getMe_whenMissing_throwsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateMe_changesNicknameAndLanguage() {
        User user = User.create("me@example.com", "Old", "en");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(false);

        UserMeResponse response = userService.updateMe(user.getId(), new UpdateUserRequest("ar", "NewName"));

        assertThat(response.languageCode()).isEqualTo("ar");
        assertThat(response.nickname()).isEqualTo("NewName");
        assertThat(response.hasProfile()).isFalse();
    }

    @Test
    void updateMe_blankFields_keepExistingValues() {
        User user = User.create("me@example.com", "Keep", "ko");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userProfileRepository.existsByUserId(user.getId())).thenReturn(false);

        UserMeResponse response = userService.updateMe(user.getId(), new UpdateUserRequest(null, null));

        assertThat(response.languageCode()).isEqualTo("ko");
        assertThat(response.nickname()).isEqualTo("Keep");
    }

    @Test
    void deleteMe_whenExists_deletes() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.deleteMe(userId);

        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteMe_whenMissing_throwsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteMe(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).deleteById(userId);
    }
}
