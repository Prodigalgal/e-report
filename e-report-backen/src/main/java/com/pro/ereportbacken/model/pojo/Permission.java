package com.pro.ereportbacken.model.pojo;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends UuidV7Entity {

    @Column(nullable = false, unique = true, length = 100)
    private String name; // 权限标识符, e.g., "work-order:network-fault:report"

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 255)
    private String description;

    // @ManyToMany(mappedBy = "permissions")

    public Permission(String name, String displayName, String description) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
    }

    // equals 和 hashCode 对于 Set 操作很重要，确保基于 name 的唯一性
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission)) return false;
        Permission that = (Permission) o;
        return getName() != null ? getName().equals(that.getName()) : that.getName() == null;
    }

    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }
}
