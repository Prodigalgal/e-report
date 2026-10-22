import { ATTACHMENT_TOTAL_LIMIT_MESSAGE } from '../constants/attachmentLimits.js';

const HTML_TITLE_PATTERN = /<title>(.*?)<\/title>/i;

const getHeaderValue = (headers, name) => {
    if (!headers) return null;
    return headers[name] || headers[name.toLowerCase()] || headers[name.toUpperCase()] || null;
};

const getRequestId = (error, data) => {
    const responseHeaders = error?.response?.headers;
    const requestHeaders = error?.config?.headers;
    return data?.traceId
        || getHeaderValue(responseHeaders, 'x-request-id')
        || getHeaderValue(requestHeaders, 'X-Request-Id')
        || null;
};

const formatFieldErrors = (errors) => {
    if (!errors || typeof errors !== 'object') return null;
    return Object.entries(errors)
        .map(([, message]) => {
            const rawMessage = Array.isArray(message) ? message.join('；') : String(message || '').trim();
            return toFriendlyMessage(rawMessage, '字段填写不正确，请检查后重试。');
        })
        .filter(Boolean)
        .join('\n');
};

const hasFieldErrors = errors => (
    errors !== null
    && typeof errors === 'object'
    && Object.keys(errors).length > 0
);

const normalizeRawMessage = (rawMessage) => {
    if (typeof rawMessage !== 'string') return '';
    const trimmed = rawMessage.trim();
    if (!trimmed) return '';
    const title = trimmed.match(HTML_TITLE_PATTERN)?.[1]?.trim();
    return (title || trimmed).replace(/\s+/g, ' ');
};

const isLoginRequest = (error) => String(error?.config?.url || '').includes('/auth/login');

const isTimeoutMessage = (message) => (
    message.includes('timeout')
    || message.includes('timed out')
    || message.includes('aborterror')
    || message.includes('aborted')
    || message.includes('econnaborted')
    || message.includes('etimedout')
);

const isNetworkMessage = (message) => (
    message.includes('network error')
    || message.includes('failed to fetch')
    || message.includes('network request failed')
    || message.includes('fetch failed')
    || message.includes('unknownhostexception')
    || message.includes('network is unreachable')
    || message.includes('enotfound')
    || message.includes('econnrefused')
    || message.includes('econnreset')
    || message.includes('err_network')
    || message.includes('internet disconnected')
);

const isTechnicalMessage = (message) => (
    message.includes('<html')
    || message.includes('</html>')
    || message.includes('exception')
    || message.includes('java.')
    || message.includes('org.springframework')
    || message.includes('nginx/')
    || message.includes('http ')
    || message.includes('typeerror')
    || message.includes('json parse')
    || message.includes('unexpected token')
    || message.includes('request failed')
    || message.includes('failed:')
);

const isGenericHttpMessage = (message) => (
    message === 'bad request'
    || message === 'unauthorized'
    || message === 'forbidden'
    || message === 'not found'
    || message === 'method not allowed'
    || message === 'unsupported media type'
    || message === 'internal server error'
    || message === 'bad gateway'
    || message === 'service unavailable'
    || message === 'gateway timeout'
);

const statusFallbackMessage = (status, error, fallback) => {
    if (status === 413) {
        return ATTACHMENT_TOTAL_LIMIT_MESSAGE;
    }
    if (status === 400) {
        return '提交内容有误，请检查表单后重试。';
    }
    if (status === 401) {
        return isLoginRequest(error) ? '用户名、密码或验证码不正确，请重新输入。' : '登录状态已失效，请重新登录后再试。';
    }
    if (status === 403) {
        return '当前账号没有执行该操作的权限。';
    }
    if (status === 404) {
        return '没有找到对应数据，可能已被删除或没有权限查看。';
    }
    if (status === 408) {
        return '请求超时，请检查网络后重试。';
    }
    if (status === 409) {
        return '当前数据状态已变化，请刷新后重试。';
    }
    if (status === 415) {
        return '文件格式不支持，请更换后重试。';
    }
    if (status === 422) {
        return '提交内容校验失败，请检查表单。';
    }
    if (status === 429) {
        return '操作过于频繁，请稍后再试。';
    }
    if (status >= 500) {
        return '服务器暂时无法处理请求，请稍后重试。';
    }
    return fallback;
};

