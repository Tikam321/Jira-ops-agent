import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Card, Button } from '../components/common';
import { commandService } from '../services/commandService';
import { useAuth } from '../contexts/AuthContext';
import type { CommandTemplate, ExecutionJob } from '../types';

export function Dashboard() {
  const { isAuthenticated, setShowLoginRequired } = useAuth();
  const [commands, setCommands] = useState<CommandTemplate[]>([]);
  const [jobs, setJobs] = useState<ExecutionJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setError(null);
        const [commandsRes, jobsRes] = await Promise.all([
          commandService.getAllCommands(),
          commandService.getJobs(),
        ]);
        setCommands(commandsRes.data);
        setJobs(jobsRes.data);
      } catch (err: any) {
        console.error('Failed to fetch data:', err);
        if (err.response?.status === 401) {
          setShowLoginRequired(true);
        }
        setError('Failed to load dashboard data. Please login to continue.');
      } finally {
        setLoading(false);
      }
    };
    
    if (isAuthenticated) {
      fetchData();
    } else {
      setLoading(false);
    }
  }, [isAuthenticated, setShowLoginRequired]);

  const recentJobs = jobs.slice(0, 5);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">Loading dashboard...</div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="text-center py-12">
        <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-blue-100 mb-4">
          <svg className="h-8 w-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        </div>
        <h2 className="text-xl font-semibold text-gray-900 mb-2">Welcome to Jira Ops Agent</h2>
        <p className="text-gray-600 mb-6">Please login to view your dashboard and manage Jira operations.</p>
        <Button onClick={() => setShowLoginRequired(true)}>Login with Jira</Button>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4">
        <div className="text-red-800 font-medium">Error</div>
        <div className="text-red-600 text-sm mt-1">{error}</div>
        <div className="mt-3">
          <Button onClick={() => setShowLoginRequired(true)}>Login</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-600">Welcome to Jira Ops Agent</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card>
          <div className="text-center">
            <div className="text-3xl font-bold text-blue-600">{commands.length}</div>
            <div className="text-gray-600">Available Commands</div>
          </div>
        </Card>
        <Card>
          <div className="text-center">
            <div className="text-3xl font-bold text-green-600">{jobs.filter(j => j.status === 'COMPLETED').length}</div>
            <div className="text-gray-600">Completed Jobs</div>
          </div>
        </Card>
        <Card>
          <div className="text-center">
            <div className="text-3xl font-bold text-yellow-600">{jobs.filter(j => j.status === 'PENDING' || j.status === 'RUNNING').length}</div>
            <div className="text-gray-600">Active Jobs</div>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Commands</h2>
            <Link to="/commands">
              <Button variant="outline" size="sm">View All</Button>
            </Link>
          </div>
          <div className="space-y-2">
            {commands.slice(0, 3).map((cmd) => (
              <div key={cmd.id} className="p-3 bg-gray-50 rounded-md">
                <div className="font-medium text-gray-900">{cmd.name}</div>
                <div className="text-sm text-gray-500">{cmd.description}</div>
              </div>
            ))}
            {commands.length === 0 && (
              <p className="text-gray-500">No commands available</p>
            )}
          </div>
        </Card>

        <Card>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Recent Jobs</h2>
          </div>
          <div className="space-y-2">
            {recentJobs.map((job) => (
              <div key={job.id} className="p-3 bg-gray-50 rounded-md flex justify-between items-center">
                <div>
                  <div className="font-medium text-gray-900">{job.commandId}</div>
                  <div className="text-sm text-gray-500">{new Date(job.createdAt).toLocaleString()}</div>
                </div>
                <span className={`px-2 py-1 rounded text-xs font-medium ${
                  job.status === 'COMPLETED' ? 'bg-green-100 text-green-800' :
                  job.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                  job.status === 'RUNNING' ? 'bg-blue-100 text-blue-800' :
                  'bg-yellow-100 text-yellow-800'
                }`}>
                  {job.status}
                </span>
              </div>
            ))}
            {jobs.length === 0 && (
              <p className="text-gray-500">No jobs yet</p>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}
