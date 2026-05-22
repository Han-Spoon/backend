package com.hanspoon.backend_api.global.exception;

import java.net.URI;
import java.time.OffsetDateTime;

import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class BusinessException extends ErrorResponseException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage(), null);
	}

	public BusinessException(ErrorCode errorCode, String detail) {
		this(errorCode, detail, null);
	}

	public BusinessException(ErrorCode errorCode, Throwable cause) {
		this(errorCode, errorCode.getMessage(), cause);
	}

	public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
		super(errorCode.getStatus(), createProblemDetail(errorCode, detail), cause);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	private static ProblemDetail createProblemDetail(ErrorCode errorCode, String detail) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail);
		problemDetail.setType(URI.create("https://api.han-spoon.com/problems/" + errorCode.getCode()));
		problemDetail.setTitle(errorCode.getMessage());
		problemDetail.setProperty("code", errorCode.getCode());
		problemDetail.setProperty("timestamp", OffsetDateTime.now());
		return problemDetail;
	}
}
