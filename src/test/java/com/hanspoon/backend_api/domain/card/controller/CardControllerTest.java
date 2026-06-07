package com.hanspoon.backend_api.domain.card.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanspoon.backend_api.domain.card.dto.CardText;
import com.hanspoon.backend_api.domain.card.dto.SavedCardResponse;
import com.hanspoon.backend_api.domain.card.entity.CardType;
import com.hanspoon.backend_api.domain.card.service.CardService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** standalone 슬라이스. @CurrentUser 는 고정 userId 로 해석. 인증 강제는 SecurityConfig 담당(여기 범위 밖). */
class CardControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final CardService cardService = mock(CardService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver currentUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return USER_ID.toString();
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new CardController(cardService))
                .setCustomArgumentResolvers(currentUserResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void saveReturns201() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(cardService.save(eq(USER_ID), any()))
                .thenReturn(new SavedCardResponse(
                        cardId,
                        CardType.INGREDIENT_CHECK,
                        "된장찌개",
                        new CardText("이 메뉴에 멸치육수가 들어가나요?", null, null),
                        Instant.now()));

        mockMvc.perform(
                        post("/api/v1/cards/saved")
                                .contentType("application/json")
                                .content(
                                        "{\"type\":\"ingredient_check\",\"menuNameKo\":\"된장찌개\",\"text\":{\"ko\":\"이 메뉴에 멸치육수가 들어가나요?\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardId").value(cardId.toString()))
                .andExpect(jsonPath("$.type").value("ingredient_check"))
                .andExpect(jsonPath("$.text.ko").value("이 메뉴에 멸치육수가 들어가나요?"));
    }

    @Test
    void saveRejectsBlankMenuName() throws Exception {
        mockMvc.perform(post("/api/v1/cards/saved")
                        .contentType("application/json")
                        .content("{\"type\":\"order\",\"menuNameKo\":\"\",\"text\":{\"ko\":\"삼겹살 하나 주세요.\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveRejectsMissingText() throws Exception {
        mockMvc.perform(post("/api/v1/cards/saved")
                        .contentType("application/json")
                        .content("{\"type\":\"order\",\"menuNameKo\":\"삼겹살\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveRejectsBlankTextKo() throws Exception {
        mockMvc.perform(post("/api/v1/cards/saved")
                        .contentType("application/json")
                        .content("{\"type\":\"order\",\"menuNameKo\":\"삼겹살\",\"text\":{\"ko\":\"\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCardsReturnsPage() throws Exception {
        UUID cardId = UUID.randomUUID();
        SavedCardResponse item = new SavedCardResponse(
                cardId, CardType.ORDER, "삼겹살", new CardText("삼겹살 하나 주세요.", null, null), Instant.now());
        when(cardService.getCards(eq(USER_ID), any())).thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/cards/saved?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].cardId").value(cardId.toString()))
                .andExpect(jsonPath("$.items[0].text.ko").value("삼겹살 하나 주세요."));
    }

    @Test
    void getCardsIgnoresClientSortParam() throws Exception {
        when(cardService.getCards(eq(USER_ID), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        // Swagger 가 채우는 잘못된 sort(=string)가 와도 500 이 아니라 정상 처리되어야 한다
        mockMvc.perform(get("/api/v1/cards/saved?sort=string")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(cardService).getCards(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID cardId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/cards/saved/{cardId}", cardId)).andExpect(status().isNoContent());

        verify(cardService).delete(USER_ID, cardId);
    }
}
