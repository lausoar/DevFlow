import { request } from './client';

// ============================================
// 自定义字段 API
// ============================================

export type CustomFieldType =
  | 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'DATE' | 'SELECT' | 'MULTI_SELECT' | 'USER' | 'CHECKBOX';

export interface CustomFieldDef {
  id: string;
  projectId: string;
  issueType: 'TASK' | 'BUG' | 'REQUIREMENT';
  fieldKey: string;
  name: string;
  type: CustomFieldType;
  options?: string | null;
  required: number;
  defaultValue?: string | null;
  sort: number;
  description?: string | null;
  enabled: number;
}

export const customFieldApi = {
  list: (projectId: string, issueType: 'TASK' | 'BUG' | 'REQUIREMENT') =>
    request<CustomFieldDef[]>('GET', `/projects/${projectId}/custom-fields`, undefined, { issueType }),

  create: (projectId: string, issueType: string, data: Partial<CustomFieldDef>) =>
    request<CustomFieldDef>('POST', `/projects/${projectId}/custom-fields`, data, { issueType }),

  update: (id: string, data: Partial<CustomFieldDef>) =>
    request<CustomFieldDef>('PUT', `/custom-fields/${id}`, data),

  remove: (id: string) =>
    request<void>('DELETE', `/custom-fields/${id}`),

  values: (issueType: string, issueId: string) =>
    request<Record<string, unknown>>('GET', `/${issueType}/${issueId}/custom-field-values`),

  saveValues: (issueType: string, issueId: string, values: Record<string, unknown>) =>
    request<void>('PUT', `/${issueType}/${issueId}/custom-field-values`, { values }),
};
