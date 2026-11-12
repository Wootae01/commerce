# Commerce

## 프로젝트 소개
Spring Boot 기반 이커머스 플랫폼을 직접 구현하며 주문, 결제, 재고 관리 등 핵심 도메인을 학습하고, 동시성 문제, 성능 이슈, 데드락 등 실제로 마주친 문제들을 해결한 프로젝트입니다.

## 시스템 아키텍처
<img src="https://github.com/user-attachments/assets/6c8576f6-14a8-44cc-942f-126e9ed539d2" width="800" alt="architecture"/>

## 배포 (CD)
<img src="https://github.com/user-attachments/assets/fd6bacd5-da88-4670-8363-5ba4bc7909b9" width="800" alt="deploy pipeline"/>

- CI: dev로 PR을 올리면 GitHub Actions가 `./gradlew build`로 테스트를 실행 (Testcontainers로 MySQL·Redis 사용)

## 기술 스택

- **Java 21** / **Spring Boot 3.5.6**
- **Spring Data JPA** + **MySQL 8.0**
- **Spring Security** + **OAuth2**
- **Redis 7.0**
- **Thymeleaf**
- **Prometheus / Grafana**
- **Terraform**
- **AWS S3**
- **GitHub Actions** (CI/CD)
- **Docker Compose** (로컬 개발 환경)
- **k6** (부하 테스트) / **WireMock** (Toss 승인 API 모킹)

## 외부 연동

- **Toss Payments** (결제)
- **Naver OAuth2** (소셜 로그인)

## 주요 기능

### 사용자
- OAuth2 소셜 로그인 (네이버)
- 상품 검색 (최신순, 가격순, 판매량순 정렬)
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

### 1. 외부 결제 응답 유실 시 승인된 주문이 취소되는 문제 (`POST /pay/confirm`)
- **문제**
  - 결제 결과가 우리 DB와 Toss 두 곳에 따로 기록돼, Toss가 승인한 뒤 응답이 유실되면 우리는 결과를 알 수 없음
  - 기존에는 Toss 호출이 실패하면 모두 결제 실패로 보고 재고를 복구하고 주문을 취소
  - 결과적으로 Toss에서는 결제가 끝났는데 주문은 취소되는 불일치 발생
- **해결**
  - 대안 비교: Toss 결제 상태 조회 결과만으로 판단하는 방식은, 승인 요청이 도착하기 전에도 '처리 중'으로 보여 미도착과 처리 중을 구분할 수 없어 제외
  - Toss 호출 전에 주문을 `PAYMENT_PENDING`으로 바꿔, 결과를 모르는 구간을 실패와 구분
  - 연결 끊김, 타임아웃, 5xx면 주문번호를 멱등키(`Idempotency-Key`)로 승인을 다시 요청해, 이미 승인된 결제는 같은 결과를 받아 `PAID`로 확정
  - 그래도 결과를 모르면 202로 "확인 중"을 응답하고(화면은 기다리게 하지 않고 바로 "다시 결제하지 말고 주문내역에서 확인" 안내), 스케줄러가 1분마다 같은 멱등키로 재요청해 Toss가 명확히 응답할 때만 `PAID` 또는 취소로 확정
- **결과**
  - 응답이 유실돼도 승인된 주문은 `PAID`로 유지되고, 승인 요청이 Toss에 도착하지 못한 경우도 재요청으로 결제 성공
  - 검증: WireMock으로 승인 요청의 응답을 끊어 승인됨, 처리 중, 미도착, 거절 네 경우를 재현

### 2. 다건 주문 동시 처리 시 데드락
- **문제**: 주문 부하 테스트 중 `SQL Error: 1213 (Deadlock found)`가 발생해, 여러 상품을 담은 장바구니 주문일수록 실패 (장바구니 주문 성공률 41.7%)
  - 패턴 1 — 결제 확정끼리: 재고 차감 UPDATE가 같은 옵션들의 X락을 트랜잭션마다 다른 순서로 잡아 순환 대기
  - 패턴 2 — 주문 생성과 결제 확정 사이: 주문상품 INSERT가 FK 검증으로 옵션에 S락을 잡는 동안, 상대가 같은 행에 X락(재고 차감)을 요청해 충돌
- **해결**
  - `SHOW ENGINE INNODB STATUS`와 `innodb_print_all_deadlocks` 로그로 두 패턴의 락 종류와 획득 순서를 확인
  - 재고 차감 UPDATE와 주문상품 Batch INSERT를 모두 상품 옵션 id 오름차순으로 정렬해, 모든 트랜잭션이 같은 순서로 락을 잡도록 통일
- **결과**:

  | | 수정 전 | 수정 후 |
  |---|---|---|
  | 데드락 발생 | 431건 | 0건 |
  | 전체 주문 성공률 | 54.9% (524/955) | 100% (941/941) |
  | 장바구니 주문 성공률 | 41.7% (306/733) | 100% (697/697) |

  - 측정 조건: 앱·MySQL 각각 2 vCPU / 1GiB로 제한, 상품 100개·유저 1,000명(장바구니당 약 15개 상품을 무작위 순서로 담음), 장바구니 주문 75%·바로구매 25% 비율로 주문을 2건/s에서 20건/s까지 70초간 늘려가며 요청(k6). Toss 승인 API는 실측 응답 시간(423~1,205ms) 기준 600ms 지연으로 모킹(WireMock)
  - 데드락 재현을 위해 상품 수 대비 주문 상품 수가 많은, 경합이 심한 조건으로 설계
  - 한계: 데드락은 사라졌지만 같은 옵션의 락을 순서대로 기다리는 대기는 남음 (결제 확정 p95 3.63s) → 락 보유 시간 단축이 다음 과제

