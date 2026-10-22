package com.pro.ereportbacken.model.pojo;

public enum AccessMode {
    NONE,
    R,
    W,
    RW;

    public static AccessMode combine(boolean readable, boolean writable) {
        if (readable && writable) {
            return RW;
        }
        if (readable) {
            return R;
        }
        if (writable) {
            return W;
        }
        return NONE;
    }
}
