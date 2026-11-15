'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Plus,
  Loader2,
  Server,
  CheckCircle2,
  XCircle,
  RotateCcw,
  Rocket,
  ShieldCheck,
  Activity,
} from 'lucide-react';
import {
  useEnvironments,
  useDeployments,
  useDoraMetrics,
  useCreateEnvironment,
  useCreateDeployment,
  useApproveDeployment,
  useRejectDeployment,
  useStartDeployment,
  useFinishDeployment,
  useRollbackDeployment,
} from '@/lib/hooks/use-deployments';
import { useReleases } from '@/lib/hooks/use-releases';
import type { Deployment, DeploymentStatus } from '@/lib/api/deployment';

/**
 * 部署标签页：环境 + 部署记录 + DORA 指标。
 *
 * 状态推进要一步一动作（发起 → 审批 → 开始 → 结束），而不是直接改状态字段：
 * 每一步都会落时间戳，DORA 的前置时间靠这些时间戳计算。
 */
export function DeploymentsTab({ projectId, canManage }: { projectId: string; canManage: boolean }) {
  const { data: envRes, isLoading: envLoading } = useEnvironments(projectId);
  const { data: depRes, isLoading: depLoading } = useDeployments(projectId);
  const { data: releasesRes } = useReleases(projectId);
  const { data: doraRes } = useDoraMetrics(projectId, 30);

  const createEnv = useCreateEnvironment(projectId);
  const createDeployment = useCreateDeployment(projectId);
  const approve = useApproveDeployment(projectId);
  const reject = useRejectDeployment(projectId);
  const start = useStartDeployment(projectId);
  const finish = useFinishDeployment(projectId);
  const rollback = useRollbackDeployment(projectId);

  const environments = envRes?.data || [];
  const deployments = depRes?.data || [];
  const releases = releasesRes?.data || [];
  const dora = doraRes?.data;

  const [envOpen, setEnvOpen] = useState(false);
  const [envForm, setEnvForm] = useState({ name: '', type: 'TEST' as 'DEV' | 'TEST' | 'STAGING' | 'PROD', url: '' });

  const [deployOpen, setDeployOpen] = useState(false);
  const [deployForm, setDeployForm] = useState({ environmentId: '', releaseId: '' });

  const envName = (id: string) => environments.find((e) => e.id === id)?.name || '未知环境';

  const handleCreateEnv = async () => {
    if (!envForm.name.trim()) return;
    await createEnv.mutateAsync({
      name: envForm.name.trim(),
      type: envForm.type,
      url: envForm.url.trim() || undefined,
      // 生产环境默认需要审批，与后端默认一致
      requiresApproval: envForm.type === 'PROD' ? 1 : 0,
    });
    setEnvForm({ name: '', type: 'TEST', url: '' });
    setEnvOpen(false);
  };

  const handleCreateDeployment = async () => {
    if (!deployForm.environmentId) return;
    await createDeployment.mutateAsync({
      environmentId: deployForm.environmentId,
      data: {
        releaseId: deployForm.releaseId || undefined,
      },
    });
    setDeployForm({ environmentId: '', releaseId: '' });
    setDeployOpen(false);
  };

  return (
    <div className="space-y-5">
      {/* DORA 指标 */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm">
            <Activity className="h-4 w-4 text-brand" />
            DORA 交付效能（近 30 天）
          </CardTitle>
        </CardHeader>
        <CardContent>
          {!dora ? (
            <div className="flex h-16 items-center justify-center">
              <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
            </div>
          ) : !dora.hasProdEnvironment ? (
            <p className="py-3 text-xs text-muted-foreground">{dora.note}</p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <MetricCard
                  label="部署频率"
                  value={dora.deploymentFrequencyLabel}
                  hint={`${dora.totalDeployments} 次部署`}
                />
                <MetricCard
                  label="变更前置时间"
                  value={dora.leadTimeHours == null ? '—' : `${dora.leadTimeHours} 小时`}
                  hint={
                    dora.leadTimeHours == null
                      ? '缺少提交时间，无法计算'
                      : `${dora.leadTimeSamples ?? 0} 个样本的中位数`
                  }
                />
                <MetricCard
                  label="变更失败率"
                  value={dora.changeFailureRate == null ? '—' : `${dora.changeFailureRate}%`}
                  hint={`${dora.failedDeployments}/${dora.totalDeployments} 次失败`}
                  warn={(dora.changeFailureRate ?? 0) > 15}
                />
                <MetricCard
                  label="平均恢复时间"
                  value={dora.mttrHours == null ? '—' : `${dora.mttrHours} 小时`}
                  hint="失败到下次成功的间隔"
                />
              </div>
              {dora.note && <p className="mt-3 text-[11px] text-warning">{dora.note}</p>}
            </>
          )}
        </CardContent>
      </Card>

      {/* 环境 */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-medium">环境</h3>
            <p className="text-xs text-muted-foreground">
              生产环境（PROD）默认需要审批，且同一环境不能并发部署
            </p>
          </div>
          {canManage && (
            <Dialog open={envOpen} onOpenChange={setEnvOpen}>
              <DialogTrigger className="inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-xs font-medium hover:bg-muted">
                <Plus className="h-3.5 w-3.5" />
                新建环境
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>新建环境</DialogTitle>
                </DialogHeader>
                <div className="space-y-3">
                  <div>
                    <Label>名称 *</Label>
                    <Input
                      value={envForm.name}
                      onChange={(e) => setEnvForm({ ...envForm, name: e.target.value })}
                      placeholder="例如 测试环境 / 预发 / 生产"
                    />
                  </div>
                  <div>
                    <Label>类型</Label>
                    <select
                      value={envForm.type}
                      onChange={(e) => setEnvForm({ ...envForm, type: e.target.value as typeof envForm.type })}
                      className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm"
                    >
                      <option value="DEV">开发</option>
                      <option value="TEST">测试</option>
                      <option value="STAGING">预发</option>
                      <option value="PROD">生产（需审批）</option>
                    </select>
                  </div>
                  <div>
                    <Label>访问地址</Label>
                    <Input
                      value={envForm.url}
                      onChange={(e) => setEnvForm({ ...envForm, url: e.target.value })}
                      placeholder="https://example.com（可选）"
                    />
                  </div>
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setEnvOpen(false)}>取消</Button>
                  <Button onClick={handleCreateEnv} disabled={!envForm.name.trim() || createEnv.isPending}>
                    创建
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          )}
        </div>

        {envLoading ? (
          <div className="flex h-16 items-center justify-center">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : environments.length === 0 ? (
          <div className="flex h-24 flex-col items-center justify-center gap-1 text-muted-foreground">
            <Server className="h-8 w-8 opacity-30" />
            <p className="text-xs">还没有环境。先建一个环境才能发起部署。</p>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {environments.map((env) => (
              <div key={env.id} className="flex items-center gap-2 rounded-lg border px-3 py-2">
                <Server className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-sm">{env.name}</span>
                <Badge variant="outline" className="text-[10px]">{env.type}</Badge>
                {env.requiresApproval === 1 && (
                  <span title="需要审批">
                    <ShieldCheck className="h-3 w-3 text-warning" />
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 部署记录 */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium">部署记录</h3>
          {canManage && environments.length > 0 && (
            <Dialog open={deployOpen} onOpenChange={setDeployOpen}>
              <DialogTrigger className="inline-flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-brand/90">
                <Rocket className="h-3.5 w-3.5" />
                发起部署
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>发起部署</DialogTitle>
                </DialogHeader>
                <div className="space-y-3">
                  <div>
                    <Label>目标环境 *</Label>
                    <select
                      value={deployForm.environmentId}
                      onChange={(e) => setDeployForm({ ...deployForm, environmentId: e.target.value })}
                      className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm"
                    >
                      <option value="">请选择环境</option>
                      {environments.map((env) => (
                        <option key={env.id} value={env.id}>
                          {env.name}{env.requiresApproval === 1 ? '（需审批）' : ''}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <Label>部署版本</Label>
                    <select
                      value={deployForm.releaseId}
                      onChange={(e) => setDeployForm({ ...deployForm, releaseId: e.target.value })}
                      className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm"
                    >
                      <option value="">不关联版本</option>
                      {releases.map((r) => (
                        <option key={r.id} value={r.id}>{r.version} · {r.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setDeployOpen(false)}>取消</Button>
                  <Button
                    onClick={handleCreateDeployment}
                    disabled={!deployForm.environmentId || createDeployment.isPending}
                  >
                    发起
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          )}
        </div>

        {depLoading ? (
          <div className="flex h-16 items-center justify-center">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : deployments.length === 0 ? (
          <div className="flex h-24 flex-col items-center justify-center gap-1 text-muted-foreground">
            <Rocket className="h-8 w-8 opacity-30" />
            <p className="text-xs">还没有部署记录</p>
          </div>
        ) : (
          <div className="space-y-1.5">
            {deployments.map((d) => (
              <DeploymentRow
                key={d.id}
                deployment={d}
                envName={envName(d.environmentId)}
                canManage={canManage}
                onApprove={() => approve.mutate({ id: d.id })}
                onReject={() => reject.mutate({ id: d.id, note: prompt('驳回原因（可选）') || undefined })}
                onStart={() => start.mutate(d.id)}
                onFinish={(ok) => finish.mutate({ id: d.id, success: ok })}
                onRollback={() => {
                  if (confirm('回滚会以该版本重新部署一次，并生成新的部署记录，确认回滚？')) {
                    rollback.mutate({ id: d.id });
                  }
                }}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function MetricCard({
  label,
  value,
  hint,
  warn,
}: {
  label: string;
  value: string;
  hint?: string;
  warn?: boolean;
}) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`text-xl font-semibold ${warn ? 'text-danger' : ''}`}>{value}</p>
      {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

const STATUS_CONFIG: Record<DeploymentStatus, { label: string; className: string }> = {
  PENDING_APPROVAL: { label: '待审批', className: 'bg-warning-muted text-warning' },
  APPROVED: { label: '已批准', className: 'bg-info-muted text-info' },
  DEPLOYING: { label: '部署中', className: 'bg-info-muted text-info' },
  SUCCEEDED: { label: '成功', className: 'bg-success-muted text-success' },
  FAILED: { label: '失败', className: 'bg-danger-muted text-danger' },
  ROLLED_BACK: { label: '已回滚', className: 'bg-orange-100 text-orange-700' },
  CANCELLED: { label: '已取消', className: 'bg-surface-2 text-ink-subtle' },
};

function DeploymentRow({
  deployment,
  envName,
  canManage,
  onApprove,
  onReject,
  onStart,
  onFinish,
  onRollback,
}: {
  deployment: Deployment;
  envName: string;
  canManage: boolean;
  onApprove: () => void;
  onReject: () => void;
  onStart: () => void;
  onFinish: (success: boolean) => void;
  onRollback: () => void;
}) {
  const st = STATUS_CONFIG[deployment.status];
  const time = deployment.finishedAt || deployment.createdAt;

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2 text-sm">
      <span className="font-mono text-xs text-muted-foreground">
        {deployment.version || (deployment.releaseId ? '未命名版本' : '—')}
      </span>
      <span className="text-xs">→ {envName}</span>
      <Badge variant="outline" className={`text-[10px] ${st.className} border-transparent`}>
        {st.label}
      </Badge>
      {deployment.rollbackOf && (
        <Badge variant="outline" className="text-[10px]">回滚</Badge>
      )}
      <span className="text-[11px] text-muted-foreground">
        {new Date(time).toLocaleString('zh-CN')}
      </span>

      <div className="ml-auto flex gap-1.5">
        {canManage && deployment.status === 'PENDING_APPROVAL' && (
          <>
            <Button variant="outline" size="sm" className="h-6 text-xs" onClick={onApprove}>
              <CheckCircle2 className="mr-1 h-3 w-3" /> 通过
            </Button>
            <Button variant="ghost" size="sm" className="h-6 text-xs" onClick={onReject}>
              <XCircle className="mr-1 h-3 w-3" /> 驳回
            </Button>
          </>
        )}
        {canManage && deployment.status === 'APPROVED' && (
          <Button variant="outline" size="sm" className="h-6 text-xs" onClick={onStart}>
            开始部署
          </Button>
        )}
        {canManage && deployment.status === 'DEPLOYING' && (
          <>
            <Button variant="outline" size="sm" className="h-6 text-xs" onClick={() => onFinish(true)}>
              标记成功
            </Button>
            <Button variant="ghost" size="sm" className="h-6 text-xs" onClick={() => onFinish(false)}>
              标记失败
            </Button>
          </>
        )}
        {canManage && deployment.status === 'SUCCEEDED' && (
          <Button variant="ghost" size="sm" className="h-6 text-xs" onClick={onRollback}>
            <RotateCcw className="mr-1 h-3 w-3" /> 回滚
          </Button>
        )}
      </div>
    </div>
  );
}
