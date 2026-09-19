import { apiClient } from './client';

// Pending app-install approvals (agent v1):
//   GET  /rest/private/agent/v1/approvals?deviceId=   -> PendingApproval[]
//   POST /rest/private/agent/v1/approvals/{id}/approve -> queues app.approveInstall (un-suspend)
//   POST /rest/private/agent/v1/approvals/{id}/deny    -> queues app.uninstall (silent removal)
// A pending row is created when the agent detects a non-MDM-initiated install (Play Store /
// sideload), immediately suspends it, and reports it — never for an install the console itself
// pushed via app.install.

export interface PendingApproval {
  id: number;
  deviceNumber: string;
  packageName: string;
  status: 'pending' | 'approved' | 'denied';
  createdAt: number;
  resolvedAt?: number | null;
  commandId?: number | null;
}

export async function listApprovals(
  deviceId?: string,
  signal?: AbortSignal,
): Promise<PendingApproval[]> {
  const q = deviceId ? `?deviceId=${encodeURIComponent(deviceId)}` : '';
  return apiClient.get<PendingApproval[]>(`/private/agent/v1/approvals${q}`, signal);
}

export async function approveInstall(id: number | string): Promise<PendingApproval> {
  return apiClient.post<PendingApproval>(`/private/agent/v1/approvals/${id}/approve`, {});
}

export async function denyInstall(id: number | string): Promise<PendingApproval> {
  return apiClient.post<PendingApproval>(`/private/agent/v1/approvals/${id}/deny`, {});
}
