package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.enums.BrokerType;

/**
 * The parts of broker authentication that are the same whichever broker it is, so callers can act
 * on a user's broker without asking which one it is.
 */
public interface BrokerAuthService {

    BrokerType getBrokerType();

    /** Drops stored credentials, forcing a fresh login. */
    void revokeAuth(long userId);

    /** Whether this broker can be logged in unattended, or the user must do it themselves. */
    boolean supportsAutoLogin();
}
