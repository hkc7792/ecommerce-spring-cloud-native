package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.UserProfileResponse;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @ResponseStatus(HttpStatus.OK)
    public UserProfileResponse getProfile(@AuthenticationPrincipal String email) {
        return userService.getProfile(email);
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public String addAddress(@AuthenticationPrincipal String email, @Valid @RequestBody AddressRequest request) {
        userService.addAddress(email, request);
        return "Address added successfully";
    }
}
