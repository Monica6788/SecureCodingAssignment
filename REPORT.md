# 시큐어 코딩 적용 보고서

- 이름: 이고은
- 제출일: 2026-11-06-금
- 자가 점검 테스트 결과: `21 / 21` 통과 
  (`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`)

## 항목별 수정 내역

항목마다 **취약 원인 → 공격 재현(수정 전) → 수정 내용 → 확인(수정 후)** 순서로 적는다.
수정하지 못한 항목은 비워 두지 말고 이유와 시도한 내용을 적는다.

### [1] SQL 삽입

- 취약 원인: `findByCustomer`에 `WHERE customer_id = '${customerId}'`를 써서 외부 입력을 그대로 쿼리문에 이어 붙였다. 이럴 경우 URL에 `/api/loans?customerId=' OR '1' = '1'`라고 작성하면 조건이 항상 참이 되어 본인 것이 아닌 대출까지 전부 조회된다. `findSorted`에도 `ORDER BY ${sortBy}` 형태로 작성되어 입력받은 값을 검증하지 않고 그대로 사용했다. 이는 보안코딩 핵심 원칙 중 "입력값 불신"에 위배된다.

- 공격 재현(수정 전): (호출한 요청과 결과)
    1. `GET /api/loans?customerId=' OR '1'='1`: 로그인된 사용자(C001)의 대출 항목과 C002, C003의 대출 항목이 모두 들어간 전체 목록이 조회된다.
    2. `GET /api/loans/sorted?customerId=C001&sortBy=nosuch`: 존재하지 않는 컬럼명이 SQL에 그대로 들어가 DB 오류(`ORA-00904`, 부적합한 식별자)가 발생하고, 오류 내용이 응답에 그대로 노출된다.

- 수정 내용:  
    1. 수정한 파일: `LoanMapper.xml`, `LoanService` 
    2. 변경 전/후 핵심코드:
        - 변경 전
        **`LoanMapper.xml`**
        ```xml
            <!-- findByCustomer -->
            WHERE customer_id = '${customerId}'
        ```

        **`LoanService`**
        ```java
            // 정렬 기준 검증 없이 그대로 전달
            public List<Loan> findSorted(String customerId, String sortBy) {
                return loanMapper.findSorted(customerId, sortBy);
            }
        ```

        - 변경 후
        **`LoanMapper.xml`**
        ```xml
            <!-- findByCustomer: #{}는 값을 ?로 바인딩하므로 작은따옴표도 제거 -->
            WHERE customer_id = #{customerId}
        ```

        **`LoanService`**
        ```java
            // Set.of("loan_id", "balance")
            // : 정렬에 쓸 수 있는 컬럼 이름들을 수정 불가 Set으로 생성
            private static final Set<String> ALLOWED_SORT = Set.of("loan_id", "balance");

            public List<Loan> findSorted(String customerId, String sortBy) {
                if (!ALLOWED_SORT.contains(sortBy)) {
                    throw new IllegalArgumentException("Invalid Sort Parameter");
                }
                return loanMapper.findSorted(customerId, sortBy);
            }
        ```

- 확인(수정 후): 
    - `' OR '1'='1`: `[]` (빈 배열). 다른 사람의 대출 항목까지 조회할 수 없다.
    - `sortBy=nosuch`: `400 {"message":"Invalid Request."}` 허용한 기준으로만 정렬할 수 있다.

### [2] 접근 통제 (IDOR)

- 취약 원인: 대출 항목 조회 시에 `loanId`만으로 조회하는 `findById`를 사용했다. 이럴 경우 대출 항목의 ID만 알면 다른 사람의 대출 항목도 조회할 수 있다. 상환도 `loanId`만으로 잔액을 갱신해서 로그인한 사용자가 대출 항목의 소유자인지 여부를 확인하지 않았다("로그인을 했는지"와 "이 항목의 소유자인지"는 다른 문제). 이럴 경우 다른 사람의 대출 잔액을 바꿔서 데이터 무결성을 깨뜨리는 공격이 들어올 수 있다.

- 공격 재현(수정 전): C001로 로그인 후 `/api/loans/1002`로 1002번 대출 항목(사용자 C002가 가진 대출 항목)을 조회할 수 있다.

