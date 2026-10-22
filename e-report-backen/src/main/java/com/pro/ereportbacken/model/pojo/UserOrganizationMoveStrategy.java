package com.pro.ereportbacken.model.pojo;

public enum UserOrganizationMoveStrategy {
    KEEP(
            "保留全部现有授权",
            "只改变用户所属组织，不调整原子权限；迁移后的数据可见性自动按新组织树计算。"),
    REVOKE_ALL(
            "撤销全部可撤销授权",
            "撤销全部由管理员维护的当前授权；系统基线授权由系统继续维护，迁移后按职责重新配置权限。");

    private final String displayName;
    private final String outcome;

    UserOrganizationMoveStrategy(String displayName, String outcome) {
        this.displayName = displayName;
        this.outcome = outcome;
    }

    public String displayName() {
        return displayName;
    }

    public String outcome() {
        return outcome;
    }
}
