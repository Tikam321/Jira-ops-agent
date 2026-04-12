import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

interface HeaderProps {
  children?: ReactNode;
}

export function Header({ children }: HeaderProps) {
  const { isAuthenticated, isLoading, login, logout } = useAuth();

  return (
    <header className="bg-white border-b border-gray-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center space-x-6">
            <Link to="/" className="text-xl font-bold text-gray-900">
              Jira Ops Agent
            </Link>
            <nav className="flex items-center space-x-2">
              <Link to="/" className="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium">
                Chat
              </Link>
              <Link to="/dashboard" className="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium">
                Dashboard
              </Link>
              <Link to="/commands" className="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium">
                Commands
              </Link>
            </nav>
          </div>
          <nav className="flex items-center space-x-4">
            {children}
            {isLoading ? (
              <div className="w-20 h-8 bg-gray-200 rounded animate-pulse" />
            ) : isAuthenticated ? (
              <button
                onClick={logout}
                className="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium"
              >
                Logout
              </button>
            ) : (
              <button
                onClick={login}
                className="bg-blue-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-blue-700"
              >
                Login
              </button>
            )}
          </nav>
        </div>
      </div>
    </header>
  );
}
