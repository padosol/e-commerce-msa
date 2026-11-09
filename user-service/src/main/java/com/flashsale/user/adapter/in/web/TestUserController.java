package com.flashsale.user.adapter.in.web;

import com.flashsale.user.application.TestUserService;
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
        return service.ensure(request.count()).stream()
            .map(u -> new TestUserResponse(u.userId(), u.accessToken())).toList();
    }
}
