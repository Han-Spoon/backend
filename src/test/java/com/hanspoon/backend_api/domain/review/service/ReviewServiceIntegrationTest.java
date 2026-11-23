package com.hanspoon.backend_api.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.review.dto.ReviewMenuRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewResponse;
import com.hanspoon.backend_api.domain.review.dto.ReviewUpdateRequest;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ReviewServiceIntegrationTest {

    private static final String HALAL = "religion:halal";
    private static final String SHRIMP = "allergy:shrimp";

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private MenuAnalysisRepository menuAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID userId;
    private long storeId;
    private UUID scanId;
    private UUID scanMenuId;

    /** 할랄 + 새우 알레르기(en) 사용자, 가게 스캔 완료 1건(메뉴 1개). */
    @BeforeEach
    void setUp() {
        userId = user("reviewer");
        userProfileRepository.save(UserProfile.create(
                userId, "US", false, false, null, ReligionType.HALAL, false, false, List.of(AllergyCode.SHRIMP)));
        storeId = insertStore("한스푼");
        scanId = completedScan(userId, storeId);
        scanMenuId = menu(scanId, "김치찌개");
    }

    @Test
    void createWithScanKeepsMenuOrderAndValues() {
        ReviewResponse review = reviewService.create(
                userId,
                storeId,
                new ReviewRequest(
                        scanId,
                        List.of(
                                new ReviewMenuRequest(
                                        "김치찌개", scanMenuId, Map.of(HALAL, "present", SHRIMP, "absent"), "Big pork"),
                                new ReviewMenuRequest(" 계란말이 ", null, Map.of(SHRIMP, "unknown"), "   "))));

        assertThat(review.storeId()).isEqualTo(storeId);
        assertThat(review.storeName()).isEqualTo("한스푼");
        assertThat(review.scanId()).isEqualTo(scanId);
        assertThat(review.mine()).isTrue();
        assertThat(review.hidden()).isFalse();
        assertThat(review.menus())
                .containsExactly(
                        new ReviewResponse.Menu(
                                "김치찌개", scanMenuId, Map.of(SHRIMP, "absent", HALAL, "present"), "Big pork", "en"),
                        new ReviewResponse.Menu("계란말이", null, Map.of(SHRIMP, "unknown"), null, null));
        assertThat(review.menus().get(0).answers().keySet()).containsExactly(SHRIMP, HALAL);
    }

    @Test
    void createWithoutScanAllowsCommentOnly() {
        ReviewResponse review =
                reviewService.create(userId, storeId, request(null, new ReviewMenuRequest("잡채", null, null, "Tasty")));

        assertThat(review.scanId()).isNull();
        assertThat(review.menus()).singleElement().satisfies(menu -> assertThat(menu.answers())
                .isEmpty());
    }

    @Test
    void secondReviewForScanConflicts() {
        reviewService.create(userId, storeId, request(scanId, line("김치찌개")));

        assertError(
                () -> reviewService.create(userId, storeId, request(scanId, line("김치찌개"))),
                ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    void scanStoreMustMatchPath() {
        long otherStore = insertStore("다른 가게");

        assertError(
                () -> reviewService.create(userId, otherStore, request(scanId, line("김치찌개"))),
                ErrorCode.REVIEW_STORE_MISMATCH);
    }

    @Test
    void storelessScanNeedsRecordStore() {
        ScanSession session = ScanSession.startWithoutStore(userId, "scans/" + userId + "/plain.jpg");
        session.applyPrepareResult(0, Instant.now(), "en");
        session.applyJudgeResult(List.of(), null);
        UUID plainScan = scanSessionRepository.saveAndFlush(session).getId();

        assertError(
                () -> reviewService.create(userId, storeId, request(plainScan, line("잡채"))),
                ErrorCode.REVIEW_SCAN_STORE_REQUIRED);

        jdbcTemplate.update(
                """
                insert into scan_records (scan_session_id, attached_store_id, attached_store_name_snapshot,
                                          attached_store_match_method, saved_at)
                values (?, ?, '한스푼', 'name_search', now())
                """,
                plainScan,
                storeId);
        assertThat(reviewService
                        .create(userId, storeId, request(plainScan, line("잡채")))
                        .scanId())
                .isEqualTo(plainScan);
    }

    @Test
    void scanMustBeMineAndCompleted() {
        assertError(
                () -> reviewService.create(user("other"), storeId, request(scanId, line("김치찌개"))),
                ErrorCode.SCAN_NOT_FOUND);

        UUID processing = scanSessionRepository
                .saveAndFlush(ScanSession.start(
                        userId, "scans/" + userId + "/p.jpg", storeId, "한스푼", StoreMatchMethod.NAME_SEARCH))
                .getId();
        assertError(
                () -> reviewService.create(userId, storeId, request(processing, line("잡채"))),
                ErrorCode.SCAN_NOT_COMPLETED);
    }

    @Test
    void inactiveStoreIsNotFound() {
        jdbcTemplate.update("update stores set status = 'inactive', inactive_at = now() where id = ?", storeId);

        assertError(() -> reviewService.create(userId, storeId, request(null, line("잡채"))), ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void invalidMenuLinesAreRejected() {
        assertInvalid(request(null, line("김치 찌개"), line("김치찌개")));
        assertInvalid(request(null, new ReviewMenuRequest("잡채", null, Map.of("allergy:egg", "present"), null)));
        assertInvalid(request(null, new ReviewMenuRequest("잡채", null, Map.of(HALAL, "yes"), null)));
        assertInvalid(request(null, new ReviewMenuRequest("잡채", null, Map.of(), " ")));
        assertInvalid(request(null, new ReviewMenuRequest("김치찌개", scanMenuId, Map.of(HALAL, "present"), null)));
        UUID otherScanMenu = menu(completedScan(userId, storeId), "제육");
        assertInvalid(request(scanId, new ReviewMenuRequest("제육", otherScanMenu, Map.of(HALAL, "present"), null)));
        assertThat(jdbcTemplate.queryForObject("select count(*) from meal_reviews", Integer.class))
                .isZero();
    }

    @Test
    void updateReplacesLinesInRequestOrder() {
        UUID reviewId = reviewService
                .create(
                        userId,
                        storeId,
                        request(
                                scanId,
                                new ReviewMenuRequest("김치찌개", scanMenuId, Map.of(HALAL, "present"), "Big pork"),
                                line("계란말이")))
                .reviewId();
        UUID kimchiLine = itemId(reviewId, "김치찌개");
        jdbcTemplate.update("update users set language_code = 'ko' where id = ?", userId);
        entityManager.clear();

        ReviewResponse updated = reviewService.update(
                userId,
                reviewId,
                new ReviewUpdateRequest(List.of(
                        new ReviewMenuRequest("잡채", null, null, "맛있어요"),
                        new ReviewMenuRequest("김치 찌개", scanMenuId, Map.of(SHRIMP, "absent"), "Big pork"))));

        assertThat(updated.menus())
                .containsExactly(
                        new ReviewResponse.Menu("잡채", null, Map.of(), "맛있어요", "ko"),
                        new ReviewResponse.Menu("김치 찌개", scanMenuId, Map.of(SHRIMP, "absent"), "Big pork", "en"));
        assertThat(itemId(reviewId, "김치찌개")).isEqualTo(kimchiLine);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from meal_review_items where review_id = ?", Integer.class, reviewId))
                .isEqualTo(2);
        assertThat(updated.scanId()).isEqualTo(scanId);
    }

    @Test
    void updateKeepsHidden() {
        UUID reviewId =
                reviewService.create(userId, storeId, request(null, line("잡채"))).reviewId();
        jdbcTemplate.update("update meal_reviews set hidden_at = now() where id = ?", reviewId);

        ReviewResponse updated = reviewService.update(userId, reviewId, new ReviewUpdateRequest(List.of(line("제육"))));

        assertThat(updated.hidden()).isTrue();
        assertThat(updated.menus()).extracting(ReviewResponse.Menu::name).containsExactly("제육");
    }

    @Test
    void updateRejectsOthersReviewAndScanMenuWithoutScan() {
        UUID reviewId =
                reviewService.create(userId, storeId, request(null, line("잡채"))).reviewId();

        assertError(
                () -> reviewService.update(user("other"), reviewId, new ReviewUpdateRequest(List.of(line("제육")))),
                ErrorCode.REVIEW_NOT_FOUND);
        assertError(
                () -> reviewService.update(
                        userId,
                        reviewId,
                        new ReviewUpdateRequest(
                                List.of(new ReviewMenuRequest("김치찌개", scanMenuId, Map.of(HALAL, "present"), null)))),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void deleteRemovesReviewAndReportsButKeepsScan() {
        UUID reviewId = reviewService
                .create(userId, storeId, request(scanId, line("김치찌개")))
                .reviewId();
        jdbcTemplate.update(
                "insert into meal_review_reports (id, review_id, reporter_user_id, reason) values (?, ?, ?, 'spam')",
                UUID.randomUUID(),
                reviewId,
                user("reporter"));

        assertError(() -> reviewService.delete(user("other"), reviewId), ErrorCode.REVIEW_NOT_FOUND);
        reviewService.delete(userId, reviewId);

        assertThat(jdbcTemplate.queryForObject(
                        """
                        select (select count(*) from meal_reviews) + (select count(*) from meal_review_items)
                             + (select count(*) from meal_review_answers) + (select count(*) from meal_review_reports)
                        """,
                        Integer.class))
                .isZero();
        assertThat(scanSessionRepository.existsById(scanId)).isTrue();
        assertError(() -> reviewService.delete(userId, reviewId), ErrorCode.REVIEW_NOT_FOUND);
    }

    private UUID itemId(UUID reviewId, String normalizedName) {
        return jdbcTemplate.queryForObject(
                "select id from meal_review_items where review_id = ? and menu_name_normalized = normalize_food_name(?)",
                UUID.class,
                reviewId,
                normalizedName);
    }

    private void assertInvalid(ReviewRequest request) {
        assertError(() -> reviewService.create(userId, storeId, request), ErrorCode.INVALID_REQUEST);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private static ReviewRequest request(UUID scanId, ReviewMenuRequest... menus) {
        return new ReviewRequest(scanId, List.of(menus));
    }

    private static ReviewMenuRequest line(String name) {
        return new ReviewMenuRequest(name, null, Map.of(HALAL, "absent"), null);
    }

    private UUID user(String nickname) {
        return userRepository
                .saveAndFlush(User.create(nickname + "+" + UUID.randomUUID() + "@example.com", nickname, "en"))
                .getId();
    }

    private UUID completedScan(UUID owner, long store) {
        ScanSession session = ScanSession.start(
                owner, "scans/" + owner + "/" + UUID.randomUUID() + ".jpg", store, "한스푼", StoreMatchMethod.NAME_SEARCH);
        session.applyPrepareResult(1, Instant.now(), "en");
        session.applyJudgeResult(List.of(RiskLevel.CAUTION), null);
        return scanSessionRepository.saveAndFlush(session).getId();
    }

    private UUID menu(UUID scan, String name) {
        return menuAnalysisRepository
                .saveAndFlush(MenuAnalysis.prepared(scan, 0, name, null, null, null, null, null, null))
                .getId();
    }

    private long insertStore(String name) {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values (?, '음식', 1) returning id",
                Long.class,
                "R" + UUID.randomUUID().toString().substring(0, 5));
        Long batchId = jdbcTemplate.queryForObject(
                "insert into store_import_batches(source, source_version) values ('sbiz', ?) returning id",
                Long.class,
                String.valueOf(100000 + Math.abs(UUID.randomUUID().hashCode() % 899999)));
        return jdbcTemplate.queryForObject(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, ?, ?, 33.41, 126.51, 'sbiz', ?) returning id
                """,
                Long.class,
                "R" + UUID.randomUUID().toString().substring(0, 8),
                name,
                categoryId,
                batchId);
    }
}
