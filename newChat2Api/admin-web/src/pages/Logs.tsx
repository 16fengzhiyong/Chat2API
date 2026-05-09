import { useEffect, useState } from 'react'
import { RefreshCw, Search, CheckCircle, XCircle, Zap } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { api } from '@/api'
import type { RequestLog } from '@/types'

export function Logs() {
  const [logs, setLogs] = useState<RequestLog[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  async function load() {
    setLoading(true)
    try {
      const data = await api.logs()
      setLogs([...data].reverse())
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const filtered = logs.filter((l) =>
    !search || [l.model, l.actualModel, l.providerId, l.status].some((v) => v?.toLowerCase().includes(search.toLowerCase()))
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">请求日志</h1>
          <p className="text-muted-foreground text-sm">查看所有代理请求的详细记录</p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          刷新
        </Button>
      </div>

      <div className="flex gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="搜索模型、提供商、状态..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <span className="text-sm text-muted-foreground self-center">{filtered.length} 条</span>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">日志列表</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10" />)}
            </div>
          ) : filtered.length === 0 ? (
            <div className="p-8 text-center text-sm text-muted-foreground">暂无日志记录</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="text-left px-4 py-2.5 font-medium text-muted-foreground">时间</th>
                    <th className="text-left px-4 py-2.5 font-medium text-muted-foreground">状态</th>
                    <th className="text-left px-4 py-2.5 font-medium text-muted-foreground">模型</th>
                    <th className="text-left px-4 py-2.5 font-medium text-muted-foreground">提供商</th>
                    <th className="text-right px-4 py-2.5 font-medium text-muted-foreground">延迟</th>
                    <th className="text-left px-4 py-2.5 font-medium text-muted-foreground">流式</th>
                    <th className="text-left px-4 py-2.5 font-medium text-muted-foreground">错误</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {filtered.map((log) => (
                    <tr key={log.id} className="hover:bg-muted/20 transition-colors">
                      <td className="px-4 py-2.5 text-xs text-muted-foreground whitespace-nowrap">
                        {new Date(log.timestamp).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                      </td>
                      <td className="px-4 py-2.5">
                        {log.status === 'success' ? (
                          <Badge variant="default" className="bg-green-100 text-green-700 hover:bg-green-100">
                            <CheckCircle className="h-3 w-3 mr-1" />成功
                          </Badge>
                        ) : (
                          <Badge variant="destructive" className="bg-red-100 text-red-700 hover:bg-red-100">
                            <XCircle className="h-3 w-3 mr-1" />失败
                          </Badge>
                        )}
                      </td>
                      <td className="px-4 py-2.5 font-mono text-xs max-w-[140px] truncate" title={log.model}>
                        {log.actualModel || log.model || '-'}
                      </td>
                      <td className="px-4 py-2.5 text-xs max-w-[100px] truncate text-muted-foreground" title={log.providerId}>
                        {log.providerId || '-'}
                      </td>
                      <td className="px-4 py-2.5 text-right text-xs">
                        <span className="flex items-center justify-end gap-1">
                          <Zap className="h-3 w-3 text-muted-foreground" />
                          {log.latency}ms
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-xs">
                        {log.streamRequest ? <Badge variant="outline" className="text-xs">流式</Badge> : '-'}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-destructive max-w-[200px] truncate" title={log.errorMessage}>
                        {log.errorMessage || '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
