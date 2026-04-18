import axios from 'axios';
import { config } from '../config';

// Both 'api' and 'backendApi' now use the same baseURL from config
export const api = axios.create({
  baseURL: config.apiBaseUrl,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
  maxRedirects: 0,
  validateStatus: (status) => status >= 200 && status < 400,
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !window.location.pathname.includes('/login')) {
      // In production, Nginx handles proxying to backend for OAuth
      // For manual logout/unauthorized, we redirect to /login
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const backendApi = axios.create({
  baseURL: config.apiBaseUrl,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
  maxRedirects: 0,
  validateStatus: (status) => status >= 200 && status < 400,
});

backendApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !window.location.pathname.includes('/login')) {
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
