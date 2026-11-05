package edu.secure.assign.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;

/** 화면(JSP) 컨트롤러 */
@Controller
public class PageController {
	private static final String CSRF_KEY = "csrfToken";
	// CSRF: Cross-Site Request Forgery, 사이트 간 요청 위조
	private final SecureRandom random = new SecureRandom();
    private final LoanService loanService;

    public PageController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/login-page")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        return "search";
    }

    @GetMapping("/repay-form")
//    public String repayForm() {
    public String repayForm(Model model, HttpSession session) {
    	// 32바이트의 랜덤 데이터를 만들고,
    	byte[] bytes = new byte[32];
    	random.nextBytes(bytes);
    	// 만들어진 랜덤 데이터를 16진수 문자열로 변환하고, 
    	String token = HexFormat.of().formatHex(bytes);
    	// 이 문자열을 서버 세션에 저장하고,
    	session.setAttribute(CSRF_KEY, token);
    	// 화면에 전달
    	// "action"은 폼이 제출될 주소, "csrf"는 위에서 만든 보안 토큰 값
    	model.addAttribute("action", "/repay-form");
    	model.addAttribute("csrf", token);
    	
        return "repay-form";
    }

    @PostMapping("/repay-form")
    @ResponseBody
//    public String repay(@RequestParam long loanId, @RequestParam long amount, HttpSession session) {
    public String repay(@RequestParam long loanId, 
    					@RequestParam long amount, 
    					// csrf: 폼의 hidden field에 담겨 브라우저가 요청과 함께 보낸 토큰
    					@RequestParam(name = "_csrf", required = false) String csrf,
    					HttpSession session) {
    	// me: 요청한 사람 정보 가져오기
        String me = LoginController.requireCustomer(session);
        // expected: 서버에 저장해 둔 토큰
        Object expected = session.getAttribute(CSRF_KEY);
        // expected == null || csrf == null: 토큰이 없거나, 사용자가 아예 토큰을 안 보냄
        // MessageDigest: 해시 계산용 클래스
        // MessageDigest.isEqual(): 서버가 가진 정답 토큰과 사용자가 보낸 토큰이 일치하는지 비교
        
        // equals() 대신 isEqual(): 틀린 부분이 생기면 멈추는 equals와 달리 isEqual은
        // 틀린 부분이 있어도 항상 끝까지 비교한다. 검사시간이 동일하므로 공격자에게 일말의 힌트도 주지 않는다.
        if (expected == null || csrf == null || !MessageDigest.isEqual(
        		expected.toString().getBytes(StandardCharsets.UTF_8),
        		csrf.getBytes(StandardCharsets.UTF_8))) {
        	throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        
        return "상환 처리: " + loanService.repay(loanId, me, amount);
    }
}
