const isProduction = import.meta.env.PROD;

// Using the exact Backend URL provided: https://jira-ops-agent-vom2.onrender.com
const apiBaseUrl = isProduction
    ? 'https://jira-ops-agent-vom2.onrender.com/v1'
    : 'http://localhost:8081/v1';

const frontendUrl = isProduction
    ? 'https://jira-ops-frontend-latest.onrender.com'
    : 'http://localhost:5173';

export const config = {
  apiBaseUrl: apiBaseUrl,
  frontendUrl: frontendUrl,
  authUrl: isProduction
    ? `https://jira-ops-agent-vom2.onrender.com/oauth2/authorization/jira`
    : `http://localhost:8081/oauth2/authorization/jira`,
};
