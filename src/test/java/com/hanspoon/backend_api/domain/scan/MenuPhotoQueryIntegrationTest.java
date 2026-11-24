package com.hanspoon.backend_api.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.scan.service.MenuPhotoResolver;
import com.hanspoon.backend_api.domain.scan.service.MenuPhotoResolver.MenuPhoto;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.cdn.base-url=https://cdn.test/")
@Import(TestcontainersConfiguration.class)
@Transactional
class MenuPhotoQueryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private MenuAnalysisRepository menuAnalysisRepository;

    @Autowired
    private MenuPhotoResolver menuPhotoResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void matchesActivePhotoOfSameStoreByNormalizedName() {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values ('I2', '음식', 1) returning id", Long.class);
        Long batchId = jdbcTemplate.queryForObject(
                "insert into store_import_batches(source, source_version) values ('sbiz', '202609') returning id",
                Long.class);
        Long storeId = insertStore("PHOTO-1", categoryId, batchId);
        Long otherStoreId = insertStore("PHOTO-2", categoryId, batchId);
        insertStoreMenu(storeId, "김치찌개", insertImage("a", "active"));
        insertStoreMenu(storeId, "된장찌개", insertImage("b", "hidden"));
        insertStoreMenu(otherStoreId, "제육볶음", insertImage("c", "active"));
        Long linkedMenuId = insertStoreMenu(storeId, "돼지불백", insertImage("d", "active"));

        User user = userRepository.save(User.create("menu-photo@example.com", "menuphoto", "ko"));
        ScanSession session = scanSessionRepository.save(ScanSession.start(
                user.getId(), "scans/" + user.getId() + "/photo.jpg", storeId, "기와", StoreMatchMethod.NAME_SEARCH));
        MenuAnalysis kimchi = menuAnalysisRepository.save(menu(session.getId(), 0, "김치 찌개", null, null));
        menuAnalysisRepository.save(menu(session.getId(), 1, "된장찌개", null, null));
        menuAnalysisRepository.save(menu(session.getId(), 2, "제육볶음", null, null));
        // 연결된 가게 메뉴는 표기가 달라도 그 사진
        MenuAnalysis linked = menuAnalysisRepository.save(menu(session.getId(), 3, "불백 정식", storeId, linkedMenuId));
        entityManager.flush();

        Map<UUID, MenuPhoto> photos = menuPhotoResolver.find(session.getId());

        assertThat(photos)
                .containsOnly(
                        Map.entry(
                                kimchi.getId(),
                                new MenuPhoto("https://cdn.test/menus/stores/" + sha("a") + ".jpg", "사진 제공: 기와")),
                        Map.entry(
                                linked.getId(),
                                new MenuPhoto("https://cdn.test/menus/stores/" + sha("d") + ".jpg", "사진 제공: 기와")));
    }

    @Test
    void fallsBackToActiveStandardMenuImage() {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values ('I2', '음식', 1) returning id", Long.class);
        Long batchId = jdbcTemplate.queryForObject(
                "insert into store_import_batches(source, source_version) values ('sbiz', '202609') returning id",
                Long.class);
        Long storeId = insertStore("PHOTO-3", categoryId, batchId);
        insertStoreMenu(storeId, "김치찌개", insertImage("a", "active"));
        insertStoreMenu(storeId, "된장찌개", insertImage("b", "hidden"));
        Long kimchiStew = insertMenu("김치찌개", insertStandardImage("e", "active"));
        Long soybeanStew = insertMenu("된장찌개", insertStandardImage("f", "active"));
        Long bibimbap = insertMenu("비빔밥", insertStandardImage("1", "active"));
        Long bulgogi = insertMenu("불고기", insertStandardImage("2", "hidden"));

        User user = userRepository.save(User.create("menu-photo-std@example.com", "menuphotostd", "ko"));
        ScanSession withStore = scanSessionRepository.save(ScanSession.start(
                user.getId(), "scans/" + user.getId() + "/a.jpg", storeId, "기와", StoreMatchMethod.NAME_SEARCH));
        ScanSession withoutStore = scanSessionRepository.save(
                ScanSession.startWithoutStore(user.getId(), "scans/" + user.getId() + "/b.jpg"));
        MenuAnalysis storePhoto = menuAnalysisRepository.save(menu(withStore.getId(), 0, "김치찌개", null, null));
        MenuAnalysis hiddenStorePhoto = menuAnalysisRepository.save(menu(withStore.getId(), 1, "된장찌개", null, null));
        MenuAnalysis noStoreMenu = menuAnalysisRepository.save(menu(withStore.getId(), 2, "비빔밥", null, null));
        MenuAnalysis noStore = menuAnalysisRepository.save(menu(withoutStore.getId(), 0, "비빔밥", null, null));
        MenuAnalysis hiddenStandard = menuAnalysisRepository.save(menu(withoutStore.getId(), 1, "불고기", null, null));
        menuAnalysisRepository.save(menu(withoutStore.getId(), 2, "잡채", null, null));
        entityManager.flush();
        judge(storePhoto, kimchiStew);
        judge(hiddenStorePhoto, soybeanStew);
        judge(noStoreMenu, bibimbap);
        judge(noStore, bibimbap);
        judge(hiddenStandard, bulgogi);

        assertThat(menuPhotoResolver.find(withStore.getId()))
                .containsOnly(
                        Map.entry(
                                storePhoto.getId(),
                                new MenuPhoto("https://cdn.test/menus/stores/" + sha("a") + ".jpg", "사진 제공: 기와")),
                        Map.entry(hiddenStorePhoto.getId(), standardPhoto("f")),
                        Map.entry(noStoreMenu.getId(), standardPhoto("1")));
        assertThat(menuPhotoResolver.find(withoutStore.getId()))
                .containsOnly(Map.entry(noStore.getId(), standardPhoto("1")));
    }

    private Long insertMenu(String name, Long imageAssetId) {
        return jdbcTemplate.queryForObject(
                """
                insert into menus(name_ko, source, needs_review, image_asset_id)
                values (?, 'curated', true, ?)
                returning id
                """,
                Long.class,
                name,
                imageAssetId);
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

    private void judge(MenuAnalysis analysis, Long menuId) {
        jdbcTemplate.update(
                "update menu_analyses set menu_id = ?, menu_match_type = 'exact' where id = ?",
                menuId,
                analysis.getId());
    }

    private static MenuPhoto standardPhoto(String seed) {
        return new MenuPhoto("https://cdn.test/menus/standard/" + sha(seed) + ".jpg", "대표 이미지");
    }

    private Long insertStore(String sbizNo, Long categoryId, Long batchId) {
        return jdbcTemplate.queryForObject(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, '기와', ?, 37.53, 126.96, 'sbiz', ?)
                returning id
                """,
                Long.class,
                sbizNo,
                categoryId,
                batchId);
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

    private Long insertStoreMenu(Long storeId, String name, Long imageAssetId) {
        return jdbcTemplate.queryForObject(
                """
                insert into store_menus(store_id, name_normalized, display_name, image_asset_id)
                values (?, normalize_food_name(?), ?, ?)
                returning id
                """,
                Long.class,
                storeId,
                name,
                name,
                imageAssetId);
    }

    private static MenuAnalysis menu(UUID scanId, int sourceIndex, String name, Long storeId, Long storeMenuId) {
        return MenuAnalysis.prepared(scanId, sourceIndex, name, null, null, null, null, storeId, storeMenuId);
    }

    private static String sha(String seed) {
        return seed.repeat(64);
    }
}
