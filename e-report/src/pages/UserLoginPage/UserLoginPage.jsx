import React from 'react';

import LoginPage from '../../components/Auth/LoginPage';
import { resolveUserLoginDestination } from '../../utils/authRedirects';

function UserLoginPage() {
    return <LoginPage title="欢迎回来" resolveDestination={resolveUserLoginDestination} />;
}

export default UserLoginPage;