- 수정 내용:
    1. 수정 파일: `LoanMapper.xml`, `LoanMapper.java`, `LoanController`
    2. 변경 전/후 핵심코드:
        - 변경 전
        **`LoanMapper.xml`**
        ```xml
            <select id="findById" resultType="edu.secure.assign.domain.Loan">
                SELECT loan_id, customer_id, balance, status
                FROM loan
                WHERE loan_id = #{loanId}
            </select>
        ```

        **`LoanMapper.java`**
        ```java
            Loan findById(@Param("loanId") long loanId);
        ```
        **`LoanController`**
        ```java
            @GetMapping("/loans/{loanId}")
            public Loan loan(@PathVariable long loanId, HttpSession session) {
                LoginController.requireCustomer(session);
                return loanMapper.findById(loanId);
            }
        ```

        - 변경 후
        **`LoanMapper.xml`**
        ```xml
            <select id="findByIdAndCustomer" resultType="edu.secure.assign.domain.Loan">
                SELECT loan_id, customer_id, balance, status
                FROM loan
                WHERE loan_id = #{loanId} AND customer_id = #{customerId}
            </select>
        ```

        **`LoanMapper.java`**
        ```java
            // 추상 메서드 추가
            Loan findByIdAndCustomer(@Param("loanId") long loanId,
                            @Param("customerId") String customerId);
        ```
        **`LoanController`**
        ```java
            @GetMapping("/loans/{loanId}")
            public Loan loan(@PathVariable long loanId, HttpSession session) {
                String me = LoginController.requireCustomer(session);
                Loan loan = loanMapper.findByIdAndCustomer(loanId, me);
                
                if (loan == null) {
                    // 타인의 대출 항목인 경우와 아예 없는 경우를 구분하지 못하도록 404로 처리
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                return loan;
            }
        ```

- 확인(수정 후): C001로 로그인 후 `/api/loans/1002`로 요청하면 `404 {"message":"Request cannot be processed."}`가 응답된다. C001(로그인한 사용자)의 대출 항목은 정상적으로 조회된다.

- 타인 자원 접근 시 응답 코드를 무엇으로 정했고, 그 이유는?
    1. 응답 코드: 404 (`HttpStatus.NOT_FOUND`)
    2. 이유: 403(FORBIDDEN)을 쓰면 해당 대출 항목이 **존재함**을 알려주는 셈이 되므로 공격자가 다른 사용자의 대출번호 목록을 알 수 있다. 404로 통일하여 존재 여부에 대한 정보를 제공하지 않도록 했다.

### [3] 민감정보 노출

- 취약 원인: (응답 / 로그 각각)
    1. 응답: `CustomerController`에서 DB에 저장된 원본 객체인 `Customer`를 그대로 반환했다. `Customer`는 주민번호, 비밀번호와 같이 숨겨야 할 정보를 포함하고 있으므로 부적절하다.
    2. 로그: 
        - `log.info("고객 조회: {}", c)`: `c`는 `Customer` 타입으로, DB의 원본 객체이다. `Customer` 클래스의 `toString()`메서드를 통해 주민번호와 비밀번호까지 로그에 기록되었다.
        - `log.info("가입 요청: {}", req)`: `req`는 가입요청 객체의 정보를 통째로 가지고 있는데, 이걸 그대로 로그에 작성하여 가입 요청 데이터가 모두 로그에 기록되었다.

- 공격 재현(수정 전): C001로 로그인 후 `GET /api/customers/C001`로 요청하면 응답에 주민번호, 전화번호와 같은 민감정보가 그대로 노출된다. 비밀번호 해시값(`null`) 또한 그대로 보인다.

