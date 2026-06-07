package com.hanspoon.backend_api.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답 표준 래퍼. Spring {@link Page} 를 그대로 노출하지 않고 필요한 메타만 평면화한다.
 *
 * @param items 현재 페이지 항목
 * @param page 현재 페이지 번호(0-base)
 * @param size 페이지 크기
 * @param totalElements 전체 개수
 * @param totalPages 전체 페이지 수
 */
@Schema(description = "목록 응답(페이지)")
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    /** {@link Page} 의 엔티티를 {@code mapper} 로 변환해 래핑한다. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
