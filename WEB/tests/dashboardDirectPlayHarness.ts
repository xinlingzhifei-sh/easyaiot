import * as assert from 'node:assert/strict'
import { runInNewContext } from 'node:vm'
import { parse } from '@vue/compiler-sfc'
import * as ts from 'typescript'
import { ref } from 'vue'
import { isGb28181Device } from '../src/views/camera/utils/deviceLabel'

interface DirectDevice {
  id: string
  source?: string
  device_kind?: string
}

export function createDashboardDirectPlayHarness(source: string, savedAiChoice: string | null) {
  const { descriptor, errors } = parse(source)
  assert.equal(errors.length, 0)
  assert.ok(descriptor.scriptSetup, 'Dashboard monitor should have a setup script.')
  const script = ts.createSourceFile(
    'VideoMonitor.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true,
  )
  // Execute the component's real state initialization and resolver; stub only storage and the stream API.
  const functionNames = new Set([
    'readPersistedEnableAi', 'shouldEnableAiForPlay', 'resolvePlayUrlsForDevice',
  ])
  const variableNames = new Set(['AI_TOGGLE_STORAGE_KEY', 'enableAi'])
  const declarations = script.statements.filter((statement) => {
    if (ts.isFunctionDeclaration(statement)) {
      return !!statement.name && functionNames.has(statement.name.text)
    }
    return ts.isVariableStatement(statement) && statement.declarationList.declarations.some(
      (declaration) => ts.isIdentifier(declaration.name) && variableNames.has(declaration.name.text),
    )
  })
  const { outputText } = ts.transpileModule(
    declarations.map((statement) => statement.getText(script)).join('\n'),
    { compilerOptions: { target: ts.ScriptTarget.ES2022 } },
  )
  const calls: Array<{ device: DirectDevice; enableAi: boolean }> = []
  const result: {
    enableAi: { value: boolean }
    resolvePlayUrlsForDevice: (device: DirectDevice) => Promise<unknown>
  } = runInNewContext(`${outputText}\n({ enableAi, resolvePlayUrlsForDevice })`, {
    ref,
    isGb28181Device,
    window: {
      sessionStorage: {
        getItem: (key: string) => key === 'yfeieye.dashboard.monitor.enableAi' ? savedAiChoice : null,
      },
    },
    pickDirectPlayUrls: (device: DirectDevice, enableAi: boolean) => {
      calls.push({ device, enableAi })
      return { url: null }
    },
  })
  return { ...result, calls }
}
