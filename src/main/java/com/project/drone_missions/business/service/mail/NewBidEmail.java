package com.project.drone_missions.business.service.mail;

import com.project.drone_missions.data.model.Mission;
import com.project.drone_missions.data.model.User;

import java.math.BigDecimal;

/**
 * Everything the "new bid" email needs about the bid that triggered it — a parameter object
 * rather than five positional arguments, so {@code pilotName} and {@code message} (both
 * {@code String}) cannot be swapped at the call site without the compiler noticing.
 *
 * @param designer  the mission's owner, who receives the email
 * @param mission   the mission that was bid on
 * @param pilotName display name of the bidding pilot
 * @param amount    the bid amount
 * @param message   the pilot's covering message, may be null
 */
public record NewBidEmail(User designer, Mission mission, String pilotName,
                          BigDecimal amount, String message) {
}
