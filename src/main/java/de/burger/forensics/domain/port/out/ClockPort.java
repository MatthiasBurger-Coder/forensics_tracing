package de.burger.forensics.domain.port.out;

import java.time.Instant;

/**
 * Abstraction over time to simplify testing.
 */
public interface ClockPort {
    Instant now();
}
