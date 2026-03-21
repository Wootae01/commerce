# Commerce

Spring Boot 기반 이커머스 플랫폼

## 기술 스택

- **Java 21** / **Spring Boot 3.5.6**
- **Spring Data JPA** + **MySQL 8.0**
- **Spring Security** + **OAuth2**
- **Redis 7.0** (캐싱, 분산 락)
- **AWS S3** (이미지 저장소)
- **Thymeleaf** (서버사이드 렌더링)
- **Prometheus / Grafana** (모니터링)
- **Terraform** (AWS 인프라 관리)
- **Docker Compose** (로컬 개발 환경)

## 외부 연동

- **Toss Payments** (결제)
- **Naver OAuth2** (소셜 로그인)

## 주요 기능

### 사용자
- OAuth2 소셜 로그인 (네이버)
- 상품 검색 (가격순, 판매량순 정렬 / 기간 필터링)
- 장바구니 (추가, 수량 변경, 선택/해제, 삭제)
- 주문 (장바구니 주문, 바로 구매)
- 결제 (Toss Payments 연동 - 카드, 가상계좌, 간편결제 등)
- 주문 취소 및 환불
- 주문 내역 조회

### 관리자
- 상품 등록/수정/삭제 (메인 이미지 + 서브 이미지)
- 주문 관리 및 배송 상태 업데이트
- 추천 상품 관리
- Redis 캐시 초기화


## 트러블슈팅
 
### 1. N+1 쿼리 문제

#### 장바구니 조회 (`GET /cart`)
- **문제**: CartProduct마다 Product, Image 추가 쿼리 발생 → 요청당 5~23개 쿼리
  - 300 VU 기준 cart_duration p95=1,191ms
- **해결**: `JOIN FETCH` 최적화 → 요청당 4개 쿼리
- **결과**: p95 1,191ms → 19.9ms (약 98% 개선, 300 VU)

#### 주문 준비 N+1 쿼리 (`POST /pay/prepare`)
- **문제**: 장바구니 아이템 N개에 대해 INSERT·SELECT가 N번씩 발생
  - 50개 아이템 기준 INSERT max=101회, SELECT max=52회, prepare_duration p95=1,552ms
- **해결**: JDBC Batch INSERT + `IN` 절로 SELECT 일괄 조회 → INSERT 1회, SELECT 1회
- **결과**: p95 1,552ms → 96.6ms (약 94% 개선, 50개 아이템 / 300 VU)

#### 쿼리 개수 모니터링
- `StatementInspector`로 요청별 쿼리 수를 카운트하고 Prometheus 메트릭으로 기록
- N+1 재발 시 런타임에 감지 가능

### 2. 동시 주문 시 재고 정합성
- **문제**: 재고 확인(SELECT) 후 차감(UPDATE)을 별도로 수행하는 구조에서 동시 요청 시 재고 정합성 문제 발생
- **해결**: `UPDATE product SET stock = stock - qty WHERE stock >= qty` 단일 쿼리로 조건 체크와 차감을 합침
  → DB가 UPDATE 실행 시점의 실제 재고를 기준으로 처리하므로, 동시 요청 중 한 쪽만 성공하고 나머지는 0 rows updated → 트랜잭션 롤백
  - 다건 상품은 `UNION ALL` 서브쿼리로 인라인 테이블을 만들어 UPDATE N번 → 1번으로 줄임
- **결과**: 단일 쿼리로 줄어든 덕분에 성능도 함께 개선됨 (동시 스레드 100 기준 avg 4,345ms → 1,012ms, 약 76.7% 개선)

### 3. 다건 주문 시 데드락
- **문제**: 동시 주문 시 INSERT 순서 차이로 데드락 발생 (SQL Error 1213)
  - 트랜잭션 A: 상품1→상품2 순 락 획득, 트랜잭션 B: 상품2→상품1 순 락 획득 → 순환 대기
- **해결**: product_id 기준 정렬로 락 획득 순서 통일 → 모든 트랜잭션이 동일한 순서로 락을 잡아 순환 대기 제거

### 4. 주문 확정 쿼리 최적화 (`POST /pay/confirm`)
- **문제**: 상품 N개 주문 확정 시 JPA cascade로 order_product INSERT N회 발행
- **해결**: `jdbcTemplate.batchUpdate()`로 Batch INSERT 적용 → INSERT 1회
- **결과**: confirm p95 2,858ms → 1,959ms (약 31% 개선, 100 VU / 상품 30개 기준)

### 5. 주문 동시성 제어
- **문제**: 결제 확정 요청이 중복으로 들어올 경우 상태 체크만으로는 레이스 컨디션 발생
  - 두 요청이 동시에 status=READY를 읽으면 둘 다 통과 → 재고 이중 차감
- **해결**: `@Lock(PESSIMISTIC_WRITE)`로 주문 레코드에 X락 적용 → 선행 트랜잭션 커밋 후 후행 트랜잭션이 변경된 상태를 읽어 중복 처리 차단

### 6. DB 인덱스 최적화

#### 상품 가격 범위 검색 (`idx_product_price`)
- **문제**: 가격 범위 조건으로 검색 시 table full scan 발생
- **해결**: `price` 컬럼 인덱스 추가 → 조건에 맞는 row만 바로 탐색
- **결과**: 110K rows 기준 actual time 39.8ms → 12.1ms (70% 개선)

#### 주문 내역 페이징 (`idx_order_user_create`)
- **문제**: 주문 내역 최신순 조회 시 user_id 인덱스로 해당 유저의 전체 주문을 읽은 뒤 filesort 발생
- **해결**: `(user_id, created_at)` 복합 인덱스 추가 → 인덱스 자체가 정렬 순서를 보장하므로 별도 정렬 불필요
- **결과**: 50K rows 기준 actual time 50.7ms → 0.105ms (약 483배 개선)

### 7. Redis 캐시 적용 (인기 상품)
- **문제**: 인기 상품 조회 시 매 요청마다 DB 직접 조회, 500 VU 기준 p95=1,280ms
- **해결**: Redis 캐시 적용 + 분산 락 + Double-Check 패턴으로 Cache Stampede 방지
- **결과**: 500 VU 기준 p95 1,280ms → 386ms (약 70% 개선)
- **부가**: TTL에 ±10% Jitter를 추가하여 Thundering Herd 방지


### 환경 변수

| 변수 | 설명 |
|------|------|
| `SPRING_DATASOURCE_URL` | MySQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자명 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 |
| `AWS_S3_BUCKET` | S3 버킷명 |
| `NAVER_CLIENT_ID` | 네이버 OAuth 클라이언트 ID |
| `NAVER_CLIENT_SECRET` | 네이버 OAuth 클라이언트 시크릿 |
| `TOSS_SECRET_KEY` | Toss Payments 시크릿 키 |