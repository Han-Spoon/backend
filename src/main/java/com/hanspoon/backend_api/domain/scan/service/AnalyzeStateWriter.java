package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse.ItemResult;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse.PersistenceCommand;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResult;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.AnalyzeJudgeRepository;
import com.hanspoon.backend_api.domain.scan.repository.AnalyzeJudgeRepository.MenuJudgment;
import com.hanspoon.backend_api.domain.scan.repository.AnalyzeJudgeRepository.StaffQuestionRow;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanAiResultRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.repository.StoreMenuRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Prepare/Judge 경로의 저장 전담 컴포넌트. 외부 I/O 추가 금지.
 *
 * @see ScanStateWriter
 */
@Component
public class AnalyzeStateWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeStateWriter.class);
    static final String OWNER_VERIFICATION = "create_owner_verification_request";
    private static final Set<String> GLOBAL_ACTIONS =
            Set.of("update_source_reliability", "save_web_search_cache", "save_menu_ingredient_cache");
    private static final Set<String> QUESTION_LOCALES = Set.of("ko", "en", "ar", "zh-CN", "zh-TW", "ja", "es");

    private final ScanSessionRepository scanSessionRepository;
    private final ScanImageRepository scanImageRepository;
    private final ScanAiResultRepository scanAiResultRepository;
    private final StoreMenuRepository storeMenuRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;
    private final AnalyzeJudgeRepository analyzeJudgeRepository;
    private final JsonMapper jsonMapper;

    public AnalyzeStateWriter(
            ScanSessionRepository scanSessionRepository,
            ScanImageRepository scanImageRepository,
            ScanAiResultRepository scanAiResultRepository,
            StoreMenuRepository storeMenuRepository,
            MenuAnalysisRepository menuAnalysisRepository,
            AnalyzeJudgeRepository analyzeJudgeRepository,
            JsonMapper jsonMapper) {
        this.scanSessionRepository = scanSessionRepository;
        this.scanImageRepository = scanImageRepository;
        this.scanAiResultRepository = scanAiResultRepository;
        this.storeMenuRepository = storeMenuRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
        this.analyzeJudgeRepository = analyzeJudgeRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Prepare 저장. 순서: scan_ai_results → 가게 있으면 store_menus upsert → menu_analyses.
     * 세션이 없으면 빈 목록.
     */
    @Transactional
    public List<MenuAnalysis> applyPrepareResult(
            ScanImage scanImage, ScanAiResult aiResult, List<PreparedItem> items, String locale, Instant scannedAt) {
        UUID scanId = aiResult.getScanSessionId();
        ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
        if (session == null) {
            log.warn("Scan session not found while saving prepare result: {}", scanId);
            return List.of();
        }
        scanImageRepository.save(scanImage);
        scanAiResultRepository.save(aiResult);

        Long storeId = session.getStoreId();
        List<MenuAnalysis> analyses = new ArrayList<>(items.size());
        for (PreparedItem item : items) {
            String priceOptions =
                    item.priceOptions() != null ? jsonMapper.writeValueAsString(item.priceOptions()) : null;
            Long storeMenuId = storeId == null
                    ? null
                    : storeMenuRepository.upsertSeen(
                            storeId,
                            item.normalizedMenuName(),
                            item.displayName(),
                            item.description(),
                            item.priceText(),
                            priceOptions != null ? priceOptions : "[]",
                            scanId);
            analyses.add(MenuAnalysis.prepared(
                    scanId,
                    item.sourceIndex(),
                    item.displayName(),
                    item.description(),
                    item.priceText(),
                    priceOptions,
                    item.spicyObserved() ? Boolean.TRUE : null,
                    storeId,
                    storeMenuId));
        }
        menuAnalysisRepository.saveAll(analyses);
        session.applyPrepareResult(items.size(), scannedAt, locale);
        return analyses;
    }

    /** 재촬영 필요. 원본 이미지와 사유만 저장. */
    @Transactional
    public void applyPrepareRetake(
            ScanImage scanImage, UUID scanId, List<String> retakeReasons, List<String> retakeSuggestions) {
        ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
        if (session == null) {
            log.warn("Scan session not found while saving retake: {}", scanId);
            return;
        }
        scanImageRepository.save(scanImage);
        session.applyNeedsRetake(retakeReasons, retakeSuggestions);
    }

    /**
     * Judge 저장. 순서: 판정·ai_risk_level·display_order → 가게 메뉴 매핑 → 카운터 → Judge 메타 → 명령 수신함.
     * 명령은 create_owner_verification_request 만 실행. analyses 는 Prepare 저장 결과.
     */
    @Transactional
    public void applyJudgeResult(UUID scanId, List<MenuAnalysis> analyses, JudgeResult result, String dietSnapshot) {
        ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
        if (session == null) {
            log.warn("Scan session not found while saving judge result: {}", scanId);
            return;
        }
        JudgeResponse response = result.response();
        JsonNode rawItems = result.raw().get("items");
        Map<Integer, MenuAnalysis> bySourceIndex =
                analyses.stream().collect(Collectors.toMap(MenuAnalysis::getSourceIndex, Function.identity()));
        Map<String, MenuAnalysis> byItemId = new LinkedHashMap<>();
        Map<String, Integer> displayOrders = displayOrders(response.items());

        for (int i = 0; i < response.items().size(); i++) {
            ItemResult item = response.items().get(i);
            JsonNode raw = rawItems.get(i);
            MenuAnalysis analysis = bySourceIndex.get(item.sourceIndex());
            byItemId.put(item.itemId(), analysis);
            analyzeJudgeRepository.updateMenuJudgment(new MenuJudgment(
                    analysis.getId(),
                    item.riskLevel().getCode(),
                    displayOrders.get(item.itemId()),
                    item.normalizationStatus(),
                    item.status(),
                    item.evidenceBasis(),
                    item.informationStatus(),
                    json(raw.get("risk_ingredients")),
                    json(raw.get("matched_tags")),
                    json(raw.get("message")),
                    json(raw.get("owner_cards")),
                    item.menuId(),
                    menuMatchType(item.menuId(), item.matchType()),
                    item.ownerAnswerStatus()));
            if (analysis.getStoreMenuId() != null && item.menuId() != null) {
                analyzeJudgeRepository.updateStoreMenuMapping(analysis.getStoreMenuId(), item.menuId());
            }
        }

        session.applyJudgeResult(
                response.items().stream().map(ItemResult::riskLevel).toList(), dietSnapshot);
        ScanAiResult aiResult = scanAiResultRepository.findById(scanId).orElseThrow();
        aiResult.applyJudge(
                response.knowledgeRevision(),
                response.bundleHash(),
                json(result.raw().get("evidence")),
                json(result.raw().get("warnings")),
                json(result.raw()),
                Instant.now());

        List<PersistenceCommand> commands =
                response.persistenceCommands() == null ? List.of() : response.persistenceCommands();
        JsonNode rawCommands = result.raw().path("persistence_commands");
        for (int i = 0; i < commands.size(); i++) {
            saveCommand(session, commands.get(i), rawCommands.get(i).get("payload"), byItemId);
        }
    }

    private void saveCommand(
            ScanSession session, PersistenceCommand command, JsonNode payload, Map<String, MenuAnalysis> byItemId) {
        String payloadJson = json(payload);
        Long storeId = GLOBAL_ACTIONS.contains(command.action()) ? null : session.getStoreId();
        boolean inserted = analyzeJudgeRepository.insertCommand(
                command.action(), command.idempotencyKey(), storeId, session.getId(), sha256(payloadJson), payloadJson);
        if (!inserted || !OWNER_VERIFICATION.equals(command.action())) {
            return;
        }
        String outcome = createStaffQuestion(session, byItemId.get(command.itemId()), payload);
        analyzeJudgeRepository.markCommandApplied(command.action(), command.idempotencyKey(), outcome);
    }

    /** 질문 생성 결과 JSON. 가게 메뉴·후보 메뉴·재료 ID 없는 질문은 만들지 않음(R-06·R-07). */
    private String createStaffQuestion(ScanSession session, MenuAnalysis analysis, JsonNode payload) {
        if (session.getStoreId() == null || analysis == null || analysis.getStoreMenuId() == null) {
            return skipped("store_menu_missing");
        }
        Long menuId = longOrNull(payload.get("menu_id"));
        if (menuId == null) {
            return skipped("menu_unmatched");
        }
        JsonNode ingredient = payload.get("ingredient");
        Long ingredientId =
                ingredient == null || ingredient.isNull() ? null : longOrNull(ingredient.get("ingredient_id"));
        String flag = text(payload.get("ambiguity_flag"));
        List<String> askedTags = null;
        if (ingredient != null && !ingredient.isNull()) {
            if (ingredientId == null) {
                return skipped("ingredient_unmapped");
            }
            flag = null;
        } else if (flag != null) {
            TreeSet<String> tags = new TreeSet<>(Comparator.naturalOrder());
            payload.path("constraint_tags").forEach(tag -> tags.add(tag.asString()));
            if (tags.isEmpty()) {
                return skipped("no_asked_tags");
            }
            askedTags = List.copyOf(tags);
        } else {
            return skipped("no_target");
        }
        ObjectNode question = jsonMapper.createObjectNode();
        payload.path("question").properties().forEach(entry -> {
            if (QUESTION_LOCALES.contains(entry.getKey()) && entry.getValue().isString()) {
                question.put(entry.getKey(), entry.getValue().asString());
            }
        });
        if (!question.has("ko")) {
            return skipped("question_missing_ko");
        }
        UUID questionId = UUID.randomUUID();
        boolean created = analyzeJudgeRepository.insertStaffQuestion(new StaffQuestionRow(
                questionId,
                session.getStoreId(),
                session.getId(),
                analysis.getStoreMenuId(),
                menuId,
                ingredientId,
                flag,
                askedTags,
                json(question),
                text(payload.get("card_id"))));
        return created
                ? json(jsonMapper.createObjectNode().put("staff_question_id", questionId.toString()))
                : skipped("duplicate_question");
    }

    /** SAFE → CAUTION+확정·추정 → CAUTION+근거 부족 → DANGER, 같은 칸은 source_index. 1부터. */
    static Map<String, Integer> displayOrders(List<ItemResult> items) {
        List<ItemResult> sorted = items.stream()
                .sorted(Comparator.comparingInt(AnalyzeStateWriter::displayRank)
                        .thenComparingInt(ItemResult::sourceIndex))
                .toList();
        Map<String, Integer> orders = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            orders.put(sorted.get(i).itemId(), i + 1);
        }
        return orders;
    }

    private static int displayRank(ItemResult item) {
        if (item.riskLevel() == RiskLevel.SAFE) {
            return 0;
        }
        if (item.riskLevel() == RiskLevel.CAUTION) {
            return "unknown".equals(item.evidenceBasis()) ? 2 : 1;
        }
        return 3;
    }

    /** AI match_type → menu_analyses.menu_match_type. 메뉴 없으면 unmatched. */
    static String menuMatchType(Long menuId, String matchType) {
        if (menuId == null) {
            return "unmatched";
        }
        if (matchType == null) {
            return "runtime_variant";
        }
        return switch (matchType) {
            case "canonical", "spacing" -> "exact";
            case "synonym" -> "alias";
            default -> "fuzzy";
        };
    }

    private String skipped(String reason) {
        return json(jsonMapper.createObjectNode().put("skipped", reason));
    }

    private String json(JsonNode node) {
        return node == null || node.isNull() ? null : jsonMapper.writeValueAsString(node);
    }

    private static Long longOrNull(JsonNode node) {
        return node == null || !node.isIntegralNumber() ? null : node.asLong();
    }

    private static String text(JsonNode node) {
        return node == null || !node.isString() ? null : node.asString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