- 수정 내용:
    1. 수정 파일: `CustomerView.java` 파일 생성, `CustomerController`, `AuthController`, `Customer.java`
    2. 변경 전/후 핵심 코드:
        - 변경 전:
        `Customer.java` 파일만 존재함.

        **`CustomerController`**
        ```java
            public Customer customer(@PathVariable String customerId, HttpSession session) {
                LoginController.requireCustomer(session);
                Customer c = customerMapper.findById(customerId);
                log.info("고객 조회: {}", c);
                return c;
            }
        ```

        **`AuthController`**
        ```java
            log.info("가입 요청: {}", req);
        ```

        **`Customer.java`**: `toString()`에 `rrn`, `phone`, `passwordHash` 포함
        
        - 변경 후:
        **`CustomerView.java` (응답 전용 DTO)**
        ```java
            package edu.secure.assign.domain;

            import lombok.AllArgsConstructor;
            import lombok.Data;
            import lombok.NoArgsConstructor;

            /** 응답 전용 DTO -> 응답에 들어갈 필수 항목만 마스킹해서 담는 DTO */
            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public class CustomerView {
                private String customerId;
                private String customerName;
                private String rrnMasked;
                private String phoneMasked;
        
            }
        ```

        **`CustomerController`**
        ```java
            public CustomerView customer(@PathVariable String customerId, HttpSession session) {
                String me = LoginController.requireCustomer(session);
                Customer c = customerMapper.findById(customerId);
                log.info("Read Customer: customerId={}", customerId);   // 식별자만 기록
                return new CustomerView(c.getCustomerId(), c.getCustomerName(),
                        MaskingUtil.maskRrn(c.getRrn()),
                        MaskingUtil.maskPhone(c.getPhone()));
            }
        ```
        **`AuthController`**
        ```java
            log.info("가입 요청: customerId={}", req.customerId());   // 식별자만 기록
        ```
        **`Customer.java`**
        ```java
            @Override
            public String toString() {
                // 주민번호, 비밀번호 해시 제외
                return "Customer{customerId='" + customerId 
                        + "',customerName='" + customerName
                        + "', phone='" + phone + "'}";
            }
        ```

- 확인(수정 후): C001로 로그인 후 `GET /api/customers/C001`로 요청하면 응답에 비밀번호 해시값은 빠지고, 주민번호와 전화번호가 마스킹 처리되어 표시된다. 주민번호는 `991122-1******` 형식, 전화번호는 `010-****-1111` 형식이다. 서버 로그에는 `customerId`만 기록된다.

### [4] 에러 처리

- 취약 원인: 모든 예외를 `e.toString()`으로 응답에 그대로 내보내서 예외 클래스명과 메시지가 노출된다. 오류를 일으킨 쿼리문, ORA 오류 코드 등을 보고 공격자가 DB의 종류, 테이블과 컬럼 구조를 알아낼 수 있다.

- 공격 재현(수정 전): `GET /api/interest?principal=1000000&rate=abc`으로 요청 시 `java.lang.NumberFormatException: For input string: "abc"`라는 오류 메시지가 화면에 표시된다.

- 수정 내용: (예외 종류별로 어떤 상태 코드/메시지를 내려주는지)
    1. 수정 파일: **`GlobalExceptionHandler`**
    2. 예외 종류별 상태 코드와 메시지
        - `ResponseStatusException`: 상태 코드는 원래 코드(401/403/404)를 유지하고, 메시지를 "Request cannot be processed."로 통일.
        - `IllegalArgumentException`: 상태 코드를 400으로, 메시지는 "Invalid Request."로 통일.
        - `MethodArgumentNotValidException`: 상태 코드를 400으로, 메시지는 "Invalid Request."로 통일.
        - 그 외 모든 `Exception`: 상태 코드를 500으로, 메시지를 "Request cannot be processed."로 통일하고, 예외의 식별자만 표시.
    
    ```java
        // 변경 전
        @ExceptionHandler(Exception.class)
        public ResponseEntity<String> handle(Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.toString());
        }

        // 변경 후
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", "Request cannot be processed."));
        }
        
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid Request."));
        }
        
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid Request."));
        }
        
        @ExceptionHandler(Exception.class)
        // @ExceptionHandler
        // : 런타임에 처리되지 않은 모든 일반 예외가 발생했을 때 얘가 가로채서 처리하도록 지정
        public ResponseEntity<Map<String, String>> unexpected(Exception e) {
            // <Map<String, String>>: String(key) - String(value) 구조
            String errorId = UUID.randomUUID().toString();
            // UUID.randomUUID(): 고유 에러 ID 생성
            // Universally Unique IDentifier
            log.error("Error during Processing: errorId={}", errorId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("message", 
                                            "Request cannot be processed.",
                                            "errorId", errorId));
        }
    ```

