package com.pro.ereportbacken.model.pojo;

public enum AppEdition {
    NATIVE,
    LITE;

    public static AppEdition orNative(AppEdition edition) {
        return edition == null ? NATIVE : edition;
    }
}
