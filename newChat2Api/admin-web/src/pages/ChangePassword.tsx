import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api } from '@/api'

interface ChangePasswordProps {
  required?: boolean
}

export function ChangePassword({ required = false }: ChangePasswordProps) {
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null)
  const [loading, setLoading] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (newPassword !== confirmPassword) {
      setMessage({ text: '两次输入的新密码不一致', ok: false })
      return
    }
    setLoading(true)
    try {
      await api.changePassword(currentPassword, newPassword)
      setMessage({ text: '密码已修改', ok: true })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      window.dispatchEvent(new Event('chat2api:auth-updated'))
      if (required) {
        navigate('/', { replace: true })
      }
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : '修改失败', ok: false })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={required ? 'min-h-screen bg-background flex items-center justify-center p-6' : 'space-y-6'}>
      <Card className={required ? 'w-full max-w-md' : 'max-w-xl'}>
        <CardHeader className="space-y-3">
          <div className="h-12 w-12 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
            <ShieldCheck className="h-6 w-6" />
          </div>
          <div>
            <CardTitle>{required ? '首次登录需要修改密码' : '修改密码'}</CardTitle>
            <CardDescription>{required ? '默认密码仅用于初始化，请修改后继续使用管理控制台。' : '修改当前管理员账号密码。'}</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-2">
              <Label htmlFor="currentPassword">当前密码</Label>
              <Input id="currentPassword" type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} autoComplete="current-password" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="newPassword">新密码</Label>
              <Input id="newPassword" type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" />
              <p className="text-xs text-muted-foreground">至少 8 位，不能继续使用默认密码。</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">确认新密码</Label>
              <Input id="confirmPassword" type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" />
            </div>
            {message && <div className={`rounded-md border px-3 py-2 text-sm ${message.ok ? 'border-primary/30 bg-primary/10 text-primary' : 'border-destructive/30 bg-destructive/10 text-destructive'}`}>{message.text}</div>}
            <Button className="w-full" disabled={loading || !currentPassword || !newPassword || !confirmPassword}>
              {loading ? '保存中...' : '保存新密码'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
