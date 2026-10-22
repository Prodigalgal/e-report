package com.pro.ereportbacken.model.pojo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderDomainTests {

    @Test
    void boxDoorOpenRequiresQuantityAndOwnsItsTypeSpecificState() {
        WorkOrder workOrder = new WorkOrder();

        assertThatThrownBy(() -> WorkOrderType.BOX_DOOR_OPEN.initializeTypeSpecificFields(workOrder, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("箱门未关数量必须大于0。");

        WorkOrderType.BOX_DOOR_OPEN.initializeTypeSpecificFields(workOrder, 3);
        assertThat(workOrder.getBoxQuantity()).isEqualTo(3);
        assertThat(workOrder.getReplacementRequired()).isFalse();
    }

    @Test
    void replacementWorkflowValidatesAndStoresItsResponse() {
        WorkOrder workOrder = new WorkOrder();
        WorkOrderType.DAMAGED_BOX_REPLACEMENT.initializeTypeSpecificFields(workOrder, 12);

        assertThat(workOrder.getBoxQuantity()).isNull();
        assertThat(workOrder.getReplacementRequired()).isTrue();
        assertThatThrownBy(() -> WorkOrderType.DAMAGED_BOX_REPLACEMENT
                .applyTypeSpecificResponse(workOrder, true, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已换箱时必须填写换箱后结果。");

        WorkOrderType.DAMAGED_BOX_REPLACEMENT.applyTypeSpecificResponse(workOrder, true, "已更换并完成封箱");
        assertThat(workOrder.getReplacementCompleted()).isTrue();
        assertThat(workOrder.getReplacementResult()).isEqualTo("已更换并完成封箱");
    }

    @Test
    void commonLifecycleIsOwnedByWorkOrderAggregate() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(com.pro.ereportbacken.TestIds.uuid(42));
        User operator = new User();

        workOrder.markResolved("链路恢复", "重新熔接", operator);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.PENDING_VERIFICATION.getValue());
        assertThat(workOrder.getResolvedBy()).isSameAs(operator);
        assertThat(workOrder.getResolutionTime()).isNotNull();

        assertThatThrownBy(() -> workOrder.markResolved("重复回单", "无", operator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("工单 " + com.pro.ereportbacken.TestIds.uuid(42) + " 已完成回单或已关闭。");

        workOrder.close("验收完成", operator);
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.CLOSED.getValue());
        assertThat(workOrder.getClosedBy()).isSameAs(operator);
        assertThat(workOrder.getClosureTime()).isNotNull();
    }

    @Test
    void persistedWorkOrderCannotReopenAfterTerminalState() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(com.pro.ereportbacken.TestIds.uuid(43));
        workOrder.transitionTo(WorkOrderStatus.CANCELLED);

        assertThatThrownBy(() -> workOrder.transitionTo(WorkOrderStatus.IN_PROGRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许从");
    }
}
