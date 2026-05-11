import { ChangePassword } from '@/pages/ChangePassword'

export function SecuritySettings() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">安全设置</h1>
        <p className="text-muted-foreground text-sm">管理当前管理员账号密码。</p>
      </div>
      <ChangePassword />
    </div>
  )
}
