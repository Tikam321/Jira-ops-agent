const isProduction = import.meta.env.PROD;
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
const frontendUrl = import.meta.env.VITE_FRONTEND_URL || 'http://localhost:5173';

export const config = {
  apiBaseUrl: apiBaseUrl,
  frontendUrl: frontendUrl,
  // For production, use relative path for OAuth (nginx will proxy)
  // For local dev, use full backend URL
  authUrl: isProduction 
    ? `/oauth2/authorization/jira` 
    : `${apiBaseUrl}/oauth2/authorization/jira`,
};
