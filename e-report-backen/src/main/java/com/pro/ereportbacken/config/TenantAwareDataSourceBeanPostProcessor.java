package com.pro.ereportbacken.config;

import com.pro.ereportbacken.security.TenantExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class TenantAwareDataSourceBeanPostProcessor implements BeanPostProcessor {

    private final TenantExecutionContext tenantExecutionContext;

    public TenantAwareDataSourceBeanPostProcessor(TenantExecutionContext tenantExecutionContext) {
        this.tenantExecutionContext = tenantExecutionContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(dataSource, tenantExecutionContext);
        }
        return bean;
    }
}
