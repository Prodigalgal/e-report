package com.pro.ereportbacken.model.pojo;

import java.util.List;

public enum MaterialCategory {
    OPTICAL_CABLE("光缆", List.of("12芯", "24芯", "48芯", "96芯", "144芯", "288芯")),
    JOINT_BOX("接头盒", List.of("12芯", "24芯", "48芯", "96芯", "144芯", "288芯")),
    SPLITTER("分光器", List.of("1:4", "1:8", "1:16", "1:32", "1:64"));

    private final String displayName;
    private final List<String> specifications;

    MaterialCategory(String displayName, List<String> specifications) {
        this.displayName = displayName;
        this.specifications = specifications;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getSpecifications() {
        return specifications;
    }

    public boolean supports(String specification) {
        return specifications.contains(specification);
    }
}
