package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.UserProfileResponse;

public interface UserService {
    UserProfileResponse getProfile(String email);
    void addAddress(String email, AddressRequest request);
}
