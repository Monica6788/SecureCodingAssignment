package edu.secure.assign.web;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 공통 예외 처리 */
@RestControllerAdvice
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// @RestControllerAdvice: 전역 예외 처리 + 공통 데이터/설정 관리용 어노테이션
// assignableTypes: 모든 컨트롤러가 아닌 특정 컨트롤러 범위에서만 예외처리 시키는 설정
public class GlobalExceptionHandler {
	// LoggerFactory.getLogger(GlobalExceptionHandler.class)
	// : GlobalExceptionHandler 클래스 전용 로거 만들기
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	
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
	
}
