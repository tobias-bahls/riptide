package org.zalando.riptide.failsafe;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static java.time.Instant.parse;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class HttpDateDelayParserTest {

    private final DelayParser unit = new HttpDateDelayParser(Clock.fixed(parse("2018-06-24T01:19:37Z"), UTC));

    @Test
    void shouldParseHttpDate() {
        assertEquals(Duration.ofSeconds(17), unit.parse("Sun, 24 Jun 2018 01:19:54 GMT"));
    }

    @Test
    void shouldIgnoreSeconds() {
        assertNull(unit.parse("17"));
    }

    @Test
    void shouldIgnoreIso8601() {
        assertNull(unit.parse("2018-04-11T22:34:28Z"));
    }

}
