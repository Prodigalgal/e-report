package com.pro.ereportbacken.model.dto;

import com.pro.ereportbacken.model.pojo.MaterialCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialUsageDTO {
    @NotNull(message = "物料类别不能为空")
    private MaterialCategory category;

    private String categoryName;

    @NotBlank(message = "物料规格不能为空")
    private String specification;

    @NotNull(message = "物料数量不能为空")
    @Min(value = 1, message = "物料数量必须大于0")
    private Integer quantity;
}
