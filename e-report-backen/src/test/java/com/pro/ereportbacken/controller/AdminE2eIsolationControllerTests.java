package com.pro.ereportbacken.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminE2eIsolationControllerTests {

    @Test
    void reportsReadyOnlyWhenEveryExternalSideEffectIsDisabled() {
        AdminE2eIsolationController controller = new AdminE2eIsolationController(
                false, false, false, false, false, false, false, false, false
        );

        assertThat(controller.status().ready()).isTrue();
    }

    @Test
    void rejectsIsolationReadinessWhenAnySideEffectRemainsEnabled() {
        AdminE2eIsolationController controller = new AdminE2eIsolationController(
                false, false, false, false, false, false, true, false, false
        );

        assertThat(controller.status().ready()).isFalse();
        assertThat(controller.status().outboxEnqueueEnabled()).isTrue();
    }
}
