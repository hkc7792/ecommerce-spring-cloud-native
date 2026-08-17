package com.ecommerce.user.mail;

/**
 * Outbound email abstraction for user-service.
 *
 * Both email verification (US-101) and password reset (US-104) depend on it, so the
 * sender is a single swappable bean rather than ad-hoc logic in each flow. The default
 * implementation logs to the console (dev); a real SMTP provider can be added later by
 * introducing another {@code MailService} bean and setting {@code app.mail.provider}.
 */
public interface MailService {

    void send(String to, String subject, String body);
}
