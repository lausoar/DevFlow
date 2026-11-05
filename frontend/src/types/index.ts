// ============================================
// DevFlow 全局类型定义
// ============================================

// 测试用例相关类型
export interface TestCaseStep {
  action: string;
  expected?: string;
}

export interface TestCase {
  id: string;
  projectId: string;
  title: string;
  precondition?: string;
  /** 步骤 JSON 字符串 [{action, expected}] */
  steps?: string;
  expectedResult?: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  requirementId?: string;
  creatorId: string;
  createdAt: string;
  updatedAt: string;
}

/** 用例视图（列表项，附最新执行信息） */
export interface TestCaseView extends TestCase {
  latestResult: 'PASS' | 'FAIL' | 'BLOCKED' | 'SKIP' | 'UNEXECUTED';
  executionCount: number;
  latestExecutedAt?: string;
  latestExecutorName?: string;
}

export interface TestCaseStats {
  total: number;
  passed: number;
  failed: number;
  blocked: number;
  skipped: number;
  unexecuted: number;
  passRate: number;
}

export interface TestExecution {
  id: string;
  caseId: string;
  projectId: string;
  result: 'PASS' | 'FAIL' | 'BLOCKED' | 'SKIP';
  note?: string;
  bugId?: string;
  bugTitle?: string;
  executorId: string;
  executorName?: string;
  createdAt: string;
}

// 用户相关类型
export interface User {
  id: string;
  username: string;
  /** 真实姓名（展示优先，空则回退用户名） */
  realName?: string;
  email: string;
  avatar?: string;
  phone?: string;
  role: UserRole;
  status: UserStatus;
  enterpriseId?: string;
  enterpriseName?: string;
  departmentId?: string;
  createdAt: string;
  updatedAt: string;
}

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'MANAGER' | 'MEMBER';
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

// 认证相关类型
export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  /** 真实姓名（必填，展示用） */
  realName: string;
  email: string;
  password: string;
  confirmPassword: string;
  enterpriseName?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

// 企业相关类型
export interface Enterprise {
  id: string;
  name: string;
  logo?: string;
  /** 企业微信群机器人 Webhook（空 = 不推送） */
  wecomWebhook?: string;
  inviteCode: string;
  ownerId: string;
  memberCount: number;
  createdAt: string;
}

// 部门相关类型
export interface Department {
  id: string;
  name: string;
  enterpriseId: string;
  parentId?: string;
  leaderId?: string;
  memberCount: number;
  sort: number;
  children?: Department[];
}

// 项目相关类型
export interface Project {
  id: string;
  name: string;
  projectKey: string;
  description?: string;
  icon?: string;
  enterpriseId: string;
  ownerId: string;
  /** 项目邀请码（分享链接加入项目） */
  inviteCode?: string;
  /** Git 仓库地址 */
  gitRepoUrl?: string;
  /** Git Webhook 密钥（拼 webhook 地址用） */
  gitWebhookSecret?: string;
  status: ProjectStatus;
  memberCount?: number;
  createdAt: string;
  updatedAt: string;
}

/** 需求变更记录 */
export interface RequirementChangeLog {
  id: string;
  requirementId: string;
  projectId: string;
  field: string;
  oldValue?: string;
  newValue?: string;
  editorId: string;
  editorName?: string;
  createdAt: string;
}

/** Git 提交记录 */
export interface GitCommit {
  id: string;
  projectId: string;
  sha: string;
  message?: string;
  authorName?: string;
  webUrl?: string;
  pushedAt: string;
}

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED' | 'PLANNING';

// 项目成员相关类型
export interface ProjectMember {
  id: string;
  projectId: string;
  userId: string;
  role: ProjectMemberRole;
  joinedAt: string;
  // JOIN 填充的用户信息
  username?: string;
  /** 真实姓名（展示优先） */
  realName?: string;
  email?: string;
  avatar?: string;
}

export type ProjectMemberRole = 'OWNER' | 'MANAGER' | 'TESTER' | 'MEMBER';

