package com.hanspoon.backend_api.domain.card.repository;

import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.entity.SavedCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedCardRepository extends JpaRepository<SavedCard, UUID> {

    // 목록은 최신 저장순 고정(중복 재저장 시 맨 위로).
    Page<SavedCard> findByUserIdOrderByLastSavedAtDesc(UUID userId, Pageable pageable);

    // 중복 판정 후보(같은 유저·종류·메뉴명) — text 동일 여부는 서비스에서 비교.
    List<SavedCard> findByUserIdAndCardTypeAndMenuNameKo(UUID userId, CardType cardType, String menuNameKo);

    Optional<SavedCard> findByIdAndUserId(UUID id, UUID userId);
}
