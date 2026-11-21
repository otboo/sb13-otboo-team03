# 제목·자유 속성 기반 LLM 코디 추천

## 목적과 범위

사용자가 소유한 모든 의상의 제목과 실제 선택한 속성 이름·값을 Groq에 전달한다. LLM은 기존 두께·소매 사전에 없는 특징도 해석하고 전체 코디를 구성한다. 예를 들어 `시스루 크롭 셔츠`, `원단 특징=비침 있음`, `핏=오버핏`을 사용하기 위해 기존 속성 해석 사전을 수정할 필요가 없다.

이 단계는 텍스트 기반 분석·추천을 한 번의 요청에서 수행한다. 사진, 임베딩, 벡터 DB, 외부 문서 검색은 사용하지 않으며 아직 RAG가 아니다. AI가 해석한 특징은 해당 요청의 판단·검증에 사용하고 DB의 사용자 속성이나 관리자 속성 정의를 변경하지 않는다. 분석 결과를 영구 저장하거나 캐시하지 않으므로 제목·속성 변경은 다음 요청에 반영된다.

## 실행 흐름

```text
GET /api/recommendations?weatherId=...
→ RecommendationInputReader: 날씨·프로필·소유 의상·속성 일괄 조회
→ 조회 트랜잭션 종료
→ Redis에서 직전 추천 ID 조회
→ BasicOutfitAlgorithm으로 장애 시 사용할 기본 추천 계산
→ LlmOutfitRecommender: 활성화·입력 크기 확인
→ GroqOutfitRequestFactory: 제목·속성 JSON과 Structured Outputs 스키마 구성
→ GroqOutfitClient: 코디 DTO 변환 / global.client.groq.GroqClient: HTTP 통신
→ LlmOutfitValidator: 소유 의상 ID·조합·분석 근거 검사
→ 유효한 AI 코디 또는 기본 추천 선택
→ 기존 순서로 DTO 변환
→ 최종 반환 의상 ID를 Redis에 한 번 기록
```

기본 조합이 불가능하면 빈 결과를 반환하고 외부 호출을 생략한다. 기존 API URL·인증·응답 DTO·이미지와 원본 속성 반환 형식을 유지한다. LLM의 분석·추천 이유는 현재 응답에 추가하지 않는다.

## 기존 알고리즘과 LLM의 관계

- 기본 알고리즘은 두께·소매 정책을 그대로 사용한다.
- LLM에는 기본 알고리즘의 동점 후보나 점수로 거른 목록을 전달하지 않는다. 입력 크기 제한 안에서 사용자 소유 의상 전체를 전달한다.
- 상의·하의가 있어도 원피스를 선택할 수 있다. 아우터와 추가 아이템은 날씨·자유 속성·전체 조화를 고려해 선택할 수 있다.
- LLM 경로에는 기본 알고리즘의 15℃ 아우터/스카프 조건, 종류별 최상위 두께·소매 점수, 추가 아이템의 무조건 포함을 강제하지 않는다.
- 직전 추천 ID는 적절한 대안이 있을 때 다른 코디를 고르는 참고 정보다. 모든 대안을 배제하는 강제 필터는 아니다.
- LLM 장애·검증 실패 시에는 기존 기본 추천을 사용한다.

시스템 프롬프트는 `src/main/resources/prompts/outfit-recommendation-system.txt`에서 관리한다. `GroqOutfitRequestFactory`는 프롬프트와 JSON 응답 스키마를 구성하고, `GroqOutfitClient`는 코디 DTO 변환을, `global.client.groq.GroqClient`는 공통 HTTP 통신·완료 상태 검사를 담당한다. 공통 응답 예외는 `global.exception.InvalidLlmResponseException`에서 관리한다. `LlmOutfitValidator`는 응답 구조, 의상 조합, 분석 근거를 순서대로 검증한다.

## 패키지 구성

