package com.hanspoon.backend_api.domain.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreOrigin;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreCandidateProjection;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    @Autowired
    private UserRepository userRepository;

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
        jdbcTemplate.update("update stores set floor_info = 'B1' where id = ?", verifiedId);

        List<StoreCandidateProjection> candidates =
                storeRepository.findNearbyCandidates(CENTER_LATITUDE, CENTER_LONGITUDE, 500, 10);

        assertThat(candidates).extracting(StoreCandidateProjection::getStoreId).containsExactly(verifiedId, nearbyId);
        assertThat(candidates.getFirst().getVerified()).isTrue();
        assertThat(candidates.getFirst().getCategoryCode()).isEqualTo("I20101");
        assertThat(candidates.getFirst().getCategoryName()).isEqualTo("한식 일반 음식점업");
        assertThat(candidates.getFirst().getFloorInfo()).isEqualTo("B1");
        assertThat(candidates.get(1).getFloorInfo()).isEmpty();
        assertThat(candidates.getFirst().getDistanceMeters()).isBetween(100, 120);
        assertThat(candidates.getFirst().getNameSimilarity()).isNull();
    }

    @Test
    void normalizesSearchTermWithDatabaseFunction() {
        assertThat(storeRepository.normalizeStoreName("cu 마트 b1")).isEqualTo("cu마트b1");
        assertThat(storeRepository.normalizeStoreName("이가 뚝배기")).isEqualTo("이가뚝배기");
        assertThat(storeRepository.normalizeStoreName("!!!")).isEmpty();
    }

    @Test
    void matchesNormalizedFullWidthName() {
        long expectedId = insertStore("search-store", "ＣＵ 마트 Ｂ１", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        insertStore("other-store", "한스푼 식당", 37.5002, 127.0000, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByName("cu마트b1", CENTER_LATITUDE, CENTER_LONGITUDE, 10);

        assertThat(candidates).extracting(StoreCandidateProjection::getStoreId).containsExactly(expectedId);
        assertThat(candidates.getFirst().getName()).isEqualTo("ＣＵ 마트 Ｂ１");
        assertThat(candidates.getFirst().getNameSimilarity()).isEqualTo(1.0);
    }

    @Test
    void searchesNameNationwideIgnoringRadius() {
        long nearId = insertStore("near-store", "이가뚝배기", 37.5087, 127.0000, StoreStatus.ACTIVE, false);
        long busanId = insertStore("busan-store", "이가뚝배기", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        insertStore("closed-store", "이가뚝배기", 37.5001, 127.0000, StoreStatus.INACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByName("이가뚝배기", CENTER_LATITUDE, CENTER_LONGITUDE, 10);

        assertThat(candidates).extracting(StoreCandidateProjection::getStoreId).containsExactly(nearId, busanId);
        assertThat(candidates.getFirst().getDistanceMeters()).isBetween(950, 980);
        assertThat(candidates.get(1).getDistanceMeters()).isGreaterThan(300_000);
    }

    @Test
    void matchesContainedNameBelowSimilarityThreshold() {
        long storeId = insertStore("contains-store", "이가뚝배기", 37.5087, 127.0000, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByName("뚝배기", CENTER_LATITUDE, CENTER_LONGITUDE, 10);

        assertThat(candidates).extracting(StoreCandidateProjection::getStoreId).containsExactly(storeId);
        assertThat(candidates.getFirst().getNameSimilarity()).isLessThan(0.3);
    }

    @Test
    void ranksVerifiedThenContainedThenSimilarWithDistanceInsideEachTier() {
        long verifiedFarId = insertStore("verified-far", "이가뚝배기", 35.1631, 129.1635, StoreStatus.ACTIVE, true);
        long containedFarId = insertStore("contained-far", "이가뚝배기", 37.5180, 127.0000, StoreStatus.ACTIVE, false);
        long containedNearId = insertStore("contained-near", "원조이가뚝배기", 37.5045, 127.0000, StoreStatus.ACTIVE, false);
        long similarNearId = insertStore("similar-near", "이가뚝베기", 37.5009, 127.0000, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByName("이가뚝배기", CENTER_LATITUDE, CENTER_LONGITUDE, 10);

        assertThat(candidates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(verifiedFarId, containedNearId, containedFarId, similarNearId);
    }

    @Test
    void ranksExactThenPrefixThenShorterContainedNameWithoutCoordinates() {
        long containedLongId = insertStore("contained-long", "원조이가뚝배기", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        long containedId = insertStore("contained", "이가뚝배기", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        long prefixId = insertStore("prefix", "뚝배기집", 37.5002, 127.0000, StoreStatus.ACTIVE, false);
        long exactId = insertStore("exact", "뚝배기", 35.1632, 129.1635, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates = storeRepository.findCandidatesByName("뚝배기", null, null, 10);

        assertThat(candidates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(exactId, prefixId, containedId, containedLongId);
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.getDistanceMeters())
                .isNull());
    }

    @Test
    void matchesShortQueryByNearbyContainsOrNationwidePrefix() {
        long nearContainsId = insertStore("near-contains", "원조이가", 37.5090, 127.0000, StoreStatus.ACTIVE, false);
        long nearPrefixId = insertStore("near-prefix", "이가뚝배기", 37.5087, 127.0000, StoreStatus.ACTIVE, false);
        long farPrefixId = insertStore("far-prefix", "이가네", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        insertStore("far-contains", "원조이가식당", 35.1632, 129.1635, StoreStatus.ACTIVE, false);
        insertStore("outside-contains", "할매이가", 37.5300, 127.0000, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByShortName("이가", CENTER_LATITUDE, CENTER_LONGITUDE, 3000, 10);

        assertThat(candidates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(nearPrefixId, nearContainsId, farPrefixId);
    }

    @Test
    void matchesShortQueryByNationwidePrefixWithoutCoordinates() {
        long longerPrefixId = insertStore("longer-prefix", "이가뚝배기", 37.5087, 127.0000, StoreStatus.ACTIVE, false);
        long shorterPrefixId = insertStore("shorter-prefix", "이가네", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        insertStore("contains-only", "원조이가", 37.5001, 127.0000, StoreStatus.ACTIVE, false);

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByShortName("이가", null, null, 3000, 10);

        assertThat(candidates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(shorterPrefixId, longerPrefixId);
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.getDistanceMeters())
                .isNull());
    }

    @Test
    void matchesNameWithBranchAheadOfOtherBranches() {
        long yongsanId = insertStore("yongsan-branch", "본죽", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        long myeongdongId = insertStore("myeongdong-branch", "본죽", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        updateBranchName(yongsanId, "용산점");
        updateBranchName(myeongdongId, "명동점");

        List<StoreCandidateProjection> fullName =
                storeRepository.findCandidatesByName("본죽용산점", CENTER_LATITUDE, CENTER_LONGITUDE, 10);
        List<StoreCandidateProjection> partialBranch =
                storeRepository.findCandidatesByName("본죽용산", CENTER_LATITUDE, CENTER_LONGITUDE, 10);
        List<StoreCandidateProjection> nameOnly =
                storeRepository.findCandidatesByName("본죽", CENTER_LATITUDE, CENTER_LONGITUDE, 10);

        assertThat(fullName).extracting(StoreCandidateProjection::getStoreId).containsExactly(yongsanId);
        assertThat(partialBranch)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(yongsanId, myeongdongId);
        assertThat(nameOnly).extracting(StoreCandidateProjection::getStoreId).containsExactly(myeongdongId, yongsanId);
    }

    @Test
    void ranksExactNameWithBranchFirstWithoutCoordinates() {
        long otherBranchId = insertStore("other-branch", "본죽", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        long exactBranchId = insertStore("exact-branch", "본죽", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        long longerNameId = insertStore("longer-name", "본죽용산점비빔밥", 37.5002, 127.0000, StoreStatus.ACTIVE, false);
        updateBranchName(otherBranchId, "용산점앞");
        updateBranchName(exactBranchId, "용산점");

        List<StoreCandidateProjection> candidates = storeRepository.findCandidatesByName("본죽용산점", null, null, 10);

        assertThat(candidates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(exactBranchId, otherBranchId, longerNameId);
    }

    @Test
    void matchesShortQueryInsideNearbyBranchName() {
        long nearBranchId = insertStore("near-branch", "본죽", 37.5050, 127.0000, StoreStatus.ACTIVE, false);
        long farBranchId = insertStore("far-branch", "본죽", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        updateBranchName(nearBranchId, "용산점");
        updateBranchName(farBranchId, "용산점");

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByShortName("용산", CENTER_LATITUDE, CENTER_LONGITUDE, 3000, 10);

        assertThat(candidates).extracting(StoreCandidateProjection::getStoreId).containsExactly(nearBranchId);
    }

    @Test
    void ranksNameThenMenuThenSimilarMatchesOncePerStore() {
        long menuNearId = insertStore("menu-near", "한스푼", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        long nameFarId = insertStore("name-far", "비빔밥천국", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        long bothId = insertStore("name-and-menu", "전주비빔밥", 37.5090, 127.0000, StoreStatus.ACTIVE, false);
        long similarId = insertStore("similar", "비빔빕", 37.5002, 127.0000, StoreStatus.ACTIVE, false);
        long hiddenMenuId = insertStore("hidden-menu", "두스푼", 37.5003, 127.0000, StoreStatus.ACTIVE, false);
        long closedId = insertStore("closed-menu", "세스푼", 37.5004, 127.0000, StoreStatus.INACTIVE, false);
        insertMenu(menuNearId, "돌솥 비빔밥", "active");
        insertMenu(menuNearId, "비빔밥", "active");
        insertMenu(bothId, "육회비빔밥", "active");
        insertMenu(hiddenMenuId, "비빔밥", "hidden");
        insertMenu(closedId, "비빔밥", "active");

        List<StoreCandidateProjection> candidates =
                storeRepository.findCandidatesByName("비빔밥", CENTER_LATITUDE, CENTER_LONGITUDE, 10);

        assertThat(candidates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(bothId, nameFarId, menuNearId, similarId);
        assertThat(candidates)
                .extracting(StoreCandidateProjection::getMatchedMenuName)
                .containsExactly(null, null, "비빔밥", null);
    }

    @Test
    void matchesShortQueryMenuAfterNameMatches() {
        long menuNearId = insertStore("short-menu-near", "한스푼", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        long prefixFarId = insertStore("short-prefix-far", "김밥나라", 35.1631, 129.1635, StoreStatus.ACTIVE, false);
        insertMenu(menuNearId, "참치김밥", "active");

        List<StoreCandidateProjection> nearby =
                storeRepository.findCandidatesByShortName("김밥", CENTER_LATITUDE, CENTER_LONGITUDE, 3000, 10);
        List<StoreCandidateProjection> withoutCoordinates =
                storeRepository.findCandidatesByShortName("김밥", null, null, 3000, 10);

        assertThat(nearby).extracting(StoreCandidateProjection::getStoreId).containsExactly(prefixFarId, menuNearId);
        assertThat(nearby)
                .extracting(StoreCandidateProjection::getMatchedMenuName)
                .containsExactly(null, "참치김밥");
        assertThat(withoutCoordinates)
                .extracting(StoreCandidateProjection::getStoreId)
                .containsExactly(prefixFarId, menuNearId);
    }

    @Test
    void countsOnlyVisibleReviews() {
        long storeId = insertStore("review-store", "후기 식당", 37.5001, 127.0000, StoreStatus.ACTIVE, false);
        UUID userId = userRepository
                .save(User.create("review-" + UUID.randomUUID() + "@example.com", "reviewer", "ko"))
                .getId();
        userRepository.flush();
        insertReview(userId, storeId, false);
        insertReview(userId, storeId, false);
        insertReview(userId, storeId, true);

        List<StoreCandidateProjection> nearby =
                storeRepository.findNearbyCandidates(CENTER_LATITUDE, CENTER_LONGITUDE, 500, 10);
        List<StoreCandidateProjection> byName = storeRepository.findCandidatesByName("후기식당", null, null, 10);

        assertThat(nearby)
                .filteredOn(c -> c.getStoreId() == storeId)
                .singleElement()
                .satisfies(c -> assertThat(c.getReviewCount()).isEqualTo(2));
        assertThat(byName)
                .filteredOn(c -> c.getStoreId() == storeId)
                .singleElement()
                .satisfies(c -> assertThat(c.getReviewCount()).isEqualTo(2));
    }

    private void insertReview(UUID userId, long storeId, boolean hidden) {
        jdbcTemplate.update(
                "insert into meal_reviews (id, user_id, store_id, hidden_at) values (?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                storeId,
                hidden ? Timestamp.from(Instant.now().plusSeconds(1)) : null);
    }

    private void insertMenu(long storeId, String displayName, String status) {
        jdbcTemplate.update(
                "insert into store_menus (store_id, name_normalized, display_name, status) "
                        + "values (?, normalize_food_name(?), ?, ?)",
                storeId,
                displayName,
                displayName,
                status);
    }

    private void updateBranchName(long storeId, String branchName) {
        jdbcTemplate.update("update stores set branch_name = ? where id = ?", branchName, storeId);
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
