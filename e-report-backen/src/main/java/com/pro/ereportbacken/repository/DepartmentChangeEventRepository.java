package com.pro.ereportbacken.repository;

import com.pro.ereportbacken.model.pojo.DepartmentChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentChangeEventRepository extends JpaRepository<DepartmentChangeEvent, UUID> {
}
