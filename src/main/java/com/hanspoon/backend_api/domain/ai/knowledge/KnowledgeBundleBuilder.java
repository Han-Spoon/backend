package com.hanspoon.backend_api.domain.ai.knowledge;

import com.hanspoon.backend_api.domain.ai.dto.analyze.EvidenceRef;
import com.hanspoon.backend_api.domain.ai.dto.analyze.IngredientRef;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.Candidate;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.CandidateKnowledge;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.CandidateLookup;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.Confirmation;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ExactFeedback;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ExactFeedbackSlice;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.FlagConfirmation;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.GraphIngredient;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientAlias;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientComposition;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientCompositionTag;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientGraph;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.IngredientTag;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.ItemKnowledge;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.MenuIngredientPrior;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.MenuRecipeCorpus;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.OntologyMenu;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.OntologySlice;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.PriorSlice;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.RecipeIngredient;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.SourceReliability;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.SourceWeight;
import com.hanspoon.backend_api.domain.ai.dto.analyze.KnowledgeBundle.WebSearchCache;
import com.hanspoon.backend_api.domain.ai.dto.analyze.SliceStatus;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeQueryRepository.CandidateRow;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeQueryRepository.ConfirmationRow;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeQueryRepository.FlagConfirmationRow;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeQueryRepository.IngredientRow;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeQueryRepository.MenuRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judge 요청용 KnowledgeBundle 조립. 한 스냅샷(REPEATABLE READ)에서 읽음.
 *
 * <p>1단계 범위: 재료 그래프·후보·레시피 prior·가게 확정값·출처 신뢰도.
 * 가게 prior·메뉴 캐시·fallback_ontology·웹 캐시는 not_found.
 */
@Service
public class KnowledgeBundleBuilder {

    static final int MAX_CANDIDATES = 5;

    private static final String HARD = "hard";
    private static final String OWNER_FEEDBACK = "owner_feedback";
    private static final String OWNER_CONFIRMED = "owner_confirmed";
    private static final double CONFIRMED_WEIGHT = 1.0;

    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparingDouble(Candidate::score).reversed().thenComparingLong(Candidate::menuId);

    private final KnowledgeQueryRepository repository;

