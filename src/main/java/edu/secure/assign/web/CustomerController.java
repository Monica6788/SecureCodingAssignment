package edu.secure.assign.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.domain.CustomerView;
import edu.secure.assign.mapper.CustomerMapper;
import edu.secure.assign.security.MaskingUtil;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerMapper customerMapper;

    public CustomerController(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @GetMapping("/customers/{customerId}")
    public CustomerView customer(@PathVariable String customerId, HttpSession session) {
//        LoginController.requireCustomer(session);
    	String me = LoginController.requireCustomer(session);
        Customer c = customerMapper.findById(customerId);
        
//        log.info("고객 조회: {}", c);
        // "{}"에서 {} 자리에 뒤에 적힌 값이 순서대로 들어간다.
        // 예: log.info("{}, {}세, {}", name, age, hobby); => 이름, nn세, 게임
        log.info("Read Customer: customerId={}", customerId);
        CustomerView cv = new CustomerView(c.getCustomerId(), 
							        	   c.getCustomerName(),
							        	   MaskingUtil.maskRrn(c.getRrn()),
							        	   MaskingUtil.maskPhone(c.getPhone()) );
//        return c;
        return cv;
    }
}
