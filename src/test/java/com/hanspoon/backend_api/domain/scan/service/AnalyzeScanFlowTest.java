package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.ai.client.AiAnalyzeClient;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResult;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeBundleBuilder;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeBundleBuilder.BuiltBundle;
import com.hanspoon.backend_api.domain.ai.knowledge.KnowledgeBundleBuilder.BundleItem;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class AnalyzeScanFlowTest {

    private static final UUID SCAN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final VerifiedUpload UPLOAD =
            new VerifiedUpload("scans/u/menu.jpg", "version-1", "\"etag-1\"", 123L, "image/jpeg");

    private static final Long STORE_ID = 123456L;
    private static final RuleProfile PROFILE = new RuleProfile("halal", false, null, false, List.of(), false);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private AiAnalyzeClient aiAnalyzeClient;

    @Mock
    private AnalyzeStateWriter analyzeStateWriter;

    @Mock
    private KnowledgeBundleBuilder knowledgeBundleBuilder;

    @Mock
    private S3StorageService s3StorageService;

    private AnalyzeScanFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AnalyzeScanFlow(
                aiAnalyzeClient, analyzeStateWriter, knowledgeBundleBuilder, s3StorageService, mapper);
    }

    @Test
    void usablePrepareIsSavedWithRawItems() throws IOException {
        ObjectNode fixture = fixture();
        when(aiAnalyzeClient.prepare(any())).thenReturn(mapper.treeToValue(fixture, PrepareResponse.class));
        when(analyzeStateWriter.applyPrepareResult(any(), any(), anyList(), eq("en"), any()))
                .thenReturn(List.of());

        var prepared = flow.prepare(SCAN_ID, UPLOAD, null, "en");

        assertThat(prepared).isPresent();
        assertThat(prepared.get().items()).extracting(PreparedItem::displayName).containsExactly("김치 찌개");
        ArgumentCaptor<PrepareRequest> request = ArgumentCaptor.forClass(PrepareRequest.class);
        verify(aiAnalyzeClient).prepare(request.capture());
        assertThat(request.getValue().scanSessionId()).isEqualTo(SCAN_ID.toString());
        assertThat(request.getValue().image().source()).isEqualTo("upload");
        assertThat(request.getValue().image().expectedEtag()).isEqualTo("\"etag-1\"");
        assertThat(request.getValue().locale()).isEqualTo("en");

        ArgumentCaptor<ScanAiResult> aiResult = ArgumentCaptor.forClass(ScanAiResult.class);
        verify(analyzeStateWriter).applyPrepareResult(any(), aiResult.capture(), anyList(), eq("en"), any());
        assertThat(mapper.readTree(aiResult.getValue().getPreparedItems()))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(fixture.get("prepared_items"))));
        assertThat(aiResult.getValue().getPreparedItems()).contains("\"origin_text\":null");
    }

    @Test
    void needsRetakeSkipsItems() throws IOException {
        ObjectNode fixture = fixture();
        fixture.putArray("prepared_items");
        ObjectNode quality = fixture.putObject("scan_quality");
        quality.put("status", "needs_retake");
        quality.putArray("reasons").add("blur");
        quality.putArray("retake_suggestions").add("hold_steady");
        when(aiAnalyzeClient.prepare(any())).thenReturn(mapper.treeToValue(fixture, PrepareResponse.class));

        assertThat(flow.prepare(SCAN_ID, UPLOAD, "camera", "ko")).isEmpty();

        verify(analyzeStateWriter)
                .applyPrepareRetake(any(), eq(SCAN_ID), eq(List.of("blur")), eq(List.of("hold_steady")));
        verify(analyzeStateWriter, never()).applyPrepareResult(any(), any(), anyList(), any(), any());
    }

    @Test
    void rejectsItemIdOfAnotherScan() throws IOException {
        ObjectNode fixture = fixture();
        ((ObjectNode) fixture.get("prepared_items").get(0)).put("item_id", "other:0");
        when(aiAnalyzeClient.prepare(any())).thenReturn(mapper.treeToValue(fixture, PrepareResponse.class));

        assertThatThrownBy(() -> flow.prepare(SCAN_ID, UPLOAD, null, "ko"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_RESULT_MISMATCH));
        verifyNoInteractions(analyzeStateWriter);
    }

    @Test
    void rejectsResponseForAnotherScan() throws IOException {
        ObjectNode fixture = fixture();
        fixture.put("scan_session_id", UUID.randomUUID().toString());
        when(aiAnalyzeClient.prepare(any())).thenReturn(mapper.treeToValue(fixture, PrepareResponse.class));

        assertThatThrownBy(() -> flow.prepare(SCAN_ID, UPLOAD, null, "ko"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scan_session_id");
        verifyNoInteractions(analyzeStateWriter);
    }

    @Test
    void judgeSendsPreparedRawItemsAndBundleThenSaves() throws IOException {
        AnalyzeScanFlow.PreparedScan prepared = preparedScan();
        when(knowledgeBundleBuilder.build(eq(STORE_ID), eq("en"), anyList()))
                .thenReturn(new BuiltBundle(null, "2026-10-12T03:00:00Z"));
        JudgeResult result = judgeResult(judgeFixture());
        when(aiAnalyzeClient.judge(any())).thenReturn(result);

        flow.judge(SCAN_ID, STORE_ID, prepared, PROFILE, "en");

        ArgumentCaptor<List<BundleItem>> bundleItems = ArgumentCaptor.forClass(List.class);
        verify(knowledgeBundleBuilder).build(eq(STORE_ID), eq("en"), bundleItems.capture());
        assertThat(bundleItems.getValue()).containsExactly(new BundleItem(SCAN_ID + ":0", "김치찌개", 7L));
        ArgumentCaptor<JudgeRequest> request = ArgumentCaptor.forClass(JudgeRequest.class);
        verify(aiAnalyzeClient).judge(request.capture());
        assertThat(request.getValue().preparedItems())
                .isSameAs(prepared.response().preparedItems());
        assertThat(request.getValue().traceId()).isEqualTo(prepared.response().traceId());
        assertThat(request.getValue().knowledgeRevision()).isEqualTo("2026-10-12T03:00:00Z");
        ArgumentCaptor<String> diet = ArgumentCaptor.forClass(String.class);
        verify(analyzeStateWriter).applyJudgeResult(eq(SCAN_ID), eq(prepared.analyses()), eq(result), diet.capture());
        assertThat(mapper.readTree(diet.getValue()).get("religion_type").asString())
                .isEqualTo("halal");
    }

    @Test
    void judgeRejectsDifferentKnowledgeRevision() throws IOException {
        when(knowledgeBundleBuilder.build(any(), any(), anyList())).thenReturn(new BuiltBundle(null, "other-revision"));
        when(aiAnalyzeClient.judge(any())).thenReturn(judgeResult(judgeFixture()));

        assertThatThrownBy(() -> flow.judge(SCAN_ID, STORE_ID, preparedScan(), PROFILE, "en"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("request context");
        verify(analyzeStateWriter, never()).applyJudgeResult(any(), any(), any(), any());
    }

    @Test
    void judgeRejectsInvalidIdempotencyKey() throws IOException {
        ObjectNode judge = judgeFixture();
        ObjectNode command = judge.putArray("persistence_commands").addObject();
        command.put("command_id", "c")
                .put("action", "create_owner_verification_request")
                .put("idempotency_key", "bad key");
        command.putObject("payload");
        when(knowledgeBundleBuilder.build(any(), any(), anyList()))
                .thenReturn(new BuiltBundle(null, "2026-10-12T03:00:00Z"));
        when(aiAnalyzeClient.judge(any())).thenReturn(judgeResult(judge));

        assertThatThrownBy(() -> flow.judge(SCAN_ID, STORE_ID, preparedScan(), PROFILE, "en"))
                .hasMessageContaining("persistence_commands");
    }

    private AnalyzeScanFlow.PreparedScan preparedScan() throws IOException {
        PrepareResponse response = mapper.treeToValue(fixture(), PrepareResponse.class);
        List<PreparedItem> items = mapper.readerForListOf(PreparedItem.class)
                .readValue(mapper.writeValueAsString(response.preparedItems()));
        MenuAnalysis analysis = MenuAnalysis.prepared(SCAN_ID, 0, "김치 찌개", null, "8,000원", "[]", null, STORE_ID, 7L);
        return new AnalyzeScanFlow.PreparedScan(response, items, List.of(analysis));
    }

    private ObjectNode judgeFixture() throws IOException {
        return (ObjectNode) mapper.readTree(resource("/ai/analyze/judge-response.json"));
    }

    private JudgeResult judgeResult(JsonNode raw) {
        return new JudgeResult(mapper.treeToValue(raw, JudgeResponse.class), raw);
    }

    /** 문서 예시의 scan-123 을 SCAN_ID 로 교체. */
    private String resource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return new String(in.readAllBytes()).replace("scan-123", SCAN_ID.toString());
        }
    }

    private ObjectNode fixture() throws IOException {
        return (ObjectNode) mapper.readTree(resource("/ai/analyze/prepare-response.json"));
    }
}
