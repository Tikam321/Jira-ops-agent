import { api } from './api';
import type { 
  CommandTemplate, 
  ExecutionJob, 
  PreviewResult, 
  ExecutionResult, 
  NlQueryRequest, 
  NlQueryResponse 
} from '../types';

// NOTE: api instance already has /api/v1 as baseURL (via config.apiBaseUrl)
export const commandService = {
  getAllCommands: () => api.get<CommandTemplate[]>('/commands'),
  
  getCommand: (id: string) => api.get<CommandTemplate>(`/commands/${id}`),
  
  preview: (commandId: string) => api.post<PreviewResult>(`/preview/${commandId}`),
  
  execute: (commandId: string) => api.post<ExecutionResult>(`/execute/${commandId}`),
  
  executeByAction: (actionType: string) => api.post<ExecutionResult>('/execute-by-action', { actionType }),
  
  getJobs: () => api.get<ExecutionJob[]>('/jobs'),
  
  nlQuery: (request: NlQueryRequest) => api.post<NlQueryResponse>('/nl-query', request),
  
  mcpQuery: (request: NlQueryRequest) => api.post<NlQueryResponse>('/mcp-query', request),
};