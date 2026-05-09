import { useEffect, useState } from 'react'
import { Trash2, RefreshCw, Settings } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Separator } from '@/components/ui/separator'
import { api } from '@/api'
import type { SessionRecord } from '@/types'

function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const toast = (text: string, ok = true) => { setMsg({ text, ok }); setTimeout(() => setMsg(null), 3000) }
  return { msg, toast }
}

interface SessionConfig {
  mode?: string
  ttlSeconds?: number
}

interface ContextConfig {
  enabled?: boolean
  slidingWindowEnabled?: boolean
  maxMessages?: number
  tokenLimitEnabled?: boolean
  maxTokens?: number
}

export function SessionManagement() {
  const [sessions, setSessions] = useState<SessionRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [sessionConfig, setSessionConfig] = useState<SessionConfig>({})
  const [contextConfig, setContextConfig] = useState<ContextConfig>({})
  const [saving, setSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      const [s, sc, cc] = await Promise.all([
        api.sessions(),
        api.sessionConfig().catch(() => ({})),
        api.contextManagement().catch(() => ({})),
      ])
      setSessions(s)
      const scParsed = (sc as { value?: string })?.value ? JSON.parse((sc as { value: string }).value) : sc
      const ccParsed = (cc as { value?: string })?.value ? JSON.parse((cc as { value: string }).value) : cc
      setSessionConfig(scParsed as SessionConfig)
      setContextConfig(ccParsed as ContextConfig)
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function deleteSession(id: string) {
    try {
      await api.deleteSession(id)
      setSessions((prev) => prev.filter((s) => s.id !== id))
      toast('会话已删除')
    } catch { toast('删除失败', false) }
  }

  async function clearAll() {
    if (!confirm('确认清空所有会话？')) return
    try {
      await api.clearSessions()
      setSessions([])
      toast('所有会话已清空')
    } catch { toast('操作失败', false) }
  }

  async function saveSessionConf() {
    setSaving(true)
    try {
      await api.saveSessionConfig(JSON.stringify(sessionConfig))
      toast('会话配置已保存')
    } catch { toast('保存失败', false) }
    finally { setSaving(false) }
  }

  async function saveContextConf() {
    setSaving(true)
    try {
      await api.saveContextManagement(JSON.stringify(contextConfig))
      toast('上下文配置已保存')
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
      <div>
        <h1 className="text-2xl font-bold">会话管理</h1>
        <p className="text-muted-foreground text-sm">管理活跃会话与上下文配置</p>
      </div>

      <Tabs defaultValue="sessions">
        <TabsList>
          <TabsTrigger value="sessions">会话列表</TabsTrigger>
          <TabsTrigger value="session-config">会话配置</TabsTrigger>
          <TabsTrigger value="context">上下文管理</TabsTrigger>
        </TabsList>

        <TabsContent value="sessions" className="mt-4 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">{sessions.length} 个会话</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={load} disabled={loading}>
                <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />刷新
              </Button>
              <Button variant="destructive" size="sm" onClick={clearAll} disabled={sessions.length === 0}>
                清空全部
              </Button>
            </div>
          </div>

          <Card>
            <CardContent className="p-0">
              {loading ? (
                <div className="p-4 space-y-2">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10" />)}</div>
              ) : sessions.length === 0 ? (
                <div className="p-8 text-center text-sm text-muted-foreground">暂无活跃会话</div>
              ) : (
                <div className="divide-y">
                  {sessions.map((s) => (
                    <div key={s.id} className="flex items-center gap-4 px-4 py-3">
                      <div className="flex-1 min-w-0 space-y-0.5">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs truncate text-muted-foreground">{s.id.slice(0, 16)}...</span>
                          <Badge variant={s.status === 'active' ? 'default' : 'secondary'} className="text-xs">{s.status}</Badge>
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {s.model && <span className="mr-3">模型: {s.model}</span>}
                          {s.providerId && <span>提供商: {s.providerId}</span>}
                        </div>
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {new Date(s.updatedAt).toLocaleString('zh-CN')}
                      </div>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deleteSession(s.id)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="session-config" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Settings className="h-4 w-4" />会话配置
              </CardTitle>
              <CardDescription>配置会话模式和过期时间</CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>会话模式</Label>
                  <select
                    className="w-full rounded-md border border-input px-3 py-2 text-sm bg-background"
                    value={sessionConfig.mode ?? 'single'}
                    onChange={(e) => setSessionConfig((p) => ({ ...p, mode: e.target.value }))}
                  >
                    <option value="single">single（单次，不保留历史）</option>
                    <option value="multi">multi（多轮，保留上下文）</option>
                  </select>
                </div>
                <div className="space-y-2">
                  <Label>会话TTL（秒）</Label>
                  <Input
                    type="number"
                    min={60}
                    value={sessionConfig.ttlSeconds ?? 3600}
                    onChange={(e) => setSessionConfig((p) => ({ ...p, ttlSeconds: Number(e.target.value) }))}
                  />
                </div>
              </div>
              <Button onClick={saveSessionConf} disabled={saving}>{saving ? '保存中...' : '保存配置'}</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="context" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">上下文管理</CardTitle>
              <CardDescription>控制多轮对话的上下文大小和裁剪策略</CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-5">
              <div className="flex items-center gap-3">
                <Switch
                  id="ctx-enabled"
                  checked={contextConfig.enabled ?? false}
                  onCheckedChange={(v) => setContextConfig((p) => ({ ...p, enabled: v }))}
                />
                <Label htmlFor="ctx-enabled">启用上下文管理</Label>
              </div>

              {contextConfig.enabled && (
                <>
                  <div className="flex items-center gap-3">
                    <Switch
                      id="ctx-sliding"
                      checked={contextConfig.slidingWindowEnabled ?? false}
                      onCheckedChange={(v) => setContextConfig((p) => ({ ...p, slidingWindowEnabled: v }))}
                    />
                    <div>
                      <Label htmlFor="ctx-sliding">滑动窗口</Label>
                      <p className="text-xs text-muted-foreground">保留最近 N 条消息</p>
                    </div>
                  </div>

                  {contextConfig.slidingWindowEnabled && (
                    <div className="space-y-2 pl-10">
                      <Label>最大消息数</Label>
                      <Input
                        type="number"
                        min={1}
                        className="max-w-[120px]"
                        value={contextConfig.maxMessages ?? 20}
                        onChange={(e) => setContextConfig((p) => ({ ...p, maxMessages: Number(e.target.value) }))}
                      />
                    </div>
                  )}

                  <div className="flex items-center gap-3">
                    <Switch
                      id="ctx-token"
                      checked={contextConfig.tokenLimitEnabled ?? false}
                      onCheckedChange={(v) => setContextConfig((p) => ({ ...p, tokenLimitEnabled: v }))}
                    />
                    <div>
                      <Label htmlFor="ctx-token">Token 限制</Label>
                      <p className="text-xs text-muted-foreground">超出时截断早期消息</p>
                    </div>
                  </div>

                  {contextConfig.tokenLimitEnabled && (
                    <div className="space-y-2 pl-10">
                      <Label>最大 Token 数</Label>
                      <Input
                        type="number"
                        min={1000}
                        className="max-w-[140px]"
                        value={contextConfig.maxTokens ?? 4096}
                        onChange={(e) => setContextConfig((p) => ({ ...p, maxTokens: Number(e.target.value) }))}
                      />
                    </div>
                  )}
                </>
              )}

              <Button onClick={saveContextConf} disabled={saving}>{saving ? '保存中...' : '保存配置'}</Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
