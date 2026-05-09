import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { Sidebar } from '@/components/Sidebar'
import { Toaster } from '@/components/ui/toaster'
import { Dashboard } from '@/pages/Dashboard'
import { Providers } from '@/pages/Providers'
import { ProxySettings } from '@/pages/ProxySettings'
import { Models } from '@/pages/Models'
import { SessionManagement } from '@/pages/SessionManagement'
import { ApiKeys } from '@/pages/ApiKeys'
import { Logs } from '@/pages/Logs'
import { Login } from '@/pages/Login'
import { isAuthenticated } from '@/api'

function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar />
      <main className="flex-1 overflow-y-auto p-6">
        {children}
      </main>
    </div>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const [authenticated, setAuthenticated] = useState(isAuthenticated())

  useEffect(() => {
    const refresh = () => setAuthenticated(isAuthenticated())
    window.addEventListener('storage', refresh)
    window.addEventListener('chat2api:unauthorized', refresh)
    return () => {
      window.removeEventListener('storage', refresh)
      window.removeEventListener('chat2api:unauthorized', refresh)
    }
  }, [])

  if (!authenticated) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={isAuthenticated() ? <Navigate to="/" replace /> : <Login />} />
        <Route path="/" element={<ProtectedRoute><Layout><Dashboard /></Layout></ProtectedRoute>} />
        <Route path="/providers" element={<ProtectedRoute><Layout><Providers /></Layout></ProtectedRoute>} />
        <Route path="/proxy" element={<ProtectedRoute><Layout><ProxySettings /></Layout></ProtectedRoute>} />
        <Route path="/models" element={<ProtectedRoute><Layout><Models /></Layout></ProtectedRoute>} />
        <Route path="/session" element={<ProtectedRoute><Layout><SessionManagement /></Layout></ProtectedRoute>} />
        <Route path="/api-keys" element={<ProtectedRoute><Layout><ApiKeys /></Layout></ProtectedRoute>} />
        <Route path="/logs" element={<ProtectedRoute><Layout><Logs /></Layout></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Toaster />
    </BrowserRouter>
  )
}
