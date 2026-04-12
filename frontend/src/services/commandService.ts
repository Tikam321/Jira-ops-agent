import { api } from './api';
import type { 
  CommandTemplate, 
  ExecutionJob, 
  PreviewResult, 
  ExecutionResult, 
  NlQueryRequest, 
  NlQueryResponse 
} from '../types';

export const commandService = {
  getAllCommands: () => api.get<CommandTemplate[]>('/api/v1/commands'),
  
  getCommand: (id: string) => api.get<CommandTemplate>(`/api/v1/commands/${id}`),
  
  preview: (commandId: string) => api.post<PreviewResult>(`/api/v1/preview/${commandId}`),
  
  execute: (commandId: string) => api.post<ExecutionResult>(`/api/v1/execute/${commandId}`),
  
  executeByAction: (actionType: string) => api.post<ExecutionResult>('/api/v1/execute-by-action', { actionType }),
  
  getJobs: () => api.get<ExecutionJob[]>('/api/v1/jobs'),
  
  nlQuery: (request: NlQueryRequest) => api.post<NlQueryResponse>('/api/v1/nl-query', request),
  
  mcpQuery: (request: NlQueryRequest) => api.post<NlQueryResponse>('/api/v1/mcp-query', request),
};