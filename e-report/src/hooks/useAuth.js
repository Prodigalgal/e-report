// src/hooks/useAuth.js
import { useContext } from 'react';
import { AuthContext } from '../contexts/AuthContext';

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === null) { // Corrected from undefined if createContext(null)
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};