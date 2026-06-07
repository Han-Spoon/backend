package com.hanspoon.backend_api.domain.card.entity;

import com.hanspoon.backend_api.domain.card.dto.CardText;
import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 자주 쓰는 카드(사장님 소통 카드 저장). users 와 N:1 (FK ON DELETE CASCADE).
 * 출처 스캔(scanSessionId)은 선택이며, 스캔 삭제 시 ON DELETE SET NULL 로 카드는 보존된다.
 *
 * <p>문구 생성은 FE 책임 — 서버는 화면에 보이는 텍스트({@link CardText})를 그대로 스냅샷 저장한다.
 * 사용 언어를 바꿔도 저장된 카드는 변하지 않는다(재렌더 없음). 타입(order/ingredient_check/exclude)별로
 * 텍스트 출처만 다를 뿐(재료확인=AI owner_card.question, 주문/빼고요청=FE 템플릿) 저장 형태는 동일하다.
 */
@Entity
@Table(name = "saved_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedCard extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "card_type", length = 20, nullable = false)
    private CardType cardType;

    @Column(name = "menu_name_ko", length = 255, nullable = false)
    private String menuNameKo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "text", columnDefinition = "jsonb", nullable = false)
    private CardText text;

    @Column(name = "scan_session_id", columnDefinition = "uuid")
    private UUID scanSessionId;

    @Column(name = "last_saved_at", nullable = false)
    private Instant lastSavedAt;

    private SavedCard(UUID userId, CardType cardType, String menuNameKo, CardText text, UUID scanSessionId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.cardType = cardType;
        this.menuNameKo = menuNameKo;
        this.text = text;
        this.scanSessionId = scanSessionId;
        this.lastSavedAt = Instant.now();
    }

    public static SavedCard create(
            UUID userId, CardType cardType, String menuNameKo, CardText text, UUID scanSessionId) {
        return new SavedCard(userId, cardType, menuNameKo, text, scanSessionId);
    }

    /** 동일 카드 재저장 시 호출 — 목록 최상단으로 올라오도록 저장 시각만 갱신한다. */
    public void resave() {
        this.lastSavedAt = Instant.now();
    }
}
