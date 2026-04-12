import { useState, useEffect } from 'react';
import { Card, Button } from '../components/common';
import { commandService } from '../services/commandService';
import { useAuth } from '../contexts/AuthContext';
import type { CommandTemplate, PreviewResult, ExecutionResult } from '../types';

export function Commands() {
  const { isAuthenticated, setShowLoginRequired } = useAuth();
  const [commands, setCommands] = useState<CommandTemplate[]>([]);
  const [selectedCommand, setSelectedCommand] = useState<CommandTemplate | null>(null);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [result, setResult] = useState<ExecutionResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [executing, setExecuting] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      setLoading(false);
      return;
    }
    commandService.getAllCommands()
      .then(res => setCommands(res.data))
      .catch((err: any) => {
        console.error('Failed to fetch commands:', err);
        if (err.response?.status === 401) {
          setShowLoginRequired(true);
        }
      })
      .finally(() => setLoading(false));
  }, [isAuthenticated, setShowLoginRequired]);

  const handlePreview = async (commandId: string) => {
    if (!isAuthenticated) {
      setShowLoginRequired(true);
      return;
    }
    try {
      const res = await commandService.preview(commandId);
      setPreview(res.data);
      setResult(null);
    } catch (error: any) {
      console.error('Failed to preview:', error);
      if (error.response?.status === 401) {
        setShowLoginRequired(true);
      }
    }
  };

  const handleExecute = async (commandId: string) => {
    if (!isAuthenticated) {
      setShowLoginRequired(true);
      return;
    }
    setExecuting(true);
    try {
      const res = await commandService.execute(commandId);
      setResult(res.data);
      setPreview(null);
    } catch (error: any) {
      console.error('Failed to execute:', error);
      if (error.response?.status === 401) {
        setShowLoginRequired(true);
      }
    } finally {
      setExecuting(false);
    }
  };

  if (loading) {
    return <div className="text-center py-8">Loading...</div>;
  }

  if (!isAuthenticated) {
    return (
      <div className="text-center py-8">
        <h2 className="text-xl font-semibold text-gray-900 mb-2">Login Required</h2>
        <p className="text-gray-600 mb-4">Please login to access commands.</p>
        <Button onClick={() => setShowLoginRequired(true)}>Login with Jira</Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Commands</h1>
        <p className="text-gray-600">Execute Jira operations using command templates</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Available Commands</h2>
          {commands.map((cmd) => (
            <Card key={cmd.id} className={`cursor-pointer hover:border-blue-500 ${selectedCommand?.id === cmd.id ? 'border-blue-500' : ''}`}>
              <div onClick={() => setSelectedCommand(cmd)} className="cursor-pointer">
                <div className="flex justify-between items-start">
                  <div>
                    <h3 className="font-medium text-gray-900">{cmd.name}</h3>
                    <p className="text-sm text-gray-500 mt-1">{cmd.description}</p>
                  </div>
                  <span className="px-2 py-1 bg-gray-100 rounded text-xs font-medium">
                    {cmd.actionType}
                  </span>
                </div>
                {cmd.parameters && Array.isArray(cmd.parameters) && cmd.parameters.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {cmd.parameters.map((param: { name: string; required: boolean }) => (
                      <span key={param.name} className="text-xs bg-blue-50 text-blue-700 px-2 py-1 rounded">
                        {param.name}{param.required ? '*' : ''}
                      </span>
                    ))}
                  </div>
                )}
              </div>
              {selectedCommand?.id === cmd.id && (
                <div className="mt-4 flex gap-2">
                  <Button onClick={() => handlePreview(cmd.id)} variant="outline" size="sm">
                    Preview
                  </Button>
                  <Button onClick={() => handleExecute(cmd.id)} disabled={executing} size="sm">
                    {executing ? 'Executing...' : 'Execute'}
                  </Button>
                </div>
              )}
            </Card>
          ))}
          {commands.length === 0 && (
            <p className="text-gray-500">No commands available</p>
          )}
        </div>

        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Result</h2>
          {preview && (
            <Card>
              <h3 className="font-medium text-gray-900 mb-4">Preview</h3>
              <div className="text-sm text-gray-600 mb-4">
                {preview.jql && <p><strong>JQL:</strong> {preview.jql}</p>}
                {preview.totalIssues !== undefined && <p><strong>Issues:</strong> {preview.totalIssues}</p>}
              </div>
              {preview.changes && preview.changes.length > 0 && (
                <div className="space-y-2">
                  {preview.changes.map((change, idx) => (
                    <div key={idx} className="p-3 bg-gray-50 rounded">
                      <div className="font-medium text-sm">{change.field}</div>
                      <div className="text-xs text-gray-500">
                        {change.currentValue || 'empty'} → {change.newValue}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          )}
          {result && (
            <Card>
              <div className={`p-4 rounded ${result.status === 'COMPLETED' ? 'bg-green-50' : 'bg-red-50'}`}>
                <div className={`font-medium ${result.status === 'COMPLETED' ? 'text-green-800' : 'text-red-800'}`}>
                  {result.status || 'Done'}
                </div>
                <p className="text-sm mt-1">{result.message}</p>
                {result.totalIssues !== undefined && (
                  <p className="text-xs text-gray-500 mt-2">
                    Total: {result.totalIssues} | Success: {result.successCount} | Failed: {result.failedCount}
                  </p>
                )}
              </div>
            </Card>
          )}
          {!preview && !result && (
            <Card>
              <p className="text-gray-500 text-center">Select a command and preview or execute</p>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
