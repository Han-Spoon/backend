package com.hanspoon.backend_api.global.common;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
		boolean success,
		T data,
		String code,
		String message,
		OffsetDateTime timestamp) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, "OK", "success", OffsetDateTime.now());
	}

	public static ApiResponse<Void> ok() {
		return ok(null);
	}

	public static ApiResponse<Void> error(String code, String message) {
		return new ApiResponse<>(false, null, code, message, OffsetDateTime.now());
	}
}
