// src/App.jsx
import React from 'react';
import MainLayout from './components/layout/MainLayout';
import AppRoutes from './router/AppRoutes';

function App() {
    return (
        <MainLayout>
            <AppRoutes />
        </MainLayout>
    );
}

export default App;