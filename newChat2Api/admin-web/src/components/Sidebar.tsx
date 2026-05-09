import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Server, Settings2, FileText, Key, Cpu, MessageSquare, PanelLeftClose, PanelLeftOpen, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'
import { api, clearSession, getConfig } from '@/api'

const navItems = [
  { title: '仪表盘', href: '/', icon: LayoutDashboard },
  { title: '提供商', href: '/providers', icon: Server },
  { title: '代理设置', href: '/proxy', icon: Settings2 },
  { title: '模型管理', href: '/models', icon: Cpu },
  { title: '会话管理', href: '/session', icon: MessageSquare },
  { title: 'API密钥', href: '/api-keys', icon: Key },
  { title: '请求日志', href: '/logs', icon: FileText },
]

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const config = getConfig()

  async function logout() {
    try {
      await api.logout()
    } catch {
      clearSession()
    }
    window.dispatchEvent(new Event('chat2api:unauthorized'))
    navigate('/login', { replace: true })
  }

  return (
    <aside
      className={cn(
        'flex flex-col shrink-0 h-screen bg-card border-r border-border transition-all duration-300',
        collapsed ? 'w-[64px]' : 'w-56'
      )}
    >
      <div className={cn('flex items-center gap-3 px-4 py-5 border-b border-border', collapsed && 'justify-center px-0')}>
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground text-xs font-black">
          C2
        </div>
        {!collapsed && (
          <div className="overflow-hidden">
            <p className="text-sm font-bold leading-none truncate">Chat2API</p>
            <p className="text-xs text-muted-foreground mt-0.5 truncate">管理控制台</p>
          </div>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto overflow-x-hidden py-3 px-2 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.href}
            to={item.href}
            end={item.href === '/'}
            title={collapsed ? item.title : undefined}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                collapsed && 'justify-center px-2',
                isActive
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
              )
            }
          >
            <item.icon className="h-4 w-4 shrink-0" />
            {!collapsed && <span className="truncate">{item.title}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-border p-2 space-y-1">
        {!collapsed && config.username && <div className="px-2 py-1 text-xs text-muted-foreground truncate">当前用户：{config.username}</div>}
        <button
          onClick={logout}
          className="flex w-full items-center justify-center rounded-md p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          title="退出登录"
        >
          <LogOut className="h-4 w-4" />
          {!collapsed && <span className="ml-2 text-xs">退出登录</span>}
        </button>
        <button
          onClick={() => setCollapsed((v) => !v)}
          className="flex w-full items-center justify-center rounded-md p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          title={collapsed ? '展开侧边栏' : '折叠侧边栏'}
        >
          {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
          {!collapsed && <span className="ml-2 text-xs">折叠</span>}
        </button>
      </div>
    </aside>
  )
}
