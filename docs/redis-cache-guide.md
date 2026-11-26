# Redis 공통 캐시 사용 가이드

## 1. 개요

Spring Cache와 Redis 기반 공통 캐시 인프라가 구성되어 있다.
각 도메인 담당자는 필요한 조회 기능에 선택적으로 캐시를 적용하면 된다.

- 기본 TTL: 5분
- 캐시별 TTL 설정 지원
- Redis 키 접두사: `otboo:cache:`
- 트랜잭션 커밋 이후 캐시 변경 반영

## 2. 캐시 적용 방법

### 캐시 이름 등록

`src/main/java/com/otboo/team03/global/config/CacheNames.java`에 캐시 이름을 추가한다.

```java
public static final String EXAMPLE = "example";
```

### 조회 캐싱

```java
@Cacheable(CacheNames.EXAMPLE)
public ExampleDto getExample(UUID id) {}
```

### 데이터 변경 시 캐시 무효화

```java
@CacheEvict(CacheNames.EXAMPLE)
public void updateExample(UUID id) {}
```

조회와 무효화에 사용하는 캐시 이름과 키가 일치해야 한다. 
전체 삭제가 필요한 경우 allEntries = true를 사용한다.

## 3. TTL 설정

`application.yaml`에서 캐시별 TTL을 지정할 수 있다.

```yaml
otboo:
  cache:
    default-ttl: 5m
    ttls:
      example: 10m
```

별도 설정이 없으면 기본 TTL 5분이 적용된다.

## 4. Redis 장애 처리 정책

| 작업 | 연결 장애·타임아웃 발생 시 |
|---|---|
| 조회(GET) | 원본 메서드 실행 |
| 저장(PUT) | 원본 결과 반환 |
| 무효화(EVICT) | 예외 전파 |
| 전체 삭제(CLEAR) | 예외 전파 |

예상하지 못한 오류는 전파한다.

이 정책은 Spring Cache에만 적용되며 JWT 및 분산 락에는 적용되지 않는다.

## 5. 주의사항

- 데이터 변경, 삭제 시 캐시 무효화를 반드시 고려한다.
- 사용자별 데이터는 캐시 키에 사용자 식별자를 반영한다.
- JPA 엔티티보다 DTO 캐싱을 권장한다.
- 같은 클래스 내부에서 호출하는 메서드에는 기본 프록시 방식의 캐시 어노테이션이 적용되지 않는다.
- 트랜잭션 커밋 후 캐시 무효화에 실패하면 DB 변경은 롤백되지 않으므로 데이터 불일치 가능성에 주의한다.
- 실제 도메인 캐시 적용 및 관련 테스트는 각 담당자가 수행한다.