- `global/client/groq`: 추천 DTO에 의존하지 않는 Groq HTTP 통신
- `global/config`: `GroqConfig`·`GroqProperties` — 공통 API 키·HTTP 연결 설정
- `global/exception`: 외부 LLM 응답 예외
- `recommendation/client`: 코디 요청·응답 변환 및 프롬프트/스키마 구성
- `recommendation/client/dto`: LLM 통신에 사용하는 입력·출력 DTO
- `recommendation/config`: `RecommendationLlmConfig`·`RecommendationLlmProperties` — 추천 활성화·모델·입출력 제한
- `recommendation/dto`: API 응답 DTO와 내부 조회 결과 `RecommendationSnapshot`
- `recommendation/service`: 추천 실행·조회·장애 복구
- `recommendation/validation`: 코디·분석 근거 검증

의존 방향은 추천 기능 → 공통 Groq 클라이언트다. 공통 클라이언트와 연결 설정은 추천 패키지를 참조하지 않는다. 날씨 클라이언트와 동일하게 외부 통신 DTO를 `client/dto`에 두고, 단순 데이터 전달 객체인 조회 결과는 `dto`에 둔다.

## 입력 데이터

전달하는 정보:

- 실제 기온(℃), 하늘 상태, 강수 형태, 강수 확률(%), 습도(%), 풍속(m/s)
- 온도 민감도: 1·2는 추위를 타는 방향, 3은 중립, 4·5는 더위를 타는 방향. 미설정은 3
- 직전 추천 의상 ID
- 의상 ID·제목·종류·실제로 선택한 모든 속성 이름과 값

성명·이메일·생년월일·정확한 위치·이미지·이미지 URL·속성 선택지 전체는 전달하지 않는다. 제목과 속성 안에 명령이 있어도 데이터로만 취급하도록 시스템 프롬프트에서 명시한다.

## 분석 결과와 근거

모델은 선택한 의상 ID와 각 의상의 특징을 반환한다.

```json
{
  "clothesIds": ["상의 UUID", "하의 UUID"],
  "analyses": [
    {
      "clothesId": "상의 UUID",
      "features": [
        {"name": "기장", "value": "크롭", "evidence": "크롭", "source": "TITLE"},
        {"name": "비침", "value": "있음", "evidence": "비침 있음", "source": "ATTRIBUTE"}
      ]
    },
    {"clothesId": "하의 UUID", "features": []}
  ],
  "reason": "입력에 근거한 짧은 추천 이유"
}
```

예시의 UUID 표기는 설명용이며 실제 요청에서는 UUID 문자열을 사용한다. `name`, `value`는 자유 문자열이고 고정된 속성 사전에 제한하지 않는다. `source`만 TITLE/ATTRIBUTE로 구분한다. 제목에서 알 수 없는 실제 보온 성능·방수 기능·소재 혼용률 등을 만들어내지 않도록 지시한다. 근거 있는 특징이 없으면 빈 features 배열을 허용한다.

검증기는 인용 문구가 해당 의상의 실제 제목이나 속성 값 안에 있는지 확인한다. 이는 인용의 존재를 검증하며, 문구를 올바르게 해석했는지나 추천 이유의 의미가 정확한지까지 보장하지 않는다. 실제 추천 품질은 대표 옷장·날씨 사례를 비교해 평가해야 한다.

## 서버 검증

- 선택 ID가 이번 요청의 사용자 소유 의상인지 검사한다.
- 중복 의상 ID, 종류별 2개 이상 선택, UNDERWEAR·ETC를 거부한다.
- 상의+하의 또는 원피스의 기본 조합을 요구하고 혼합을 거부한다.
- 신발 없는 양말을 거부한다.
- 선택한 의상마다 분석 항목이 하나씩 있는지 확인한다.
- 특징의 이름·값·근거와 출처를 확인하고 다른 의상에서 가져온 근거를 거부한다.
- 모델 응답이 끝까지 완료됐는지(`finish_reason=stop`) 확인한다. 거부·잘린 응답·잘못된 JSON을 사용하지 않는다.
- 반환 의상의 순서는 기본 조합 → 아우터 → 신발 → 양말 → 가방 → 모자 → 액세서리 → 스카프다.

