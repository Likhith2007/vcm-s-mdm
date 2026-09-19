import { apiClient } from './client';

// Group Policies — a policy attached to a device Group (see server: GroupPolicyResource,
// separate from the legacy /private/groups CRUD-only resource):
//   GET    /rest/private/group-policies       -> GroupPolicyView[] (self-seeds the two defaults)
//   PUT    /rest/private/group-policies       body: GroupPolicySaveRequest -> GroupPolicyView
//   DELETE /rest/private/group-policies/{id}
// Enforcement of a 'blockScheduled' policy's time window runs server-side (GroupPolicyScheduler,
// once a minute) — `blockedNow` below is that scheduler's own authoritative tracked state, not
// something the console computes from the time window itself.

export type PolicyType = 'allowAll' | 'blockScheduled';

export interface GroupPolicyView {
  id: number;
  name: string;
  policyType: PolicyType;
  packages: string[];
  startTime?: string | null;
  endTime?: string | null;
  blockedNow: boolean;
  deviceCount: number;
  deviceIds: number[];
}

export interface GroupPolicySaveRequest {
  id?: number;
  name: string;
  policyType: PolicyType;
  packages: string[];
  startTime?: string | null;
  endTime?: string | null;
  deviceIds: number[];
}

export async function listGroupPolicies(): Promise<GroupPolicyView[]> {
  return apiClient.get<GroupPolicyView[]>('/private/group-policies');
}

/** Create (id absent) or update (id present) a group's name, policy, and device membership. */
export async function saveGroupPolicy(req: GroupPolicySaveRequest): Promise<GroupPolicyView> {
  return apiClient.put<GroupPolicyView>('/private/group-policies', req);
}

export async function deleteGroupPolicy(id: number): Promise<void> {
  await apiClient.del(`/private/group-policies/${id}`);
}
