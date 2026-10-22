package com.pro.ereportbacken.model.pojo;

public enum UserTransferJobStatus {
    QUEUED,
    UPLOADED,
    VALIDATING,
    PREVIEW_READY,
    STALE,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}
