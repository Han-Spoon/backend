package com.hanspoon.backend_api.domain.store.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공개 데이터와 사용자 제출를 통합한 가게 마스터. */
@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sbiz_store_no", length = 24, unique = true)
    private String sbizStoreNo;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "branch_name", length = 100, nullable = false)
    private String branchName;

    /** DB 생성 컬럼이므로 애플리케이션에서는 읽기만 한다. 사용자 화면에는 {@link #name}을 사용한다. */
    @Column(name = "name_normalized", length = 200, nullable = false, insertable = false, updatable = false)
    private String nameNormalized;

    // 대량 후보 조회에서 불필요한 연관 엔티티 로딩을 막기 위해 FK를 값으로 매핑한다.
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "ksic_code", length = 6)
    private String ksicCode;

    @Column(name = "admin_dong_code", length = 8, nullable = false)
    private String adminDongCode;

    @Column(name = "road_address", length = 300, nullable = false)
    private String roadAddress;

    @Column(name = "floor_info", length = 20, nullable = false)
    private String floorInfo;

    @Column(name = "lat", nullable = false)
    private double latitude;

    @Column(name = "lng", nullable = false)
    private double longitude;

    @Column(name = "status", length = 20, nullable = false)
    private StoreStatus status;

    @Column(name = "origin", length = 20, nullable = false)
    private StoreOrigin origin;

    @Column(name = "inactive_at")
    private Instant inactiveAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "submitted_by", columnDefinition = "uuid")
    private UUID submittedBy;

    @Column(name = "last_batch_id")
    private Long lastBatchId;

    public boolean isActive() {
        return status == StoreStatus.ACTIVE;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
