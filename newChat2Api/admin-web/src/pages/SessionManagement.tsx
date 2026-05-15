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
  enabled?: boolean
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

interface ToolCallingConfig {
  enabled?: boolean
  mode?: string
  diagnostics?: boolean
}

export function SessionManagement() {
  const [sessions, setSessions] = useState<SessionRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [sessionConfig, setSessionConfig] = useState<SessionConfig>({})
  const [contextConfig, setContextConfig] = useState<ContextConfig>({})
  const [toolCallingConfig, setToolCallingConfig] = useState<ToolCallingConfig>({})
  const [saving, setSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      const [s, sc, cc, tc] = await Promise.all([
        api.sessions(),
        api.sessionConfig().catch(() => ({})),
        api.contextManagement().catch(() => ({})),
        api.toolCalling().catch(() => ({})),
      ])
      setSessions(s)
      const scParsed = (sc as { value?: string })?.value ? JSON.parse((sc as { value: string }).value) : sc
      const ccParsed = (cc as { value?: string })?.value ? JSON.parse((cc as { value: string }).value) : cc
      setSessionConfig(scParsed as SessionConfig)
      setContextConfig(ccParsed as ContextConfig)
      setToolCallingConfig(tc as ToolCallingConfig)
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

  async function saveToolCallingConf() {
    setSaving(true)
    try {
      await api.saveToolCalling(JSON.stringify(toolCallingConfig))
      toast('工具调用配置已保存')
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
          <TabsTrigger value="tool-calling">工具调用</TabsTrigger>
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
                <div className="p-8 text-center text-sm text-muted-foreground">暂无活跃会话（多轮模式下才会保留会话）</div>
              ) : (
                <div className="divide-y">
                  {sessions.map((s) => (
                    <div key={s.id} className="flex items-center gap-4 px-4 py-3">
                      <div className="flex-1 min-w-0 space-y-0.5">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs truncate text-muted-foreground">{s.id.slice(0, 20)}...</span>
                          <Badge variant={s.status === 'active' ? 'default' : 'secondary'} className="text-xs">{s.status === 'active' ? '活跃' : s.status}</Badge>
                        </div>
                        <div className="text-xs text-muted-foreground flex flex-wrap gap-3">
                          {s.model && <span>模型: {s.model}</span>}
                          {s.providerId && <span>提供商: {s.providerId}</span>}
                          {s.expiresAt && <span>过期: {new Date(s.expiresAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</span>}
                        </div>
                      </div>
                      <div className="text-xs text-muted-foreground shrink-0">
                        更新: {new Date(s.updatedAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                      </div>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deleteSession(s.id)} title="删除会话">
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
              <CardDescription>配置客户端会话的持久化策略。启用会话后，系统会根据 sessionId 跟踪对话状态。</CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-5">
              <div className="flex items-center gap-3">
                <Switch
                  id="session-enabled"
                  checked={sessionConfig.enabled !== false}
                  onCheckedChange={(v) => setSessionConfig((p) => ({ ...p, enabled: v }))}
                />
                <div>
                  <Label htmlFor="session-enabled">启用会话管理</Label>
                  <p className="text-xs text-muted-foreground">关闭后每次请求都会被独立处理，不保留上下文</p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>会话模式</Label>
                  <select
                    className="w-full rounded-md border border-input px-3 py-2 text-sm bg-background"
                    value={sessionConfig.mode ?? 'multi'}
                    onChange={(e) => setSessionConfig((p) => ({ ...p, mode: e.target.value }))}
                  >
                    <option value="multi">multi — 多轮对话，自动维护对话历史</option>
                    <option value="single">single — 单次对话，每次响应后删除会话</option>
                  </select>
                  <p className="text-xs text-muted-foreground">
                    {(sessionConfig.mode ?? 'multi') === 'multi'
                      ? '客户端携带相同 sessionId 时，自动合并历史消息并继续对话'
                      : '每次请求独立，响应后会话自动清除，适合无状态使用场景'}
                  </p>
                </div>
                <div className="space-y-2">
                  <Label>会话过期时间（秒）</Label>
                  <Input
                    type="number"
                    min={60}
                    value={sessionConfig.ttlSeconds ?? 3600}
                    onChange={(e) => setSessionConfig((p) => ({ ...p, ttlSeconds: Number(e.target.value) }))}
                  />
                  <p className="text-xs text-muted-foreground">超时后会话失效，默认 3600 秒（1 小时）</p>
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
              <CardDescription>在多轮对话中，控制发送给提供商的历史消息数量，防止超出模型的 Context 长度限制。</CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-5">
              <div className="flex items-center gap-3">
                <Switch
                  id="ctx-enabled"
                  checked={contextConfig.enabled ?? false}
                  onCheckedChange={(v) => setContextConfig((p) => ({ ...p, enabled: v }))}
                />
                <div>
                  <Label htmlFor="ctx-enabled">启用上下文管理</Label>
                  <p className="text-xs text-muted-foreground">对发往提供商的消息列表进行自动裁剪，适用于多轮模式</p>
                </div>
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
                      <Label htmlFor="ctx-sliding">消息数量限制（滑动窗口）</Label>
                      <p className="text-xs text-muted-foreground">保留最近 N 条非系统消息，system 消息始终保留</p>
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
                      <Label htmlFor="ctx-token">Token 总量限制</Label>
                      <p className="text-xs text-muted-foreground">估算消息 Token 数，超出限制时从早期消息截断，系统消息始终保留</p>
                    </div>
                  </div>

                  {contextConfig.tokenLimitEnabled && (
                    <div className="space-y-2 pl-10">
                      <Label>最大 Token 数</Label>
                      <Input
                        type="number"
                        min={1000}
                        className="max-w-[140px]"
                        value={contextConfig.maxTokens ?? 4000}
                        onChange={(e) => setContextConfig((p) => ({ ...p, maxTokens: Number(e.target.value) }))}
                      />
                      <p className="text-xs text-muted-foreground">按字符数 ÷ 3 估算 Token，建议设为模型 Context 窗口的 60%–70%</p>
                    </div>
                  )}
                </>
              )}

              <Button onClick={saveContextConf} disabled={saving}>{saving ? '保存中...' : '保存配置'}</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="tool-calling" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Settings className="h-4 w-4" />工具调用（Function Calling）
              </CardTitle>
              <CardDescription>
                对于不支持原生 Function Calling 的提供商，通过 Prompt 注入的方式实现工具调用兼容。
                当客户端传入 tools 时，系统将工具定义注入 System Prompt，并解析模型回复中的函数调用指令。
              </CardDescription>
            </CardHeader>
            <Separator />
            <CardContent className="pt-6 space-y-5">
              <div className="flex items-center gap-3">
                <Switch
                  id="tc-enabled"
                  checked={toolCallingConfig.enabled !== false}
                  onCheckedChange={(v) => setToolCallingConfig((p) => ({ ...p, enabled: v }))}
                />
                <div>
                  <Label htmlFor="tc-enabled">启用工具调用兼容</Label>
                  <p className="text-xs text-muted-foreground">关闭后，工具定义将被原样传递给提供商，适用于提供商本身支持 Function Calling 的情况</p>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <Switch
                  id="tc-diagnostics"
                  checked={toolCallingConfig.diagnostics ?? false}
                  onCheckedChange={(v) => setToolCallingConfig((p) => ({ ...p, diagnostics: v }))}
                />
                <div>
                  <Label htmlFor="tc-diagnostics">调试模式</Label>
                  <p className="text-xs text-muted-foreground">开启后，API 响应中会附加工具调用进程详情，便于排查问题</p>
                </div>
              </div>

              <div className="rounded-md bg-muted/50 px-4 py-3 text-xs text-muted-foreground space-y-1">
                <p><span className="font-medium text-foreground">工作方式：</span>提示词注入 (prompt-injection)</p>
                <p><span className="font-medium text-foreground">协议格式：</span>Managed XML &mdash; 工具定义和调用指令均使用 XML 格式嵌入</p>
                <p><span className="font-medium text-foreground">客户端适配：</span>兼容 OpenAI 标准 tools / tool_calls 格式</p>
              </div>

              <Button onClick={saveToolCallingConf} disabled={saving}>{saving ? '保存中...' : '保存配置'}</Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
