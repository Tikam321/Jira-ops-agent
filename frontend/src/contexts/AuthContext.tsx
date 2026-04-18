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
         if (window.location.pathname.includes('/login')) {
                 return false; // Skip auth check on login page                                                                                                            ▼ Modified Files
         }
      console.log('Checking auth...');
      // config.apiBaseUrl already includes /api/v1
      console.log("calling the authme api");
      
      const response = await backendApi.get('/auth/me');
      console.log('Auth check response:', response);
      if (response.status === 200) {
        setIsAuthenticated(true);
        setUser(response.data);
        return true;
      }
    } catch (error: any) {
      console.log('Auth check error:', error);
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
    // Clear session and redirect to frontend login page
    window.location.href = config.frontendUrl + '/login?logout=true';
  };

  useEffect(() => {
    const initAuth = async () => {
      setIsLoading(true);
      
      const urlParams = new URLSearchParams(window.location.search);
      const hasCode = urlParams.has('code');
      const hasState = urlParams.has('state');
      
      console.log('OAuth params detected:', { hasCode, hasState });
      
      if (hasCode || hasState) {
        console.log('OAuth callback detected, waiting for backend to process...');
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
      
      await checkAuth();
      
      if (hasCode || hasState) {
        window.history.replaceState({}, '', window.location.pathname);
      }
      
      setIsLoading(false);
    };
    
    initAuth();
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
