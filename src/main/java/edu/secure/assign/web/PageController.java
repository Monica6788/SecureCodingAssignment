package edu.secure.assign.web;

import edu.secure.assign.service.LoanService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** 화면(JSP) 컨트롤러 */
@Controller
public class PageController {

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
    public String repayForm() {
        return "repay-form";
    }

    @PostMapping("/repay-form")
    @ResponseBody
    public String repay(@RequestParam long loanId, @RequestParam long amount, HttpSession session) {
        String me = LoginController.requireCustomer(session);
        return "상환 처리: " + loanService.repay(loanId, me, amount);
    }
}
