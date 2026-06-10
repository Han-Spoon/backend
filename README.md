<div align="center">

# 🥄 한스푼 (Han-Spoon) · Backend API

**외국인을 위한 한국 식당 메뉴판 OCR · 식단/알레르기 위험 분석 서비스의 백엔드**

<!-- ░░░ BADGES ░░░ 실제 값으로 교체하세요 -->
![Version](https://img.shields.io/badge/version-0.0.1--SNAPSHOT-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)](../../actions)

</div>

---

## 📖 목차

- [소개](#-소개)
- [개발 동기](#-개발-동기)
- [주요 기능](#-주요-기능)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [시작하기 (Getting Started)](#-시작하기-getting-started)
- [사용법 (Usage)](#-사용법-usage)
- [프로젝트 구조](#-프로젝트-구조)
- [기여하기 (Contributing)](#-기여하기-contributing)
- [라이선스](#-라이선스)
- [문의](#-문의)

---

## 🍽 소개

> **메뉴판을 촬영하면, 내가 먹어도 되는 메뉴인지 알려드립니다.**

**한스푼**은 한국을 방문한 외국인이 식당 메뉴판을 촬영하면, **OCR → 메뉴 정규화 → 식단·알레르기 위험 판정 → 사장님 소통 카드**까지 제공하는 서비스입니다. 이 저장소는 그 핵심 비즈니스 로직을 담당하는 **Spring Boot 백엔드 API**입니다.

종교(할랄·코셔·힌두), 채식 유형(비건·락토·오보 등), 식약처 알레르기 19종 + 알코올을 기준으로 메뉴의 위험도를 **규칙 기반으로 즉시 판정**하고, 규칙으로 가릴 수 없는 애매한 경우(숨은 육수·양념 등)에만 AI를 보조적으로 사용합니다.

---

## 💡 개발 동기

> 외국인 관광객은 한국 메뉴판을 읽기 어려울 뿐 아니라, **종교·알레르기·채식** 제약 때문에 "이 음식을 먹어도 되는지"를 식당에서 확인하기가 매우 까다롭습니다.

- 📷 **언어 장벽** — 한글 메뉴판을 읽고 재료를 추정하기 어렵다.
- ⚠️ **식단 제약** — 돼지고기·알코올·갑각류 등이 보이지 않게 들어가는 경우가 많다(육수, 젓갈, 맛술 등).
- 💸 **가벼운 운영** — 3주 MVP·소규모 예산 안에서, 무거운 인프라 없이 **서버리스 + 관리형 서비스**로 빠르게 만든다.

이 문제를 "촬영 한 번"으로 해결하고자 개발을 시작했습니다.

---

## ✨ 주요 기능

| #   | 기능               | 설명                                                                  |
|-----|------------------|---------------------------------------------------------------------|
| 1️⃣ | **구글 OAuth 로그인** | Google ID Token 검증 기반의 Stateless JWT 인증 (Access/Refresh)            |
| 2️⃣ | **온보딩 프로필 관리**   | 국적·종교·채식 유형·알레르기(식약처 19종)·금주·매운맛 선호 저장/수정                           |
| 3️⃣ | **메뉴판 스캔 분석**    | Blob 업로드 → OCR → 메뉴 정규화 → **Rule Engine 위험 판정** → 결과 저장 (비동기 파이프라인) |
| 4️⃣ | **위험도 안내**       | 메뉴별 `danger` / `caution` / `safe` 판정 + 매칭된 위험 태그(hits) + 다국어 안내문    |
| 5️⃣ | **사장님 소통 카드**    | "이 메뉴에 ○○가 들어가나요?" 같은 다국어 소통 카드 저장/조회/삭제                            |
| 6️⃣ | **스캔 이력**        | 사용자별 스캔 이력 목록 조회·제목 수정·삭제 (페이지네이션)                                  |
| 7️⃣ | **큐레이션 제공**      | 한국 식문화가 낯선 사용자들을 위한 한식 및 식문화 인사이트 큐레이션 카드 제공                        |                                                            |

> 🤖 **AI 사용 원칙:** 위험 판정은 **규칙 기반(GPT 호출 없음)** 으로 즉시 처리하고, 숨은 재료처럼 규칙으로 못 잡는 케이스에만 AI를 보조적으로 호출합니다.

---

## 🛠 기술 스택

### Backend

| 구분 | 기술 |
|------|------|
| Language | **Java 21 (LTS)** |
| Framework | Spring Boot **4.0.6**, Spring Web MVC |
| Security | Spring Security + OAuth2 Resource Server (Google ID Token), JWT (jjwt) |
| ORM / DB | Spring Data JPA + Hibernate 6, **PostgreSQL 16 + pgvector** |
| Migration | Flyway |
| Cache / Rate Limit | Caffeine, Bucket4j |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle (Kotlin DSL) |

### Cloud & Infra

| 구분 | 서비스 |
|------|--------|
| Compute | Azure Container Apps (ACA) |
| Registry | Azure Container Registry (ACR) |
| Storage | Azure Blob Storage |
| Secret | Azure Key Vault |
| AI | Azure Document Intelligence (OCR), Azure OpenAI (GPT-4o-mini) |
| CI/CD | GitHub Actions (OIDC) → ACR → ACA |

---

## 🏗 시스템 아키텍처

![시스템 아키텍처 다이어그램]('/Users/yoonseo/Desktop/스크린샷 2026-06-09 오전 12.24.47.png')

---

## 📂 프로젝트 구조

```text
backend/
├── src/main/java/com/hanspoon/backend_api/
│   ├── domain/            # 도메인별 패키지 (user, scan, ai, card, upload ...)
│   ├── global/            # 공통 설정·예외·보안
│   └── ...
├── src/main/resources/
│   ├── db/migration/      # Flyway 마이그레이션 (V1__init.sql ...)
│   ├── application.yml    # 공통 설정
│   ├── application-local.yml
│   └── application-prod.yml
├── docs/                  # 설계 문서 (아키텍처·ERD·API·Rule Engine ...)
├── infra/                 # Azure 프로비저닝·배포 스크립트
└── docker/Dockerfile      # 멀티스테이지 빌드 (Temurin 21)
```

> 📑 설계 문서 모음은 [`docs/README.md`](docs/README.md)에서 한눈에 볼 수 있습니다.

---

## 🤝 기여하기 (Contributing)

기여는 언제나 환영합니다! 아래 절차를 따라주세요.

1. 이슈를 먼저 등록하거나 기존 이슈를 확인합니다. ([이슈 템플릿](.github/ISSUE_TEMPLATE) 사용)
2. `dev` 브랜치에서 `feat/#이슈번호-설명` 형식으로 브랜치를 생성합니다. *(Git-flow)*
3. 코드 스타일을 맞춥니다.
   ```bash
   ./gradlew spotlessApply   # 포매팅 자동 적용
   ./gradlew build           # 빌드 + 테스트 통과 확인
   ```
4. 커밋 컨벤션을 지켜 커밋합니다. (예: `✨feat: ...`, `🐛fix: ...` — [`docs/06-git-convention.md`](docs/06-git-convention.md))
5. `dev`를 향해 Pull Request를 보냅니다. ([PR 템플릿](.github/PULL_REQUEST_TEMPLATE.md))

> ✅ 모든 PR은 GitHub Actions CI(`spotlessCheck` + `build`)를 통과해야 합니다.

---

## 📄 라이선스

이 프로젝트는 **MIT License** 를 따릅니다. 자세한 내용은 `LICENSE` 파일을 참고하세요.

---

## 📮 문의

| 채널 | 주소                                                  |
|------|-----------------------------------------------------|
| Organization | [github.com/Han-Spoon](https://github.com/Han-Spoon) |
| Issues | [이슈 등록하기](../../issues)                             |
| Email | `lys8167@gmail.com`                    |

<div align="center">

**Made with 🥄 by Team Han-Spoon**

</div>
