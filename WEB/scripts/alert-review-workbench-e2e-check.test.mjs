import { spawn } from 'node:child_process'

const result = await runScript(['--mode=invalid'])

if (result.code === 0) {
  throw new Error('expected invalid mode to fail')
}

if (!result.stderr.includes('unsupported alert review E2E mode')) {
  throw new Error(`expected unsupported mode message, got: ${result.stderr || result.stdout}`)
}

for (const mode of ['dev-api-mock', 'dev-api-real-drawer']) {
  const browserResult = await runScript([`--mode=${mode}`])
  if (browserResult.code !== 0)
    throw new Error(`expected ${mode} to pass, got: ${browserResult.stderr || browserResult.stdout}`)
}

console.log('alert-review-workbench-e2e-check.test OK')

function runScript(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ['scripts/alert-review-workbench-e2e-check.mjs', ...args], {
      cwd: process.cwd(),
      windowsHide: true,
    })
    let stdout = ''
    let stderr = ''
    const timeout = setTimeout(() => {
      const rejectTimeout = () => reject(new Error('alert-review-workbench-e2e-check test timed out'))
      if (child.pid) {
        void terminateProcessTree(child.pid).finally(rejectTimeout)
      }
      else {
        child.kill()
        rejectTimeout()
      }
    }, 150000)
    child.stdout.on('data', chunk => {
      stdout += chunk
    })
    child.stderr.on('data', chunk => {
      stderr += chunk
    })
    child.on('error', error => {
      clearTimeout(timeout)
      reject(error)
    })
    child.on('close', code => {
      clearTimeout(timeout)
      resolve({ code, stdout, stderr })
    })
  })
}

function terminateProcessTree(pid) {
  return new Promise(resolve => {
    const taskkill = spawn('taskkill', ['/PID', String(pid), '/T', '/F'], { windowsHide: true })
    taskkill.on('close', resolve)
    taskkill.on('error', resolve)
  })
}
