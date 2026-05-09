import { useEffect, useState } from 'react'
import { Plus, Trash2, RefreshCw } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { api } from '@/api'
import type { Provider, SystemPrompt } from '@/types'

function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const toast = (text: string, ok = true) => { setMsg({ text, ok }); setTimeout(() => setMsg(null), 3000) }
  return { msg, toast }
}

interface ModelMapping { model: string; preferredProviderId?: string; preferredAccountId?: string }

export function Models() {
  const [providers, setProviders] = useState<Provider[]>([])
  const [mappings, setMappings] = useState<ModelMapping[]>([])
  const [prompts, setPrompts] = useState<SystemPrompt[]>([])
  const [loading, setLoading] = useState(true)
  const [showAddMapping, setShowAddMapping] = useState(false)
  const [showAddPrompt, setShowAddPrompt] = useState(false)
  const [newModel, setNewModel] = useState('')
  const [newPreferred, setNewPreferred] = useState('')
  const [newPromptName, setNewPromptName] = useState('')
  const [newPromptContent, setNewPromptContent] = useState('')
  const [saving, setSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      const [p, m, pr] = await Promise.all([api.providers(), api.modelMappings(), api.systemPrompts()])
      setProviders(p)
      setMappings(m as unknown as ModelMapping[])
      setPrompts(pr)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function addMapping() {
    if (!newModel.trim()) return
    setSaving(true)
    try {
      await api.saveModelMapping({ model: newModel.trim(), preferredProviderId: newPreferred || undefined })
      await load()
      setShowAddMapping(false)
      setNewModel('')
      setNewPreferred('')
      toast('模型映射已保存')
    } catch { toast('保存失败', false) }
    finally { setSaving(false) }
  }

  async function deleteMapping(model: string) {
    try {
      await api.deleteModelMapping(model)
      setMappings((prev) => prev.filter((m) => m.model !== model))
      toast('映射已删除')
    } catch { toast('删除失败', false) }
  }

  async function addPrompt() {
    if (!newPromptName.trim() || !newPromptContent.trim()) return
    setSaving(true)
    try {
      await api.saveSystemPrompt({ name: newPromptName.trim(), content: newPromptContent.trim(), enabled: true })
      await load()
      setShowAddPrompt(false)
      setNewPromptName('')
      setNewPromptContent('')
      toast('提示词已保存')
    } catch { toast('保存失败', false) }
    finally { setSaving(false) }
  }

  async function togglePrompt(p: SystemPrompt) {
    try {
      await api.saveSystemPrompt({ ...p, enabled: !p.enabled })
      await load()
    } catch { toast('操作失败', false) }
  }

  async function deletePrompt(id: string) {
    try {
      await api.deleteSystemPrompt(id)
      setPrompts((prev) => prev.filter((p) => p.id !== id))
      toast('提示词已删除')
    } catch { toast('删除失败', false) }
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
          <h1 className="text-2xl font-bold">模型管理</h1>
          <p className="text-muted-foreground text-sm">查看可用模型、配置映射和系统提示词</p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />刷新
        </Button>
      </div>

      <Tabs defaultValue="models">
        <TabsList>
          <TabsTrigger value="models">模型列表</TabsTrigger>
          <TabsTrigger value="mappings">模型映射</TabsTrigger>
          <TabsTrigger value="prompts">系统提示词</TabsTrigger>
        </TabsList>

        <TabsContent value="models" className="mt-4">
          <div className="space-y-4">
            {loading ? (
              Array.from({ length: 2 }).map((_, i) => <Card key={i}><CardContent className="pt-4"><Skeleton className="h-20" /></CardContent></Card>)
            ) : providers.length === 0 ? (
              <Card><CardContent className="p-8 text-center text-sm text-muted-foreground">暂无提供商</CardContent></Card>
            ) : (
              providers.map((p) => (
                <Card key={p.id}>
                  <CardHeader className="pb-2">
                    <div className="flex items-center gap-3">
                      <CardTitle className="text-base">{p.name}</CardTitle>
                      <Badge variant={p.enabled ? 'default' : 'secondary'}>{p.enabled ? '启用' : '禁用'}</Badge>
                      <span className="text-xs text-muted-foreground ml-auto">{p.vendor}</span>
                    </div>
                  </CardHeader>
                  <Separator />
                  <CardContent className="pt-4">
                    {p.supportedModels?.length > 0 ? (
                      <div className="flex flex-wrap gap-2">
                        {p.supportedModels.map((m) => (
                          <Badge key={m} variant="outline" className="font-mono text-xs">{m}</Badge>
                        ))}
                      </div>
                    ) : (
                      <p className="text-sm text-muted-foreground">无支持模型</p>
                    )}
                  </CardContent>
                </Card>
              ))
            )}
          </div>
        </TabsContent>

        <TabsContent value="mappings" className="mt-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-base">模型映射</CardTitle>
                <CardDescription>将请求模型名称路由到指定提供商</CardDescription>
              </div>
              <Button size="sm" onClick={() => setShowAddMapping(true)}>
                <Plus className="h-4 w-4 mr-2" />添加映射
              </Button>
            </CardHeader>
            <Separator />
            <CardContent className="p-0">
              {loading ? (
                <div className="p-4 space-y-2">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10" />)}</div>
              ) : mappings.length === 0 ? (
                <div className="p-8 text-center text-sm text-muted-foreground">暂无模型映射</div>
              ) : (
                <div className="divide-y">
                  {mappings.map((m) => (
                    <div key={m.model} className="flex items-center gap-4 px-4 py-3">
                      <div className="flex-1 min-w-0">
                        <p className="font-mono text-sm font-medium">{m.model}</p>
                        {m.preferredProviderId && (
                          <p className="text-xs text-muted-foreground">→ {m.preferredProviderId}</p>
                        )}
                      </div>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deleteMapping(m.model)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="prompts" className="mt-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-base">系统提示词</CardTitle>
                <CardDescription>注入到请求的系统提示配置</CardDescription>
              </div>
              <Button size="sm" onClick={() => setShowAddPrompt(true)}>
                <Plus className="h-4 w-4 mr-2" />添加提示词
              </Button>
            </CardHeader>
            <Separator />
            <CardContent className="p-0">
              {loading ? (
                <div className="p-4 space-y-2">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10" />)}</div>
              ) : prompts.length === 0 ? (
                <div className="p-8 text-center text-sm text-muted-foreground">暂无系统提示词</div>
              ) : (
                <div className="divide-y">
                  {prompts.map((p) => (
                    <div key={p.id} className="flex items-center gap-4 px-4 py-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-sm">{p.name}</span>
                          {p.builtin && <Badge variant="secondary" className="text-xs">内置</Badge>}
                          {p.category && <Badge variant="outline" className="text-xs">{p.category}</Badge>}
                        </div>
                        <p className="text-xs text-muted-foreground mt-0.5 truncate max-w-lg">{p.content}</p>
                      </div>
                      <Switch checked={p.enabled} onCheckedChange={() => togglePrompt(p)} />
                      {!p.builtin && (
                        <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deletePrompt(p.id)}>
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog open={showAddMapping} onOpenChange={setShowAddMapping}>
        <DialogContent>
          <DialogHeader><DialogTitle>添加模型映射</DialogTitle></DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>请求模型名称 *</Label>
              <Input placeholder="例如：gpt-4" value={newModel} onChange={(e) => setNewModel(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>优先提供商 ID（可选）</Label>
              <Input placeholder="留空则自动选择" value={newPreferred} onChange={(e) => setNewPreferred(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowAddMapping(false)}>取消</Button>
            <Button onClick={addMapping} disabled={saving || !newModel.trim()}>{saving ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={showAddPrompt} onOpenChange={setShowAddPrompt}>
        <DialogContent className="max-w-xl">
          <DialogHeader><DialogTitle>添加系统提示词</DialogTitle></DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>名称 *</Label>
              <Input placeholder="提示词名称" value={newPromptName} onChange={(e) => setNewPromptName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>内容 *</Label>
              <Textarea placeholder="系统提示词内容..." rows={6} value={newPromptContent} onChange={(e) => setNewPromptContent(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowAddPrompt(false)}>取消</Button>
            <Button onClick={addPrompt} disabled={saving || !newPromptName.trim() || !newPromptContent.trim()}>{saving ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