    public KnowledgeBundleBuilder(KnowledgeQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BuiltBundle build(Long storeId, String locale, List<BundleItem> items) {
        String knowledgeRevision = repository.snapshotAt().toString();

        Map<String, List<Candidate>> candidatesByItem = new LinkedHashMap<>();
        for (BundleItem item : items) {
            candidatesByItem.put(item.itemId(), lookupCandidates(item.normalizedMenuName()));
        }
        Set<Long> menuIds = candidatesByItem.values().stream()
                .flatMap(List::stream)
                .map(Candidate::menuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, OntologySlice> ontologies = loadOntologies(menuIds);
        Map<Long, StoreMenuFeedback> feedback = storeId == null ? Map.of() : loadFeedback(items);

        List<ItemKnowledge> itemKnowledge = new ArrayList<>(items.size());
        for (BundleItem item : items) {
            List<Candidate> candidates = candidatesByItem.get(item.itemId());
            StoreMenuFeedback storeMenuFeedback = item.storeMenuId() == null ? null : feedback.get(item.storeMenuId());
            List<CandidateKnowledge> knowledge = candidates.stream()
                    .map(candidate -> new CandidateKnowledge(
                            candidate.menuId(),
                            exactFeedback(storeId, candidate.menuId(), storeMenuFeedback),
                            ontologies.get(candidate.menuId()),
                            PriorSlice.notFound()))
                    .toList();
            itemKnowledge.add(new ItemKnowledge(
                    item.itemId(),
                    candidates.isEmpty() ? CandidateLookup.notFound() : new CandidateLookup(SliceStatus.OK, candidates),
                    knowledge,
                    OntologySlice.notFound(),
                    WebSearchCache.notFound()));
        }

        KnowledgeBundle bundle = new KnowledgeBundle(
                KnowledgeBundle.SCHEMA_VERSION, ingredientGraph(locale), itemKnowledge, sourceReliability());
        return new BuiltBundle(bundle, knowledgeRevision);
    }

    /** 정확·별칭 일치 우선, 없으면 fuzzy. 같은 메뉴는 최고 점수만, 최대 5. */
    private List<Candidate> lookupCandidates(String menuName) {
        if (menuName == null || menuName.isBlank()) {
            return List.of();
        }
        List<CandidateRow> rows = repository.findExactCandidates(menuName);
        if (rows.isEmpty()) {
            rows = repository.findFuzzyCandidates(menuName);
        }
        Map<Long, Candidate> best = new LinkedHashMap<>();
        for (CandidateRow row : rows) {
            Candidate candidate =
                    new Candidate(row.menuId(), row.categoryId(), row.nameKo(), row.score(), row.matchType());
            best.merge(row.menuId(), candidate, (a, b) -> a.score() >= b.score() ? a : b);
        }
        return best.values().stream()
                .sorted(CANDIDATE_ORDER)
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private Map<Long, OntologySlice> loadOntologies(Collection<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RecipeIngredient>> recipes = repository.findRecipeIngredients(menuIds).stream()
                .collect(Collectors.groupingBy(
                        KnowledgeQueryRepository.RecipeRow::menuId,
                        Collectors.mapping(row -> new RecipeIngredient(row.ingredientId()), Collectors.toList())));
        Map<Long, List<MenuRecipeCorpus>> corpora = repository.findCorpora(menuIds).stream()
                .collect(Collectors.groupingBy(
                        KnowledgeQueryRepository.CorpusRow::menuId,
                        Collectors.mapping(
                                row -> new MenuRecipeCorpus(row.corpus(), row.nTotal()), Collectors.toList())));
        Map<Long, List<MenuIngredientPrior>> priors = repository.findPriors(menuIds).stream()
                .collect(Collectors.groupingBy(
                        KnowledgeQueryRepository.PriorRow::menuId,
                        Collectors.mapping(
                                row -> new MenuIngredientPrior(row.corpus(), row.ingredientId(), row.kCount()),
                                Collectors.toList())));

        Map<Long, OntologySlice> slices = new LinkedHashMap<>();
        for (MenuRow menu : repository.findMenus(menuIds)) {
            slices.put(
                    menu.id(),
                    new OntologySlice(
                            SliceStatus.OK,
                            new OntologyMenu(
                                    menu.id(),
                                    menu.baseMenuId(),
                                    menu.remainToken(),
                                    menu.categoryId(),
                                    menu.source(),
                                    menu.ambiguityFlags(),
                                    menu.knowledgeRevision()),
                            recipes.getOrDefault(menu.id(), List.of()),
                            corpora.getOrDefault(menu.id(), List.of()),
                            priors.getOrDefault(menu.id(), List.of()),
                            null));
        }
        return slices;
    }

    private Map<Long, StoreMenuFeedback> loadFeedback(List<BundleItem> items) {
        Set<Long> storeMenuIds = items.stream()
                .map(BundleItem::storeMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (storeMenuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Confirmation>> confirmations = repository.findConfirmations(storeMenuIds).stream()
                .collect(Collectors.groupingBy(
                        ConfirmationRow::storeMenuId, Collectors.mapping(this::confirmation, Collectors.toList())));
        Map<Long, List<FlagConfirmation>> flags = repository.findFlagConfirmations(storeMenuIds).stream()
                .collect(Collectors.groupingBy(
                        FlagConfirmationRow::storeMenuId,
                        Collectors.mapping(this::flagConfirmation, Collectors.toList())));
        return storeMenuIds.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> new StoreMenuFeedback(
                                confirmations.getOrDefault(id, List.of()), flags.getOrDefault(id, List.of()))));
    }

    /** 가게 메뉴 확정값은 실제 요리 기준이라 모든 후보에 동일 적용. 가게·가게 메뉴 없으면 not_found. */
    private ExactFeedbackSlice exactFeedback(Long storeId, long menuId, StoreMenuFeedback feedback) {
        if (storeId == null || feedback == null) {
            return ExactFeedbackSlice.notFound();
        }
        return new ExactFeedbackSlice(
                SliceStatus.OK, new ExactFeedback(menuId, feedback.confirmations(), feedback.flags()));
    }

    private Confirmation confirmation(ConfirmationRow row) {
        String ref = "ingredient_confirmations:" + row.id();
        return new Confirmation(
                new IngredientRef(row.ingredientId(), row.ingredientName()),
                List.of(),
                status(row.present()),
                confirmedEvidence(ref, row.confirmedAt()));
    }

    private FlagConfirmation flagConfirmation(FlagConfirmationRow row) {
        String ref = "flag_confirmations:" + row.id();
        return new FlagConfirmation(
                row.ambiguityFlag(), row.askedTags(), status(row.present()), confirmedEvidence(ref, row.confirmedAt()));
    }

    private EvidenceRef confirmedEvidence(String ref, Instant confirmedAt) {
        return new EvidenceRef(ref, HARD, OWNER_FEEDBACK, ref, OWNER_CONFIRMED, CONFIRMED_WEIGHT, confirmedAt);
    }

    private static String status(boolean present) {
        return present ? "present" : "absent";
    }

    private IngredientGraph ingredientGraph(String locale) {
        List<IngredientRow> ingredients = repository.findIngredients(locale);
        if (ingredients.isEmpty()) {
            return new IngredientGraph(SliceStatus.NOT_FOUND, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        List<IngredientComposition> compositions = new ArrayList<>();
        List<IngredientCompositionTag> compositionTags = new ArrayList<>();
        for (var row : repository.findCompositions()) {
            compositions.add(new IngredientComposition(row.parentId(), row.childId(), row.ambiguityFlag()));
            row.tagCodes()
                    .forEach(tag ->
                            compositionTags.add(new IngredientCompositionTag(row.parentId(), row.childId(), tag)));
        }
        return new IngredientGraph(
                SliceStatus.OK,
                ingredients.stream()
                        .map(row -> new GraphIngredient(
                                row.id(),
                                row.nameKo(),
                                row.nameNormalized(),
                                row.localizedName(),
                                row.taxonomyCategory()))
                        .toList(),
                repository.findIngredientAliases().stream()
                        .map(row -> new IngredientAlias(row.ingredientId(), row.aliasNormalized()))
                        .toList(),
                ingredients.stream()
                        .flatMap(row -> row.tagCodes().stream().map(tag -> new IngredientTag(row.id(), tag)))
                        .toList(),
                compositions,
                compositionTags);
    }

    private SourceReliability sourceReliability() {
        List<SourceWeight> weights = repository.findSourceWeights().stream()
                .map(row -> new SourceWeight(row.sourceType(), row.weight()))
                .toList();
        return weights.isEmpty() ? SourceReliability.notFound() : new SourceReliability(SliceStatus.OK, weights);
    }

    /** Bundle 입력 메뉴. storeMenuId 는 가게 스캔이고 정규화 이름이 있을 때만. */
    public record BundleItem(String itemId, String normalizedMenuName, Long storeMenuId) {}

    public record BuiltBundle(KnowledgeBundle bundle, String knowledgeRevision) {}

    private record StoreMenuFeedback(List<Confirmation> confirmations, List<FlagConfirmation> flags) {}
}
