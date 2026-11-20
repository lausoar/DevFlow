import { request } from './client';

// ============================================
// 设计稿 API（平台只存指针与评审状态，画布在 Figma / 蓝湖 / MasterGo）
// ============================================

export type DesignProvider = 'FIGMA' | 'LANHU' | 'MASTERGO' | 'OTHER';

export type DesignStatus = 'DESIGNING' | 'IN_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED';

export interface DesignAsset {
  id: string;
  projectId: string;
  requirementId?: string;
  taskId?: string;
  provider?: DesignProvider;
  title: string;
  fileKey?: string;
  nodeId?: string;
  embedUrl?: string;
  thumbnailUrl?: string;
  isPrimary?: number;
  status: DesignStatus;
  currentVersion?: number;
  sort?: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DesignAssetVersion {
  id: string;
  assetId: string;
  version: number;
  snapshotUrl?: string;
  note?: string;
  editorId?: string;
  editorName?: string;
  createdAt: string;
}

export interface DesignAssetListResponse {
  assets: DesignAsset[];
  stats: {
    total: number;
    approved: number;
    inReview: number;
    changesRequested: number;
    requirementsWithDesign: number;
    requirementTotal: number;
    coverageRate: number;
  };
}

export const designApi = {
  /** 需求下的设计稿 */
  listByRequirement: (requirementId: string) =>
    request<DesignAsset[]>('GET', `/requirements/${requirementId}/designs`),

  /** 项目维度：总览与覆盖率 */
  listByProject: (projectId: string, status?: string) =>
    request<DesignAssetListResponse>('GET', `/projects/${projectId}/designs`, undefined,
      status ? { status } : undefined),

  /** 关联设计稿 */
  create: (requirementId: string, data: Partial<DesignAsset>) =>
    request<DesignAsset>('POST', `/requirements/${requirementId}/designs`, data),

  /** 更新元信息（改状态走 changeStatus） */
  update: (id: string, data: Partial<DesignAsset>) =>
    request<DesignAsset>('PUT', `/designs/${id}`, data),

  /** 评审状态流转 */
  changeStatus: (id: string, status: DesignStatus) =>
    request<DesignAsset>('PUT', `/designs/${id}/status`, { status }),

  /** 提交新版本 */
  addVersion: (id: string, data: { snapshotUrl?: string; note?: string }) =>
    request<DesignAssetVersion>('POST', `/designs/${id}/versions`, data),

  /** 版本历史 */
  versions: (id: string) =>
    request<DesignAssetVersion[]>('GET', `/designs/${id}/versions`),

  /** 删除 */
  delete: (id: string) => request<void>('DELETE', `/designs/${id}`),
};

/** 评审状态 → 中文与颜色（列表与详情共用一套） */
export const DESIGN_STATUS_CONFIG: Record<DesignStatus, { label: string; className: string }> = {
  DESIGNING: { label: '设计中', className: 'bg-muted text-muted-foreground' },
  IN_REVIEW: { label: '待评审', className: 'bg-warning/15 text-warning' },
  CHANGES_REQUESTED: { label: '待修改', className: 'bg-danger/15 text-danger' },
  APPROVED: { label: '已通过', className: 'bg-success/15 text-success' },
};

/** 允许的下一步状态：与后端 canTransition 保持一致 */
export function nextDesignStatuses(status: DesignStatus): DesignStatus[] {
  switch (status) {
    case 'DESIGNING':
      return ['IN_REVIEW'];
    case 'IN_REVIEW':
      return ['APPROVED', 'CHANGES_REQUESTED'];
    case 'CHANGES_REQUESTED':
      return ['IN_REVIEW', 'DESIGNING'];
    case 'APPROVED':
      return ['DESIGNING'];
    default:
      return [];
  }
}
