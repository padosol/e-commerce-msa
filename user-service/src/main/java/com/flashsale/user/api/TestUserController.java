package com.flashsale.user.api;

import com.flashsale.user.TestUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestUserController {

    private final TestUserService service;

    public TestUserController(TestUserService service) {
        this.service = service;
    }

    @PostMapping("/internal/test-users")
    List<TestUserResponse> create(@Valid @RequestBody TestUserRequest request) {
        return service.ensure(request.count());
    }
}
