'use client';

import { useState, useMemo, useEffect } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import {
  Building2,
  Users,
  FolderTree,
  UserPlus,
  Copy,
  Check,
  ChevronRight,
  ChevronDown,
  MoreHorizontal,
  Search,
  Shield,
  Crown,
  Trash2,
  Pencil,
  Plus,
  Loader2,
  LogOut,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  useCurrentEnterprise,
  useEnterpriseDetail,
  useUpdateEnterprise,
  useInviteMember,
  useResetMemberPassword,
  useCreateDepartment,
  useDeleteDepartment,
  useUpdateDepartment,
  useCreateEnterprise,
  useJoinEnterprise,
  useDissolveEnterprise,
  useUpdateMemberRole,
  useUpdateMemberDepartment,
  useRemoveMember,
  useLeaveEnterprise,
} from '@/lib/hooks/use-enterprise';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { usePermissionMatrix, useUpdatePermissionMatrix, useAuditLogs } from '@/lib/hooks/use-permissions';
import { permissionApi } from '@/lib/api/permission';
import { authApi } from '@/lib/api/auth';
import type { PermissionDef } from '@/types';
import { useAuthStore } from '@/stores';
import { usePresenceStore } from '@/stores/presence-store';
import { request } from '@/lib/api/client';
import type { DepartmentTree } from '@/lib/api/enterprise';

// ============================================
// 四级职级体系（与后端 EnterpriseRoles 对应，2026-10 从七级收敛：
// 总监/主管并入经理、组长并入成员；等级越高权限越大）
// ============================================
const roleLabels: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  ADMIN: '管理员',
  MANAGER: '经理',
  MEMBER: '成员',
};
const roleColors: Record<string, string> = {
  SUPER_ADMIN: 'bg-red-500/10 text-red-600 dark:bg-red-900/30 dark:text-red-400',
  ADMIN: 'bg-amber-500/10 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400',
  MANAGER: 'bg-blue-500/10 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
  MEMBER: 'bg-gray-500/10 text-gray-500 dark:bg-gray-900/30 dark:text-gray-400',
};
const roleLevels: Record<string, number> = {
  SUPER_ADMIN: 100, ADMIN: 80, MANAGER: 60, MEMBER: 10,
};
const roleLevel = (role?: string) => roleLevels[role || ''] ?? 10;
// 可被授予的角色（低于自身职级，从高到低列出）
const grantableRoles = (myRole?: string) =>
  Object.keys(roleLabels).filter((r) => roleLevel(r) < roleLevel(myRole))
    .sort((a, b) => roleLevels[b] - roleLevels[a]);