- 확인(수정 후): 
    1. `GET /api/interest?principal=1000000&rate=abc`으로 요청 시 `{"message":"Invalid Request."}`라는 공통 오류 메시지만 표시된다.
    2. 이미 가입된 ID로 회원가입 요청 시 `500 {"message":"Request cannot be processed.","errorId":"..."}`가 응답된다. DB 오류 내용은 응답에 없고, 화면에 표시된 `errorId`로 서버 로그에서 원인을 찾을 수 있다.

### [5] 입력값 검증

- 취약 원인: `POST /api/signup`(회원가입)의 요청을 검증 없이 DB에 저장했다. 빈 값, `<script>`, 특수문자, 형식이 틀린 주민번호나 전화번호, 너무 짧은 비밀번호가 모두 저장된다.

- 수정 내용: (필드별 검증 규칙과 그렇게 정한 이유)
    1. 수정 파일: `SignupRequest.java`, `AuthController`
    2. 변경 전/후 핵심코드:
        - 변경 전
        **`SignupRequest.java`**
        ```java
            public record SignupRequest(
                    String customerId,
                    String customerName,
                    String rrn,
                    String phone,
                    String password) {
            }
        ```
        **`AuthController`**
        ```java
            public Map<String, String> signup(@RequestBody SignupRequest req)
        ```

        - 변경 후
        **`SignupRequest.java`**
        ```java
            public record SignupRequest(
                    @NotBlank	// 빈 문자열과 공백 제한
                    @Pattern(regexp="^[A-Za-z0-9]+$")	// 정규표현식 패턴 지정 (영문, 숫자만)
                    String customerId,
                    @NotBlank
                    @Pattern(regexp="^[가-힣A-Za-z]{1,30}$")	// 특수문자 제한, 1~30자
                    String customerName,
                    @NotBlank
                    @Pattern(regexp="^\\d{6}-[1-4]\\d{6}$")	// 숫자 6개 - 1~4 중 하나 + 숫자 6개
                    String rrn,
                    @NotBlank
                    @Pattern(regexp="^01[0-9]-\\d{3,4}-\\d{4}$")
                    String phone,
                    @NotBlank
                    @Size(min = 8, max = 64)
                    String password) {
            }
        ```

        **`AuthController`**
        ```java
            // @Valid가 있어야 위 검증 규칙이 실제 요청에 적용된다.
            public Map<String, String> signup(@RequestBody @Valid SignupRequest req)
        ```

        - 필드별 검증 규칙과 정한 이유

            - `customerId`: 영문, 숫자만 입력받는다. 빈 값은 허용하지 않는다. 기존 ID가 영문(대문자)+숫자 형식이므로 따옴표, 공백, `=`과 같은 특수문자를 막아 SQL Injection이나 로그 위조에 쓰일 가능성이 있는 특수문자를 원천 차단하기 위함이다.
            - `customerName`: 한글, 영문만 입력받는다(특수문자 제한). 1~30자 범위에서 입력할 수 있고, 빈 값은 입력받지 않는다. `<script>`와 같은 태그가 포함되지 않게 하기 위함이다. 이름이 화면에 출력될 때 스크립트가 실행되는 저장형 XSS를 막는다.
            - `rrn`: (숫자 6개) + `-` + (1, 2, 3, 4 중 하나(성별)) + (숫자 6개) 형식으로 입력받는다. 주민번호 형식(YYMMDD-Snnnnnn (S는 성별))을 지키기 위함이다. (형식이 맞아야 `MaskingUtil.maskRrn`도 정상적으로 마스킹 처리를 할 수 있다.)
            - `phone`: "01" + (0~9 범위 내 숫자 하나) + `-` + (숫자 3~4개) + `-` + (숫자 4개) 형식으로 입력 받는다. 전화번호 형식(01n-nnnn-nnnn 또는 01n-nnn-nnnn)을 지키기 위함이다. (`phone`도 마스킹 처리를 할 것이므로 형식을 맞춰주어야 한다.)
            - `password`: 빈 값은 입력받지 않고, 글자 수를 8~64자로 제한한다. 비밀번호가 너무 짧으면 대입 공격에 방어하기 어려우므로 최소 글자 수를 정했고, 너무 길면 해시 계산의 부담이 커지므로 최대 글자 수를 정했다. 최근 보안을 위해 비밀번호에 특수문자를 포함시키는 것이 권장되고, 웹사이트마다 허용되는 특수문자에 차이가 있으므로 형식은 지정하지 않았다.

