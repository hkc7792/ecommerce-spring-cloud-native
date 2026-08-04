package com.ecommerce.user.service.impl;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.UserProfileResponse;
import com.ecommerce.user.entities.Address;
import com.ecommerce.user.entities.User;
import com.ecommerce.user.exceptions.UserNotFoundException;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        List<UserProfileResponse.AddressDto> addressDtos = user.getAddresses().stream()
                .map(a -> new UserProfileResponse.AddressDto(
                        a.getId(), a.getLabel(), a.getStreet(), a.getCity(),
                        a.getState(), a.getPincode(), a.getPhone(), a.getIsDefault()))
                .toList();

        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getStatus().name(),
                addressDtos
        );
    }

    @Override
    @Transactional
    public void addAddress(String email, AddressRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (user.getAddresses().size() >= 5) {
            throw new IllegalArgumentException("Maximum 5 addresses allowed.");
        }

        Address address = Address.builder()
                .user(user)
                .label(request.label())
                .street(request.street())
                .city(request.city())
                .state(request.state())
                .pincode(request.pincode())
                .phone(request.phone())
                .isDefault(Boolean.TRUE.equals(request.isDefault()))
                .build();

        addressRepository.save(address);
    }
}
