import { useEffect, useState } from 'react';
import { config } from '../config';

export function Login() {
  const [error, setError] = useState<string | null>(null);
  const [logout, setLogout] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const errorParam = params.get('error');
    const logoutParam = params.get('logout');

    if (errorParam) {
      setError('Login cancelled or authentication failed. Please try again.');
      // Clear the URL parameter
      window.history.replaceState({}, '', '/login');
    }

    if (logoutParam) {
      setLogout(true);
      window.history.replaceState({}, '', '/login');
    }
  }, []);

  const handleLogin = () => {
    window.location.href = config.authUrl;
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full p-8 bg-white rounded-lg shadow-lg">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Jira Ops Agent</h1>
          <p className="text-gray-600 mt-2">Sign in to manage your Jira operations</p>
        </div>

        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-center">
              <svg className="w-5 h-5 text-red-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="text-red-700 text-sm">{error}</p>
            </div>
          </div>
        )}

        {logout && (
          <div className="mb-4 p-4 bg-green-50 border border-green-200 rounded-lg">
            <div className="flex items-center">
              <svg className="w-5 h-5 text-green-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="text-green-700 text-sm">You have been logged out successfully.</p>
            </div>
          </div>
        )}
        
        <button
          onClick={handleLogin}
          className="w-full flex items-center justify-center px-4 py-3 border border-gray-300 rounded-md shadow-sm bg-white text-gray-700 font-medium hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
        >
          <svg className="w-5 h-5 mr-2" viewBox="0 0 24 24" fill="currentColor">
            <path d="M11.571 11.513H0l5.786 5.786 5.785-5.786zm5.786 0l-5.786 5.786 5.786 5.786L23.143 17.3l-5.786-5.786zM11.571.93L5.786 6.716 0 12.5l5.786 5.786L17.357.93 11.571.93z"/>
          </svg>
          Sign in with Jira
        </button>
        
        <p className="mt-4 text-center text-sm text-gray-500">
          You will be redirected to Atlassian to authenticate
        </p>
      </div>
    </div>
  );
}
