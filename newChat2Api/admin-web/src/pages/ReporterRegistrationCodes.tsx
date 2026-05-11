import { useEffect, useState } from 'react'
import { Copy, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { api } from '@/api'
import type { CreatedReporterRegistrationCode, ReporterRegistrationCode } from '@/types'

function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const toast = (text: string, ok = true) => {
    setMsg({ text, ok })
    setTimeout(() => setMsg(null), 3000)
  }
  return { msg, toast }
}

export function ReporterRegistrationCodes() {
  const [codes, setCodes] = useState<ReporterRegistrationCode[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [manualCode, setManualCode] = useState('')
  const [createdCode, setCreatedCode] = useState<CreatedReporterRegistrationCode | null>(null)
  const [editingCode, setEditingCode] = useState<ReporterRegistrationCode | null>(null)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editCode, setEditCode] = useState('')
  const [saving, setSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      setCodes(await api.reporterRegistrationCodes())
    } catch {
      toast('加载注册口令失败', false)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function createCode() {
    if (!name.trim()) return
    setSaving(true)
    try {
      const created = await api.createReporterRegistrationCode({ name: name.trim(), description: description.trim() || undefined, code: manualCode.trim() || undefined })
      setCodes((prev) => [...prev, created])
      setCreatedCode(created)
      setShowCreate(false)
      setName('')
      setDescription('')
      setManualCode('')
      toast('注册口令已创建')
    } catch (error) {
      toast(error instanceof Error ? error.message : '创建失败', false)
    } finally {
      setSaving(false)
    }
  }

  async function toggleCode(code: ReporterRegistrationCode) {
    try {
      const updated = await api.updateReporterRegistrationCode(code.id, { enabled: !code.enabled })
      setCodes((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
      toast(`注册口令已${updated.enabled ? '启用' : '禁用'}`)
    } catch {
      toast('操作失败', false)
    }
  }

  function openEdit(code: ReporterRegistrationCode) {
    setEditingCode(code)
    setEditName(code.name)
    setEditDescription(code.description || '')
    setEditCode('')
  }

  async function saveEdit() {
    if (!editingCode || !editName.trim()) return
    setSaving(true)
    try {
      const updated = await api.updateReporterRegistrationCode(editingCode.id, { name: editName.trim(), description: editDescription.trim() || undefined, code: editCode.trim() || undefined })
      setCodes((prev) => prev.map((item) => (item.id === updated.id ? updated : item)))
      setEditingCode(null)
      setEditCode('')
      toast('注册口令已更新')
    } catch (error) {
      toast(error instanceof Error ? error.message : '保存失败', false)
    } finally {
      setSaving(false)
    }
  }

  async function deleteCode(id: string) {
    if (!confirm('确认删除此注册口令？删除后使用该口令的上报端将无法重新注册。')) return
    try {
      await api.deleteReporterRegistrationCode(id)
      setCodes((prev) => prev.filter((item) => item.id !== id))
      toast('注册口令已删除')
    } catch {
      toast('删除失败', false)
    }
  }

  function copy(value: string) {
    navigator.clipboard.writeText(value).then(() => toast('已复制到剪贴板'))
  }

  return (
    <div className="space-y-6">
      {msg && <div className={`fixed top-4 right-4 z-50 px-4 py-2 rounded-md text-sm text-white shadow-lg ${msg.ok ? 'bg-primary' : 'bg-destructive'}`}>{msg.text}</div>}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">上报注册口令</h1>
          <p className="text-muted-foreground text-sm">管理 Reporter 客户端注册到后端时使用的口令。</p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4 mr-2" />新建口令
        </Button>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-base">口令列表</CardTitle>
            <CardDescription>{codes.length} 个注册口令，可在列表中复制给 Reporter 客户端使用</CardDescription>
          </div>
          <Button variant="ghost" size="sm" onClick={load} disabled={loading}>
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </CardHeader>
        <Separator />
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 text-sm text-muted-foreground">加载中...</div>
          ) : codes.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">
              <p>暂无注册口令，请先创建一个给上报端使用。</p>
              <Button className="mt-4" onClick={() => setShowCreate(true)}>
                <Plus className="h-4 w-4 mr-2" />创建第一个口令
              </Button>
            </div>
          ) : (
            <div className="divide-y">
              {codes.map((code) => (
                <div key={code.id} className="flex items-center gap-4 px-4 py-3">
                  <div className="flex-1 min-w-0 space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm truncate">{code.name}</span>
                      <Badge variant={code.enabled ? 'default' : 'secondary'}>{code.enabled ? '启用' : '禁用'}</Badge>
                    </div>
                    {code.description && <p className="text-xs text-muted-foreground">{code.description}</p>}
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <span className="font-mono truncate">{code.code || '此口令创建于加密保存前，请编辑重置后再复制'}</span>
                      {code.code && (
                        <Button variant="ghost" size="sm" className="h-6 px-2" onClick={() => copy(code.code!)}>
                          <Copy className="h-3 w-3 mr-1" />复制
                        </Button>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground">创建时间：{new Date(code.createdAt).toLocaleString('zh-CN')}</p>
                  </div>
                  <Switch checked={code.enabled} onCheckedChange={() => toggleCode(code)} />
                  <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => openEdit(code)}>
                    <Pencil className="h-3.5 w-3.5" />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deleteCode(code.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={showCreate} onOpenChange={setShowCreate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建注册口令</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>名称 *</Label>
              <Input value={name} onChange={(event) => setName(event.target.value)} placeholder="例如：办公室上报端" />
            </div>
            <div className="space-y-2">
              <Label>备注</Label>
              <Input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="可选" />
            </div>
            <div className="space-y-2">
              <Label>自定义口令</Label>
              <Input type="password" value={manualCode} onChange={(event) => setManualCode(event.target.value)} placeholder="留空则自动生成" />
              <p className="text-xs text-muted-foreground">至少 8 位；后端会保存哈希用于校验，并加密保存口令用于后续复制。</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreate(false)}>取消</Button>
            <Button onClick={createCode} disabled={saving || !name.trim()}>{saving ? '创建中...' : '创建'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(editingCode)} onOpenChange={(open) => { if (!open) setEditingCode(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑注册口令</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>名称 *</Label>
              <Input value={editName} onChange={(event) => setEditName(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>备注</Label>
              <Input value={editDescription} onChange={(event) => setEditDescription(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>重置口令</Label>
              <Input type="password" value={editCode} onChange={(event) => setEditCode(event.target.value)} placeholder="留空则不修改" />
              <p className="text-xs text-muted-foreground">需要更换口令或历史口令无法复制时填写。</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditingCode(null)}>取消</Button>
            <Button onClick={saveEdit} disabled={saving || !editName.trim()}>{saving ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(createdCode)} onOpenChange={(open) => { if (!open) setCreatedCode(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>注册口令已生成</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <p className="text-sm text-muted-foreground">可以立即复制保存，之后也可以在列表中再次复制。</p>
            <div className="flex items-center gap-2 rounded-md border bg-muted/40 p-3 font-mono text-sm break-all">
              <span className="flex-1">{createdCode?.code}</span>
              <Button variant="ghost" size="icon" onClick={() => createdCode && copy(createdCode.code)}>
                <Copy className="h-4 w-4" />
              </Button>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => setCreatedCode(null)}>我已保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
