import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { backendApi, api } from '../services/api';
import { config } from '../config';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: { name: string; email: string } | null;
  login: () => void;
  logout: () => Promise<void>;
  checkAuth: () => Promise<boolean>;
  showLoginRequired: boolean;
  setShowLoginRequired: (show: boolean) => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState<{ name: string; email: string } | null>(null);
  const [showLoginRequired, setShowLoginRequired] = useState(false);

  const checkAuth = useCallback(async (): Promise<boolean> => {
    try {
      console.log('Checking auth...');
      console.log('Backend URL:', config.apiBaseUrl);
      const response = await backendApi.get('/v1/auth/me');
      console.log('Auth check response:', response);
      if (response.status === 200) {
        setIsAuthenticated(true);
        setUser(response.data);
        return true;
      }
    } catch (error: any) {
      console.log('Auth check error:', error);
      console.log('Auth check error status:', error.response?.status);
      if (error.response?.status === 401) {
        setIsAuthenticated(false);
        setUser(null);
      }
    }
    setIsAuthenticated(false);
    setUser(null);
    return false;
  }, []);

  const login = () => {
    window.location.href = config.authUrl;
  };

  const logout = async () => {
    try {
      await api.post('/logout');
    } catch (error) {
      console.error('Logout error:', error);
    }
    setIsAuthenticated(false);
    setUser(null);
    window.location.href = '/login';
  };

  useEffect(() => {
    const initAuth = async () => {
      setIsLoading(true);
      
      // Check if we just returned from OAuth redirect
      const urlParams = new URLSearchParams(window.location.search);
      const oauthSuccess = urlParams.has('code') || urlParams.has('state');
      
      await checkAuth();
      
      // If we just came back from OAuth, reload to clear URL params and refresh state
      if (oauthSuccess) {
        window.history.replaceState({}, '', window.location.pathname);
      }
      
      setIsLoading(false);
    };
    
    initAuth();
    
    const handleFocus = () => {
      checkAuth();
    };
    
    window.addEventListener('focus', handleFocus);
    
    return () => {
      window.removeEventListener('focus', handleFocus);
    };
  }, [checkAuth]);

  return (
    <AuthContext.Provider value={{ 
      isAuthenticated, 
      isLoading,
      user, 
      login, 
      logout, 
      checkAuth,
      showLoginRequired,
      setShowLoginRequired 
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
