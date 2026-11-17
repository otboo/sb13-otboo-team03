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

이번 변경 범위는 구매 링크를 검증하고 상품명·대표 이미지 URL을 반환하는 백엔드 API다.
프런트 번들 수정과 JavaScript 테스트는 포함하지 않는다.
응답의 imageUrl은 외부 쇼핑몰 이미지 URL이며, 브라우저에서 다운로드할 때 이미지 서버의 CORS 정책에 따라 실패할 수 있다.
이미지 다운로드·실패 안내·수동 이미지 선택은 프런트에서 별도로 처리해야 한다.
추출 API는 외부 이미지 URL을 DB에 자동 저장하거나 S3에 임시 업로드하지 않는다.

## 검증 범위 (2026-11-16)


- ClothesPageClientTest: URL 형식, 호스트 위장/내부 IP, 리다이렉트·HTTP 오류, 타임아웃, 콘텐츠 타입·크기 제한.
- ClothesExtractionServiceTest: 세 쇼핑몰 도메인별 합성 HTML의 이름·상대 이미지 경로, 이미지 없음, 메타데이터 누락.
- ClothesHtmlParserTest: 기존 파서의 OG 우선순위 및 이미지 URL 검증.
- ClothesControllerTest: 인증과 초기값 응답 계약.
- 위 사이트별 테스트는 합성 HTML 테스트다. 실제 상품 페이지의 HTML 호환성, 접근 성공 및 화면 이미지 반영은 아직 확인하지 않았다.
- 최근 Gradle 테스트·커버리지 실행은 캐시 잠금 파일 접근 권한 제한으로 시작하지 못했다. 현재 커버리지 수치는 확인하지 못했다.
- 이전 문서에 기록된 대체 검증(이번 범위 정리에서는 재실행하지 않음): 로컬 캐시 의존성과 기존 빌드 클래스를 사용해 변경 코드 및 관련 테스트를 javac(-parameters)로 컴파일하고 JUnit Launcher로 직접 실행했다. 위 네 테스트 클래스에서 리뷰 수정 후 92개 성공, 실패 0개다. Gradle이 해석한 전체 의존성/전체 애플리케이션 빌드를 검증한 결과는 아니다.
- 실제 HTTP 보안 테스트(SpaSecurityHttpTest), 전체 테스트 및 커버리지는 재실행이 필요하다.

실제 상품 링크로 추출 → 이름 확인 → 이미지 표시 → 타입/속성 입력 → 등록까지 검증한다.
차단·동적 렌더링으로 메타데이터가 없는 사이트는 추출 실패가 예상되며 수동 등록을 안내한다.

```bash
./gradlew test --tests '*ClothesPageClientTest' --tests '*ClothesExtractionServiceTest' --tests '*ClothesHtmlParserTest' --tests '*ClothesControllerTest' --tests '*SpaSecurityHttpTest'
./gradlew test jacocoTestCoverageVerification
```
