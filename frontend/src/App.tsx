import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { Layout } from './components/layout';
import { ProtectedRoute } from './components/common';
import { Login, Dashboard, Commands, Chat } from './pages';

function ProtectedRoutes() {
  const { isAuthenticated, isLoading } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={
        !isLoading && isAuthenticated ? <Navigate to="/dashboard" replace /> : <Login />
      } />
      <Route path="/" element={
        <ProtectedRoute>
          <Layout />
        </ProtectedRoute>
      }>
        <Route index element={<Chat />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="commands" element={<Commands />} />
      </Route>
    </Routes>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <ProtectedRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