// 任务相关类型
export interface Task {
  id: string;
  title: string;
  description?: string;
  projectId: string;
  sprintId?: string;
  assigneeId?: string;
  reporterId: string;
  status: TaskStatus;
  priority: Priority;
  storyPoints?: number;
  dueDate?: string;
  tags: string[];
  checklist: ChecklistItem[];
  createdAt: string;
  updatedAt: string;
}

export type TaskStatus = 'TODO' | 'DOING' | 'TESTING' | 'DONE';
export type Priority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface ChecklistItem {
  id: string;
  title: string;
  completed: boolean;
  sort: number;
}

// Sprint 相关类型
export interface Sprint {
  id: string;
  name: string;
  projectId: string;
  goal?: string;
  status: SprintStatus;
  startDate: string;
  endDate: string;
  taskIds: string[];
}

export type SprintStatus = 'PLANNING' | 'ACTIVE' | 'COMPLETED';

// Bug 相关类型
export interface Bug {
  id: string;
  title: string;
  description?: string;
  projectId: string;
  assigneeId?: string;
  reporterId: string;
  status: BugStatus;
  priority: Priority;
  severity: BugSeverity;
  environment?: string;
  /** 截图：后端存 JSON 字符串（兼容历史 string[] 形态） */
  screenshots?: string | string[];
  /** 报告人类型: USER / GUEST（游客提交） */
  reporterType?: 'USER' | 'GUEST';
  /** 游客 ID（reporterType=GUEST 时） */
  guestId?: string;
  /** 游客昵称 */
  guestNickname?: string;
  /** 游客提交待确认: 0-否 1-待确认（确认前不进入正常流转） */
  pending?: number;
  createdAt: string;
  updatedAt: string;
}

export type BugStatus = 'NEW' | 'ASSIGNED' | 'DOING' | 'RESOLVED' | 'REOPENED' | 'CLOSED';
export type BugSeverity = 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'TRIVIAL';

// 企业邀请成员响应（初始密码明文仅此一次返回）
export interface InviteMemberResponse {
  user: User;
  /** 已注册用户直接加入时为 null（保持原密码） */
  initialPassword: string | null;
}

// 游客分享相关类型
export interface GuestLink {
  id: string;
  /** 游客昵称（首次提交后回填） */
  nickname?: string;
  email?: string;
  /** 0-有效 1-已撤销 */
  revoked: number;
  /** 空 = 长期有效 */
  expiresAt?: string;
  createdAt: string;
}

export type FeedbackType = 'SUGGESTION' | 'IMPROVEMENT' | 'OTHER';
export type FeedbackStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED' | 'CONVERTED';

export interface ProjectFeedback {
  id: string;
  projectId: string;
  type: FeedbackType;
  title: string;
  content?: string;
  submitterType: 'USER' | 'GUEST';
  submitterUserId?: string;
  guestId?: string;
  guestNickname?: string;
  status: FeedbackStatus;
  /** 转化后的 Bug ID（status=CONVERTED 时） */
  convertedBugId?: string;
  createdAt: string;
  updatedAt?: string;
}

/** 游客页项目脱敏信息 */
export interface GuestProjectInfo {
  project: { name: string; description?: string; projectKey?: string };
  /** 最新已通过需求（仅标题/状态/时间） */
  requirements: { title: string; status: string; createdAt: string }[];
}

/** 游客查看自己的提交记录 */
export interface GuestSubmissions {
  bugs: { title: string; status: BugStatus; pending: number; createdAt: string }[];
  feedbacks: { title: string; status: FeedbackStatus; createdAt: string }[];
}

// 文档相关类型
export interface Document {
  id: string;
  title: string;
  content?: string;
  projectId?: string;
  authorId: string;
  type: DocType;
  parentId?: string;
  sort: number;
  isPublished: boolean;
  createdAt: string;
  updatedAt: string;
}

export type DocType = 'DOC' | 'WIKI' | 'MEETING' | 'REPORT';

// 聊天相关类型
export interface ChatMessage {
  id: string;
  channelId: string;
  senderId: string;
  senderName: string;
  senderAvatar?: string;
  content: string;
  type: MessageType;
  replyToId?: string;
  isEdited: boolean;
  isDeleted: boolean;
  mentions: string[];
  attachments: Attachment[];
  createdAt: string;
}

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'CODE' | 'SYSTEM';

