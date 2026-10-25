package ru.mephi.hotelbooking.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TraceContextTest {

    @Test
    void wellFormedIncomingIdIsKept() {
        assertThat(TraceContext.resolveTraceId("client-trace-1")).isEqualTo("client-trace-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "with space and more", "line\nbreak-injection"})
    void missingOrMalformedIdIsReplaced(String incoming) {
        assertThat(TraceContext.resolveTraceId(incoming))
                .isNotEqualTo(incoming)
                .matches("[0-9a-f]{32}");
    }
}
