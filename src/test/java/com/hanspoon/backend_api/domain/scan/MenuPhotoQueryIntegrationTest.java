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
import java.util.List;
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

        User user = userRepository.save(User.create("menu-photo@example.com", "menuphoto", "ko"));
        ScanSession session = scanSessionRepository.save(ScanSession.start(
                user.getId(), "scans/" + user.getId() + "/photo.jpg", storeId, "기와", StoreMatchMethod.NAME_SEARCH));
        MenuAnalysis kimchi = menuAnalysisRepository.save(menu(session.getId(), 1, "김치 찌개"));
        menuAnalysisRepository.save(menu(session.getId(), 2, "된장찌개"));
        menuAnalysisRepository.save(menu(session.getId(), 3, "제육볶음"));
        entityManager.flush();

        Map<UUID, MenuPhoto> photos = menuPhotoResolver.find(session.getId(), storeId);

        assertThat(photos)
                .containsExactly(Map.entry(
                        kimchi.getId(),
                        new MenuPhoto("https://cdn.test/menus/stores/" + sha("a") + ".jpg", "사진 제공: 기와")));
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

    private void insertStoreMenu(Long storeId, String name, Long imageAssetId) {
        jdbcTemplate.update(
                """
                insert into store_menus(store_id, name_normalized, display_name, image_asset_id)
                values (?, normalize_food_name(?), ?, ?)
                """,
                storeId,
                name,
                name,
                imageAssetId);
    }

    private static MenuAnalysis menu(UUID scanId, int order, String name) {
        return MenuAnalysis.create(
                scanId, order, name, null, null, null, null, null, null, null, List.of(), null, null);
    }

    private static String sha(String seed) {
        return seed.repeat(64);
    }
}
