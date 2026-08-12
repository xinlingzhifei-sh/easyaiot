import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const algorithmTaskModal = readFileSync(
  fileURLToPath(
    new URL(
      '../src/views/camera/components/AlgorithmTask/AlgorithmTaskModal.vue',
      import.meta.url,
    ),
  ),
  'utf8',
)

const dynamicSelectOptions = [
  ['model_ids', 'modelOptions'],
  ['face_library_ids', 'faceLibraryOptions'],
  ['plate_library_ids', 'plateLibraryOptions'],
  ['pose_library_ids', 'poseLibraryOptions'],
] as const

for (const [field, optionsRef] of dynamicSelectOptions) {
  assert.doesNotMatch(
    algorithmTaskModal,
    new RegExp(`options:\\s*${optionsRef}(?!\\.value)`),
    `${field} must not pass a nested ref directly to the Select component.`,
  )
  assert.match(
    algorithmTaskModal,
    new RegExp(
      `updateSchema\\(\\{\\s*field:\\s*'${field}',\\s*componentProps:\\s*\\{\\s*options:\\s*${optionsRef}\\.value`,
    ),
    `${field} must refresh its Select schema with the loaded options array instead of passing a nested ref.`,
  )
}
