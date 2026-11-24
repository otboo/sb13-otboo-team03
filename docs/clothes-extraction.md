# 구매 링크 의상 초기값 추출

## API

인증된 사용자가 `GET /api/clothes/extractions?url=<URL 인코딩한 구매 링크>`를 요청한다.
응답은 기존 ClothesDto 형태이며 id=null, ownerId=인증 사용자, name=상품명,
imageUrl=대표 이미지(없으면 null), type=null, attributes=[]다.
DB 저장과 S3 업로드는 수행하지 않는다. 타입과 필수 속성은 사용자가 선택한 후 등록한다.

## 요청 범위와 제한

- 허용 호스트: musinsa.com, www.musinsa.com, store.musinsa.com, m.store.musinsa.com, zigzag.kr, www.zigzag.kr, 29cm.co.kr, www.29cm.co.kr, product.29cm.co.kr, wconcept.co.kr, www.wconcept.co.kr.
- HTTPS 및 기본 포트/443만 허용한다. 사용자 정보, fragment, 2048자를 넘는 URL은 거절한다.
- 유사 도메인·임의 서브도메인·IP 주소는 허용하지 않는다.
- 리다이렉트는 따라가지 않는다. 공유/단축 링크 대신 브라우저 주소창의 최종 상품 URL을 사용한다.
- 요청 제한 5초, HTML 크기 제한 2MiB, HTTP 200 및 text/html만 처리한다.
- 상품 경로만 허용한다: 무신사 `/products/{숫자}` 또는 `/app/goods/{숫자}`, 29CM `/catalog/{숫자}` 또는 `/products/{숫자}`, 구형 무신사 모바일 `/app/goods/{숫자}/0`, 지그재그 `/catalog/products/{숫자}`, W컨셉 `/Product/{숫자}`. 쿼리 파라미터와 마지막 슬래시는 허용한다.
- 무신사와 29CM은 검증한 상품 번호로 각각 www.musinsa.com/products/{번호}, www.29cm.co.kr/products/{번호}를 요청한다.
- 상품 근거를 확인한 뒤 og:title, JSON-LD Product.name, HTML 제목 순서로 상품명을 찾는다. 상품 근거는 og:type=product/og:product 또는 JSON-LD의 Product 타입이다. JSON-LD 배열, @graph, mainEntity도 검사하며 추천 목록(itemListElement)은 근거로 사용하지 않는다.
- 상품 메타데이터가 없을 때 무신사는 상품 번호가 일치하는 CDN 이미지와 제목을, 29CM은 본문 상품번호와 상품 제목을 대조한다.
- og:image가 없거나 유효하지 않으면 JSON-LD Product.image를 확인한다. 문자열, 이미지 객체, 배열을 지원하며 상대 경로는 요청한 상품 주소 기준으로 변환한다. 상품 URL이더라도 일반 웹사이트/로그인 HTML만 내려오면 추출 실패다.
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

쇼핑몰의 403·429, 타임아웃, 상품 근거 또는 상품명 누락은 공통 추출 실패로 처리한다. 등록 화면은 “이 사이트는 자동 불러오기가 안 돼요. 직접 입력해 주세요”를 표시하고 기존 입력값을 유지한 채 수동 입력 폼으로 돌아간다. 잘못된 URL이나 미지원 사이트는 링크 입력 화면에서 수정할 수 있다.

등록 화면은 대표 이미지 다운로드가 끝날 때까지 저장을 대기한다. 다운로드가 실패하면 상품명은 채우고 사진을 직접 선택하도록 안내한다. 링크 불러오기를 취소하면 늦게 도착한 결과를 적용하지 않는다.
추출 API는 외부 이미지 URL을 DB에 자동 저장하거나 S3에 임시 업로드하지 않는다.

## 검증 범위

URL 검증, 외부 HTTP 오류, 응답 크기 제한, 상품 메타데이터 조합과 컨트롤러 응답은 로컬 테스트로 검증한다. 쇼핑몰 HTML 테스트는 합성 데이터를 사용한다.

실제 상품 링크로 추출 → 이름 확인 → 이미지 표시 → 타입/속성 입력 → 등록까지 검증한다.
차단·동적 렌더링으로 메타데이터가 없는 사이트는 추출 실패가 예상되며 수동 등록을 안내한다.

```bash
./gradlew test --tests '*ClothesPageClientTest' --tests '*ClothesExtractionServiceTest' --tests '*ClothesHtmlParserTest' --tests '*ClothesControllerTest' --tests '*SpaSecurityHttpTest'
./gradlew test jacocoTestCoverageVerification
```
