package com.pro.ereportbacken.config;

import java.util.Locale;

public final class WorkOrderAttachmentLimits {

    public static final int MAX_FILE_COUNT = 10;
    public static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L;
    public static final String REPORT_COUNT_LIMIT_MESSAGE = "现场附件不能超过10份";
    public static final String RESPONSE_COUNT_LIMIT_MESSAGE = "回单附件不能超过10份";
    public static final String TOTAL_LIMIT_MESSAGE =
            "上传材料总大小不能超过100兆字节，请删除部分附件，或压缩后重新上传。";
    public static final String SINGLE_FILE_LIMIT_MESSAGE =
            "单个附件不能超过100兆字节，请压缩后重新上传。";

    private WorkOrderAttachmentLimits() {
    }

    public static String countLimitMessage(int actualCount) {
        return "附件最多上传10个，当前共" + actualCount + "个，请删除多余附件后再提交。";
    }

    public static String totalLimitMessage(long actualBytes) {
        double actualMegabytes = Math.ceil(actualBytes * 10D / (1024D * 1024D)) / 10D;
        return "上传材料总大小不能超过100兆字节，当前合计"
                + String.format(Locale.ROOT, "%.1f", actualMegabytes)
                + "兆字节。请删除部分附件，或压缩后重新上传。";
    }
}