// ============================================
// 部门树节点组件（可选中）
// ============================================
function DepartmentTreeNode({
  node, level = 0, selectedId, onSelect, onEdit, onDelete, manageable,
}: { node: DepartmentTree; level?: number; selectedId: string | null; onSelect: (id: string) => void; onEdit: (dept: DepartmentTree) => void; onDelete: (dept: DepartmentTree) => void; manageable: boolean }) {
  const [expanded, setExpanded] = useState(level === 0);
  const hasChildren = node.children && node.children.length > 0;
  const isSelected = node.id === selectedId;

  return (
    <div>
      <div
        className={`group/dept flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-devflow cursor-pointer ${
          isSelected ? 'bg-primary/10 font-medium text-primary' : 'hover:bg-muted/50'
        }`}
        style={{ paddingLeft: `${level * 16 + 8}px` }}
        onClick={() => { onSelect(node.id); if (hasChildren) setExpanded(true); }}
      >
        {hasChildren ? (
          <span
            className="inline-flex h-3.5 w-3.5 items-center justify-center text-muted-foreground"
            onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
          >
            {expanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
          </span>
        ) : (
          <span className="w-3.5" />
        )}
        <FolderTree className={`h-3.5 w-3.5 shrink-0 ${isSelected ? 'text-primary' : 'text-muted-foreground'}`} />
        <span className="flex-1 truncate">{node.name}</span>
        {manageable && (
          <span className="hidden shrink-0 items-center gap-0.5 group-hover/dept:flex">
            <button
              className="inline-flex h-5 w-5 items-center justify-center rounded text-muted-foreground hover:bg-accent hover:text-foreground"
              title="编辑部门"
              onClick={(e) => { e.stopPropagation(); onEdit(node); }}
            >
              <Pencil className="h-3 w-3" />
            </button>
            <button
              className="inline-flex h-5 w-5 items-center justify-center rounded text-muted-foreground hover:bg-accent hover:text-destructive"
              title="删除部门"
              onClick={(e) => { e.stopPropagation(); onDelete(node); }}
            >
              <Trash2 className="h-3 w-3" />
            </button>
          </span>
        )}
        <span className={`shrink-0 text-xs text-muted-foreground ${manageable ? 'group-hover/dept:hidden' : ''}`}>{node.memberCount}</span>
      </div>
      {expanded && hasChildren && (
        <div>
          {node.children.map((child) => (
            <DepartmentTreeNode
              key={child.id}
              node={child}
              level={level + 1}
              selectedId={selectedId}
              onSelect={onSelect}
              onEdit={onEdit}
              onDelete={onDelete}
              manageable={manageable}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ============================================
// 企业管理内容
// ============================================
export default function EnterpriseContent() {
  const [activeTab, setActiveTab] = useState<'members' | 'departments' | 'settings'>('members');
  const [searchQuery, setSearchQuery] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  const [deptOpen, setDeptOpen] = useState(false);
  const [copied, setCopied] = useState(false);

  // 从后端获取企业数据
  const { data: enterpriseRes, isLoading: entLoading } = useCurrentEnterprise();
  const enterprise = enterpriseRes?.data;

  const { data: detailRes, isLoading: detailLoading } = useEnterpriseDetail(enterprise?.id || null);
  const detail = detailRes?.data;

  const updateEnterprise = useUpdateEnterprise(enterprise?.id || '');
  const inviteMember = useInviteMember(enterprise?.id || '');
  const createDepartment = useCreateDepartment(enterprise?.id || '');

  const members = detail?.members || [];
  const departments = detail?.departments || [];

  // 解散企业
  const currentUser = useAuthStore((st) => st.user);
  const dissolveEnterprise = useDissolveEnterprise();
  const [dissolveOpen, setDissolveOpen] = useState(false);
  const [dissolveConfirmName, setDissolveConfirmName] = useState('');
  const [dissolveError, setDissolveError] = useState('');

  // 密码管理：邀请初始密码 / 重置密码（均一次性展示）
  const resetPassword = useResetMemberPassword(enterprise?.id || '');

  // 部门编辑/删除
  const updateDepartment = useUpdateDepartment();

  // 企业微信机器人 webhook（企业设置）
  const [settingsWebhook, setSettingsWebhook] = useState('');
  const [wecomTestResult, setWecomTestResult] = useState<{ ok: boolean; msg: string } | null>(null);
  const [wecomSaving, setWecomSaving] = useState(false);
  const deleteDept = useDeleteDepartment();
  const [editingDept, setEditingDept] = useState<DepartmentTree | null>(null);
  const [deptEditName, setDeptEditName] = useState('');
  const [deptEditParent, setDeptEditParent] = useState('');
  const [oneTimePassword, setOneTimePassword] = useState<{
    title: string;
    username?: string;
    password: string | null;
  } | null>(null);
  const [pwdCopied, setPwdCopied] = useState(false);
  const [userCopied, setUserCopied] = useState(false);

  // 自助退出企业
  const leaveEnterprise = useLeaveEnterprise();
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [leaveError, setLeaveError] = useState('');
  const setUser = useAuthStore((st) => st.setUser);

  const handleLeave = async () => {
    if (!enterprise) return;
    setLeaveError('');
    try {
      await leaveEnterprise.mutateAsync(enterprise.id);
      // 刷新用户态（enterpriseId/角色已变），企业页自动回到无企业视图
      const me = await authApi.getCurrentUser();
      if (me.data) setUser(me.data);
      setLeaveOpen(false);
    } catch (e) {
      setLeaveError(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          '退出失败，请稍后重试',
      );
    }
  };

  // 成员管理（职级/部门调整）
  const updateMemberRole = useUpdateMemberRole(enterprise?.id || '');
  const updateMemberDepartment = useUpdateMemberDepartment(enterprise?.id || '');
  const removeMember = useRemoveMember(enterprise?.id || '');
  const [editingMember, setEditingMember] = useState<typeof members[number] | null>(null);
  const [editRole, setEditRole] = useState('MEMBER');
  const [editDept, setEditDept] = useState('');
  const [editError, setEditError] = useState('');

  // 职级实时化：优先取企业成员列表中的角色（管理员调整职级后立即生效，而非登录时缓存）
  const myMember = members.find((m) => m.id === currentUser?.id);
  const myRole = myMember?.role || currentUser?.role;
  const myLevel = roleLevel(myRole);
  const isSuperAdmin = myRole === 'SUPER_ADMIN';

  // 权限可视化后台：角色-权限矩阵 + 审计日志（仅超管设置页启用）
  const queryClient = useQueryClient();
  const matrixQuery = usePermissionMatrix(activeTab === 'settings' && isSuperAdmin);
  const updateMatrix = useUpdatePermissionMatrix();
  const auditLogsQuery = useAuditLogs(enterprise?.id || null, activeTab === 'settings' && isSuperAdmin, { page: 1, pageSize: 10 });
  const memberNameMap: Record<string, string> = {};
  members.forEach((m) => { memberNameMap[m.id] = m.realName || m.username || m.id; });
  const auditActionLabels: Record<string, string> = {
    'enterprise.create': '创建企业',
    'enterprise.dissolve': '解散企业',
    'member.invite': '邀请成员',
    'member.remove': '移除成员',
    'member.role': '调整成员职级',
    'member.department': '调整成员部门',
    'project.member.add': '添加项目成员',
    'project.member.remove': '移除项目成员',
    'project.member.role': '调整项目角色',
    'permission.matrix.update': '修改权限配置',
    'permission.denied': '权限拒绝',
  };

  // 在线状态：进入页面拉取快照，WebSocket 推送实时更新
  const onlineUserIds = usePresenceStore((st) => st.onlineUserIds);
  useEffect(() => {
    if (!enterprise?.id) return;
    request<{ onlineUserIds: string[] }>('GET', '/presence', undefined, { enterpriseId: enterprise.id })
      .then((res) => {
        if (res.data?.onlineUserIds) {
          usePresenceStore.getState().setOnlineUserIds(res.data.onlineUserIds);
        }
      })
      .catch(() => {});
  }, [enterprise?.id]);
  const isOnline = (userId: string) => onlineUserIds.includes(userId);

  const canInvite = myLevel >= roleLevel('MANAGER');
  const canEditMember = (memberRole?: string) => roleLevel(memberRole) < myLevel && myLevel >= roleLevel('ADMIN');
  const canMoveMember = (memberRole?: string) => roleLevel(memberRole) < myLevel && myLevel >= roleLevel('MANAGER');
  const canRemoveMemberFn = (memberRole?: string) => roleLevel(memberRole) < myLevel && myLevel >= roleLevel('MANAGER');
  const canManageDepts = myRole === 'SUPER_ADMIN' || myRole === 'ADMIN';

  // 部门架构：选中部门（默认第一个根部门）
  // 注意校验须递归整棵树：只查顶层会把子部门的选中态重置掉
  const [selectedDeptId, setSelectedDeptId] = useState<string | null>(null);

  const findDeptNode = (nodes: DepartmentTree[], id: string | null): DepartmentTree | null => {
    for (const n of nodes) {
      if (n.id === id) return n;
      if (n.children?.length) {
        const found = findDeptNode(n.children, id);
        if (found) return found;
      }
    }
    return null;
  };

  useEffect(() => {
    if (departments.length > 0 && (!selectedDeptId || !findDeptNode(departments, selectedDeptId))) {
      setSelectedDeptId(departments[0]?.id ?? null);
    }
  }, [departments, selectedDeptId]);

  const selectedDept = findDeptNode(departments, selectedDeptId);

  // 扁平化部门列表（供下拉选择）
  const flattenDepts = (
    nodes: DepartmentTree[],
    out: { id: string; name: string }[] = [],
  ): { id: string; name: string }[] => {
    nodes.forEach((n) => {
      out.push({ id: n.id, name: n.name });
      if (n.children?.length) flattenDepts(n.children, out);
    });
    return out;
  };
  const deptOptions = departments.length ? flattenDepts(departments) : [];

  // 部门自身的子树 ID 集合（编辑时上级选项要排除，防止移到自己后代下）
  const collectSubtreeIds = (nodes: DepartmentTree[], id: string, out: Set<string>) => {
    for (const n of nodes) {
      if (n.id === id) {
        const collect = (x: DepartmentTree) => {
          out.add(x.id);
          x.children?.forEach(collect);
        };
        collect(n);
        return true;
      }
      if (n.children?.length && collectSubtreeIds(n.children, id, out)) return true;
    }
    return false;
  };
  const excludedIds = editingDept
    ? (collectSubtreeIds(departments, editingDept.id, new Set()), undefined)
    : undefined;
  const parentOptions = editingDept
    ? flattenDepts(departments).filter((d) => {
        const sub = new Set<string>();
        collectSubtreeIds(departments, editingDept.id, sub);
        return !sub.has(d.id);
      })
    : [];

  const handleDeleteDept = async (dept: DepartmentTree) => {
    if (!confirm(`确定要删除部门「${dept.name}」吗？

该部门下的成员将变为未分配；有子部门的部门需先删除或移走子部门。`)) return;
    try {
      await deleteDept.mutateAsync(dept.id);
      if (selectedDeptId === dept.id) setSelectedDeptId(null);
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除失败');
    }
  };

  const openEditDept = (dept: DepartmentTree) => {
    setEditingDept(dept);
    setDeptEditName(dept.name);
    setDeptEditParent(dept.parentId || '');
  };

  const handleSaveDept = async () => {
    if (!editingDept || !deptEditName.trim()) return;
    try {
      await updateDepartment.mutateAsync({
        id: editingDept.id,
        name: deptEditName.trim(),
        parentId: deptEditParent, // 空串 = 移为顶级
      });
      setEditingDept(null);
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败');
    }
  };

  // 创建 / 加入企业弹窗状态
  const [setupOpen, setSetupOpen] = useState(false);
  const [setupMode, setSetupMode] = useState<'create' | 'join'>('create');
  const [newEntName, setNewEntName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [setupError, setSetupError] = useState('');
  const [setupSubmitting, setSetupSubmitting] = useState(false);
  const createEnterprise = useCreateEnterprise();
  const joinEnterprise = useJoinEnterprise();

  const handleSetupSubmit = async () => {
    setSetupError('');
    if (setupMode === 'create' && !newEntName.trim()) {
      setSetupError('请输入企业名称');
      return;
    }
    if (setupMode === 'join' && !joinCode.trim()) {
      setSetupError('请输入邀请码');
      return;
    }
    setSetupSubmitting(true);
    try {
      if (setupMode === 'create') {
        await createEnterprise.mutateAsync({ name: newEntName.trim() });
      } else {
        await joinEnterprise.mutateAsync(joinCode.trim());
      }
      setSetupOpen(false);
      setNewEntName('');
      setJoinCode('');
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setSetupError(msg || (setupMode === 'create' ? '创建失败' : '邀请码无效或已过期'));
    } finally {
      setSetupSubmitting(false);
    }
  };

  // 企业设置 / 邀请 / 部门表单状态
  const [settingsName, setSettingsName] = useState('');
  const [settingsLogo, setSettingsLogo] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRealName, setInviteRealName] = useState('');
  const [inviteRole, setInviteRole] = useState('MEMBER');
  const [deptName, setDeptName] = useState('');
  const [deptParent, setDeptParent] = useState('');

  // 企业数据加载后同步表单初始值
  useEffect(() => {
    if (enterprise) {
      setSettingsName(enterprise.name);
      setSettingsLogo(enterprise.logo || '');
      setSettingsWebhook(enterprise.wecomWebhook || '');
    }
  }, [enterprise]);

  const filteredMembers = useMemo(() => {
    if (!searchQuery) return members;
    return members.filter(
      (m) => m.username.includes(searchQuery) || m.email.includes(searchQuery)
    );
  }, [members, searchQuery]);

  const isLoading = entLoading || detailLoading;

  const handleCopyInvite = () => {
    if (!enterprise?.inviteCode) return;
    navigator.clipboard.writeText(enterprise.inviteCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const openEditMember = (member: typeof members[number]) => {
    setEditingMember(member);
    setEditRole(member.role);
    // 回显成员当前部门（未分配则用哨兵值）
    setEditDept(member.departmentId || '__NONE__');
    setEditError('');
  };

  const handleSaveMember = async () => {
    if (!editingMember) return;
    setEditError('');
    try {
      if (editRole !== editingMember.role) {
        await updateMemberRole.mutateAsync({ memberId: editingMember.id, role: editRole });
      }
      if (editDept !== (editingMember.departmentId || '__NONE__')) {
        await updateMemberDepartment.mutateAsync({
          memberId: editingMember.id,
          departmentId: editDept === '__NONE__' ? undefined : editDept,
        });
      }
      setEditingMember(null);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setEditError(msg || '保存失败，请稍后重试');
    }
  };

  const handleRemoveMember = async (memberId: string) => {
    if (!window.confirm('确定将该成员移出企业吗？')) return;
    try {
      await removeMember.mutateAsync(memberId);
    } catch {
      // 失败静默
    }
  };

  const handleDissolve = async () => {
    if (!enterprise) return;
    setDissolveError('');
    if (dissolveConfirmName.trim() !== enterprise.name) {
      setDissolveError('企业名称不一致，请重新输入');
      return;
    }
    try {
      await dissolveEnterprise.mutateAsync(enterprise.id);
      setDissolveOpen(false);
      setDissolveConfirmName('');
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setDissolveError(msg || '解散失败，请稍后重试');
    }
  };

  // 加载中
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">加载企业数据...</span>
      </div>
    );
  }

  // 无企业数据
  if (!enterprise) {
    return (
      <>
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <Building2 className="mb-4 h-12 w-12 opacity-30" />
          <p className="text-sm">尚未加入企业</p>
          <Button className="mt-4" size="sm" onClick={() => { setSetupMode('create'); setSetupOpen(true); }}>
            创建或加入企业
          </Button>
        </div>

        <Dialog open={setupOpen} onOpenChange={setSetupOpen}>
          <DialogContent className="max-w-md">
            <DialogHeader>
              <DialogTitle>{setupMode === 'create' ? '创建企业' : '加入企业'}</DialogTitle>
              <DialogDescription>
                {setupMode === 'create'
                  ? '创建后你将成为超级管理员'
                  : '输入企业成员分享给你的邀请码'}
              </DialogDescription>
            </DialogHeader>

            <div className="mb-2 grid grid-cols-2 gap-2">
              <Button
                type="button"
                variant={setupMode === 'create' ? 'default' : 'outline'}
                size="sm"
                onClick={() => { setSetupMode('create'); setSetupError(''); }}
              >
                创建企业
              </Button>
              <Button
                type="button"
                variant={setupMode === 'join' ? 'default' : 'outline'}
                size="sm"
                onClick={() => { setSetupMode('join'); setSetupError(''); }}
              >
                邀请码加入
              </Button>
            </div>

            {setupMode === 'create' ? (
              <div className="space-y-2">
                <Label htmlFor="new-ent-name">企业名称</Label>
                <Input
                  id="new-ent-name"
                  placeholder="请输入企业名称"
                  value={newEntName}
                  onChange={(e) => setNewEntName(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleSetupSubmit()}
                />
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="join-code">邀请码</Label>
                <Input
                  id="join-code"
                  placeholder="请输入邀请码"
                  value={joinCode}
                  onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                  onKeyDown={(e) => e.key === 'Enter' && handleSetupSubmit()}
                />
              </div>
            )}

            {setupError && (
              <p className="text-xs text-destructive">{setupError}</p>
            )}

            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={() => setSetupOpen(false)}>
                取消
              </Button>
              <Button size="sm" onClick={handleSetupSubmit} disabled={setupSubmitting}>
                {setupSubmitting && <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />}
                {setupMode === 'create' ? '创建' : '加入'}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </>
    );
  }

  const tabItems = [
    { key: 'members' as const, label: '成员管理', icon: Users },
    { key: 'departments' as const, label: '部门架构', icon: FolderTree },
    ...(isSuperAdmin ? [{ key: 'settings' as const, label: '企业设置', icon: Building2 }] : []),
  ];

  return (
    <div className="space-y-6">
      {/* 页面头部 */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand/10">
            <Building2 className="h-6 w-6 text-brand" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-foreground">{enterprise.name}</h1>
            <p className="text-sm text-muted-foreground">
              {members.length} 名成员 · 创建于 {new Date(enterprise.createdAt).toLocaleDateString('zh-CN')}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {isSuperAdmin && (
            <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/50 px-3 py-1.5">
              <span className="text-xs text-muted-foreground">邀请码</span>
              <code className="text-sm font-mono font-bold">{enterprise.inviteCode}</code>
              <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleCopyInvite}>
                {copied ? <Check className="h-3 w-3 text-green-500" /> : <Copy className="h-3 w-3" />}
              </Button>
            </div>
          )}
          {/* 我的职级 + 自助退出（所有成员可见） */}
          <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/50 px-3 py-1.5">
            <span className="text-xs text-muted-foreground">我的职级</span>
            <Badge className={`border-0 text-[10px] ${roleColors[myRole || 'MEMBER'] || ''}`}>
              {roleLabels[myRole || 'MEMBER'] || myRole}
            </Badge>
          </div>
          <Button
            variant="outline"
            size="sm"
            className="gap-1.5 text-destructive hover:text-destructive"
            onClick={() => { setLeaveError(''); setLeaveOpen(true); }}
          >
            <LogOut className="h-3.5 w-3.5" />
            退出企业
          </Button>
        </div>
      </div>

      {/* Tab 切换 */}
      <div className="flex gap-1 rounded-lg border border-border bg-muted/30 p-1">
        {tabItems.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex flex-1 items-center justify-center gap-1.5 rounded-md px-3 py-2 text-sm transition-devflow ${
              activeTab === key
                ? 'bg-background font-medium text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      {/* 成员管理 */}
      {activeTab === 'members' && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base">成员列表</CardTitle>
                <CardDescription>管理企业成员和权限</CardDescription>
              </div>
              {canInvite && (
                <Button size="sm" className="gap-1.5" onClick={() => setInviteOpen(true)}>
                  <UserPlus className="h-3.5 w-3.5" />
                  邀请
                </Button>
              )}
            </div>
            <div className="relative mt-3">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="搜索成员..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-8"
              />
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-1">
              {filteredMembers.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">暂无成员</p>
              ) : (
                filteredMembers.map((member) => (
                  <div
                    key={member.id}
                    className="flex items-center gap-3 rounded-lg p-3 transition-devflow hover:bg-muted/50"
                  >
                    <div className="flex h-9 w-9 items-center justify-center rounded-full bg-muted text-sm font-medium">
                      {member.avatar || member.username.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium">{member.realName || member.username}</span>
                        <Badge variant="secondary" className={`text-[10px] ${roleColors[member.role]}`}>
                          {roleLabels[member.role] || member.role}
                        </Badge>
                      </div>
                      <p className="text-xs text-muted-foreground truncate">
                        {member.email}
                      </p>
                    </div>
                    <div className="flex items-center gap-1">
                      {member.role === 'SUPER_ADMIN' && <Crown className="h-3.5 w-3.5 text-red-500" />}
                      {member.role === 'ADMIN' && <Shield className="h-3.5 w-3.5 text-amber-500" />}
                      <div
                        className={`h-2 w-2 rounded-full ${isOnline(member.id) ? 'bg-green-500' : 'bg-gray-300 dark:bg-gray-600'}`}
                        title={isOnline(member.id) ? '在线' : '离线'}
                      />
                      {(canEditMember(member.role) || canMoveMember(member.role) || canRemoveMemberFn(member.role) || (isSuperAdmin && member.id !== currentUser?.id)) ? (
                        <DropdownMenu>
                          <DropdownMenuTrigger className="inline-flex h-7 w-7 items-center justify-center rounded-md hover:bg-accent">
                            <MoreHorizontal className="h-3.5 w-3.5" />
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            {canEditMember(member.role) && (
                              <DropdownMenuItem onClick={() => openEditMember(member)}>修改职级 / 部门</DropdownMenuItem>
                            )}
                            {isSuperAdmin && member.id !== currentUser?.id && (
                              <DropdownMenuItem
                                onClick={async () => {
                                  if (!confirm(`确定要重置「${member.realName || member.username}」的密码吗？将生成新的随机临时密码。`)) return;
                                  const res = await resetPassword.mutateAsync(member.id);
                                  setPwdCopied(false);
                                  setOneTimePassword({
                                    title: `已重置「${member.realName || member.username}」的密码`,
                                    username: member.username,
                                    password: res.data?.password ?? null,
                                  });
                                }}
                              >
                                <Shield className="mr-2 h-3.5 w-3.5" /> 重置密码
                              </DropdownMenuItem>
                            )}
                            {canRemoveMemberFn(member.role) && (
                              <DropdownMenuItem
                                className="text-destructive focus:text-destructive"
                                onClick={() => handleRemoveMember(member.id)}
                              >
                                <Trash2 className="mr-2 h-3.5 w-3.5" /> 移除成员
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      ) : null}
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 部门架构 */}
      {activeTab === 'departments' && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="lg:col-span-1">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <div>
                <CardTitle className="text-base">部门结构</CardTitle>
                <CardDescription>点击部门查看成员</CardDescription>
              </div>
              {canManageDepts && (
                <Dialog open={deptOpen} onOpenChange={setDeptOpen}>
                  <DialogTrigger className="inline-flex h-7 w-7 items-center justify-center rounded-md border border-input bg-background hover:bg-accent">
                    <Plus className="h-3.5 w-3.5" />
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>新建部门</DialogTitle>
                      <DialogDescription>创建新的部门</DialogDescription>
                    </DialogHeader>
                     <div className="space-y-4 pt-2">
                       <div className="space-y-2">
                         <Label>部门名称</Label>
                         <Input
                           placeholder="例如：设计部"
                           value={deptName}
                           onChange={(e) => setDeptName(e.target.value)}
                         />
                       </div>
                       <div className="space-y-2">
                         <Label>上级部门（可选）</Label>
                         <Select
                           value={deptParent || '__NONE__'}
                           onValueChange={(v) => setDeptParent(!v || v === '__NONE__' ? '' : v)}
                         >
                           <SelectTrigger className="w-full">
                             <SelectValue>
                               {deptParent
                                 ? (departments.find((d) => d.id === deptParent)?.name || deptParent)
                                 : '无（顶级部门）'}
                             </SelectValue>
                           </SelectTrigger>
                           <SelectContent>
                             <SelectItem value="__NONE__">无（顶级部门）</SelectItem>
                             {departments.map((d) => (
                               <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                             ))}
                           </SelectContent>
                         </Select>
                       </div>
                       <Button
                         className="w-full"
                         disabled={createDepartment.isPending || !deptName}
                         onClick={async () => {
                           await createDepartment.mutateAsync({
                             name: deptName,
                             parentId: deptParent || undefined,
                           });
                           setDeptOpen(false);
                           setDeptName('');
                           setDeptParent('');
                         }}
                       >
                         {createDepartment.isPending ? '创建中...' : '创建部门'}
                       </Button>
                     </div>
                  </DialogContent>
                </Dialog>
              )}
            </CardHeader>
            <CardContent>
              <div className="space-y-0.5">
                {departments.length === 0 ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">暂无部门</p>
                ) : (
                  departments.map((dept) => (
                    <DepartmentTreeNode
                      key={dept.id}
                      node={dept}
                      selectedId={selectedDeptId}
                      onSelect={setSelectedDeptId}
                      onEdit={openEditDept}
                      onDelete={handleDeleteDept}
                      manageable={canManageDepts}
                    />
                  ))
                )}
              </div>
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle className="text-base">
                {selectedDept ? selectedDept.name : '部门成员'}
              </CardTitle>
              <CardDescription>
                {selectedDept
                  ? `该部门共 ${selectedDept.memberCount} 名成员，点击左侧部门切换`
                  : '选择左侧部门查看该部门的成员'}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {(() => {
              const deptMembers = selectedDept?.members ?? [];
              if (!selectedDept || deptMembers.length === 0) {
                return (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  该部门暂无成员，可在「邀请成员」时选择部门
                </p>
                );
              }
              return (
                <div className="space-y-2">
                  {deptMembers.map((member) => (
                    <div
                      key={member.id}
                      className="flex items-center gap-3 rounded-lg border border-border p-3"
                    >
                      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-muted text-sm font-medium">
                        {member.avatar || member.username.charAt(0)}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium">{member.realName || member.username}</span>
                          <Badge variant="secondary" className={`text-[10px] ${roleColors[member.role]}`}>
                            {roleLabels[member.role] || member.role}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground truncate">{member.email}</p>
                      </div>
                      <div
                        className={`h-2 w-2 rounded-full ${isOnline(member.id) ? 'bg-green-500' : 'bg-gray-300 dark:bg-gray-600'}`}
                        title={isOnline(member.id) ? '在线' : '离线'}
                      />
                    </div>
                  ))}
                </div>
              );
              })()}
            </CardContent>
          </Card>
        </div>
      )}

      {/* 企业设置（仅超级管理员） */}
      {activeTab === 'settings' && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">基本信息</CardTitle>
              <CardDescription>修改企业名称、Logo 等信息</CardDescription>
            </CardHeader>
             <CardContent className="space-y-4">
               <div className="space-y-2">
                 <Label>企业名称</Label>
                 <Input
                   value={settingsName}
                   onChange={(e) => setSettingsName(e.target.value)}
                 />
               </div>
               <div className="space-y-2">
                 <Label>企业 Logo</Label>
                 <div className="flex items-center gap-4">
                   <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-xl border-2 border-dashed border-border">
                     {settingsLogo ? (
                       <img src={settingsLogo} alt="Logo" className="h-full w-full object-cover" />
                     ) : (
                       <Building2 className="h-6 w-6 text-muted-foreground" />
                     )}
                   </div>
                   <Button
                     variant="outline"
                     size="sm"
                     onClick={() => {
                       const url = window.prompt('请输入 Logo 图片 URL');
                       if (url) setSettingsLogo(url);
                     }}
                   >
                     设置 Logo
                   </Button>
                 </div>
               </div>
               <Button
                 disabled={updateEnterprise.isPending}
                 onClick={() => updateEnterprise.mutate({ name: settingsName, logo: settingsLogo, wecomWebhook: settingsWebhook })}
               >
                 {updateEnterprise.isPending ? '保存中...' : '保存修改'}
               </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">企业微信机器人</CardTitle>
              <CardDescription>
                配置群机器人 Webhook 后，新缺陷、用例执行失败等关键事件将自动推送到企业群
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-2">
                <Label>机器人 Webhook 地址</Label>
                <Input
                  value={settingsWebhook}
                  onChange={(e) => setSettingsWebhook(e.target.value)}
                  placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
                  className="font-mono text-xs"
                />
                <p className="text-[11px] text-muted-foreground">
                  在企业群 → 群设置 → 群机器人 → 添加机器人 获取；留空表示不推送
                </p>
              </div>
              {wecomTestResult && (
                <p className={`text-xs ${wecomTestResult.ok ? 'text-green-600' : 'text-destructive'}`}>
                  {wecomTestResult.msg}
                </p>
              )}
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={updateEnterprise.isPending || !settingsWebhook.trim()}
                  onClick={async () => {
                    // 先保存配置再测试
                    await updateEnterprise.mutateAsync({
                      name: settingsName,
                      logo: settingsLogo,
                      wecomWebhook: settingsWebhook,
                    });
                    try {
                      const r = await request<{ ok: boolean; error?: string }>(
                        'POST',
                        `/enterprises/${enterprise.id}/wecom/test`,
                      );
                      setWecomTestResult({
                        ok: !!r.data?.ok,
                        msg: r.data?.ok ? '测试消息已发送，请到企业群查看' : '发送失败：' + (r.data?.error || '未知原因'),
                      });
                    } catch (e) {
                      setWecomTestResult({ ok: false, msg: '发送失败，请检查 Webhook 地址' });
                    }
                  }}
                >
                  发送测试消息
                </Button>
                <span className="text-[11px] text-muted-foreground">推送事件：新缺陷、用例执行失败、用例转缺陷</span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">邀请码</CardTitle>
              <CardDescription>分享邀请码让成员加入企业</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-3 rounded-lg border border-border bg-muted/30 p-4">
                <div className="flex-1">
                  <p className="text-xs text-muted-foreground">当前邀请码</p>
                  <p className="text-2xl font-mono font-bold tracking-wider">{enterprise.inviteCode}</p>
                </div>
                <Button variant="outline" onClick={handleCopyInvite} className="gap-2">
                  {copied ? <Check className="h-4 w-4 text-green-500" /> : <Copy className="h-4 w-4" />}
                  {copied ? '已复制' : '复制'}
                </Button>
              </div>
               <Button
                 variant="outline"
                 className="w-full"
                 disabled={updateEnterprise.isPending}
                 onClick={() => updateEnterprise.mutate({ regenerateInviteCode: true })}
               >
                 重新生成邀请码
               </Button>
              <p className="text-xs text-muted-foreground">
                重新生成后，旧邀请码将立即失效
              </p>
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle className="text-base">角色权限</CardTitle>
              <CardDescription>
                全局权限配置（影响所有企业），勾选即授予角色对应权限点，保存后即时生效
              </CardDescription>
            </CardHeader>
            <CardContent>
              {matrixQuery.isLoading ? (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : matrixQuery.isError || !matrixQuery.data?.data ? (
                <p className="py-4 text-center text-sm text-muted-foreground">权限矩阵加载失败</p>
              ) : (
                <div className="space-y-6">
                  {(['ENTERPRISE', 'PROJECT'] as const).map((scope) => {
                    const perms: PermissionDef[] = (matrixQuery.data!.data.permissions || []).filter((p) => p.scope === scope);
                    const roles = scope === 'ENTERPRISE' ? matrixQuery.data!.data.enterpriseRoles : matrixQuery.data!.data.projectRoles;
                    const enabled = (role: string, code: string) =>
                      (matrixQuery.data!.data.mapping || []).some(
                        (m) => m.scope === scope && m.role === role && m.permissionCode === code,
                      );
                    return (
                      <div key={scope}>
                        <p className="mb-2 text-sm font-medium">
                          {scope === 'ENTERPRISE' ? '企业级权限' : '项目级权限'}
                        </p>
                        <div className="overflow-x-auto rounded-lg border">
                          <table className="w-full text-xs">
                            <thead>
                              <tr className="bg-muted/50">
                                <th className="px-3 py-2 text-left font-medium">权限点</th>
                                {roles.map((r) => (
                                  <th key={r} className="px-3 py-2 text-center font-medium">{roleLabels[r] || r}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {perms.map((p) => (
                                <tr key={p.code} className="border-t">
                                  <td className="px-3 py-2">
                                    <p className="font-mono text-[10px] text-muted-foreground">{p.code}</p>
                                    <p>{p.name}</p>
                                  </td>
                                  {roles.map((r) => (
                                    <td key={r} className="px-3 py-2 text-center">
                                      <input
                                        type="checkbox"
                                        className="h-4 w-4 cursor-pointer accent-[var(--primary)]"
                                        checked={enabled(r, p.code)}
                                        disabled={updateMatrix.isPending}
                                        onChange={(e) =>
                                          updateMatrix.mutate({
                                            scope,
                                            role: r,
                                            permissionCode: p.code,
                                            enabled: e.target.checked,
                                          })
                                        }
                                      />
                                    </td>
                                  ))}
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-base">审计日志</CardTitle>
                  <CardDescription>敏感操作与权限拒绝记录（最近 10 条）</CardDescription>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={auditLogsQuery.isFetching}
                  onClick={() => auditLogsQuery.refetch()}
                >
                  {auditLogsQuery.isFetching ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : null}
                  刷新
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {auditLogsQuery.isLoading ? (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : !auditLogsQuery.data?.data?.records?.length ? (
                <p className="py-4 text-center text-sm text-muted-foreground">暂无日志</p>
              ) : (
                <div className="space-y-1.5">
                  {auditLogsQuery.data.data.records.map((logItem) => (
                    <div
                      key={logItem.id}
                      className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/30 px-3 py-2 text-xs"
                    >
                      <span className="font-mono text-[10px] text-muted-foreground">
                        {new Date(logItem.createdAt).toLocaleString('zh-CN')}
                      </span>
                      <Badge variant="outline" className="text-[10px]">
                        {auditActionLabels[logItem.action] || logItem.action}
                      </Badge>
                      <span className="font-medium">{memberNameMap[logItem.userId] || '未知用户'}</span>
                      <span className="min-w-0 flex-1 truncate text-muted-foreground">{logItem.detail}</span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="border-destructive/40 lg:col-span-2">
            <CardHeader>
              <CardTitle className="text-base text-destructive">危险操作</CardTitle>
              <CardDescription>以下操作不可撤销</CardDescription>
            </CardHeader>
            <CardContent className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium">解散企业</p>
                <p className="text-xs text-muted-foreground">
                  所有成员将被移出，企业及其项目将被删除且无法恢复
                </p>
              </div>
              <Button
                variant="destructive"
                size="sm"
                disabled={dissolveEnterprise.isPending}
                onClick={() => { setDissolveConfirmName(''); setDissolveError(''); setDissolveOpen(true); }}
              >
                解散企业
              </Button>
            </CardContent>
          </Card>
        </div>
      )}

      {/* 邀请成员弹窗 */}
      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>邀请成员</DialogTitle>
            <DialogDescription>通过邮箱邀请成员加入企业</DialogDescription>
          </DialogHeader>
           <div className="space-y-4 pt-2">
             <div className="space-y-2">
               <Label>真实姓名（选填）</Label>
               <Input
                 placeholder="对方的真实姓名"
                 value={inviteRealName}
                 onChange={(e) => setInviteRealName(e.target.value)}
               />
             </div>
             <div className="space-y-2">
               <Label>邮箱地址</Label>
               <Input
                 placeholder="name@company.com"
                 type="email"
                 value={inviteEmail}
                 onChange={(e) => setInviteEmail(e.target.value)}
               />
             </div>
             <div className="space-y-2">
               <Label>职级</Label>
               <Select value={inviteRole} onValueChange={(v) => v && setInviteRole(v)}>
                 <SelectTrigger className="w-full">
                   <SelectValue>{roleLabels[inviteRole] || '选择职级'}</SelectValue>
                 </SelectTrigger>
                 <SelectContent>
                   {grantableRoles(myRole).map((r) => (
                     <SelectItem key={r} value={r}>{roleLabels[r]}</SelectItem>
                   ))}
                 </SelectContent>
               </Select>
             </div>
             <Button
               className="w-full"
               disabled={inviteMember.isPending || !inviteEmail}
               onClick={async () => {
                 const res = await inviteMember.mutateAsync({
                   email: inviteEmail,
                   realName: inviteRealName.trim() || undefined,
                   role: inviteRole,
                 });
                 setInviteOpen(false);
                 setInviteEmail('');
                 setInviteRealName('');
                 setPwdCopied(false);
                 setOneTimePassword({
                   title: `已邀请 ${res.data?.user?.realName || res.data?.user?.username || inviteEmail} 加入企业`,
                   username: res.data?.user?.username,
                   password: res.data?.initialPassword ?? null,
                 });
               }}
             >
               {inviteMember.isPending ? '发送中...' : '发送邀请'}
             </Button>
           </div>
        </DialogContent>
      </Dialog>

      {/* 修改职级 / 部门弹窗 */}
      <Dialog open={!!editingMember} onOpenChange={(o) => !o && setEditingMember(null)}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>修改成员：{editingMember?.realName || editingMember?.username}</DialogTitle>
            <DialogDescription>调整该成员的职级与所属部门</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="space-y-2">
              <Label>职级</Label>
              <Select value={editRole} onValueChange={(v) => v && setEditRole(v)}>
                <SelectTrigger className="w-full">
                  <SelectValue>{roleLabels[editRole] || '选择职级'}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {grantableRoles(myRole).map((r) => (
                    <SelectItem key={r} value={r}>{roleLabels[r]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                可选职级为低于你当前职级（{roleLabels[myRole || 'MEMBER']}）的岗位
              </p>
            </div>
            <div className="space-y-2">
              <Label>所属部门</Label>
              <Select value={editDept} onValueChange={(v) => setEditDept(v || '__NONE__')}>
                <SelectTrigger className="w-full">
                  <SelectValue>
                    {deptOptions.find((d) => d.id === editDept)?.name || '未分配部门'}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__NONE__">未分配部门</SelectItem>
                  {deptOptions.map((d) => (
                    <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {editError && <p className="text-xs text-destructive">{editError}</p>}
            <Button
              className="w-full"
              disabled={updateMemberRole.isPending || updateMemberDepartment.isPending}
              onClick={handleSaveMember}
            >
              {(updateMemberRole.isPending || updateMemberDepartment.isPending) ? '保存中...' : '保存修改'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 解散企业确认弹窗 */}
      <Dialog open={dissolveOpen} onOpenChange={setDissolveOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>解散企业</DialogTitle>
            <DialogDescription>
              此操作不可撤销！所有成员将被移出，企业下的全部项目、部门将被删除。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="dissolve-confirm">
              请输入企业名称 <span className="font-semibold text-destructive">{enterprise.name}</span> 以确认
            </Label>
            <Input
              id="dissolve-confirm"
              value={dissolveConfirmName}
              onChange={(e) => setDissolveConfirmName(e.target.value)}
              placeholder={enterprise.name}
            />
            {dissolveError && <p className="text-xs text-destructive">{dissolveError}</p>}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => setDissolveOpen(false)}>
              取消
            </Button>
            <Button
              variant="destructive"
              size="sm"
              disabled={dissolveConfirmName !== enterprise.name || dissolveEnterprise.isPending}
              onClick={handleDissolve}
            >
              {dissolveEnterprise.isPending && <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />}
              确认解散
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 编辑部门弹窗 */}
      <Dialog open={!!editingDept} onOpenChange={(o) => !o && setEditingDept(null)}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>编辑部门</DialogTitle>
            <DialogDescription>修改部门名称或调整上级部门</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 pt-2">
            <div className="space-y-2">
              <Label>部门名称</Label>
              <Input
                value={deptEditName}
                onChange={(e) => setDeptEditName(e.target.value)}
                placeholder="部门名称"
              />
            </div>
            <div className="space-y-2">
              <Label>上级部门</Label>
              <Select
                value={deptEditParent || '__ROOT__'}
                onValueChange={(v) => setDeptEditParent(!v || v === '__ROOT__' ? '' : v)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue>
                    {deptEditParent
                      ? (parentOptions.find((d) => d.id === deptEditParent)?.name || deptEditParent)
                      : '无（顶级部门）'}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__ROOT__">无（顶级部门）</SelectItem>
                  {parentOptions.map((d) => (
                    <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-[11px] text-muted-foreground">不能移动到自己或自己的下级部门之下</p>
            </div>
            <Button
              className="w-full"
              disabled={updateDepartment.isPending || !deptEditName.trim()}
              onClick={handleSaveDept}
            >
              {updateDepartment.isPending ? '保存中...' : '保存修改'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 一次性密码展示弹窗（邀请初始密码 / 重置密码） */}
      <Dialog open={!!oneTimePassword} onOpenChange={(o) => !o && setOneTimePassword(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{oneTimePassword?.title}</DialogTitle>
            <DialogDescription>
              {oneTimePassword?.password
                ? '请把登录用户名和初始密码都复制告知对方（用户名是邮箱前缀加随机后缀，不是邮箱本身）。关闭后无法再次查看，建议对方登录后尽快修改密码。'
                : '该成员已是注册用户，保持其原密码，无需告知新密码。'}
            </DialogDescription>
          </DialogHeader>
          {oneTimePassword?.username && (
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">登录用户名</Label>
              <div className="flex items-center gap-2">
                <Input readOnly value={oneTimePassword.username} className="font-mono text-sm" onFocus={(e) => e.target.select()} />
                <Button
                  variant="outline"
                  size="sm"
                  className="shrink-0 gap-1 text-xs"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(oneTimePassword.username || '');
                      setUserCopied(true);
                      setTimeout(() => setUserCopied(false), 2000);
                    } catch {}
                  }}
                >
                  {userCopied ? <><Check className="h-3 w-3 text-green-500" /> 已复制</> : <><Copy className="h-3 w-3" /> 复制</>}
                </Button>
              </div>
            </div>
          )}
          {oneTimePassword?.password && (
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">初始密码</Label>
              <div className="flex items-center gap-2">
                <Input readOnly value={oneTimePassword.password} className="font-mono text-sm" onFocus={(e) => e.target.select()} />
                <Button
                  variant="outline"
                  size="sm"
                  className="shrink-0 gap-1 text-xs"
                  onClick={async () => {
                    const pwd = oneTimePassword.password;
                    if (!pwd) return;
                    try {
                      await navigator.clipboard.writeText(pwd);
                    } catch {
                      const inputs = document.querySelectorAll<HTMLInputElement>('[role="dialog"] input');
                      inputs[1]?.select();
                      document.execCommand('copy');
                    }
                    setPwdCopied(true);
                    setTimeout(() => setPwdCopied(false), 2000);
                  }}
                >
                  {pwdCopied ? <><Check className="h-3 w-3 text-green-500" /> 已复制</> : <><Copy className="h-3 w-3" /> 复制</>}
                </Button>
              </div>
            </div>
          )}
          <div className="flex justify-end">
            <Button variant="outline" size="sm" onClick={() => setOneTimePassword(null)}>我已记录，关闭</Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 退出企业确认弹窗 */}
      <Dialog open={leaveOpen} onOpenChange={setLeaveOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>退出企业</DialogTitle>
            <DialogDescription>
              退出后将失去「{enterprise.name}」的访问权限，你在该企业所有项目中的成员身份也会被移除。如需再次加入，请向企业管理员索取邀请码。
            </DialogDescription>
          </DialogHeader>
          {leaveError && <p className="text-xs text-destructive">{leaveError}</p>}
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => setLeaveOpen(false)}>
              取消
            </Button>
            <Button
              variant="destructive"
              size="sm"
              disabled={leaveEnterprise.isPending}
              onClick={handleLeave}
            >
              {leaveEnterprise.isPending && <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />}
              确认退出
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
