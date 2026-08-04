package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank(message = "Label is required (e.g. HOME, OFFICE)")
        String label,

        @NotBlank(message = "Street address is required")
        String street,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "State is required")
        String state,

        @NotBlank(message = "Pincode is required")
        String pincode,

        @NotBlank(message = "Phone number is required")
        String phone,

        Boolean isDefault
) {}
