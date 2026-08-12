import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const source = readFileSync(resolve(repositoryRoot, 'WEB/src/api/axios.ts'), 'utf8')
const rejectedInterceptors = source.match(
  /return Promise\.reject\((?:error|err)\)/g,
)

assert.equal(
  rejectedInterceptors?.length,
  2,
  '请求与响应拦截器都必须把拒绝状态返回给调用方',
)
