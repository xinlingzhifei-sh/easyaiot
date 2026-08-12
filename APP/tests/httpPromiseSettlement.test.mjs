import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const repositoryRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const source = readFileSync(resolve(repositoryRoot, 'APP/src/http/http.ts'), 'utf8')

test('encrypted response failures reject the outer request promise', () => {
  const decryptFailureHandler = source.match(
    /catch \(error\) \{[\s\S]{0,300}?return reject\(new Error\(`/,
  )

  assert.ok(
    decryptFailureHandler,
    'decryptResponse 异常必须 reject 外层 Promise，不能只记录日志后 return',
  )
})
