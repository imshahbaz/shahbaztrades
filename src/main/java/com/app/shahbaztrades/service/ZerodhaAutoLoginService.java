package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.entity.User;

import java.util.Set;

/**
 * Logging users into Zerodha without them present, by handing their TOTP credentials to the session
 * manager and taking its callback.
 * <p>
 * Split from {@link ZerodhaService}, which serves a user who is at the keyboard.
 */
public interface ZerodhaAutoLoginService {

    /** Auto-logs in whoever has it enabled, and nudges the rest to log in themselves. */
    void autoLogin(Set<Long> userIds);

    /** Asks the session manager for a token, if this user has auto-login configured. */
    void autoConnectZerodhaSession(User user);

    /** Consumes the session manager's callback and stores the resulting token. */
    void sessionManagerCallback(ZerodhaLoginResponseDTO request);
}
