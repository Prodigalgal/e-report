package com.pro.ereportbacken.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationTraceContextTests {

    @Test
    void validTraceIdIsPreservedWithoutBusinessTextRedaction() {
        String traceId = "ef13850515818d92e693a3035fc31a4e";

        assertThat(ObservationTraceContext.traceId(traceId)).isEqualTo(traceId);
    }

    @Test
    void uppercaseTraceIdIsCanonicalizedAndZeroIdentifierIsRejected() {
        assertThat(ObservationTraceContext.traceId("ABCDEF0123456789ABCDEF0123456789"))
                .isEqualTo("abcdef0123456789abcdef0123456789");
        assertThat(ObservationTraceContext.traceId("00000000000000000000000000000000"))
                .isNull();
        assertThat(ObservationTraceContext.spanId("0000000000000000"))
                .isNull();
    }

    @Test
    void malformedIdentifiersAreRejected() {
        assertThat(ObservationTraceContext.traceId("not-a-trace-id")).isNull();
        assertThat(ObservationTraceContext.spanId("1234")).isNull();
    }
}
