package edu.secure.assign.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.CustomerMapper;
import edu.secure.assign.mapper.LoanMapper;
import edu.secure.assign.security.AesCrypto;
import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class LoanController {

    private final LoanMapper loanMapper;
    private final LoanService loanService;
    private final CustomerMapper customerMapper;
    private final AesCrypto crypto;

    public LoanController(LoanMapper loanMapper, LoanService loanService,
    		CustomerMapper customerMapper, AesCrypto crypto) {
        this.loanMapper = loanMapper;
        this.loanService = loanService;
        this.customerMapper = customerMapper;
        this.crypto = crypto;
    }

    @GetMapping("/loans")
    public List<Loan> loans(@RequestParam String customerId,
    						HttpSession session) {
        LoginController.requireCustomer(session);
        return loanMapper.findByCustomer(customerId);
    }

    @GetMapping("/loans/sorted")
    public List<Loan> sorted(@RequestParam String customerId, 
    						 @RequestParam String sortBy,
    						 HttpSession session) {
        LoginController.requireCustomer(session);
        return loanService.findSorted(customerId, sortBy);
    }

    @GetMapping("/loans/{loanId}")
    public Loan loan(@PathVariable long loanId, HttpSession session) {
    	String me = LoginController.requireCustomer(session);
//    	Loan loan = loanMapper.findById(loanId);
    	Loan loan = loanMapper.findByIdAndCustomer(loanId, me);
    	
    	if (loan == null) {
    		// 타인의 대출 항목인 경우와 아예 없는 경우를 구분하지 못하도록 404로 처리
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    	}
//      return loanMapper.findById(loanId);
    	return loan;
    }

    @PostMapping("/loans/{loanId}/repay")
    public Map<String, Object> repay(@PathVariable long loanId, @RequestParam long amount, HttpSession session) {
        String me = LoginController.requireCustomer(session);
        return Map.of("success", loanService.repay(loanId, me, amount));
    }

    @GetMapping("/interest")
    public Map<String, Object> interest(@RequestParam long principal, @RequestParam String rate) {
        return Map.of("monthlyInterest", loanService.monthlyInterest(principal, rate));
    }
}
