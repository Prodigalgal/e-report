import React from 'react';

import LoginPage from '../../components/Auth/LoginPage';
import { resolveAdminLoginDestination } from '../../utils/authRedirects';

function AdminLoginPage() {
    return <LoginPage title="管理端登录" resolveDestination={resolveAdminLoginDestination} />;
}

export default AdminLoginPage;