export interface ChatChannel {
  id: string;
  name: string;
  type: ChannelType;
  projectId?: string;
  departmentId?: string;
  memberIds: string[];
  lastMessage?: ChatMessage;
  unreadCount: number;
}

export type ChannelType = 'PROJECT' | 'DEPARTMENT' | 'ENTERPRISE' | 'PRIVATE';

// 通知相关类型
export interface Notification {
  id: string;
  userId: string;
  type: NotificationType;
  title: string;
  content: string;
  isRead: boolean;
  actionUrl?: string;
  createdAt: string;
}

export type NotificationType =
  | 'COMMENT'
  | 'MENTION'
  | 'TASK_UPDATE'
  | 'BUG_UPDATE'
  | 'SPRINT_UPDATE'
  | 'SYSTEM'
  | 'AI';

// 文件相关类型
export interface FileItem {
  id: string;
  name: string;
  size: number;
  mimeType: string;
  url: string;
  thumbnailUrl?: string;
  uploaderId: string;
  projectId?: string;
  tags: string[];
  version: number;
  createdAt: string;
}

export interface Attachment {
  id: string;
  name: string;
  url: string;
  size: number;
  mimeType: string;
}

// API 通用类型
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  sortField?: string;
  sortOrder?: 'ASC' | 'DESC';
}

// ============================================
// 需求相关类型
// ============================================

export interface Requirement {
  id: string;
  title: string;
  description?: string;
  projectId: string;
  projectName?: string;
  requesterId: string;
  requesterName?: string;
  requesterAvatar?: string;
  approverId?: string;
  approverName?: string;
  status: RequirementStatus;
  priority: Priority;
  source?: RequirementSource;
  category?: RequirementCategory;
  estimatedHours?: number;
  approvedAt?: string;
  rejectedAt?: string;
  rejectReason?: string;
  parentId?: string;
  sort: number;
  createdAt: string;
  updatedAt: string;
  linkedTasks?: LinkedTask[];
  approvalRecords?: ApprovalRecord[];
}

export type RequirementStatus = 'OPEN' | 'COMPLETED' | 'CLOSED';
export type RequirementSource = 'CUSTOMER' | 'INTERNAL' | 'MARKET' | 'TECHNICAL';
export type RequirementCategory = 'FEATURE' | 'IMPROVEMENT' | 'BUGFIX' | 'OTHER';

export interface LinkedTask {
  taskId: string;
  taskTitle: string;
  taskStatus: TaskStatus;
  assigneeName?: string;
}

export interface ApprovalRecord {
  id: string;
  approverId: string;
  approverName?: string;
  approverAvatar?: string;
  approvalLevel: number;
  action: ApprovalAction;
  comment?: string;
  createdAt: string;
}

export type ApprovalAction = 'PENDING' | 'APPROVE' | 'REJECT';

export interface CreateRequirementRequest {
  title: string;
  description?: string;
  priority?: Priority;
  source?: RequirementSource;
  category?: RequirementCategory;
  estimatedHours?: number;
  parentId?: string;
}

export interface UpdateRequirementRequest {
  title?: string;
  description?: string;
  priority?: Priority;
  source?: RequirementSource;
  category?: RequirementCategory;
  estimatedHours?: number;
  status?: RequirementStatus;
}

export interface ApprovalRequest {
  action: 'APPROVE' | 'REJECT';
  comment?: string;
}

export interface RequirementStats {
  total: number;
  open: number;
  completed: number;
  closed: number;
  priorityDistribution: Record<string, number>;
  sourceDistribution: Record<string, number>;
  categoryDistribution: Record<string, number>;
}

// 任务评论相关类型
export interface TaskComment {
  id: string;
  taskId: string;
  userId: string;
  content: string;
  replyToId?: string;
  deleted: number;
  createdAt: string;
  updatedAt: string;
  // JOIN 填充的用户信息（前端补充）
  username?: string;
  avatar?: string;
}

// ============================================
// 统计报表相关类型
// ============================================

