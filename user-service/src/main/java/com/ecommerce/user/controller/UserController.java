package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.UserProfileResponse;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Endpoints for user profile and addresses")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get user profile", description = "Retrieves the profile of the authenticated user")
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully")
    public UserProfileResponse getProfile(@AuthenticationPrincipal String email) {
        return userService.getProfile(email);
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new address", description = "Adds a shipping/billing address to the user profile")
    @ApiResponse(responseCode = "201", description = "Address added successfully")
    public String addAddress(@AuthenticationPrincipal String email, @Valid @RequestBody AddressRequest request) {
        userService.addAddress(email, request);
        return "Address added successfully";
    }
}
