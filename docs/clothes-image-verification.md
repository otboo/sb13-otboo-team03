# 의상 이미지 및 피드 연동 검증 (#43)

## 응답 계약

- 의상 등록·수정은 `multipart/form-data` 요청을 사용한다. `request` 파트는 `application/json`, `image`는 선택 파일이다. 브라우저에서는 FormData를 사용하고 boundary를 직접 설정하지 않는다.
- 등록에는 속성이 최소 한 개 필요하다. 이미지가 없는 의상도 등록할 수 있다.
- 수정 시 이미지 미첨부는 기존 이미지 유지다. 첨부한 빈 파일은 미첨부와 다르게 400으로 거부한다.
- DB에는 `clothes.image_key`를 저장한다. 서명 URL은 저장하지 않는다.
- 의상 응답의 `imageUrl`과 피드 응답의 `ootds[].imageUrl`은 응답 생성 시 `ImageStorage.getAccessUrl(imageKey)`로 만든다. key가 없는 기존 데이터는 `image_url`을 유지하며 이미지가 없으면 null이다.
- 기본 서명 유효기간은 5분이다. 프런트는 만료된 URL을 재사용하지 말고 응답을 재조회해야 한다. 같은 초에 만든 URL은 동일할 수 있으므로 매 호출 시 문자열이 달라야 한다는 조건은 두지 않는다.
- 피드 변환 위치는 `FeedDtoAssembler.toOotdDto`다. 현재 `FeedController`에는 피드 생성 POST만 구현되어 있다. 향후 조회 API에서도 이 변환기를 사용해야 하며, 피드 목록/상세의 실제 프런트 재조회는 해당 API 구현 후 별도 확인한다.
- 의상 삭제 시 FK의 `ON DELETE CASCADE`로 의상 속성·피드 연결만 제거한다. 피드와 본문은 남으며, 이후 생성한 피드 응답에는 삭제된 의상이 포함되지 않는다.

## 로컬 검증 결과와 자동 검증 범위

사용자가 로컬 프런트 및 실제 S3에서 확인한 항목:

- 이미지 없는 의상 CRUD와 속성 선택
- 이미지 포함 등록·표시·교체
- 이미지 미첨부 수정 시 기존 이미지 유지
- 교체 전 S3 파일 정리
- 5분 경과 후 재조회 시 이미지 표시
- 의상 삭제 시 S3 파일 정리

자동 테스트는 실제 AWS 대신 저장소 대역을 사용한다. PostgreSQL의 실제 트랜잭션·FK 동작과 HTTP 보안 필터는 통합 테스트로 확인한다.

| 항목 | 자동 검증 위치 |
| --- | --- |
| 피드 기존 URL·이미지 없음·key 우선·재서명·삭제 후 본문 보존 | `FeedDtoAssemblerTest` |
| DB 실패 시 신규 파일 삭제·롤백 시 기존 key 유지 | `ClothesCreateIntegrationTest` |
| 정리 실패 시 커밋/롤백 결과 보존 및 key 로그 | `ClothesCreateIntegrationTest` |
| multipart 입력 오류 400·저장소 오류 500·비인증 401·타인 소유/CSRF 오류 403 | `ClothesCreateIntegrationTest` |
| 파일 헤더 형식·5MiB 한도·빈 파일·스트림/SDK 실패 | `S3ImageStorageTest` |

파일 검증은 JPEG·PNG·WebP 헤더 식별이며 이미지 전체 디코딩 검증은 아니다. MockMvc는 실제 서블릿 컨테이너의 multipart 용량 제한을 재현하지 않으므로 실제 HTTP에서 5MiB 초과 업로드 거부도 별도 확인한다.

Docker 실행 상태에서 프로젝트 루트에서 실행한다.

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

HTML 테스트 결과는 `build/reports/tests/test/index.html`, 커버리지는 `build/reports/jacoco/test/html/index.html`에 생성된다. LINE 비율 80% 검사는 Gradle `jacocoTestCoverageVerification`이 수행한다. 피드·애플리케이션 컨텍스트 테스트는 개발 `.env` 대신 `PostgresTestSupport`의 전용 PostgreSQL과 test 프로필을 사용한다.

2026-11-03 검증 결과: 전체 271개 테스트 통과(실패·오류·건너뜀 0개), 전체 LINE 커버리지 **94.97% (1,227/1,292)**, 80% 검증 통과. 위 명령으로 재현했다.

## S3 정리 시점과 실패 처리

| 작업 | DB 결과 | 파일 정리 |
| --- | --- | --- |
| 신규 이미지 등록 | 커밋 | 신규 파일 유지 |
| 신규 이미지 등록 | 롤백 | 업로드된 신규 key 삭제 |
| 이미지 교체 | 커밋 | 기존 key 삭제, 신규 key 유지 |
| 이미지 교체 | 롤백 | 신규 key 삭제, 기존 key 유지 |
| 의상 삭제 | 커밋 | 기존 key 삭제 |
| 의상 삭제 | 롤백 | 기존 파일 유지 |

