const { execFileSync, spawn } = require('node:child_process')
const fs = require('node:fs')
const http = require('node:http')
const os = require('node:os')
const path = require('node:path')

const url = process.env.LOGIN_LAYOUT_URL || 'http://127.0.0.1:8888/login'
const port = Number(process.env.LOGIN_LAYOUT_CDP_PORT || 9335)
const chromePath =
  process.env.CHROME_PATH ||
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

const viewports = [
  { name: 'desktop-wide', width: 1538, height: 789 },
  { name: 'desktop-medium', width: 1280, height: 789 },
]

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function getJson(targetUrl) {
  return new Promise((resolve, reject) => {
    http
      .get(targetUrl, res => {
        let data = ''
        res.on('data', chunk => {
          data += chunk
        })
        res.on('end', () => {
          try {
            resolve(JSON.parse(data))
          } catch (error) {
            reject(error)
          }
        })
      })
      .on('error', reject)
  })
}

async function waitForCdp() {
  for (let i = 0; i < 50; i += 1) {
    try {
      await getJson(`http://127.0.0.1:${port}/json/version`)
      return
    } catch {
      await sleep(200)
    }
  }
  throw new Error(`Chrome CDP port ${port} did not become ready`)
}

async function connectPage() {
  const targets = await getJson(`http://127.0.0.1:${port}/json/list`)
  const target = targets.find(item => item.type === 'page')
  if (!target) {
    throw new Error('No Chrome page target found')
  }

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    ws.onopen = resolve
    ws.onerror = reject
  })

  let id = 1
  const pending = new Map()
  ws.addEventListener('message', event => {
    const msg = JSON.parse(event.data)
    if (!msg.id || !pending.has(msg.id)) {
      return
    }
    const { resolve, reject } = pending.get(msg.id)
    pending.delete(msg.id)
    if (msg.error) {
      reject(new Error(JSON.stringify(msg.error)))
    } else {
      resolve(msg.result)
    }
  })

  const call = (method, params = {}) => {
    const callId = id
    id += 1
    ws.send(JSON.stringify({ id: callId, method, params }))
    return new Promise((resolve, reject) => {
      pending.set(callId, { resolve, reject })
    })
  }

  await call('Page.enable')
  await call('Runtime.enable')
  return { ws, call }
}

function assertLayout(result) {
  const failures = []

  if (!result.form) {
    failures.push('login card must be rendered')
  }
  if (!result.logoTitle) {
    failures.push('brand title must be rendered')
  }
  if (!result.toolbar) {
    failures.push('toolbar must be rendered')
  }
  if (!result.form || !result.logoTitle || !result.toolbar) {
    return failures
  }

  const viewportCenter = result.width / 2
  const formCenter = (result.form.left + result.form.right) / 2
  const centerOffset = Math.abs(formCenter - viewportCenter)

  if (result.hasHorizontalScroll) {
    failures.push('page must not create horizontal scroll')
  }
  if (result.form.width < 340 || result.form.width > 365) {
    failures.push(`login card width should be about 350px, got ${result.form.width}px`)
  }
  if (centerOffset > 80) {
    failures.push(`login card should stay visually centered, offset ${Math.round(centerOffset)}px`)
  }
  if (result.logoTitle.top > 30) {
    failures.push(`brand title should move upward, title top ${result.logoTitle.top}px`)
  }
  if (result.titleFormOverlap) {
    failures.push('brand title must not overlap login card')
  }
  if (result.toolbarTitleOverlap) {
    failures.push('toolbar must not overlap brand title')
  }

  return failures
}

async function measure(call, viewport) {
  await call('Emulation.setDeviceMetricsOverride', {
    width: viewport.width,
    height: viewport.height,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await call('Page.navigate', {
    url: `${url}${url.includes('?') ? '&' : '?'}layout-test=${viewport.name}-${Date.now()}`,
  })

  for (let i = 0; i < 120; i += 1) {
    const ready = await call('Runtime.evaluate', {
      returnByValue: true,
      expression:
        "Boolean(document.querySelector('.xingyuv-login-form') && document.querySelector('.xingyuv-login-brand') && document.querySelector('.xingyuv-login-toolbar'))",
    })
    if (ready.result.value) {
      break
    }
    await sleep(250)
  }

  await sleep(800)
  const measured = await call('Runtime.evaluate', {
    returnByValue: true,
    expression: `(() => {
      const rect = selector => {
        const el = document.querySelector(selector)
        if (!el) return null
        const r = el.getBoundingClientRect()
        return {
          left: Math.round(r.left),
          right: Math.round(r.right),
          top: Math.round(r.top),
          bottom: Math.round(r.bottom),
          width: Math.round(r.width),
          height: Math.round(r.height),
        }
      }
      const logoTitleEl = document.querySelector('.xingyuv-login-brand .xingyuv-app-logo__title, .xingyuv-login-brand .logo-title')
      const titleRect = logoTitleEl ? logoTitleEl.getBoundingClientRect() : null
      const logoTitle = titleRect ? {
        left: Math.round(titleRect.left),
        right: Math.round(titleRect.right),
        top: Math.round(titleRect.top),
        bottom: Math.round(titleRect.bottom),
        width: Math.round(titleRect.width),
        height: Math.round(titleRect.height),
      } : null
      const intersects = (a, b) => a && b && !(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
      const form = rect('.xingyuv-login-form')
      const toolbar = rect('.xingyuv-login-toolbar')
      return {
        width: innerWidth,
        viewport: ${JSON.stringify(viewport.name)},
        docScrollWidth: document.documentElement.scrollWidth,
        hasHorizontalScroll: document.documentElement.scrollWidth > innerWidth + 1,
        form,
        toolbar,
        logoTitle,
        titleFormOverlap: intersects(logoTitle, form),
        toolbarTitleOverlap: intersects(toolbar, logoTitle),
      }
    })()`,
  })
  return measured.result.value
}

async function main() {
  if (!fs.existsSync(chromePath)) {
    throw new Error(`Chrome not found: ${chromePath}`)
  }

  const userDataDir = path.join(os.tmpdir(), `yfeieye-login-layout-${Date.now()}`)
  const chrome = spawn(chromePath, [
    '--headless=new',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    'about:blank',
  ], {
    stdio: 'ignore',
  })

  try {
    await waitForCdp()
    const { ws, call } = await connectPage()
    const results = []
    for (const viewport of viewports) {
      const result = await measure(call, viewport)
      results.push(result)
    }
    ws.close()

    const failures = results.flatMap(result =>
      assertLayout(result).map(message => `${result.viewport}: ${message}`),
    )
    console.log(JSON.stringify(results, null, 2))
    if (failures.length) {
      console.error(`\nLogin layout regression failed:\n- ${failures.join('\n- ')}`)
      process.exitCode = 1
    }
  } finally {
    if (process.platform === 'win32' && chrome.pid) {
      try {
        execFileSync('taskkill', ['/PID', String(chrome.pid), '/T', '/F'], { stdio: 'ignore' })
      } catch {
        chrome.kill()
      }
    } else {
      chrome.kill()
    }
    await new Promise(resolve => {
      chrome.once('exit', resolve)
      setTimeout(resolve, 2000)
    })
    for (let i = 0; i < 5; i += 1) {
      try {
        fs.rmSync(userDataDir, { recursive: true, force: true })
        break
      } catch (error) {
        if (i === 4) {
          console.warn(`Warning: could not remove temporary Chrome profile ${userDataDir}: ${error.message}`)
          break
        }
        await sleep(300)
      }
    }
  }
}

main().catch(error => {
  console.error(error)
  process.exit(1)
})
