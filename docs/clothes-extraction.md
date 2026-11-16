# 구매 링크 의상 초기값 추출

## API

인증된 사용자가 `GET /api/clothes/extractions?url=<URL 인코딩한 구매 링크>`를 요청한다.
응답은 기존 ClothesDto 형태이며 id=null, ownerId=인증 사용자, name=상품명,
imageUrl=대표 이미지(없으면 null), type=null, attributes=[]다.
DB 저장과 S3 업로드는 수행하지 않는다. 타입과 필수 속성은 사용자가 선택한 후 등록한다.

## 요청 범위와 제한

- 허용 호스트: musinsa.com, www.musinsa.com, store.musinsa.com, 29cm.co.kr, www.29cm.co.kr, product.29cm.co.kr, wconcept.co.kr, www.wconcept.co.kr.
- HTTPS 및 기본 포트/443만 허용한다. 사용자 정보, fragment, 2048자를 넘는 URL은 거절한다.
- 유사 도메인·임의 서브도메인·IP 주소는 허용하지 않는다.
- 리다이렉트는 따라가지 않는다. 공유/단축 링크 대신 브라우저 주소창의 최종 상품 URL을 사용한다.
- 요청 제한 5초, HTML 크기 제한 2MiB, HTTP 200 및 text/html만 처리한다.
- 상품 경로만 허용한다: 무신사 `/products/{숫자}` 또는 `/app/goods/{숫자}`, 29CM `/catalog/{숫자}` 또는 `/products/{숫자}`, W컨셉 `/Product/{숫자}`. 쿼리 파라미터와 마지막 슬래시는 허용한다.
- og:title과 상품 근거가 함께 있어야 한다. 상품 근거는 og:type=product/og:product 또는 JSON-LD의 Product 타입이다. JSON-LD 배열, @graph, mainEntity도 검사하며 추천 목록(itemListElement)은 근거로 사용하지 않는다.
- og:image는 선택이며 상대 경로는 절대 경로로 변환한다. 상품 URL이더라도 일반 웹사이트/로그인 HTML만 내려오면 추출 실패다.
- JavaScript 실행, 로그인, 접근 차단 우회, LLM 추론은 하지 않는다.
- 상품 근거가 없는 페이지는 실패 처리한다. 사이트가 제공하는 메타데이터의 내용 자체를 보증하지는 않으므로 등록 전에 확인한다. 상품 근거가 HTML에 없는 동적 페이지도 실패할 수 있다.

## 실패 응답

| HTTP | exceptionName | 의미 |
| --- | --- | --- |
| 400 | INVALID_PURCHASE_URL | 잘못된 URL 형식/프로토콜/포트 또는 비상품 경로 |
| 400 | UNSUPPORTED_SHOPPING_SITE | 허용 목록 밖의 사이트 |
| 400 | CLOTHES_EXTRACTION_FAILED | 요청 실패, 리다이렉트, 차단, 비HTML, 크기 초과, 상품명 메타데이터 누락 |
| 401 | AUTHENTICATION_REQUIRED | 인증 없음 |

## 프런트 연동 주의점

현재 배포된 프런트 번들은 imageUrl을 fetch한 뒤 File로 변환하여 기존 multipart 등록에 사용한다.
쇼핑몰 이미지 서버가 CORS를 허용하지 않으면 상품명 추출에 성공해도 이미지 다운로드가 실패할 수 있다.
이미지 다운로드를 await하고 HTTP 성공·image MIME·빈 파일·5MiB 한도를 검사한다. 다운로드에는 10초 제한이 있다.
다운로드 실패/이미지 URL 없음 시 상품명 초기값은 유지하고 수동 이미지 선택을 안내한다. 이미지 적용까지 성공했을 때만 이미지 포함 성공 메시지를 표시한다.
기존 선택 이미지는 추출 성공 직후 초기화하여 다른 상품에 이전 이미지가 섞이지 않도록 한다. File 생성 시 MIME 타입을 보존한다.

이 저장소에는 프런트 원본이 없어 `static/assets/index-CqeESGX6.js`의 추출 핸들러를 직접 수정했다.
**프런트 원본에도 같은 수정(await, 응답 검증, 실패 안내, 이전 이미지 초기화, MIME 보존)을 반영해야 한다.** 그렇지 않으면 새 프런트 빌드를 복사할 때 수정이 사라진다.
`src/test/js/clothes-extraction.test.mjs`는 이 번들의 실제 핸들러를 분리해 실행하는 회귀 테스트다. 번들 파일명이 바뀌면 테스트 참조도 갱신한다.
외부 이미지 URL을 DB에 자동 저장하거나 S3에 임시 업로드하지 않는다.

## 검증 범위 (2026-11-16)

- 재검증에서 프런트 회귀 테스트 7개 실패를 확인한 뒤, 번들의 이미지 처리에 await·기존 이미지 초기화·응답 검증·실패 안내·MIME 보존·10초 중단 처리를 반영했다. 수정 후 `node --test src/test/js/clothes-extraction.test.mjs` 7개 통과 및 `node --check src/main/resources/static/assets/index-CqeESGX6.js` 통과를 직접 확인했다. 아래 Java 테스트 결과는 이전 검증 기록이며 이번 프런트 수정에서는 재실행하지 않았다.

- ClothesPageClientTest: URL 형식, 호스트 위장/내부 IP, 리다이렉트·HTTP 오류, 타임아웃, 콘텐츠 타입·크기 제한.
- ClothesExtractionServiceTest: 세 쇼핑몰 도메인별 합성 HTML의 이름·상대 이미지 경로, 이미지 없음, 메타데이터 누락.
- ClothesHtmlParserTest: 기존 파서의 OG 우선순위 및 이미지 URL 검증.
- ClothesControllerTest: 인증과 초기값 응답 계약.
- 위 사이트별 테스트는 합성 HTML 테스트다. 실제 상품 페이지의 HTML 호환성, 접근 성공 및 화면 이미지 반영은 아직 확인하지 않았다.
- Gradle은 환경의 소켓 생성 제한으로 시작 실패했다.
- 대체 검증: 로컬 캐시 의존성과 기존 빌드 클래스를 사용해 변경 코드 및 관련 테스트를 javac(-parameters)로 컴파일하고 JUnit Launcher로 직접 실행했다. 위 네 테스트 클래스에서 리뷰 수정 후 92개 성공, 실패 0개다. Gradle이 해석한 전체 의존성/전체 애플리케이션 빌드를 검증한 결과는 아니다.
- 프런트: 번들 구문 검사 통과, `node --test src/test/js/clothes-extraction.test.mjs` 7개 성공. 다운로드 대기/성공, 네트워크·HTTP 오류, 비이미지·빈 파일·초과 용량, 이미지 URL 없음을 검증했다. 실제 브라우저의 CORS 동작을 검증한 결과는 아니다.
- 실제 HTTP 보안 테스트(SpaSecurityHttpTest), 전체 테스트 및 커버리지는 재실행이 필요하다.

실제 상품 링크로 추출 → 이름 확인 → 이미지 표시 → 타입/속성 입력 → 등록까지 검증한다.
차단·동적 렌더링으로 메타데이터가 없는 사이트는 추출 실패가 예상되며 수동 등록을 안내한다.

```bash
node --test src/test/js/clothes-extraction.test.mjs
./gradlew test --tests '*ClothesPageClientTest' --tests '*ClothesExtractionServiceTest' --tests '*ClothesHtmlParserTest' --tests '*ClothesControllerTest' --tests '*SpaSecurityHttpTest'
./gradlew test jacocoTestCoverageVerification
```
