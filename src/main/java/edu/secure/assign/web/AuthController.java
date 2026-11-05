package edu.secure.assign.web;

import edu.secure.assign.domain.Customer;
import edu.secure.assign.domain.SignupRequest;
import edu.secure.assign.mapper.CustomerMapper;
import edu.secure.assign.security.AesCrypto;
import edu.secure.assign.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final CustomerMapper customerMapper;
    private final AesCrypto crypto;

    public AuthController(CustomerMapper customerMapper, AesCrypto crypto) {
        this.customerMapper = customerMapper;
        this.crypto = crypto;
    }

    @PostMapping("/signup")
    public Map<String, String> signup(@RequestBody SignupRequest req) {
        log.info("가입 요청: {}", req);
        Customer c = new Customer(req.customerId(), req.customerName(),
                crypto.encrypt(req.rrn()), req.phone(), PasswordHasher.hash(req.password()));
        customerMapper.insert(c);
        return Map.of("customerId", req.customerId());
    }
}
