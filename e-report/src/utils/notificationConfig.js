export const notificationSettingSourceLabel = (setting, tenantName = '当前市公司') => {
    if (setting?.sourceScope === 'UNCONFIGURED') return '未配置';
    if (setting?.sourceScope === 'RUNTIME_SECRET') return '系统默认发件池';
    if (setting?.sourceScope === 'MIXED') return '本级与上级共同使用';
    if (setting?.sourceScope === 'ORGANIZATION_OVERRIDE') {
        return setting.sourceOrgUnitName ? `${setting.sourceOrgUnitName}配置` : '区县配置';
    }
    if (setting?.sourceScope === 'TENANT_OVERRIDE') return `${tenantName}默认`;
    return '平台默认';
};
