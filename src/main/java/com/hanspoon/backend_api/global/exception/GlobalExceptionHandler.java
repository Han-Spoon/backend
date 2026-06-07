package com.hanspoon.backend_api.global.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException exception, WebRequest request) {
        ProblemDetail problemDetail = exception.getBody();
        enrich(problemDetail, exception.getErrorCode().getCode(), request);
        return ResponseEntity.status(exception.getStatusCode()).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception, WebRequest request) {
        log.error("Unhandled exception", exception);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(problemType(ErrorCode.INTERNAL_SERVER_ERROR.getCode()));
        enrich(problemDetail, ErrorCode.INTERNAL_SERVER_ERROR.getCode(), request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, "Request validation failed.");
        problemDetail.setTitle("Invalid Request");
        problemDetail.setType(problemType(ErrorCode.INVALID_REQUEST.getCode()));
        problemDetail.setProperty("errors", fieldViolations(exception));
        enrich(problemDetail, ErrorCode.INVALID_REQUEST.getCode(), request);

        return handleExceptionInternal(exception, problemDetail, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ProblemDetail problemDetail = body instanceof ProblemDetail detail
                ? detail
                : ProblemDetail.forStatusAndDetail(statusCode, resolveDetail(exception, statusCode));

        if (problemDetail.getTitle() == null) {
            problemDetail.setTitle(resolveTitle(statusCode));
        }
        if (problemDetail.getType() == null || URI.create("about:blank").equals(problemDetail.getType())) {
            problemDetail.setType(problemType(resolveCode(statusCode)));
        }
        enrich(problemDetail, resolveCode(statusCode), request);

        return super.handleExceptionInternal(exception, problemDetail, headers, statusCode, request);
    }

    private List<FieldViolation> fieldViolations(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
    }

    private FieldViolation toFieldViolation(FieldError fieldError) {
        return new FieldViolation(fieldError.getField(), fieldError.getRejectedValue(), fieldError.getDefaultMessage());
    }

    private void enrich(ProblemDetail problemDetail, String code, WebRequest request) {
        // 이미 설정된 code(예: 검증 실패의 INVALID_REQUEST)는 보존한다.
        // handleExceptionInternal 이 마지막에 다시 enrich 하면서 HTTP_xxx 로 덮어쓰는 것을 방지.
        Map<String, Object> properties = problemDetail.getProperties();
        if (properties == null || properties.get("code") == null) {
            problemDetail.setProperty("code", code);
        }
        problemDetail.setProperty("timestamp", OffsetDateTime.now());
        problemDetail.setProperty("traceId", resolveTraceId());
        problemDetail.setInstance(URI.create(requestPath(request)));
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank())
                ? traceId
                : UUID.randomUUID().toString();
    }

    private URI problemType(String code) {
        return URI.create("https://api.han-spoon.com/problems/" + code);
    }

    private String resolveCode(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return ErrorCode.INTERNAL_SERVER_ERROR.getCode();
        }
        return "HTTP_" + statusCode.value();
    }

    private String resolveDetail(Exception exception, HttpStatusCode statusCode) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "HTTP request failed with status " + statusCode.value() + ".";
    }

    private String resolveTitle(HttpStatusCode statusCode) {
        return HttpStatus.resolve(statusCode.value()) == null
                ? "HTTP " + statusCode.value()
                : HttpStatus.valueOf(statusCode.value()).getReasonPhrase();
    }

    private String requestPath(WebRequest request) {
        return request.getDescription(false).replaceFirst("^uri=", "");
    }

    private record FieldViolation(String field, Object rejectedValue, String reason) {}
}
