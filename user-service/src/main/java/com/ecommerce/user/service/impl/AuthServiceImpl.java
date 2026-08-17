package com.ecommerce.user.service.impl;

import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.entities.User;
import com.ecommerce.user.exceptions.DuplicateEmailException;
import com.ecommerce.user.exceptions.EmailAlreadyVerifiedException;
import com.ecommerce.user.exceptions.InvalidVerificationTokenException;
import com.ecommerce.user.exceptions.UserNotFoundException;
import com.ecommerce.user.mail.MailService;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.JwtTokenProvider;
import com.ecommerce.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final MailService mailService;

    @Value("${app.verification.token-ttl-minutes:60}")
    private long verificationTokenTtlMinutes;

    @Value("${app.verification.frontend-verify-url:http://localhost:4200/auth/verify}")
    private String frontendVerifyUrl;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email " + request.email() + " is already in use.");
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(verificationTokenTtlMinutes);

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .verificationToken(token)
                .verificationTokenExpiresAt(expiresAt)
                .build();

        userRepository.save(user);

        sendVerificationEmail(user);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        String token = tokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId());

        return new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidVerificationTokenException("Invalid or expired verification token.");
        }

        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token."));

        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException("This email has already been verified. You can log in.");
        }

        if (user.getVerificationTokenExpiresAt() == null
                || user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidVerificationTokenException("Verification token has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account found for email " + email + "."));

        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException("This email is already verified. You can log in.");
        }

        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(verificationTokenTtlMinutes));
        userRepository.save(user);

        sendVerificationEmail(user);
    }

    private void sendVerificationEmail(User user) {
        String verifyLink = frontendVerifyUrl + "?token=" + user.getVerificationToken();
        String body = String.format(
                "Hello %s,%n%nThank you for registering with ShopEase. Please verify your email address by "
                        + "clicking the link below:%n%n%s%n%nThis verification link is valid for %d minutes. "
                        + "If you did not create an account, you can safely ignore this email.%n%nShopEase Team",
                user.getFullName(),
                verifyLink,
                verificationTokenTtlMinutes);

        mailService.send(user.getEmail(), "ShopEase — Verify your email", body);
    }
}
