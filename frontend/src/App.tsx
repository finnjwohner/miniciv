import { useEffect, useState } from 'react'

type ServerStatus = 'checking' | 'up' | 'down' | 'unreachable'

function App() {
  const [status, setStatus] = useState<ServerStatus>('checking')

  useEffect(() => {
    fetch('/api/health')
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((body: { status: string }) => setStatus(body.status === 'UP' ? 'up' : 'down'))
      .catch(() => setStatus('unreachable'))
  }, [])

  return (
    <main>
      <h1>miniciv</h1>
      <p>Server: {status}</p>
    </main>
  )
}

export default App