## 로컬 활성화

기본값은 비활성화다. `.env`에 아래 값을 설정하고 앱을 다시 시작한다.

```dotenv
RECOMMENDATION_LLM_ENABLED=true
GROQ_API_KEY=발급받은_키
GROQ_RECOMMENDATION_MODEL=openai/gpt-oss-120b
```

IDE 실행 시 dev 프로필이 `.env`를 읽는다. Compose의 app 서비스는 기존 `env_file: .env`를 사용한다. 키는 `.env.example`이나 Git에 저장하지 않는다. 활성화했는데 키나 모델이 없으면 시작 시 설정 오류를 알린다.

Spring AI 추가 의존성 없이 프로젝트의 Spring RestClient와 Jackson을 사용한다. 호출 대상은 고정된 `https://api.groq.com/openai/v1/chat/completions`다. 실제 외부 호출과 무료 한도는 활성화한 계정에 적용된다.

`application.yaml`의 `groq`에서 공통 API 키와 HTTP 연결 제한을 설정한다. 추천 활성화·모델·입출력 제한은 `otboo.recommendation.llm`에서 설정한다. 위 환경 변수 이름은 그대로 사용한다.

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| groq.connect-timeout | 2s | HTTP 연결 제한 |
| groq.read-timeout | 8s | HTTP 응답 읽기 제한. 총 실행 시간의 절대 상한은 아님 |
| otboo.recommendation.llm.max-clothes | 80 | 초과 시 일부 옷만 잘라 보내지 않고 기본 추천 사용 |
| otboo.recommendation.llm.max-input-characters | 12000 | 사용자 입력 JSON 길이 제한. 토큰 수와 동일하지 않음 |
| otboo.recommendation.llm.max-completion-tokens | 2048 | 추론 토큰을 포함한 출력 상한. 초과로 잘리면 기본 추천 사용 |

429·인증/서버 오류·통신 실패·시간 초과·JSON 오류·검증 실패는 기본 추천으로 복구하며 자동 재시도하지 않는다. 예상하지 못한 코드 오류는 숨기지 않는다. 로그에는 실패 종류·입력 크기만 남기고 키·제목·속성·외부 응답 본문은 남기지 않는다.

추천은 조회 시점의 옷장 스냅샷을 사용한다. 동시에 수정된 의상 정보는 다음 요청에서 반영된다.

## 검증 방법

```bash
./gradlew test --tests 'com.otboo.team03.recommendation.*' --tests 'com.otboo.team03.global.client.groq.*'
```

- Groq 클라이언트: 모의 HTTP 서버로 경로·키 헤더·전체 입력·자유 특징 스키마·미완료 응답 검사
- 검증기: 사전에 없는 특징 허용, 소유권·기본 조합·중복·잘못된 근거 검사
- 복구: 비활성화·입력 제한·429·시간 초과·JSON 오류 시 기본 추천 사용
- API 통합: 실제 PostgreSQL·Redis와 모의 Groq를 사용해 기본 추천과 다른 원피스 선택, 최종 Redis 기록, 트랜잭션 종료 후 호출 및 장애 복구 검사

자동 테스트는 실제 Groq를 호출하지 않는다. 실제 연결은 키를 설정하고 서버를 실행한 뒤 추천 API로 확인한다. 모델의 추천 품질·속도·무료 계정 한도는 모의 테스트로 확인할 수 없으며, 추천 품질은 여러 옷장·날씨 사례로 별도로 평가해야 한다.

## 참고

- [기본 추천 정책](recommendation-design.md)
- [Groq Structured Outputs](https://console.groq.com/docs/structured-outputs)
- [Groq GPT-OSS 120B](https://console.groq.com/docs/model/openai/gpt-oss-120b)
