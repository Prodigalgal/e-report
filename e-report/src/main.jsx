// src/main.jsx (或 App.jsx)
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './contexts/AuthProvider';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import theme from './theme';
import { registerServiceWorker } from './registerServiceWorker';
import TelemetryErrorBoundary from './observability/TelemetryErrorBoundary';
import TelemetryRouteObserver from './observability/TelemetryRouteObserver';
import { initializeTelemetry } from './observability/telemetry';

initializeTelemetry();

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <ThemeProvider theme={theme}>
            <CssBaseline />
            <BrowserRouter>
                <TelemetryErrorBoundary>
                    <TelemetryRouteObserver />
                    <AuthProvider>
                        <App />
                    </AuthProvider>
                </TelemetryErrorBoundary>
            </BrowserRouter>
        </ThemeProvider>
    </React.StrictMode>,
);

registerServiceWorker();
