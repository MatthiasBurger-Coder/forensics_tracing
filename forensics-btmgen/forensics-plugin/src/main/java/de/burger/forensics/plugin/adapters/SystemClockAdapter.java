package de.burger.forensics.plugin.adapters;

import de.burger.forensics.domain.port.out.ClockPort;
import java.time.Instant;

/**
 * Production clock implementation using the system time.
 */
public final class SystemClockAdapter implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
