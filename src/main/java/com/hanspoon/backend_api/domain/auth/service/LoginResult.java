package com.hanspoon.backend_api.domain.auth.service;

import com.hanspoon.backend_api.domain.user.entity.User;

public record LoginResult(String accessToken, String refreshToken, boolean hasProfile, boolean newUser, User user) {}
