package com.pro.ereportbacken.model.pojo;

public enum WorkOrderType {
    NETWORK_FAULT("网络故障", false, false),
    BOX_DOOR_OPEN("箱门未关", true, false),
    DAMAGED_BOX_REPLACEMENT("破损箱体更换", false, true);

    private final String displayName;
    private final boolean boxQuantityRequired;
    private final boolean replacementWorkflow;

    WorkOrderType(String displayName, boolean boxQuantityRequired, boolean replacementWorkflow) {
        this.displayName = displayName;
        this.boxQuantityRequired = boxQuantityRequired;
        this.replacementWorkflow = replacementWorkflow;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void initializeTypeSpecificFields(WorkOrder workOrder, Integer boxQuantity) {
        if (boxQuantityRequired && (boxQuantity == null || boxQuantity < 1)) {
            throw new IllegalArgumentException("箱门未关数量必须大于0。");
        }
        workOrder.setBoxQuantity(boxQuantityRequired ? boxQuantity : null);
        workOrder.setReplacementRequired(replacementWorkflow);
        workOrder.setReplacementCompleted(null);
        workOrder.setReplacementResult(null);
    }

    public void applyTypeSpecificResponse(WorkOrder workOrder,
                                          Boolean replacementCompleted,
                                          String replacementResult) {
        if (!replacementWorkflow) {
            workOrder.setReplacementCompleted(null);
            workOrder.setReplacementResult(null);
            return;
        }
        if (replacementCompleted == null) {
            throw new IllegalArgumentException("破损箱体更换处理必须选择是否已换箱。");
        }
        if (Boolean.TRUE.equals(replacementCompleted)
                && (replacementResult == null || replacementResult.isBlank())) {
            throw new IllegalArgumentException("已换箱时必须填写换箱后结果。");
        }
        workOrder.setReplacementCompleted(replacementCompleted);
        workOrder.setReplacementResult(replacementResult);
    }
}
