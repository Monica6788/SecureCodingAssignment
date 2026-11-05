package edu.secure.assign.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 요청 본문 */
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
