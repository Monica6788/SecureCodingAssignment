package edu.secure.assign.domain;

/** 회원가입 요청 본문 */
public record SignupRequest(
        String customerId,
        String customerName,
        String rrn,
        String phone,
        String password) {
}