### 3. 관리자 주문 목록 조회 성능 개선 (`idx_order_created`)
- **문제**
  - 관리자 주문 목록은 최신순으로 20건씩 보여주는데, 주문이 쌓인 만큼 조회가 느려짐
  - EXPLAIN 결과 `type ALL` + `Using filesort`, 주문 10만 건 전체를 읽고 정렬 (예상 행 수 98,250)
- **해결**
  - 정렬 컬럼인 `created_at`에 인덱스를 추가해, 최신순 정렬을 인덱스 순서로 대체
  - 인덱스를 끝에서부터 거꾸로 읽어 20건을 채우면 바로 멈추므로, 주문이 늘어도 읽는 행 수가 일정
  - EXPLAIN으로 `type ALL → index`, `Using filesort → Backward index scan`으로 바뀐 것을 확인
- **결과**: 주문 수가 늘어도 쿼리 실행 시간이 거의 일정

  | 주문 수 | 인덱스 전 | 인덱스 후 |
  |---|---|---|
  | 2만 건 | 17.8ms | 0.053ms |
  | 10만 건 | 90.9ms | 0.059ms |
  | 50만 건 | 554ms | 0.052ms |

  - 측정 조건: MySQL 8.0(2 vCPU, 1GB), 회원당 주문 평균 20건, EXPLAIN ANALYZE 21회 중 첫 회를 뺀 중앙값 (`bench/measure-orders.ps1`)
  - 한계: 키워드 LIKE 검색은 `created_at` 인덱스를 타면서 오히려 2~4배 느려짐 → 키워드 검색 시에는 인덱스를 쓰지 않도록 분리 필요

### 4. 그 밖의 개선

#### 장바구니 조회 N+1 (`GET /cart`)
- **문제**: CartProduct마다 Product, Image 추가 쿼리 발생
  - 300 VU 기준 cart_duration p95=1,191ms
- **해결**: JPQL DTO 프로젝션으로 Product, mainImage 필요한 부분만 한 번에 조회해 N+1 제거
- **결과**: p95 1,191ms → 19.9ms (약 98% 개선, 300 VU)

#### 장바구니 주문 준비 쿼리 (`POST /pay/prepare`)
- **문제**: 장바구니 상품 N개를 주문하면 주문상품 INSERT와 SELECT가 상품 수만큼 발생
- **해결**: 장바구니 상품을 `IN` 절로 한 번에 조회하고, 주문상품·주문-장바구니 연결 행은 JDBC Batch INSERT(`rewriteBatchedStatements`)로 저장
- **결과**: 상품 수와 관계없이 요청당 INSERT 3회(주문 1 + 주문상품 1 + 연결 1), SELECT 2회로 일정

  | 장바구니 상품 수 | INSERT (전 → 후) | p95 (전 → 후) |
  |---|---|---|
  | 1개 | 3 → 3 | 30.4ms → 27.7ms |
  | 5개 | 11 → 3 | 30.4ms → 24.4ms |
  | 10개 | 21 → 3 | 29.7ms → 22.4ms |
  | 20개 | 41 → 3 | 34.2ms → 23.5ms |

  - 측정 조건: 앱·MySQL 각각 2 vCPU / 1GiB, 초당 20건 고정으로 60초 요청(k6), 조건당 1회 실행이라 몇 ms 차이는 오차 범위일 수 있음

#### 재고 차감 원자적 처리
- **문제**: 재고 확인(SELECT) 후 차감(UPDATE)을 별도로 수행하는 구조에서 동시 요청 시 재고 정합성 문제 발생
- **해결**: 재고 확인·차감을 단일 쿼리로 합쳐 원자적으로 처리
  - `UPDATE ... WHERE stock >= qty` → 차감된 행 수가 주문 옵션 수보다 적으면 재고 부족으로 롤백
  - 여러 옵션을 주문하면 옵션별 수량을 UNION ALL 서브쿼리로 묶어 `product_option`과 조인한 UPDATE 1회로 차감
- **결과**: avg 4,345ms → 1,012ms (약 76.7% 개선, 동시 스레드 100 기준)

#### 중복 결제 요청
- **문제**: 결제 확정 요청이 중복으로 들어올 경우, 두 요청이 동시에 검증을 통과하면서 결제가 중복 처리되는 문제 발생
- **해결**: `@Lock(PESSIMISTIC_WRITE)`로 주문 레코드에 X락 적용 → 선행 트랜잭션 커밋 후 후행 트랜잭션이 변경된 상태를 읽어 중복 처리 차단

#### Redis 캐시 적용 (인기 상품)
- **문제**: 인기 상품 조회 시 매 요청마다 DB 직접 조회
- **해결**: Redis 캐시 적용 + 분산 락 + Double-Check 패턴으로 Cache Stampede 문제 방지
- **결과**: p95 756ms → 386ms (약 49% 개선, 500 VU)

#### 쿼리 개수 모니터링
- `StatementInspector`로 요청별 쿼리 수를 카운트하고 Prometheus 메트릭으로 기록
- N+1 재발 시 런타임에 감지 가능 (JdbcTemplate으로 실행한 쿼리는 집계되지 않음)


### 환경 변수

| 변수 | 설명 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | 실행 프로필 |
| `SPRING_DATASOURCE_URL` | MySQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자명 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 |
| `AWS_S3_BUCKET` | S3 버킷명 |
| `NAVER_CLIENT_ID` | 네이버 OAuth 클라이언트 ID |
| `NAVER_CLIENT_SECRET` | 네이버 OAuth 클라이언트 시크릿 |
| `TOSS_SECRET_KEY` | Toss Payments 시크릿 키 |
| `TOSS_CLIENT_KEY` | Toss Payments 클라이언트 키 (결제 위젯) |
