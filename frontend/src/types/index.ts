export interface CommandTemplate {
  id: string;
  name: string;
  description: string;
  actionType: string;
  parameters: Parameter[];
}

export interface Parameter {
  name: string;
  type: string;
  required: boolean;
  description: string;
}

export interface ExecutionJob {
  id: string;
  commandId: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  result?: string;
  error?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PreviewChange {
  field: string;
  currentValue: string;
  newValue: string;
}

export interface PreviewResult {
  jql?: string;
  totalIssues?: number;
  changes: PreviewChange[];
  summary: string;
}

export interface ExecutionResult {
  jobId: number;
  status: string;
  totalIssues: number;
  successCount: number;
  failedCount: number;
  message: string;
}

export interface NlQueryRequest {
  query: string;
}

export interface NlQueryResponse {
  originalQuery: string;
  generatedJql: string;
  actionType: string;
  issues: unknown[];
  confidence: number;
  message: string;
  totalIssues?: number;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_at: string;
  refresh_token?: string;
}