- 확인(수정 후): (정상 요청 / 비정상 요청 각각)
    1. 정상 (C100 / 박민수 / 950101-1234567 / 010-1234-5678 / Passw0rd!23)
        : `200` `{"customerId":"C100"}`
    2. `customerId`가 빈 값: 400
    3. `customerId`에 `C1' OR '1'='1`: 400
    4. `customerName`에 `<script>alert(1)</script>`: 400
    5. `rrn`이 `not-a-rrn`: 400
    6. `phone`이 `123`: 400
    7. `password`가 `1234`: 400

### [6] 비밀번호 해시 / 암호화 / 키 관리

- 취약 원인: (해시, 암호화, DB 접속 정보, 키 각각)
    1. 해시: 솔트(랜덤값) 없는 MD5일 때 같은 비밀번호는 항상 같은 해시가 되어 계산이 빠르다. 해시값이 유출되면 사전 대입(자주 쓰는 비밀번호 목록을 하나씩 해시 해보며 비교)이나 레인보우 테이블(사전 대입의 계산을 미리 수행하여 표로 저장한 것) 공격으로 단시간에 원래 비밀번호가 복구된다. 특히 레인보우 테이블은 해시만 훔치면 표에서 찾아만 내면 되므로 거의 즉시 풀린다.
    2. 암호화: 고정 키`"0123456789abcdef"`(하드코딩) + `AES/ECB` 상태는 같은 평문이면 항상 같은 암호문이 된다. 따라서 패턴이 드러나기도 쉽고 무결성 검증도 없다. 고정 키(`"0123456789abcdef"`)가 코드에 그대로 드러나서 깃허브에 한 번이라도 잘못 업로드하면 키가 유출된다.
    3. 키: DB 접속 정보와 키가 `application.properties`에 그대로 있다. 이것도 깃허브에 푸시 한 번 잘못하면 유출된다.

- 수정 내용: 
    1. 수정 파일: `PasswordHasher.java`, `AesCrypto.java`, `application.properties`
    2. 변경 전/후 핵심코드:
        - 변경 전
        **`PasswordHasher.java`**
        ```java
        public final class PasswordHasher {

            private PasswordHasher() {
            }

            public static String hash(String password) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    return HexFormat.of().formatHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }

            public static boolean verify(String password, String stored) {
                return hash(password).equals(stored);
            }
        }
        ```

        **`AesCrypto.java`**
        ```java
            private static final String KEY = "16자고정키값";

            public String encrypt(String plain) {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
                return Base64.getEncoder()
                        .encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
            }
        ```

        **`application.properties`**
        ```properties
            spring.datasource.password=****   # 실제 비밀번호가 평문으로 적혀 있었음 (값 생략)
        ```

        - 변경 후
        **`PasswordHasher.java`**
        ```java
            public final class PasswordHasher {
                // 해시 함수 반복 계산 횟수
                private static final int ITERATIONS = 600_000;
                // 암호화 키의 길이 지정
                private static final int KEY_BITS = 256;
                // 보안용 난수 생성기
                private static final SecureRandom RANDOM = new SecureRandom();

                private PasswordHasher() {
                }
                
                private static byte[] derive(String password, byte[] salt) {
                    try {
                        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), 
                                                        salt, ITERATIONS, 
                                                        KEY_BITS);
                        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                            .generateSecret(spec)
                                            .getEncoded();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        throw new IllegalStateException(e);
                    }
                }

                public static String hash(String password) {
                    byte[] salt = new byte[16];
                    RANDOM.nextBytes(salt);
                    return HexFormat.of().formatHex(salt) + ":"
                            + HexFormat.of().formatHex(derive(password, salt));
                }
                
                public static boolean verify(String password, String stored) {
                    String[] parts = stored.split(":");
                    if (parts.length != 2) {
                        return false;
                    }
                    byte[] salt = HexFormat.of().parseHex(parts[0]);
                    byte[] expected = HexFormat.of().parseHex(parts[1]);
                    
                    return MessageDigest.isEqual(derive(password, salt), expected);
                    
                }
            }
        ```

        **`AesCrypto.java`**
        ```java
            private static final int IV_BYTES = 12;     // IV: 같은 평문도 매번 다른 암호문이 나오게 하는 난수
            private static final int TAG_BITS = 128;    // TAG: 무결성 검증용 인증 태그
            private final SecureRandom random = new SecureRandom();

            // 고정 KEY 상수 삭제 → 주입받은 키(base64Key)만 사용
            private SecretKeySpec key() {
                if (base64Key == null || base64Key.isBlank()) {
                    throw new IllegalStateException("ASSIGN_AES_KEY environment variable is not set.");
                }
                return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
            }

            public String encrypt(String plain) {
                byte[] iv = new byte[IV_BYTES];
                random.nextBytes(iv);           // 새로운 IV를 매번 다시 생성
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
                byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
                // IV + 암호문을 합쳐서 저장 (복호화할 때 IV가 필요하므로)
                return Base64.getEncoder().encodeToString(
                        ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
            }

            public String decrypt(String token) {
                ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(token));
                byte[] iv = new byte[IV_BYTES];
                buf.get(iv);                    // 앞 12바이트 = IV
                byte[] enc = new byte[buf.remaining()];
                buf.get(enc);                   // 나머지 = 암호문
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
                return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
            }
        ```

        **`application.properties`**
        ```properties
            # 기본값 없는 환경 변수 참조 (환경 변수가 없으면 기동되지 않음)
            spring.datasource.password=${ASSIGN_DB_PASSWORD}
            assign.aes-key=${ASSIGN_AES_KEY}
        ```

