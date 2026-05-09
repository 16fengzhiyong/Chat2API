import { useEffect, useState } from 'react'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { Activity, CheckCircle, XCircle, Clock, Server, RefreshCw } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { api } from '@/api'
import type { Provider } from '@/types'

interface Stats {
  totalRequests: number
  successRequests: number
  failedRequests: number
  averageLatency: number
}

interface DayStats {
  date: string
  total: number
  success: number
  failed: number
}

function StatCard({ title, value, icon: Icon, description }: { title: string; value: string | number; icon: React.ComponentType<{ className?: string }>; description?: string }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        <Icon className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {description && <p className="text-xs text-muted-foreground mt-1">{description}</p>}
      </CardContent>
    </Card>
  )
}

export function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [daily, setDaily] = useState<DayStats[]>([])
  const [providers, setProviders] = useState<Provider[]>([])
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    try {
      const [s, d, p] = await Promise.all([
        api.statistics(),
        api.dailyStatistics(),
        api.providers(),
      ])
      setStats(s as unknown as Stats)
      setDaily(d as unknown as DayStats[])
      setProviders(p)
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const enabledProviders = providers.filter((p) => p.enabled)
  const chartData = daily.map((d) => ({ ...d, date: d.date.slice(5) }))

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">仪表盘</h1>
          <p className="text-muted-foreground text-sm">系统运行状态概览</p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          刷新
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {loading ? (
          Array.from({ length: 4 }).map((_, i) => (
            <Card key={i}><CardContent className="pt-6"><Skeleton className="h-16" /></CardContent></Card>
          ))
        ) : (
          <>
            <StatCard title="总请求数" value={stats?.totalRequests ?? 0} icon={Activity} description="全部时段" />
            <StatCard title="成功请求" value={stats?.successRequests ?? 0} icon={CheckCircle} description={`成功率 ${stats ? Math.round((stats.successRequests / Math.max(stats.totalRequests, 1)) * 100) : 0}%`} />
            <StatCard title="失败请求" value={stats?.failedRequests ?? 0} icon={XCircle} description="包含所有错误" />
            <StatCard title="平均延迟" value={`${Math.round(stats?.averageLatency ?? 0)}ms`} icon={Clock} description="所有请求" />
          </>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">近7天请求趋势</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-48" />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <AreaChart data={chartData}>
                  <defs>
                    <linearGradient id="colorSuccess" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(140 25% 50%)" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="hsl(140 25% 50%)" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(0 50% 60%)" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="hsl(0 50% 60%)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(40 15% 85%)" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Area type="monotone" dataKey="success" stroke="hsl(140 25% 50%)" fill="url(#colorSuccess)" name="成功" />
                  <Area type="monotone" dataKey="failed" stroke="hsl(0 50% 60%)" fill="url(#colorFailed)" name="失败" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Server className="h-4 w-4" />
              提供商状态
            </CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-8" />)}</div>
            ) : (
              <div className="space-y-2">
                {providers.length === 0 ? (
                  <p className="text-sm text-muted-foreground">暂无提供商</p>
                ) : (
                  providers.map((p) => (
                    <div key={p.id} className="flex items-center justify-between py-1">
                      <span className="text-sm font-medium truncate">{p.name}</span>
                      <Badge variant={p.enabled ? 'default' : 'secondary'}>
                        {p.enabled ? '启用' : '禁用'}
                      </Badge>
                    </div>
                  ))
                )}
                {providers.length > 0 && (
                  <p className="text-xs text-muted-foreground pt-2 border-t">
                    {enabledProviders.length}/{providers.length} 个启用
                  </p>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
