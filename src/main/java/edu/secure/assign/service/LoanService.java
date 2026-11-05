package edu.secure.assign.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;

@Service
public class LoanService {

    private final LoanMapper loanMapper;
    
    // Set.of("loan_id", "balance")
    // : 정렬에 쓸 수 있는 컬럼 이름들을 수정 불가 Set으로 생성
    private static final Set<String> ALLOWED_SORT = Set.of("loan_id", "balance");

    public LoanService(LoanMapper loanMapper) {
        this.loanMapper = loanMapper;
    }

    /** 정렬 기준(sortBy)으로 대출 목록 조회 */
    public List<Loan> findSorted(String customerId, String sortBy) {
    	if (!ALLOWED_SORT.contains(sortBy)) {
    		throw new IllegalArgumentException("Invalid Sort Parameter");
    	}
    	
        return loanMapper.findSorted(customerId, sortBy);
    }

    /** 대출 상환. 잔액이 부족하면 false */
    public boolean repay(long loanId, String customerId, long amount) {
    	if (amount <= 0) {
    		throw new IllegalArgumentException("Amount should be bigger than 0.");
    	}
    	boolean result = loanMapper.repayConditional(loanId, customerId, amount) == 1; 
        return result;
    }

    /** 월 이자 = 원금 × 연이율 ÷ 12 (원 단위 반올림) */
    public long monthlyInterest(long principal, String annualRate) {
    	
    	if (principal <= 0) {
    		throw new IllegalArgumentException("Principal should be bigger than 0.");
    	}	
    	
    	BigDecimal annualRateBD = new BigDecimal(annualRate);
    	// .signum(): 숫자의 부호를 알려주는 BigDecimal 메서드
    	//			  음수는 -1, 0은 0, 양수는 1 반환
    	// x.compareTo(y): x < y이면 음수, x = y이면 0, x > y이면 양수를 반환하는 메서드 
    	if (annualRateBD.signum() < 0 || annualRateBD.compareTo(BigDecimal.ONE) > 0) {
    		throw new IllegalArgumentException("Rate should be in closed interval [0, 1].");
    	}
//        return (long) (principal * rate / 12);
    	return BigDecimal.valueOf(principal).multiply(annualRateBD)
    					 .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
    					 .longValueExact();
    }
}