const toFriendlyMessage = (rawMessage, fallback, status, error) => {
    const message = normalizeRawMessage(rawMessage);
    const lowerMessage = message.toLowerCase();

    if (!message) return statusFallbackMessage(status, error, fallback);
    if (lowerMessage.includes('request entity too large') || lowerMessage.includes('413')) {
        return ATTACHMENT_TOTAL_LIMIT_MESSAGE;
    }
    if (lowerMessage.includes('unsupported formdatapart')) {
        return '附件上传失败，请更新应用后重试；如仍失败，请减少附件数量或重新选择图片。';
    }
    if (isTimeoutMessage(lowerMessage) || error?.code === 'ECONNABORTED') {
        return '请求超时，请检查网络信号后重试。';
    }
    if (isNetworkMessage(lowerMessage) || error?.code === 'ERR_NETWORK') {
        return '网络连接失败，请确认设备已连接可用网络，然后重试。';
    }
    if (isLoginRequest(error) && status === 401) {
        return '用户名、密码或验证码不正确，请重新输入。';
    }
    if (lowerMessage.includes('bad credentials') || lowerMessage.includes('invalid username') || lowerMessage.includes('invalid password')) {
        return '用户名或密码不正确，请重新输入。';
    }
    if (lowerMessage.includes('username cannot be blank')) {
        return '用户名不能为空。';
    }
    if (lowerMessage.includes('username must be between')) {
        return '用户名长度必须在3到50字符之间。';
    }
    if (lowerMessage.includes('password cannot be blank')) {
        return '密码不能为空。';
    }
  if (lowerMessage.includes('password must be between') || lowerMessage.includes('new password must be between')) {
    return '密码长度必须在8到100字符之间。';
  }
    if (lowerMessage.includes('new password cannot be blank')) {
        return '新密码不能为空。';
    }
    if (lowerMessage.includes('name cannot be blank')) {
        return '姓名不能为空。';
    }
    if (lowerMessage.includes('name cannot exceed')) {
        return '姓名不能超过100字符。';
    }
    if (lowerMessage.includes('config key cannot be blank')) {
        return '配置键不能为空。';
    }
    if (lowerMessage.includes('config key cannot exceed')) {
        return '配置键不能超过100字符。';
    }
    if (lowerMessage.includes('permission name cannot be blank')) {
        return '权限名称不能为空。';
    }
    if (lowerMessage.includes('permission name cannot exceed')) {
        return '权限名称不能超过100字符。';
    }
    if (lowerMessage.includes('description cannot exceed')) {
        return '描述不能超过255字符。';
    }
    if (lowerMessage.includes('captcha') || message.includes('验证码')) {
        if (lowerMessage.includes('invalid') || lowerMessage.includes('incorrect') || message.includes('错误') || message.includes('过期')) {
            return '验证码不正确或已过期，请刷新后重新输入。';
        }
    }
    if (lowerMessage.includes('account is disabled') || message.includes('账号已停用')) {
        return '账号已停用，请联系管理员。';
    }
    if (status && isGenericHttpMessage(lowerMessage)) {
        return statusFallbackMessage(status, error, fallback);
    }
    if (status >= 500) {
        return '服务器暂时无法处理请求，请稍后重试。';
    }
    if (status && isTechnicalMessage(lowerMessage)) {
        return statusFallbackMessage(status, error, fallback);
    }
    if (isTechnicalMessage(lowerMessage)) {
        return fallback;
    }
    return message;
};

export const formatApiErrorMessage = (error, fallback = '操作失败，请稍后重试。') => {
    const status = error?.response?.status;
    const data = error?.response?.data;
    let message = statusFallbackMessage(status, error, fallback);

    if (status === 413) {
        message = ATTACHMENT_TOTAL_LIMIT_MESSAGE;
    } else if (hasFieldErrors(data?.errors)) {
        const fieldErrors = formatFieldErrors(data.errors);
        message = fieldErrors ? `${fallback}\n${fieldErrors}` : fallback;
    } else if (data?.message) {
        message = toFriendlyMessage(data.message, message, status, error);
    } else if (typeof data === 'string' && data.trim()) {
        message = toFriendlyMessage(data, message, status, error);
    } else if (error?.message) {
        message = toFriendlyMessage(error.message, message, status, error);
    } else if (error?.code) {
        message = toFriendlyMessage(error.code, message, status, error);
    }

    const requestId = getRequestId(error, data);
    return requestId ? `${message}\n错误编号：${requestId}` : message;
};
