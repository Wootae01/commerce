# Commerce

Spring Boot 기반 이커머스 플랫폼

## 프로젝트 소개
Spring Boot 기반 이커머스 플랫폼을 직접 구현하며 주문, 결제, 재고 관리 등 핵심 도메인을 학습하고, 동시성 문제, 성능 이슈, 데드락 등 실제로 마주친 문제들을 해결한 프로젝트입니다.

## 아키텍처
<img src="https://github.com/user-attachments/assets/71fa7063-8f04-409b-a40b-5cd09363b8dc" width="800" alt="architecture"/>

## 기술 스택

- **Java 21** / **Spring Boot 3.5.6**
- **Spring Data JPA** + **MySQL 8.0**
- **Spring Security** + **OAuth2**
- **Redis 7.0**
- **Thymeleaf**
- **Prometheus / Grafana**
- **Terraform**
- **AWS S3**
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
- 결제 (Toss Payments 연동)
- 주문 취소 및 환불
- 주문 내역 조회

### 관리자
- 상품 등록/수정/삭제
- 주문 관리 및 배송 상태 업데이트
- 메인 노출 상품 관리

## 화면 구성

### 사용자
| 메인 | 상품 상세 |
|------|---------|
| ![메인](https://github.com/user-attachments/assets/61668b63-8f6e-4c0a-82aa-3014dbec5e79) | ![상품 상세](https://github.com/user-attachments/assets/6b4c116e-4e1f-4fca-a7c2-a2f8b49221ef) |

| 상품 목록 | 장바구니 |
|---------|---------|
| ![상품 목록](https://github.com/user-attachments/assets/9c282c27-b1b5-4872-9079-790985c41ef8) | ![장바구니](https://github.com/user-attachments/assets/cafb6f18-f2d7-4e36-a604-24ddce602c69) |

| 주문/결제 |
|---------|
| ![주문](https://github.com/user-attachments/assets/8719fef5-d5cd-4f74-ae47-0e0acce443d8) |

### 관리자
| 상품 관리 | 주문 관리 |
|---------|---------|
| ![상품 관리](https://github.com/user-attachments/assets/44501a1e-fe94-4981-98cf-8cb46a2ac8cf) | ![주문 관리](https://github.com/user-attachments/assets/50cc8f90-c8db-4cac-bdbf-7ee62f8ad642) |

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

### 2. 동시성 문제

#### 재고 정합성 및 쿼리 최적화
- **문제**: 재고 확인(SELECT) 후 차감(UPDATE)을 별도로 수행하는 구조에서 동시 요청 시 재고 정합성 문제 발생
- **해결**: 재고 확인·차감을 단일 쿼리로 합쳐 원자적으로 처리
  - `UPDATE ... WHERE stock >= qty` → 재고 부족 시 롤백
  - 다건 상품 주문은 UNION ALL 서브쿼리로 묶어 다중 UPDATE를 1회 쿼리로 수행
- **결과**: avg 4,345ms → 1,012ms (약 76.7% 개선, 동시 스레드 100 기준)

#### 다건 주문 시 데드락
- **문제**: 두 가지 패턴으로 데드락 발생
  - 패턴 1 — 동시 결제 확정: 트랜잭션 A·B가 각자 다른 순서로 `product` row에 X락을 획득하면서 순환 대기 발생
  - 패턴 2 — 주문 생성 + 결제 확정 동시 실행: `order_product` INSERT 시 FK 무결성 검증으로 `product` row에 S락을 요청하는데, 동시에 다른 트랜잭션이 동일 row에 X락(재고 차감 UPDATE)을 보유 중이면 충돌

- **해결**: product_id 오름차순 정렬로 모든 트랜잭션의 락 획득 순서를 통일 → 순환 대기 제거

#### 중복 결제 요청
- **문제**: 결제 확정 요청이 중복으로 들어올 경우, 두 요청이 동시에 검증을 통과하면서 결제가 중복 처리되는 문제 발생
- **해결**: `@Lock(PESSIMISTIC_WRITE)`로 주문 레코드에 X락 적용 → 선행 트랜잭션 커밋 후 후행 트랜잭션이 변경된 상태를 읽어 중복 처리 차단

### 3. 주문 확정 쿼리 최적화 (`POST /pay/confirm`)
- **문제**: 상품 N개 주문 확정 시 JPA cascade로 order_product INSERT N회 발행
- **해결**: `jdbcTemplate.batchUpdate()`로 Batch INSERT 적용 → INSERT 1회
- **결과**: p95 2,858ms → 1,959ms (약 31% 개선, 100 VU / 상품 30개 기준)

### 4. DB 인덱스 최적화

#### 주문 내역 페이징 (`idx_order_user_create`)
- **문제**: 주문 내역 최신순 조회 시 user_id 인덱스로 해당 유저의 전체 주문을 읽은 뒤 filesort 발생
- **해결**: `(user_id, created_at)` 복합 인덱스 추가 → 인덱스 자체가 정렬 순서를 보장하므로 별도 정렬 불필요
- **결과**: 쿼리 실행 시간 50.7ms → 0.105ms (약 483배 개선, 50K rows 기준)

#### 상품 가격 범위 검색 (`idx_product_price`)
- **문제**: 가격 범위 조건으로 검색 시 table full scan 발생
- **해결**: `price` 컬럼 인덱스 추가 → 조건에 맞는 row만 바로 탐색
- **결과**: 쿼리 실행 시간 39.8ms → 12.1ms (70% 개선, 110K rows 기준)

### 5. Redis 캐시 적용 (인기 상품)
- **문제**: 인기 상품 조회 시 매 요청마다 DB 직접 조회
- **해결**: Redis 캐시 적용 + 분산 락 + Double-Check 패턴으로 Hot Key 문제 방지
- **결과**: p95 756ms → 386ms (약 49% 개선, 500 VU)
- **부가**: TTL에 ±10% Jitter를 추가하여 Cache Avalanche 방지


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