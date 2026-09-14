package com.hanspoon.backend_api.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreOrigin;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreCandidateProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 가게 엔티티 매핑과 PostgreSQL 위치·상호명 검색 쿼리를 실제 DB에서 검증한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class StoreRepositoryIntegrationTest {

    private static final double CENTER_LATITUDE = 37.5000;
    private static final double CENTER_LONGITUDE = 127.0000;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long batchId;

    @BeforeEach
    void setUpReferences() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories (code, name, level) values ('I20101', '한식 일반 음식점업', 1) returning id",
                Long.class);
        batchId = jdbcTemplate.queryForObject(
                """
                insert into store_import_batches
                    (source, source_version, row_count, status, finished_at)
                values ('sbiz', '209901', 4, 'completed', now())
                returning id
                """,
                Long.class);
    }

    @Test
    void mapsGeneratedNameAndLowercaseEnumCodesToStoreEntity() {
        long storeId = insertStore("entity-store", "ＣＵ 마트 Ｂ１", 37.5001, 127.0000, StoreStatus.ACTIVE, true);

        Store store =
                storeRepository.findByIdAndStatus(storeId, StoreStatus.ACTIVE).orElseThrow();

        assertThat(store.getNameNormalized()).isEqualTo("cu마트b1");
        assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
        assertThat(store.getOrigin()).isEqualTo(StoreOrigin.SBIZ);
        assertThat(store.isActive()).isTrue();
        assertThat(store.isVerified()).isTrue();
    }

    @Test
    void returnsOnlyActiveStoresInsideRadiusInStablePriorityOrder() {
        long nearbyId = insertStore("nearby-store", "한스푼 역삼점", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        long verifiedId = insertStore("verified-store", "한스푼 강남점", 37.5010, 127.0000, StoreStatus.ACTIVE, true);
        insertStore("inactive-store", "한스푼 휴업점", 37.5000, 127.0000, StoreStatus.INACTIVE, true);
        insertStore("outside-store", "한스푼 외곽점", 37.5200, 127.0000, StoreStatus.ACTIVE, true);

        List<StoreCandidateProjection> candidates =
                storeRepository.findNearbyCandidates(CENTER_LATITUDE, CENTER_LONGITUDE, 500, 10);

        assertThat(candidates).extracting(StoreCandidateProjection::getStoreId).containsExactly(verifiedId, nearbyId);
        assertThat(candidates.getFirst().getVerified()).isTrue();
        assertThat(candidates.getFirst().getCategoryCode()).isEqualTo("I20101");
        assertThat(candidates.getFirst().getCategoryName()).isEqualTo("한식 일반 음식점업");
        assertThat(candidates.getFirst().getDistanceMeters()).isBetween(100, 120);
        assertThat(candidates.getFirst().getNameSimilarity()).isNull();
    }

    @Test
    void normalizesSearchTermAndFiltersCandidatesWithTrigramIndexOperator() {
        long expectedId = insertStore("search-store", "ＣＵ 마트 Ｂ１", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        insertStore("other-store", "한스푼 식당", 37.5002, 127.0000, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findNearbyCandidatesByName("cu 마트 b1", CENTER_LATITUDE, CENTER_LONGITUDE, 500, 10);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getStoreId()).isEqualTo(expectedId);
        assertThat(candidates.getFirst().getName()).isEqualTo("ＣＵ 마트 Ｂ１");
        assertThat(candidates.getFirst().getNameSimilarity()).isEqualTo(1.0);
    }

    private long insertStore(
            String storeNo, String name, double latitude, double longitude, StoreStatus status, boolean verified) {
        Timestamp inactiveAt = status == StoreStatus.INACTIVE ? Timestamp.from(Instant.now()) : null;
        Timestamp verifiedAt = verified ? Timestamp.from(Instant.now()) : null;

        return jdbcTemplate.queryForObject(
                """
                insert into stores
                    (sbiz_store_no, name, category_id, road_address, lat, lng, status, origin,
                     inactive_at, verified_at, last_batch_id)
                values (?, ?, ?, '서울특별시 강남구 테헤란로', ?, ?, ?, 'sbiz', ?, ?, ?)
                returning id
                """,
                Long.class,
                storeNo,
                name,
                categoryId,
                latitude,
                longitude,
                status.getCode(),
                inactiveAt,
                verifiedAt,
                batchId);
    }
}
