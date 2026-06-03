package com.hanspoon.backend_api.domain.user.controller;

import com.hanspoon.backend_api.domain.user.dto.ProfileRequest;
import com.hanspoon.backend_api.domain.user.dto.ProfileResponse;
import com.hanspoon.backend_api.domain.user.dto.UpdateUserRequest;
import com.hanspoon.backend_api.domain.user.dto.UserMeResponse;
import com.hanspoon.backend_api.domain.user.service.UserProfileService;
import com.hanspoon.backend_api.domain.user.service.UserService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;

    public UserController(UserService userService, UserProfileService userProfileService) {
        this.userService = userService;
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserMeResponse getMe(@CurrentUser String userId) {
        return userService.getMe(UUID.fromString(userId));
    }

    @PatchMapping("/me")
    public UserMeResponse updateMe(@CurrentUser String userId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateMe(UUID.fromString(userId), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@CurrentUser String userId) {
        userService.deleteMe(UUID.fromString(userId));
    }

    @PostMapping("/me/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse createProfile(@CurrentUser String userId, @Valid @RequestBody ProfileRequest request) {
        return userProfileService.createProfile(UUID.fromString(userId), request);
    }

    @GetMapping("/me/profile")
    public ProfileResponse getProfile(@CurrentUser String userId) {
        return userProfileService.getProfile(UUID.fromString(userId));
    }

    @PatchMapping("/me/profile")
    public ProfileResponse updateProfile(@CurrentUser String userId, @Valid @RequestBody ProfileRequest request) {
        return userProfileService.updateProfile(UUID.fromString(userId), request);
    }
}
