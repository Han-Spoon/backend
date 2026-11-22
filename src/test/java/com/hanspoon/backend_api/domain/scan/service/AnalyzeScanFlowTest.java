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
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareRequest;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PrepareResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class AnalyzeScanFlowTest {

    private static final UUID SCAN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final VerifiedUpload UPLOAD =
            new VerifiedUpload("scans/u/menu.jpg", "version-1", "\"etag-1\"", 123L, "image/jpeg");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private AiAnalyzeClient aiAnalyzeClient;

    @Mock
    private AnalyzeStateWriter analyzeStateWriter;

    @Mock
    private S3StorageService s3StorageService;

    private AnalyzeScanFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AnalyzeScanFlow(aiAnalyzeClient, analyzeStateWriter, s3StorageService, mapper);
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

    /** 문서 예시의 scan-123 을 SCAN_ID 로 교체. */
    private ObjectNode fixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ai/analyze/prepare-response.json")) {
            String json = new String(in.readAllBytes()).replace("scan-123", SCAN_ID.toString());
            return (ObjectNode) mapper.readTree(json);
        }
    }
}
