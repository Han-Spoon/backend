package com.hanspoon.backend_api.domain.review.service;

import com.hanspoon.backend_api.domain.review.dto.ReviewMenuRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewResponse;
import com.hanspoon.backend_api.domain.review.repository.ReviewRepository;
import com.hanspoon.backend_api.domain.review.repository.ReviewRepository.ItemRow;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanRecordRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 식사 후기. 식단 항목은 내 현재 프로필 기준. */
@Service
public class ReviewService {

    private static final Set<String> ANSWERS = Set.of("present", "absent", "unknown");

    private final ReviewRepository reviewRepository;
    private final StoreRepository storeRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final ScanRecordRepository scanRecordRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            StoreRepository storeRepository,
            ScanSessionRepository scanSessionRepository,
            ScanRecordRepository scanRecordRepository,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository) {
        this.reviewRepository = reviewRepository;
        this.storeRepository = storeRepository;
        this.scanSessionRepository = scanSessionRepository;
        this.scanRecordRepository = scanRecordRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    /** 스캔 후기는 그 스캔의 가게가 경로 가게와 같아야 함. 스캔당 1건. */
    @Transactional
    public ReviewResponse create(UUID userId, long storeId, ReviewRequest request) {
        storeRepository
                .findByIdAndStatus(storeId, StoreStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (request.scanId() != null) {
            checkScan(userId, storeId, request.scanId());
        }
        List<ItemRow> items = items(userId, request.scanId(), request.menus());

        UUID reviewId = UUID.randomUUID();
        try {
            reviewRepository.insertReview(reviewId, userId, storeId, request.scanId());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS, e);
        }
        reviewRepository.insertItems(reviewId, items);
        return reviewRepository.findOne(reviewId, userId).orElseThrow();
    }

    private void checkScan(UUID userId, long storeId, UUID scanId) {
        ScanSession session = scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        if (session.getScanStatus() != ScanStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SCAN_NOT_COMPLETED);
        }
        Long scanStoreId = session.getStoreId() != null
                ? session.getStoreId()
                : scanRecordRepository
                        .findById(scanId)
                        .map(ScanRecord::getAttachedStoreId)
                        .orElse(null);
        if (scanStoreId == null) {
            throw new BusinessException(ErrorCode.REVIEW_SCAN_STORE_REQUIRED);
        }
        if (scanStoreId != storeId) {
            throw new BusinessException(ErrorCode.REVIEW_STORE_MISMATCH);
        }
        if (reviewRepository.existsByScan(scanId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    /** 메뉴 줄 검증 → 저장 행. 이름 중복·빈 줄·내 식단 밖 항목·다른 스캔 메뉴는 400. */
    private List<ItemRow> items(UUID userId, UUID scanId, List<ReviewMenuRequest> menus) {
        Set<String> dietCodes = userProfileRepository
                .findByUserId(userId)
                .map(ReviewDietCodes::of)
                .orElse(Set.of());
        String locale = userRepository
                .findById(userId)
                .map(User::getLanguageCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<String> names = menus.stream().map(menu -> menu.name().strip()).toList();
        List<String> normalized = reviewRepository.normalizeNames(names);
        if (normalized.stream().anyMatch(name -> name == null || name.isEmpty())
                || new HashSet<>(normalized).size() != normalized.size()) {
            throw invalid("Menu names must be unique and not empty.");
        }
        checkScanMenus(scanId, menus);

        List<ItemRow> items = new ArrayList<>();
        for (int i = 0; i < menus.size(); i++) {
            ReviewMenuRequest menu = menus.get(i);
            Map<String, String> answers = answers(menu.answers(), dietCodes);
            String comment = menu.comment() == null || menu.comment().isBlank() ? null : menu.comment();
            if (answers.isEmpty() && comment == null) {
                throw invalid("Each menu needs answers or a comment.");
            }
            items.add(new ItemRow(
                    UUID.randomUUID(),
                    names.get(i),
                    menu.scanMenuId(),
                    comment,
                    comment == null ? null : locale,
                    answers));
        }
        return items;
    }

    private void checkScanMenus(UUID scanId, List<ReviewMenuRequest> menus) {
        Set<UUID> menuIds = new HashSet<>();
        menus.stream()
                .map(ReviewMenuRequest::scanMenuId)
                .filter(Objects::nonNull)
                .forEach(menuIds::add);
        if (menuIds.isEmpty()) {
            return;
        }
        if (scanId == null || !reviewRepository.findScanMenuIds(scanId, menuIds).containsAll(menuIds)) {
            throw invalid("scanMenuId must be a menu of the review scan.");
        }
    }

    private static Map<String, String> answers(Map<String, String> answers, Set<String> dietCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (answers == null) {
            return result;
        }
        answers.forEach((code, answer) -> {
            if (!dietCodes.contains(code) || !ANSWERS.contains(answer)) {
                throw invalid("Answers must use my diet items and present, absent or unknown.");
            }
            result.put(code, answer);
        });
        return result;
    }

    private static BusinessException invalid(String detail) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, detail);
    }
}