- 확인(수정 후): 
    - 자가 점검 테스트 4개 항목 통과
        1. 같은 비밀번호 -> 다른 해시 + 검증 성공
        2. 같은 평문 -> 다른 암호문 + 복호화 성공
        3. 다른 키로 복호화 실패
        4. 키 없이 암호화 실패
    - 환경변수 없이 실행 시 기동 X 확인

### [7] 금액 계산 / 동시성

- 취약 원인: (이자 계산, 상환 금액 검증, 경쟁 조건 각각)
    1. 이자 계산: `double`로 `(long)(principal * rate / 12)`하여 부동소수점 오차가 생긴다. `long` 변환은 반올림이 아닌 버림이라서 규칙이 불명확하다.
    2. 금액 검증: 원금이 0 이하일 때, 금리가 0 이상 1 이하 범위를 벗어날 때, 상환 금액이 0 이하일 때 reject되지 않는다. 상환 금액이 0 미만인 경우 잔액이 오히려 증가하게 된다.
    3. 경쟁 조건: "`selectBalance` 잔액 조회 -> 서비스에서 비교 및 계산 -> `updateBalance`로 잔액 업데이트"와 같이 분리되어 동시 요청으로 같은 잔액을 읽고 각각 차감하면 잔액 검사의 의미가 없다.

- 공격 재현(수정 전): (음수 상환 결과, 동시 요청 결과 등)
    1. `GET /api/interest?principal=1000000&rate=-1`로 요청 시 이자가 음수로 계산된다.
    2. `POST /api/loans/1001/repay?amount=-1000000`로 요청 시 상환인데도 잔액이 증가하게 된다.
    3. 동시 상환 시에 잔액보다 크게 차감된다.

