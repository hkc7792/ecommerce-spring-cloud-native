package com.ecommerce.user.dto;

import java.util.List;

public record UserProfileResponse(
        Long userId,
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        List<AddressDto> addresses
) {
    public record AddressDto(
            Long id,
            String label,
            String street,
            String city,
            String state,
            String pincode,
            String phone,
            Boolean isDefault
    ) {}
}
