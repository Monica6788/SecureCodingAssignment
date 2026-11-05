package edu.secure.assign.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;

@Service
public class LoanService {

    private final LoanMapper loanMapper;
    
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
        Long balance = loanMapper.selectBalance(loanId);
        if (balance == null || balance < amount) {
            return false;
        }
        loanMapper.updateBalance(loanId, balance - amount);
        return true;
    }

    /** 월 이자 = 원금 × 연이율 ÷ 12 (원 단위 반올림) */
    public long monthlyInterest(long principal, String annualRate) {
        double rate = Double.parseDouble(annualRate);
        return (long) (principal * rate / 12);
    }
}