- 수정 내용:
    1. 수정 파일: `LoanService.java`, `LoanMapper.java`, `LoanMapper.xml`
    2. 변경 전/후 핵심코드:
        - 변경 전
        **`LoanService`**
        ```java
            public boolean repay(long loanId, String customerId, long amount) {
                Long balance = loanMapper.selectBalance(loanId);     // 1. 조회
                if (balance == null || balance < amount) {           // 2. 판단
                    return false;
                }
                loanMapper.updateBalance(loanId, balance - amount);  // 3. 갱신
                return true;
            }

            public long monthlyInterest(long principal, String annualRate) {
                double rate = Double.parseDouble(annualRate);
                return (long) (principal * rate / 12);
            }
        ```
        **`LoanMapper.xml`**
        ```xml
            <select id="selectBalance" resultType="java.lang.Long">
                SELECT balance FROM loan WHERE loan_id = #{loanId}
            </select>

            <update id="updateBalance">
                UPDATE loan SET balance = #{balance} WHERE loan_id = #{loanId}
            </update>
        ```

        - 변경 후
        **`LoanService`**
        ```java
            public boolean repay(long loanId, String customerId, long amount) {
                if (amount <= 0) {
                    throw new IllegalArgumentException("Amount should be bigger than 0.");
                }
                // 소유자 확인 + 잔액 확인 + 차감을 UPDATE 한 문장으로 처리
                return loanMapper.repayConditional(loanId, customerId, amount) == 1;
            }

            public long monthlyInterest(long principal, String annualRate) {
                if (principal <= 0) {
                    throw new IllegalArgumentException("Principal should be bigger than 0.");
                }
                // 문자열 생성자 사용 (숫자가 아니면 NumberFormatException → 400)
                BigDecimal annualRateBD = new BigDecimal(annualRate);
                if (annualRateBD.signum() < 0 || annualRateBD.compareTo(BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException("Rate should be in closed interval [0, 1].");
                }
                // 원 단위 반올림(HALF_UP) 명시
                return BigDecimal.valueOf(principal).multiply(annualRateBD)
                                 .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
                                 .longValueExact();
            }
        ```
        **`LoanMapper.xml`**
        ```xml
            <update id="repayConditional">
                UPDATE loan
                   SET balance = balance - #{amount}
                 WHERE loan_id = #{loanId} AND customer_id = #{customerId} AND balance >= #{amount}
            </update>
        ```
        **`LoanMapper.java`**
        ```java
            // 추상 메서드 추가
            int repayConditional(@Param("loanId") long loanId,
                                 @Param("customerId") String customerId,
                                 @Param("amount") long amount);
        ```

- 확인(수정 후):
    1. `GET /api/interest?principal=1000000&rate=-1`: `400 {"message":"Invalid Request."}`
    2. `GET /api/interest?principal=1000000&rate=abc`: `400 {"message":"Invalid Request."}`
    3. 상환 폼에서 금액 `-1000000` 입력: `400 {"message":"Invalid Request."}`, 잔액 변화도 없다.
    4. 상환 폼에서 금액 `1000` 입력: `상환 처리: true` (정상 기능 유지 확인)
    5. 동시성: 자가 점검 테스트 "[7] 조건부 UPDATE 한 문장" 통과. 잔액 확인과 차감이 한 문장이라 동시 요청에도 잔액이 음수가 되지 않는다.

### [8] XSS / CSRF

- 취약 원인: 
    1. XSS: `search.jsp`가 검색어를 `${q}`(EL), `<%= request.getParameter("q") %>`(스크립틀릿), `innerHTML`로 이스케이프 없이 출력했다. 검색어에 `<script>`를 넣으면 그대로 HTML로 해석되어 실행된다.
    2. CSRF: 상환 폼(`POST /repay-form`)이 세션 쿠키만 확인하고 요청을 처리했다. 사용자가 로그인한 상태로 공격 페이지를 열면 브라우저가 쿠키를 자동으로 붙여 보내서 사용자가 모르는 사이에 상환 요청이 들어간다.

- 공격 재현(수정 전):
    1. `GET /search?q=<script>alert(1)</script>`로 요청: 알림 팝업이 실행된다.
    2. C001 로그인 후 `attack/csrf-attack.html` 실행: `loanId`가 `1001`인 대출 항목의 잔액이 줄어든다.

