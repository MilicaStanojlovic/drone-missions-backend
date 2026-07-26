package com.project.drone_missions.business.service.mail;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Sends the app-styled HTML emails. Rendering uses Thymeleaf templates under
 * {@code templates/email/}. All sends run asynchronously and never propagate a
 * failure back to the triggering action (a bid, a scheduled sweep) — email is
 * best-effort. When {@code app.mail.enabled=false} (the default), the rendered
 * HTML is logged instead of dispatched, so the app runs with no SMTP credentials.
 */
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final boolean enabled;
    private final String from;
    private final String frontendUrl;
    private final String redirectTo;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:DroneMissions <no-reply@dronemissions.app>}") String from,
                        @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                        @Value("${app.mail.redirect-to:}") String redirectTo) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.enabled = enabled;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.redirectTo = redirectTo;
    }
// TODO COMMENTS
    /**
     *
     *
     * @param designer
     * @param mission
     * @param pilotName
     * @param amount
     * @param message
     */
    @Async
    public void sendNewBid(User designer, Mission mission, String pilotName, BigDecimal amount, String message) {
        Context ctx = baseContext(designer, mission); // smesti u record TODO
        ctx.setVariable("pilotName", pilotName);
        ctx.setVariable("amount", amount);
        ctx.setVariable("bidMessage", message);
        ctx.setVariable("ctaUrl", missionUrl(mission.getId()));
        send(designer.getEmail(), "New bid on \"%s\"".formatted(mission.getName()), "email/new-bid", ctx);
    }

    /** Notify the pilot that their bid was accepted or rejected. */
    @Async
    public void sendBidDecision(User pilot, Mission mission, BigDecimal amount, boolean accepted) {
        Context ctx = baseContext(pilot, mission);
        ctx.setVariable("amount", amount);
        if (accepted) {
            ctx.setVariable("ctaUrl", missionUrl(mission.getId()));
            send(pilot.getEmail(), "Your bid on \"%s\" was accepted".formatted(mission.getName()),
                    "email/bid-accepted", ctx);
        } else {
            ctx.setVariable("ctaUrl", frontendUrl + "/missions");
            send(pilot.getEmail(), "Update on your bid for \"%s\"".formatted(mission.getName()),
                    "email/bid-rejected", ctx);
        }
    }

    /** Ask the winning pilot whether the flight has ended (mission past its end date). */
    @Async
    public void sendMissionOverdue(User pilot, Mission mission) {
        Context ctx = baseContext(pilot, mission);
        ctx.setVariable("ctaUrl", missionUrl(mission.getId()));
        send(pilot.getEmail(), "Has your flight for \"%s\" ended?".formatted(mission.getName()),
                "email/mission-overdue", ctx);
    }

    /** Tell the awarded pilot that the designer cancelled the mission they had won. */
    @Async
    public void sendMissionCancelled(User pilot, Mission mission) { //
        Context ctx = baseContext(pilot, mission);
        ctx.setVariable("ctaUrl", missionUrl(mission.getId()));
        send(pilot.getEmail(), "Mission \"%s\" was cancelled".formatted(mission.getName()),
                "email/mission-cancelled", ctx);
    }

    private Context baseContext(User recipient, Mission mission) {
        Context ctx = new Context();
        ctx.setVariable("recipientName", recipient.getUsername());
        ctx.setVariable("missionName", mission.getName());
        ctx.setVariable("missionLocation", mission.getLocation());
        return ctx;
    }

    private String missionUrl(Long missionId) {
        return frontendUrl + "/missions/" + missionId;
    }

    private void send(String to, String subject, String template, Context ctx) {
        String html;
        try {
            html = templateEngine.process(template, ctx);
        } catch (RuntimeException e) {
            log.error("Failed to render email template {} for {}", template, to, e);
            return;
        }

        if (!enabled) {
            log.info("[mail disabled] would send to={} subject=\"{}\"\n{}", to, subject, html);
            return;
        }

        // Dev testing: when app.mail.redirect-to is set, deliver every message to that
        // inbox instead of the real recipient, tagging the subject with the address it
        // was actually meant for. Blank (the default) = normal delivery to `to`.
        String recipient = to;
        if (!redirectTo.isBlank()) {
            subject = "[→ %s] %s".formatted(to, subject);
            recipient = redirectTo;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent email to={} (intended {}) subject=\"{}\"", recipient, to, subject);
        } catch (Exception e) {
            // Best-effort: a mail failure must never break the bid/scheduler flow.
            log.error("Failed to send email to={} subject=\"{}\"", recipient, subject, e);
        }
    }
}