정리 중 예외가 발생하면 `ClothesService.safelyDeleteImage`가 아래 로그와 예외 원인을 남긴다. DB 커밋 성공은 유지하고, 롤백 시에는 원래 발생한 DB 오류를 보존한다.

```text
S3 image cleanup failed: key=clothes/<UUID>.<extension>
```

현재 자동 재시도 큐나 정리 작업 테이블은 없다. 로그 기반 수동 재처리를 사용한다. 서버 프로세스가 업로드 후 비정상 종료되거나 S3 요청이 응답 전에 끊긴 경우에는 콜백/로그가 남지 않을 수도 있다. 따라서 로그만으로 고아 객체 전체를 찾을 수 있다고 보장하지 않는다.

## 정리 실패 수동 재처리

1. 실패 시각, 실행 환경, 버킷, 정확한 key와 예외 원인을 기록한다. `AccessDenied`는 삭제 권한을, 인증 만료는 세션을, 연결 오류는 네트워크를 먼저 복구한다.
2. 진행 중인 이미지 등록·교체가 없는지 확인하고, 해당 환경 DB에서 key가 참조 중인지 조회한다. 진행 중인 업로드는 커밋 전 DB에 보이지 않을 수 있으므로 장애 시각 직후 즉시 고아 파일로 판단하지 않는다. 판단이 어렵다면 쓰기를 잠시 중지한 유지보수 시간에 확인한다.

```sql
SELECT id, owner_id, image_key
FROM clothes
WHERE image_key = 'clothes/정확한-파일-key.png';
```

3. 참조가 한 건이라도 있으면 삭제하지 않는다. DB 조회 실패·접속 환경 불명확 상태에서도 삭제하지 않는다. 피드는 이 의상 key를 사용하므로 별도의 피드 이미지 key는 없다.
4. 참조가 없고 실패 로그와 일치하는 key에 대해서만, 해당 버킷 삭제 권한이 있는 프로필로 다음을 실행한다. placeholder는 실제 값으로 바꾼다. prefix 전체 일괄 삭제는 하지 않는다.

```bash
aws sts get-caller-identity --profile otboo-dev
aws s3api head-object --bucket BUCKET_NAME --key 'clothes/정확한-파일-key.png' --profile otboo-dev
aws s3api delete-object --bucket BUCKET_NAME --key 'clothes/정확한-파일-key.png' --profile otboo-dev
aws s3api head-object --bucket BUCKET_NAME --key 'clothes/정확한-파일-key.png' --profile otboo-dev
```

5. 삭제 성공과 후속 조회 결과를 기록한다. `403`은 미존재 증거가 아니다. 권한에 따라 `head-object`가 없는 객체에도 403을 반환할 수 있으므로 삭제 성공 응답과 버킷 관리자 확인을 함께 사용한다. 실패하면 동일 key에 한해 원인 복구 후 재시도한다.

버킷 버전 관리가 활성화되어 있으면 versionId 없는 삭제는 일반적으로 삭제 마커를 생성하며 과거 버전의 물리 삭제와 다르다. 과거 버전의 보존·정리는 인프라 담당자의 수명 주기 정책을 따른다. 이 절차에서 임의로 버전을 영구 삭제하지 않는다.

로그가 없는 고아 객체는 인프라 담당자가 S3 Inventory/객체 목록과 DB key 목록을 대조해 후보를 만든다. 충분한 유예 시간을 둔 후 진행 중인 업로드를 제외하고 위의 개별 확인 절차를 적용한다. 향후 자동화가 필요하면 DB 트랜잭션과 함께 저장하는 정리 작업 테이블/outbox, 재시도 횟수·다음 시각·완료 상태, 주기적 고아 객체 대조를 도입한다.

## 남은 수동 확인

- 피드 담당자와 이미지 없는 의상의 `ootds` 포함 및 `imageUrl: null`, 의상 삭제 시 본문 유지, `FeedDtoAssembler`에서 key 기반 URL 생성과 기존 URL 유지 방향을 확인했다. 같은 변환기를 양쪽에서 중복 수정하지 않도록 최종 반영 주체를 맞춘다.
- 조회 API 구현 후 실제 프런트에서 피드 이미지 표시·만료 후 재조회·의상 삭제 후 본문 유지까지 확인한다.
- 실제 HTTP에서 5MiB 초과 파일이 400으로 거부되는지 확인한다.
- 일반 `<img src="서명 URL">` 표시와 프런트 fetch/캔버스 사용을 구분해 CORS 필요 여부를 확인한다. 업로드 자체는 서버에서 S3로 수행한다.
