package com.hanspoon.backend_api.domain.upload.dto;

import java.time.Instant;

/** 객체 읽기 presigned URL과 만료 시각. */
public record ReadUrl(String url, Instant expiresAt) {}