/** 企业级统计 */
export interface EnterpriseStats {
  projectOverview: {
    totalProjects: number;
    activeProjects: number;
    planningProjects: number;
    archivedProjects: number;
    totalMembers: number;
  };
  taskOverview: {
    totalTasks: number;
    completedTasks: number;
    inProgressTasks: number;
    todoTasks: number;
    overdueTasks: number;
    completionRate: number;
    overdueRate: number;
  };
  bugOverview: {
    totalBugs: number;
    openBugs: number;
    resolvedBugs: number;
    criticalBugs: number;
    resolutionRate: number;
  };
  requirementOverview: {
    totalRequirements: number;
    openRequirements: number;
    completedRequirements: number;
    closedRequirements: number;
    completionRate: number;
  };
  projectProgressList: Array<{
    projectId: string;
    projectName: string;
    projectKey: string;
    status: string;
    totalTasks: number;
    completedTasks: number;
    totalBugs: number;
    openBugs: number;
    progress: number;
    memberCount: number;
  }>;
  taskTrend: Array<{
    date: string;
    created: number;
    completed: number;
  }>;
  taskStatusDistribution: Record<string, number>;
  bugSeverityDistribution: Record<string, number>;
  requirementStatusDistribution: Record<string, number>;
}

/** 项目健康度 */
export interface ProjectHealth {
  projectId: string;
  projectName: string;
  overallScore: number;
  healthLevel: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';
  dimensions: Array<{
    name: string;
    key: string;
    score: number;
    weight: number;
    description: string;
  }>;
  suggestions: string[];
  metrics: {
    taskCompletionRate: number;
    bugResolutionRate: number;
    requirementCompletionRate: number;
    overdueRate: number;
    avgTaskCompletionHours: number;
    sprintVelocity: number;
    totalTasks: number;
    completedTasks: number;
    totalBugs: number;
    resolvedBugs: number;
    totalRequirements: number;
    completedRequirements: number;
  };
}

/** 成员工作量 */
export interface MemberWorkloadStats {
  projectId: string;
  members: Array<{
    userId: string;
    username: string;
    avatar?: string;
    totalTasks: number;
    completedTasks: number;
    inProgressTasks: number;
    todoTasks: number;
    bugCount: number;
    resolvedBugCount: number;
    criticalTasks: number;
    overdueTasks: number;
    completionRate: number;
    loadLevel: 'HIGH' | 'MEDIUM' | 'LOW';
    taskDistribution: Record<string, number>;
  }>;
  summary: {
    avgTasksPerMember: number;
    avgCompletionRate: number;
    maxLoadMember: string;
    minLoadMember: string;
    balanced: boolean;
  };
}

/** Sprint 燃尽图 */
export interface BurndownData {
  sprintId: string;
  sprintName: string;
  sprintStatus: string;
  startDate: string;
  endDate: string;
  totalStoryPoints: number;
  totalTasks: number;
  dataPoints: Array<{
    date: string;
    idealRemaining: number;
    actualRemaining: number;
    completedPoints: number;
    completedTasks: number;
  }>;
  velocity: number;
  estimatedCompletionDate: string | null;
  onTrack: boolean;
}

/** 甘特图 */
export interface GanttData {
  projectId: string;
  projectName: string;
  sprintGroups: Array<{
    id: string;
    name: string;
    status: string;
    startDate: string | null;
    endDate: string | null;
  }>;
  tasks: Array<{
    id: string;
    title: string;
    sprintId: string | null;
    assigneeId: string | null;
    assigneeName: string;
    status: string;
    priority: string;
    startDate: string;
    endDate: string;
    progress: number;
    storyPoints: number | null;
    overdue: boolean;
    dependencies: string[];
  }>;
}

// 权限相关类型
export interface PermissionDef {
  code: string;
  name: string;
  scope: 'ENTERPRISE' | 'PROJECT';
  description?: string;
  sort?: number;
}

export interface RolePermissionRow {
  id: string;
  scope: string;
  role: string;
  permissionCode: string;
}

export interface PermissionMatrix {
  permissions: PermissionDef[];
  enterpriseRoles: string[];
  projectRoles: string[];
  mapping: RolePermissionRow[];
}

export interface MePermissions {
  enterprise: string[];
  project: string[];
}

export interface AuditLog {
  id: string;
  userId: string;
  enterpriseId?: string;
  projectId?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  detail?: string;
  createdAt: string;
}
