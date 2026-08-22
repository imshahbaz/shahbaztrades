package com.app.shahbaztrades.service;

/**
 * Rebuilds the margin master from upstream sources. Batch imports, kept apart from the
 * {@link MarginService} read path that the whole trading engine depends on.
 */
public interface MarginSyncService {

    /** Imports Zerodha's MTF leverage file, enriches it with Rupeezy leverage, drops stale symbols. */
    void syncMTF(byte[] fileBytes);

    /** Fills in AngelOne instrument tokens for symbols that lack them. */
    void syncAngelOneToken();
}