- 수정 내용:
    1. 수정 파일: `search.jsp`, `PageController`, `repay-form.jsp`
    2. 변경 전/후 핵심코드:
        - 변경 전
        **`search.jsp`**
        ```jsp
            <p>검색어: <span>${q}</span></p>
            <p>검색어(재표시): <span><%= request.getParameter("q") %></span></p>
            <p>검색 결과 요약: <span id="summary"></span></p>
            <script>
                const q = new URLSearchParams(location.search).get("q") || "";
                document.getElementById("summary").innerHTML = q + " 에 대한 검색 결과가 없습니다.";
            </script>
        ```
        **`PageController`**
        ```java
            @GetMapping("/repay-form")
            public String repayForm() {
                return "repay-form";
            }

            @PostMapping("/repay-form")
            @ResponseBody
            public String repay(@RequestParam long loanId, @RequestParam long amount, HttpSession session) {
                String me = LoginController.requireCustomer(session);
                return "상환 처리: " + loanService.repay(loanId, me, amount);
            }
        ```
        **`repay-form.jsp`**: CSRF 토큰 필드 없음

        - 변경 후
        **`search.jsp`**
        ```jsp
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

            <p>c:out <span><c:out value="${q}" /></span></p>
            <p>fn:escapeXml <span>${fn:escapeXml(q)}</span></p>
            <p>textContent <span id="dom-result"></span></p>
            <script>
                const params = new URLSearchParams(location.search);
                const q = params.get("q") || "";
                const element = document.getElementById("dom-result");
                element.textContent = q;   // innerHTML 대신 textContent: 태그를 글자로 출력
            </script>
        ```
        **`PageController`**
        ```java
            @GetMapping("/repay-form")
            public String repayForm(Model model, HttpSession session) {
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);                          // SecureRandom 32바이트
                String token = HexFormat.of().formatHex(bytes);
                session.setAttribute(CSRF_KEY, token);            // 서버 세션에 저장
                model.addAttribute("action", "/repay-form");
                model.addAttribute("csrf", token);                // 화면(폼)에 전달
                return "repay-form";
            }

            @PostMapping("/repay-form")
            @ResponseBody
            public String repay(@RequestParam long loanId, @RequestParam long amount,
                                @RequestParam(name = "_csrf", required = false) String csrf,
                                HttpSession session) {
                String me = LoginController.requireCustomer(session);
                Object expected = session.getAttribute(CSRF_KEY);
                // 세션 토큰과 요청 토큰을 상수 시간 비교, 없거나 다르면 403
                if (expected == null || csrf == null || !MessageDigest.isEqual(
                        expected.toString().getBytes(StandardCharsets.UTF_8),
                        csrf.getBytes(StandardCharsets.UTF_8))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
                return "상환 처리: " + loanService.repay(loanId, me, amount);
            }
        ```
        **`repay-form.jsp`**
        ```jsp
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            ...
            <input type="hidden" name="_csrf" value="<c:out value='${csrf}' />">
        ```

- 확인(수정 후): (`attack/csrf-attack.html` 실행 결과 포함)
    1. `GET /search?q=<script>alert(1)</script>`로 요청: 알림팝업 없이 문자열로 표시된다.
    2. C001 로그인 후 `attack/csrf-attack.html` 실행: 403, 잔액에도 변화가 없다.
    3. 정상 폼 제출: `상환 처리: true`가 표시된다.

## 수업 내용과 다르게 적용한 부분 / 어려웠던 점
- 수업 내용과 다르게 적용한 부분: 실습 코드와 과제 README.md 파일만 참고하여 풀어서 대부분 동일할 것으로 예상됩니다. 

- 어려웠던 점: 데이터 흐름을 따라 코드를 고쳐나가는 것이 어려웠습니다. 이 문제를 고치려면 어느 파일로 가야 할지에 대한 고민이 길어지곤 했습니다. 이번 주에 처음 본 개념, 용어가 많아서 이해하는 데에 긴 시간이 필요했습니다. 예외 처리기에 `assignableTypes`를 지정했더니 목록에 없는 컨트롤러의 예외가 처리되지 않아서 범위 제한을 풀었습니다. 가급적 원본 코드를 남겨(주석처리하여) 비교하려고 했는데 test 실행 시에 주석 내용 속 코드도 실패 범위에 들어가서 삭제하게 되었습니다. 그랬더니 보고서 작성할 때에 원본 코드가 뭐였는지, 수정 전 코드가 뭐였는지 다시 찾고 비교하기가 어려웠습니다. (원본 파일을 다시 받아 포트번호만 다르게 하여 완성한 과제와 동시 실행하고, 결과를 비교하는 식으로 보고서를 작성했습니다.)
