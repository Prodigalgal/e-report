package com.pro.ereportbacken.notification;

import com.pro.ereportbacken.model.pojo.WorkOrderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMarkdownFormatterTests {

    @Test
    void formatsWorkOrderCreatedMessageForWeComRobot() {
        WorkOrderNotification notification = new WorkOrderNotification(
                com.pro.ereportbacken.TestIds.uuid(1024),
                com.pro.ereportbacken.TestIds.uuid(1),
                com.pro.ereportbacken.TestIds.uuid(1),
                WorkOrderNotificationKind.CREATED,
                WorkOrderType.DAMAGED_BOX_REPLACEMENT,
                "破损箱体更换",
                "普通",
                "等待接单",
                null,
                null,
                "漳州芗城分光箱 A-01",
                "118.796877,32.060255",
                "箱体门锁损坏，需要换箱",
                "13000000095",
                "张三",
                LocalDateTime.of(2026, 7, 3, 10, 30, 15),
                List.of(),
                null,
                null,
                false
        );

        String markdown = NotificationMarkdownFormatter.workOrderCreated(notification);

        assertThat(markdown)
                .contains("### 新工单通知 #" + com.pro.ereportbacken.TestIds.uuid(1024))
                .contains("- **工单类型**：破损箱体更换")
                .contains("- **故障地点**：漳州芗城分光箱 A-01")
                .contains("- **定位坐标**：118.796877,32.060255")
                .contains("- **联系人**：张三")
                .contains("- **联系电话**：13000000095")
                .contains("- **上报时间**：2026-07-03 18:30:15")
                .contains("- **问题描述**：箱体门锁损坏，需要换箱");
    }
}
