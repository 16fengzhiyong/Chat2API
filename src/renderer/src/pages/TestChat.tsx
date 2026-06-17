import { useState, useRef, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Send, Trash2, Image, Video, MessageSquare, Copy, Check, Loader2, Wifi, WifiOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { useSettingsStore } from '@/stores/settingsStore'

interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  /** For image/video results */
  mediaUrl?: string
  mediaType?: 'image' | 'video'
  /** Thinking/reasoning content */
  reasoning?: string
  timestamp: number
}

type ChatMode = 'chat' | 'image' | 'video'

interface Preset {
  label: string
  model: string
  mode: ChatMode
  size?: string
}

const PRESETS: Preset[] = [
  { label: 'Chat (qwen3-max)', model: 'qwen', mode: 'chat' },
  { label: 'Chat (Thinking)', model: 'qwen-thinking', mode: 'chat' },
  { label: 'Chat (Search)', model: 'qwen-search', mode: 'chat' },
  { label: 'Image (t2i)', model: 'qwen-image', mode: 'image', size: '16:9' },
  { label: 'Image + Ref', model: 'qwen-image', mode: 'image', size: '16:9' },
  { label: 'Video (t2v)', model: 'qwen-video', mode: 'video', size: '16:9' },
  { label: 'Video (i2v)', model: 'qwen-video', mode: 'video', size: '16:9' },
]

const SIZE_OPTIONS = ['1:1', '4:3', '3:4', '16:9', '9:16']

