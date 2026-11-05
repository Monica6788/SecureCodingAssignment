package edu.secure.assign.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.mapper.CustomerMapper;
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
    public Customer customer(@PathVariable String customerId, HttpSession session) {
        LoginController.requireCustomer(session);
        Customer c = customerMapper.findById(customerId);
        log.info("고객 조회: {}", c);
        return c;
    }
}
