package com.tlaloc.clouseau.plugins.parsers;

import com.tlaloc.clouseau.api.LogEntry;
import com.tlaloc.clouseau.api.LogEntry.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootPatternParserTest {

    private SpringBootPatternParser parser;

    @BeforeEach
    void setUp() {
        parser = new SpringBootPatternParser();
    }

    // --- canParse ---

    @Test
    void canParse_springBoot34WithAppName() {
        String line = "2025-06-10T23:43:06.269-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.demo.DemoApplication         : Starting DemoApplication";
        assertThat(parser.canParse(line)).isTrue();
    }

    @Test
    void canParse_springBootPre34NoAppName() {
        String line = "2024-01-15T10:30:45.123+01:00  INFO 12345 --- [           main] com.example.DemoApplication              : Started in 2.3 s";
        assertThat(parser.canParse(line)).isTrue();
    }

    @Test
    void canParse_utcZone() {
        String line = "2024-01-15T10:30:45.789Z  WARN 9999 --- [main] c.e.service.JobService                   : Job took too long";
        assertThat(parser.canParse(line)).isTrue();
    }

    @Test
    void canParse_springBootAsciiLogo_returnsFalse() {
        assertThat(parser.canParse("  .   ____          _            __ _ _")).isFalse();
        assertThat(parser.canParse(" :: Spring Boot ::                (v3.5.0)")).isFalse();
        assertThat(parser.canParse("")).isFalse();
        assertThat(parser.canParse(null)).isFalse();
    }

    @Test
    void canParse_log4jFormat_returnsFalse() {
        String line = "2024-01-15 10:30:45.123 [main] INFO  com.example.App - Hello world";
        assertThat(parser.canParse(line)).isFalse();
    }

    // --- parse ---

    @Test
    void parse_springBoot34WithAppName_extractsAllFields() {
        String line = "2025-06-10T23:43:06.269-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.demo.DemoApplication         : Starting DemoApplication using Java 21";

        LogEntry entry = parser.parse(line);

        assertThat(entry.level()).isEqualTo(LogLevel.INFO);
        assertThat(entry.thread()).isEqualTo("restartedMain");
        assertThat(entry.logger()).isEqualTo("com.example.demo.DemoApplication");
        assertThat(entry.message()).isEqualTo("Starting DemoApplication using Java 21");
        assertThat(entry.timestamp()).isNotNull();
        assertThat(entry.fields()).containsEntry("pid", "31280").containsEntry("app", "demo");
    }

    @Test
    void parse_springBootPre34NoAppName_extractsAllFields() {
        String line = "2024-01-15T10:30:45.456+01:00 ERROR 12345 --- [           main] o.s.b.SpringApplication                  : Application run failed";

        LogEntry entry = parser.parse(line);

        assertThat(entry.level()).isEqualTo(LogLevel.ERROR);
        assertThat(entry.thread()).isEqualTo("main");
        assertThat(entry.logger()).isEqualTo("o.s.b.SpringApplication");
        assertThat(entry.message()).isEqualTo("Application run failed");
        assertThat(entry.fields()).containsEntry("pid", "12345").doesNotContainKey("app");
    }

    @Test
    void parse_allLinesFromTestLog() {
        String[] lines = {
            "2025-06-10T23:43:06.269-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.demo.DemoApplication         : Starting DemoApplication using Java 21.0.7 with PID 31280",
            "2025-06-10T23:43:06.273-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.demo.DemoApplication         : No active profile set, falling back to 1 default profile: \"default\"",
            "2025-06-10T23:43:06.327-05:00  INFO 31280 --- [demo] [  restartedMain] .e.DevToolsPropertyDefaultsPostProcessor : Devtools property defaults active! Set 'spring.devtools.add-properties' to 'false' to disable",
            "2025-06-10T23:43:06.894-05:00  INFO 31280 --- [demo] [  restartedMain] o.s.b.d.a.OptionalLiveReloadServer       : LiveReload server is running on port 35729",
            "2025-06-10T23:43:06.926-05:00  INFO 31280 --- [demo] [  restartedMain] com.example.demo.DemoApplication         : Started DemoApplication in 1.201 seconds (process running for 1.815)"
        };

        for (String line : lines) {
            assertThat(parser.canParse(line))
                .as("should match: %s", line)
                .isTrue();
            LogEntry entry = parser.parse(line);
            assertThat(entry.level()).isEqualTo(LogLevel.INFO);
            assertThat(entry.timestamp()).isNotNull();
        }
    }
}