export function TestChat() {
  const { t } = useTranslation()
  const config = useSettingsStore((s) => s.config)

  const [baseUrl, setBaseUrl] = useState('http://127.0.0.1:8080')
  const [apiKey, setApiKey] = useState('')
  const [model, setModel] = useState('qwen')
  const [mode, setMode] = useState<ChatMode>('chat')
  const [size, setSize] = useState('16:9')
  const [stream, setStream] = useState(true)
  const [input, setInput] = useState('')
  const [refImageUrl, setRefImageUrl] = useState('')
  const [messages, setMessages] = useState<Message[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [connectionOk, setConnectionOk] = useState<boolean | null>(null)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  // Auto-detect proxy URL from config
  useEffect(() => {
    if (config) {
      const host = config.proxyHost || '127.0.0.1'
      const port = config.proxyPort || 8080
      setBaseUrl(`http://${host}:${port}`)
    }
  }, [config])

  // Check connectivity
  const checkConnection = useCallback(async () => {
    try {
      const res = await fetch(`${baseUrl}/health`, { signal: AbortSignal.timeout(3000) })
      setConnectionOk(res.ok)
    } catch {
      setConnectionOk(false)
    }
  }, [baseUrl])

  useEffect(() => {
    checkConnection()
  }, [checkConnection])

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const addMessage = (msg: Omit<Message, 'id' | 'timestamp'>) => {
    const id = crypto.randomUUID()
    setMessages((prev) => [...prev, { ...msg, id, timestamp: Date.now() }])
    return id
  }

  const updateMessage = (id: string, updates: Partial<Message>) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === id ? { ...m, ...updates } : m)),
    )
  }

  const buildMessages = (userContent: string | Array<{ type: string; text?: string; image_url?: { url: string } }>) => {
    const msgs: Array<{ role: string; content: string | Array<{ type: string; text?: string; image_url?: { url: string } }> }> = []
    // Include last few messages for context
    const recent = messages.slice(-6)
    for (const m of recent) {
      if (m.role === 'user') {
        msgs.push({ role: 'user', content: m.content })
      } else if (m.role === 'assistant' && m.content) {
        msgs.push({ role: 'assistant', content: m.content })
      }
    }
    msgs.push({ role: 'user', content: userContent })
    return msgs
  }

  const handleSend = async () => {
    const text = input.trim()
    if (!text || isLoading) return

    setError(null)

    // Build content - with or without reference image
    let userContent: string | Array<{ type: string; text?: string; image_url?: { url: string } }>
    const refUrl = refImageUrl.trim()
    if (refUrl && (mode === 'image' || mode === 'video')) {
      userContent = [
        { type: 'text', text },
        { type: 'image_url', image_url: { url: refUrl } },
      ]
    } else {
      userContent = text
    }

    addMessage({
      role: 'user',
      content: refUrl ? `${text}\n[ref: ${refUrl}]` : text,
    })

    setInput('')
    setIsLoading(true)

    const assistantMsgId = addMessage({
      role: 'assistant',
      content: '',
    })

    abortRef.current = new AbortController()

    try {
      const body: Record<string, unknown> = {
        model,
        messages: buildMessages(userContent),
        stream: mode === 'video' ? false : stream,
      }
      if (size && (mode === 'image' || mode === 'video')) {
        body.size = size
      }

      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (apiKey.trim()) {
        headers['Authorization'] = `Bearer ${apiKey.trim()}`
      }
      if (mode === 'video') {
        headers['Accept'] = 'text/event-stream'
      }

      const res = await fetch(`${baseUrl}/v1/chat/completions`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: abortRef.current.signal,
      })

      if (!res.ok) {
        const errText = await res.text()
        let errMsg = `HTTP ${res.status}`
        try {
          const errJson = JSON.parse(errText)
          errMsg = errJson.error?.message || errJson.message || errMsg
        } catch {
          errMsg = errText || errMsg
        }
        throw new Error(errMsg)
      }

      const contentType = res.headers.get('content-type') || ''

      if (contentType.includes('text/event-stream') || (stream && mode !== 'video')) {
        // SSE streaming
        await handleStream(res, assistantMsgId)
      } else {
        // Non-streaming JSON
        const data = await res.json()
        const choice = data.choices?.[0]
        const msg = choice?.message || choice?.delta || {}
        const content = msg.content || ''
        const reasoning = msg.reasoning_content || ''
        updateMessage(assistantMsgId, {
          content,
          reasoning,
          mediaUrl: detectMediaUrl(content),
          mediaType: detectMediaType(content),
        })
      }
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        updateMessage(assistantMsgId, { content: '[cancelled]' })
      } else {
        const errMsg = err instanceof Error ? err.message : String(err)
        setError(errMsg)
        updateMessage(assistantMsgId, { content: `[error] ${errMsg}` })
      }
    } finally {
      setIsLoading(false)
      abortRef.current = null
    }
  }

  const handleStream = async (res: Response, msgId: string) => {
    const reader = res.body?.getReader()
    if (!reader) {
      updateMessage(msgId, { content: '[no response body]' })
      return
    }

    const decoder = new TextDecoder()
    let content = ''
    let reasoning = ''
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (!data || data === '[DONE]') continue

        try {
          const parsed = JSON.parse(data)
          const choice = parsed.choices?.[0]
          const delta = choice?.delta || {}

          if (delta.reasoning_content) {
            reasoning += delta.reasoning_content
          }
          if (delta.content) {
            content += delta.content
          }

          updateMessage(msgId, {
            content,
            reasoning,
            mediaUrl: detectMediaUrl(content),
            mediaType: detectMediaType(content),
          })

          if (choice?.finish_reason) {
            // stream finished
          }
        } catch {
          // skip malformed JSON
        }
      }
    }
  }

  const detectMediaUrl = (text: string): string | undefined => {
    const match = text.match(/https?:\/\/\S+\.(?:png|jpg|jpeg|gif|webp|mp4|mov|avi|webm)/i)
    return match ? match[0] : undefined
  }

  const detectMediaType = (text: string): 'image' | 'video' | undefined => {
    const url = detectMediaUrl(text)
    if (!url) return undefined
    return /\.(mp4|mov|avi|webm)/i.test(url) ? 'video' : 'image'
  }

  const handleCancel = () => {
    abortRef.current?.abort()
  }

  const handleClear = () => {
    setMessages([])
    setError(null)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const copyUrl = async (url: string, id: string) => {
    try {
      await navigator.clipboard.writeText(url)
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 2000)
    } catch {
      // ignore
    }
  }

  const applyPreset = (preset: Preset) => {
    setModel(preset.model)
    setMode(preset.mode)
    if (preset.size) setSize(preset.size)
    // For "Image + Ref" preset, don't clear ref input
    if (preset.label !== 'Image + Ref') {
      setRefImageUrl('')
    }
    setStream(preset.mode !== 'video')
  }

  return (
    <div className="flex h-[calc(100vh-4rem)] gap-4 p-4">
      {/* Left: Chat area */}
      <div className="flex flex-1 flex-col gap-3">
        {/* Messages */}
        <Card className="flex-1 flex flex-col min-h-0">
          <CardHeader className="py-3 px-4 flex flex-row items-center justify-between space-y-0">
            <CardTitle className="text-sm font-medium">{t('testChat.title', 'Test Chat')}</CardTitle>
            <div className="flex items-center gap-2">
              {connectionOk === true && (
                <Badge variant="outline" className="text-green-600 border-green-600 gap-1">
                  <Wifi className="h-3 w-3" />
                  {baseUrl}
                </Badge>
              )}
              {connectionOk === false && (
                <Badge variant="outline" className="text-red-600 border-red-600 gap-1">
                  <WifiOff className="h-3 w-3" />
                  {t('testChat.disconnected', 'Disconnected')}
                </Badge>
              )}
              <Button variant="ghost" size="icon" onClick={handleClear} disabled={isLoading} title={t('testChat.clear', 'Clear')}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </CardHeader>
          <CardContent className="flex-1 min-h-0 p-0">
            <ScrollArea className="h-full px-4">
              {messages.length === 0 && (
                <div className="flex flex-col items-center justify-center h-full text-muted-foreground gap-2 py-12">
                  <MessageSquare className="h-8 w-8 opacity-30" />
                  <p className="text-sm">{t('testChat.emptyHint', 'Type a message to start testing')}</p>
                </div>
              )}
              <div className="space-y-3 py-4">
                {messages.map((msg) => (
                  <div key={msg.id} className={cn('flex', msg.role === 'user' ? 'justify-end' : 'justify-start')}>
                    <div className={cn(
                      'max-w-[80%] rounded-lg px-3 py-2 text-sm',
                      msg.role === 'user'
                        ? 'bg-primary text-primary-foreground'
                        : msg.content.startsWith('[error]')
                          ? 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
                          : msg.content === '[cancelled]'
                            ? 'bg-muted text-muted-foreground italic'
                            : 'bg-muted',
                    )}>
                      {/* Reasoning */}
                      {msg.reasoning && (
                        <details className="mb-1">
                          <summary className="text-xs text-muted-foreground cursor-pointer">
                            {t('testChat.thinking', 'Thinking')}...
                          </summary>
                          <p className="text-xs text-muted-foreground mt-1 whitespace-pre-wrap border-l-2 border-muted-foreground/30 pl-2">
                            {msg.reasoning}
                          </p>
                        </details>
                      )}
                      {/* Content */}
                      {msg.content && (
                        <p className="whitespace-pre-wrap break-words">{msg.content}</p>
                      )}
                      {/* Media preview */}
                      {msg.mediaUrl && msg.mediaType === 'image' && (
                        <div className="mt-2">
                          <img src={msg.mediaUrl} alt="Generated" className="max-w-full rounded-md max-h-80 object-contain" />
                          <Button
                            variant="ghost" size="sm" className="mt-1 h-6 text-xs"
                            onClick={() => copyUrl(msg.mediaUrl!, msg.id)}
                          >
                            {copiedId === msg.id ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                            {copiedId === msg.id ? t('testChat.copied', 'Copied') : t('testChat.copyUrl', 'Copy URL')}
                          </Button>
                        </div>
                      )}
                      {msg.mediaUrl && msg.mediaType === 'video' && (
                        <div className="mt-2">
                          <video controls className="max-w-full rounded-md max-h-80">
                            <source src={msg.mediaUrl} />
                          </video>
                          <Button
                            variant="ghost" size="sm" className="mt-1 h-6 text-xs"
                            onClick={() => copyUrl(msg.mediaUrl!, msg.id)}
                          >
                            {copiedId === msg.id ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                            {copiedId === msg.id ? t('testChat.copied', 'Copied') : t('testChat.copyUrl', 'Copy URL')}
                          </Button>
                        </div>
                      )}
                      {/* Loading indicator */}
                      {isLoading && msg.id === messages[messages.length - 1]?.id && msg.role === 'assistant' && !msg.content && (
                        <div className="flex items-center gap-2">
                          <Loader2 className="h-4 w-4 animate-spin" />
                          <span className="text-xs text-muted-foreground">
                            {mode === 'video' ? t('testChat.generatingVideo', 'Generating video...') : t('testChat.generating', 'Generating...')}
                          </span>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
                <div ref={messagesEndRef} />
              </div>
            </ScrollArea>
          </CardContent>
        </Card>

        {/* Error banner */}
        {error && (
          <div className="rounded-md bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 px-3 py-2 text-sm text-red-600 dark:text-red-400">
            {error}
          </div>
        )}

        {/* Input area */}
        <div className="flex gap-2 items-end">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={t('testChat.inputPlaceholder', 'Type a message... (Enter to send, Shift+Enter for newline)')}
            className="min-h-[60px] max-h-[120px] resize-none"
            disabled={isLoading}
            rows={2}
          />
          <div className="flex flex-col gap-1">
            {isLoading ? (
              <Button variant="destructive" size="icon" onClick={handleCancel} title={t('testChat.cancel', 'Cancel')}>
                <Trash2 className="h-4 w-4" />
              </Button>
            ) : (
              <Button size="icon" onClick={handleSend} disabled={!input.trim()} title={t('testChat.send', 'Send')}>
                <Send className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* Right: Settings sidebar */}
      <div className="w-72 flex flex-col gap-3">
        {/* Connection */}
        <Card>
          <CardHeader className="py-3 px-4">
            <CardTitle className="text-sm font-medium">{t('testChat.connection', 'Connection')}</CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-4 pt-0 space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">{t('testChat.baseUrl', 'API URL')}</Label>
              <Input
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                className="h-8 text-xs font-mono"
                placeholder="http://127.0.0.1:8080"
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">{t('testChat.apiKey', 'API Key (optional)')}</Label>
              <Input
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                className="h-8 text-xs font-mono"
                placeholder="sk-..."
                type="password"
              />
            </div>
            <Button variant="outline" size="sm" className="w-full" onClick={checkConnection}>
              {connectionOk === true ? <Wifi className="h-3 w-3 mr-1 text-green-600" /> : <WifiOff className="h-3 w-3 mr-1" />}
              {t('testChat.testConnection', 'Test Connection')}
            </Button>
          </CardContent>
        </Card>

        {/* Quick presets */}
        <Card>
          <CardHeader className="py-3 px-4">
            <CardTitle className="text-sm font-medium">{t('testChat.presets', 'Presets')}</CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-4 pt-0">
            <div className="flex flex-wrap gap-1.5">
              {PRESETS.map((preset) => (
                <Badge
                  key={preset.label}
                  variant={model === preset.model && mode === preset.mode ? 'default' : 'outline'}
                  className="cursor-pointer hover:opacity-80"
                  onClick={() => applyPreset(preset)}
                >
                  {preset.mode === 'image' ? <Image className="h-3 w-3 mr-1" /> : preset.mode === 'video' ? <Video className="h-3 w-3 mr-1" /> : <MessageSquare className="h-3 w-3 mr-1" />}
                  {preset.label}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Model & Mode */}
        <Card>
          <CardHeader className="py-3 px-4">
            <CardTitle className="text-sm font-medium">{t('testChat.config', 'Configuration')}</CardTitle>
          </CardHeader>
          <CardContent className="px-4 pb-4 pt-0 space-y-3">
            <div className="space-y-1">
              <Label className="text-xs">{t('testChat.model', 'Model')}</Label>
              <Input
                value={model}
                onChange={(e) => {
                  setModel(e.target.value)
                  const lower = e.target.value.toLowerCase()
                  if (lower.includes('image') || lower.includes('t2i')) setMode('image')
                  else if (lower.includes('video') || lower.includes('t2v')) setMode('video')
                  else setMode('chat')
                }}
                className="h-8 text-xs"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs">{t('testChat.mode', 'Mode')}</Label>
              <Select value={mode} onValueChange={(v) => { setMode(v as ChatMode); if (v === 'video') setStream(false) }}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="chat">
                    <span className="flex items-center gap-1"><MessageSquare className="h-3 w-3" /> {t('testChat.modeChat', 'Chat')}</span>
                  </SelectItem>
                  <SelectItem value="image">
                    <span className="flex items-center gap-1"><Image className="h-3 w-3" /> {t('testChat.modeImage', 'Image')}</span>
                  </SelectItem>
                  <SelectItem value="video">
                    <span className="flex items-center gap-1"><Video className="h-3 w-3" /> {t('testChat.modeVideo', 'Video')}</span>
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            {(mode === 'image' || mode === 'video') && (
              <div className="space-y-1">
                <Label className="text-xs">{t('testChat.size', 'Size / Ratio')}</Label>
                <Select value={size} onValueChange={setSize}>
                  <SelectTrigger className="h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SIZE_OPTIONS.map((s) => (
                      <SelectItem key={s} value={s}>{s}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="flex items-center justify-between">
              <Label className="text-xs">{t('testChat.stream', 'Stream')}</Label>
              <Switch
                checked={stream}
                onCheckedChange={setStream}
                disabled={mode === 'video'}
              />
            </div>
          </CardContent>
        </Card>

        {/* Reference image */}
        {(mode === 'image' || mode === 'video') && (
          <Card>
            <CardHeader className="py-3 px-4">
              <CardTitle className="text-sm font-medium">{t('testChat.refImage', 'Reference Image')}</CardTitle>
            </CardHeader>
            <CardContent className="px-4 pb-4 pt-0 space-y-2">
              <Input
                value={refImageUrl}
                onChange={(e) => setRefImageUrl(e.target.value)}
                className="h-8 text-xs font-mono"
                placeholder="https://example.com/image.png"
              />
              {refImageUrl.trim() && (
                <img
                  src={refImageUrl.trim()}
                  alt="Reference"
                  className="max-w-full rounded-md max-h-32 object-contain border"
                  onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }}
                />
              )}
              <p className="text-xs text-muted-foreground">
                {t('testChat.refImageHint', 'For i2i or i2v, provide a reference image URL')}
              </p>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
