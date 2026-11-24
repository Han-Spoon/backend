package com.hanspoon.backend_api.domain.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.store.dto.StoreDetailResponse;
import com.hanspoon.backend_api.domain.store.service.StoreDetailService;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.cdn.base-url=https://cdn.test/")
@Import(TestcontainersConfiguration.class)
@Transactional
class StoreDetailQueryIntegrationTest {

    @Autowired
    private StoreDetailService storeDetailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long categoryId;
    private Long batchId;

    @BeforeEach
    void setUp() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values ('I2', '음식', 1) returning id", Long.class);
        batchId = jdbcTemplate.queryForObject(
                "insert into store_import_batches(source, source_version) values ('sbiz', '202609') returning id",
                Long.class);
    }

    @Test
    void returnsActiveMenusWithImageFallbackInIdOrder() {
        Long storeId = insertStore("DETAIL-1", "active", null);
        Long kimchiStew = insertMenu("김치찌개", insertStandardImage("e", "active"));
        Long soybeanStew = insertMenu("된장찌개", insertStandardImage("f", "active"));
        Long bulgogi = insertMenu("불고기", insertStandardImage("1", "hidden"));
        Long storePhoto = insertStoreMenu(storeId, "김치찌개", insertImage("a", "active"), kimchiStew, "active");
        Long hiddenPhoto = insertStoreMenu(storeId, "된장찌개", insertImage("b", "hidden"), soybeanStew, "active");
        Long noImage = insertStoreMenu(storeId, "불고기", null, bulgogi, "active");
        Long unlinked = insertStoreMenu(storeId, "잡채", null, null, "active");
        insertStoreMenu(storeId, "비빔밥", insertImage("c", "active"), null, "hidden");
        jdbcTemplate.update(
                "update store_menus set price_text = '9,000원', price_options = '[{\"label\":\"곱빼기\",\"price\":10000}]'"
                        + " where id = ?",
                storePhoto);

        StoreDetailResponse detail = storeDetailService.getDetail(user("en").getId(), storeId);

        assertThat(detail.storeId()).isEqualTo(storeId);
        assertThat(detail.name()).isEqualTo("기와");
        assertThat(detail.categoryCode()).isEqualTo("I2");
        assertThat(detail.verified()).isFalse();
        assertThat(detail.reviewCount()).isZero();
        assertThat(detail.menus())
                .extracting(
                        StoreDetailResponse.Menu::storeMenuId,
                        StoreDetailResponse.Menu::translatedName,
                        StoreDetailResponse.Menu::imageUrl,
                        StoreDetailResponse.Menu::imageAttribution,
                        StoreDetailResponse.Menu::imageSource)
                .containsExactly(
                        tuple(storePhoto, "Kimchi stew", storeUrl("a"), "사진 제공: 기와", "store"),
                        tuple(hiddenPhoto, "Soybean paste stew", standardUrl("f"), "대표 이미지", "standard"),
                        tuple(noImage, "Bulgogi", null, null, null),
                        tuple(unlinked, null, null, null, null));
        StoreDetailResponse.Menu first = detail.menus().getFirst();
        assertThat(first.priceText()).isEqualTo("9,000원");
        assertThat(first.priceOptions().get(0).get("label").asString()).isEqualTo("곱빼기");
        assertThat(detail.menus().get(1).priceOptions().isArray()).isTrue();
    }

    @Test
    void hidesTranslatedNameForKoreanUser() {
        Long storeId = insertStore("DETAIL-2", "active", null);
        insertStoreMenu(storeId, "김치찌개", null, insertMenu("김치찌개", null), "active");

        StoreDetailResponse detail = storeDetailService.getDetail(user("ko").getId(), storeId);

        assertThat(detail.menus()).singleElement().satisfies(menu -> {
            assertThat(menu.name()).isEqualTo("김치찌개");
            assertThat(menu.translatedName()).isNull();
        });
    }

    @Test
    void picksStoreImageThenFirstMenuPhotoForThumbnail() {
        Long withStoreImage = insertStore("DETAIL-3", "active", insertImage("2", "active"));
        insertStoreMenu(withStoreImage, "김치찌개", insertImage("3", "active"), null, "active");
        Long withMenuPhoto = insertStore("DETAIL-4", "active", insertImage("4", "hidden"));
        insertStoreMenu(withMenuPhoto, "제육볶음", insertImage("5", "hidden"), null, "active");
        insertStoreMenu(withMenuPhoto, "잡채", insertImage("6", "active"), null, "hidden");
        insertStoreMenu(withMenuPhoto, "된장찌개", insertImage("7", "active"), null, "active");
        insertStoreMenu(withMenuPhoto, "비빔밥", insertImage("8", "active"), null, "active");
        Long standardOnly = insertStore("DETAIL-5", "active", null);
        insertStoreMenu(standardOnly, "김치찌개", null, insertMenu("김치찌개", insertStandardImage("9", "active")), "active");
        User user = user("ko");

        assertThat(storeDetailService.getDetail(user.getId(), withStoreImage).thumbnailUrl())
                .isEqualTo(storeUrl("2"));
        assertThat(storeDetailService.getDetail(user.getId(), withMenuPhoto).thumbnailUrl())
                .isEqualTo(storeUrl("7"));
        assertThat(storeDetailService.getDetail(user.getId(), standardOnly).thumbnailUrl())
                .isNull();
    }

    @Test
    void countsVisibleReviewsAndVerified() {
        Long storeId = insertStore("DETAIL-6", "active", null);
        jdbcTemplate.update("update stores set verified_at = now() where id = ?", storeId);
        User user = user("ko");
        insertReview(user, storeId, false);
        insertReview(user, storeId, true);

        StoreDetailResponse detail = storeDetailService.getDetail(user.getId(), storeId);

        assertThat(detail.verified()).isTrue();
        assertThat(detail.reviewCount()).isEqualTo(1);
    }

    @Test
    void rejectsInactiveOrMissingStore() {
        Long inactive = insertStore("DETAIL-7", "inactive", null);
        User user = user("ko");

        assertThatThrownBy(() -> storeDetailService.getDetail(user.getId(), inactive))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.STORE_NOT_FOUND));
        assertThatThrownBy(() -> storeDetailService.getDetail(user.getId(), inactive + 1000))
                .isInstanceOf(BusinessException.class);
    }

    private User user(String languageCode) {
        String nickname = "detail" + languageCode + System.nanoTime() % 100000;
        return userRepository.saveAndFlush(User.create(nickname + "@example.com", nickname, languageCode));
    }

    private void insertReview(User user, Long storeId, boolean hidden) {
        jdbcTemplate.update(
                "insert into meal_reviews(id, user_id, store_id, hidden_at) values (gen_random_uuid(), ?, ?, ?)",
                user.getId(),
                storeId,
                hidden ? Timestamp.from(Instant.now().plusSeconds(1)) : null);
    }

    private Long insertStore(String sbizNo, String status, Long imageAssetId) {
        return jdbcTemplate.queryForObject(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id, status,
                                   inactive_at, image_asset_id)
                values (?, '기와', ?, 37.53, 126.96, 'sbiz', ?, ?,
                        case when cast(? as text) = 'inactive' then now() end, ?)
                returning id
                """,
                Long.class,
                sbizNo,
                categoryId,
                batchId,
                status,
                status,
                imageAssetId);
    }

    private Long insertMenu(String name, Long imageAssetId) {
        String english =
                switch (name) {
                    case "김치찌개" -> "Kimchi stew";
                    case "된장찌개" -> "Soybean paste stew";
                    default -> "Bulgogi";
                };
        return jdbcTemplate.queryForObject(
                """
                insert into menus(name_ko, name_i18n, source, needs_review, image_asset_id)
                values (?, jsonb_build_object('ko', ?, 'en', ?), 'curated', true, ?)
                on conflict (name_normalized) do update set name_ko = excluded.name_ko
                returning id
                """,
                Long.class,
                name,
                name,
                english,
                imageAssetId);
    }

    private Long insertStoreMenu(Long storeId, String name, Long imageAssetId, Long menuId, String status) {
        return jdbcTemplate.queryForObject(
                """
                insert into store_menus(store_id, name_normalized, display_name, image_asset_id, menu_id,
                                        mapping_source, status)
                values (?, normalize_food_name(?), ?, ?, ?,
                        case when cast(? as bigint) is null then 'none' else 'admin' end, ?)
                returning id
                """,
                Long.class,
                storeId,
                name,
                name,
                imageAssetId,
                menuId,
                menuId,
                status);
    }

    private Long insertImage(String seed, String status) {
        return jdbcTemplate.queryForObject(
                """
                insert into image_assets(storage_key, content_sha256, mime_type, source_type,
                                         author, license_code, source_url, attribution_text, status)
                values (?, ?, 'image/jpeg', 'external_licensed',
                        '기와', 'store-permission', 'https://naver.me/x', '사진 제공: 기와', ?)
                returning id
                """,
                Long.class,
                "menus/stores/" + sha(seed) + ".jpg",
                sha(seed),
                status);
    }

    private Long insertStandardImage(String seed, String status) {
        return jdbcTemplate.queryForObject(
                """
                insert into image_assets(storage_key, content_sha256, mime_type, source_type,
                                         author, license_code, source_url, attribution_text, status)
                values (?, ?, 'image/jpeg', 'external_licensed',
                        '작성자', 'CC-BY-SA-4.0', 'https://commons.wikimedia.org/wiki/File:x.jpg',
                        '대표 이미지', ?)
                returning id
                """,
                Long.class,
                "menus/standard/" + sha(seed) + ".jpg",
                sha(seed),
                status);
    }

    private static String storeUrl(String seed) {
        return "https://cdn.test/menus/stores/" + sha(seed) + ".jpg";
    }

    private static String standardUrl(String seed) {
        return "https://cdn.test/menus/standard/" + sha(seed) + ".jpg";
    }

    private static String sha(String seed) {
        return seed.repeat(64);
    }
}
