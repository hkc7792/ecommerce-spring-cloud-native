package com.ecommerce.user.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Dev-friendly {@link MailService} that logs the message instead of sending via SMTP.
 *
 * Active by default ({@code app.mail.provider=console}). The token/link is part of the
 * logged body so a developer can complete the flow from the service's log output.
 * Swap in a real implementation by setting {@code app.mail.provider} to another value
 * (e.g. {@code smtp}) and providing the corresponding bean.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleMailService implements MailService {

    private final String from;

    public ConsoleMailService(@Value("${app.mail.from:noreply@shopease.local}") String from) {
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("[MAIL SIMULATED] from={} to={} subject={}\n{}", from, to, subject, body);
    }
}
