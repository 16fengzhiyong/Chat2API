import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Separator } from '@/components/ui/separator'
import { Badge } from '@/components/ui/badge'
import { RefreshCw, Activity, CheckCircle } from 'lucide-react'
import { api } from '@/api'

function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const toast = (text: string, ok = true) => { setMsg({ text, ok }); setTimeout(() => setMsg(null), 3000) }
  return { msg, toast }
}

interface ProxyConfig { requestTimeoutSeconds?: number; retryCount?: number }
interface LBConfig { strategy?: string }
interface HealthStatus { status?: string; timestamp?: string }

export function ProxySettings() {
  const [health, setHealth] = useState<HealthStatus | null>(null)
  const [proxyConfig, setProxyConfig] = useState<ProxyConfig>({ requestTimeoutSeconds: 60, retryCount: 3 })
  const [lbConfig, setLBConfig] = useState<LBConfig>({ strategy: 'round_robin' })
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      const [h, pc, lb] = await Promise.all([
        api.health().catch(() => ({ status: 'unknown' })),
        api.proxyConfig().catch(() => ({})),
        api.loadBalanceConfig().catch(() => ({})),
      ])
      setHealth(h as HealthStatus)
      setProxyConfig({ requestTimeoutSeconds: 60, retryCount: 3, ...(pc as ProxyConfig) })
      setLBConfig({ strategy: 'round_robin', ...(lb as LBConfig) })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function saveProxy() {
    setSaving(true)
    try {
      await api.saveProxyConfig(proxyConfig as Record<string, unknown>)
      toast('代理配置已保存')
    } catch { toast('保存失败', false) }
    finally { setSaving(false) }
  }

  async function saveLB() {
    setSaving(true)
    try {
      await api.saveLoadBalanceConfig(lbConfig as Record<string, unknown>)
      toast('负载均衡配置已保存')
    } catch { toast('保存失败', false) }
    finally { setSaving(false) }
  }

  return (
    <div className="space-y-6">
      {msg && (
        <div className={`fixed top-4 right-4 z-50 px-4 py-2 rounded-md text-sm text-white shadow-lg ${msg.ok ? 'bg-primary' : 'bg-destructive'}`}>
          {msg.text}
        </div>
      )}

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">代理设置</h1>
          <p className="text-muted-foreground text-sm">配置请求转发、负载均衡和高级选项</p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />刷新
        </Button>
      </div>

      <Tabs defaultValue="status">
        <TabsList>
          <TabsTrigger value="status">状态监控</TabsTrigger>
          <TabsTrigger value="advanced">高级配置</TabsTrigger>
          <TabsTrigger value="loadbalance">负载均衡</TabsTrigger>
        </TabsList>

        <TabsContent value="status" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Activity className="h-4 w-4" />服务状态
              </CardTitle>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-4">
              <div className="flex items-center gap-3">
                <div className={`h-3 w-3 rounded-full ${health?.status === 'ok' ? 'bg-green-500' : 'bg-red-500'}`} />
                <span className="font-medium">{health?.status === 'ok' ? '服务正常运行' : '服务状态未知'}</span>
                {health?.status === 'ok' && <Badge variant="default"><CheckCircle className="h-3 w-3 mr-1" />在线</Badge>}
              </div>
              {health?.timestamp && (
                <p className="text-xs text-muted-foreground">
                  最后检查: {new Date(health.timestamp).toLocaleString('zh-CN')}
                </p>
              )}
              <div className="rounded-md bg-muted/40 px-4 py-3 text-sm space-y-1">
                <p><span className="text-muted-foreground">代理地址：</span><span className="font-mono">{localStorage.getItem('chat2api.baseUrl') || 'http://localhost:8080'}</span></p>
                <p><span className="text-muted-foreground">API 端点：</span><span className="font-mono">/v1/chat/completions</span></p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="advanced" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">高级配置</CardTitle>
              <CardDescription>超时和重试策略</CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-5">
              <div className="grid grid-cols-2 gap-6">
                <div className="space-y-2">
                  <Label>请求超时（秒）</Label>
                  <Input
                    type="number"
                    min={10}
                    max={300}
                    value={proxyConfig.requestTimeoutSeconds ?? 60}
                    onChange={(e) => setProxyConfig((p) => ({ ...p, requestTimeoutSeconds: Number(e.target.value) }))}
                  />
                  <p className="text-xs text-muted-foreground">单个请求最长等待时间</p>
                </div>
                <div className="space-y-2">
                  <Label>最大重试次数</Label>
                  <Input
                    type="number"
                    min={0}
                    max={10}
                    value={proxyConfig.retryCount ?? 3}
                    onChange={(e) => setProxyConfig((p) => ({ ...p, retryCount: Number(e.target.value) }))}
                  />
                  <p className="text-xs text-muted-foreground">失败后自动重试次数</p>
                </div>
              </div>
              <Button onClick={saveProxy} disabled={saving}>{saving ? '保存中...' : '保存配置'}</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="loadbalance" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">负载均衡</CardTitle>
              <CardDescription>配置多账号之间的请求分发策略</CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-5">
              <div className="space-y-2">
                <Label>负载均衡策略</Label>
                <select
                  className="w-full rounded-md border border-input px-3 py-2 text-sm bg-background"
                  value={lbConfig.strategy ?? 'round_robin'}
                  onChange={(e) => setLBConfig((p) => ({ ...p, strategy: e.target.value }))}
                >
                  <option value="round_robin">轮询（Round Robin）</option>
                  <option value="fill_first">优先填满（Fill First）</option>
                  <option value="failover">故障转移（Failover）</option>
                </select>
                <div className="mt-1 text-xs text-muted-foreground space-y-1 pl-1">
                  <p><b>轮询：</b>均匀分配到所有可用账号</p>
                  <p><b>优先填满：</b>尽量使用最少使用的账号</p>
                  <p><b>故障转移：</b>按顺序使用，前者失败才切换</p>
                </div>
              </div>
              <Button onClick={saveLB} disabled={saving}>{saving ? '保存中...' : '保存配置'}</Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
