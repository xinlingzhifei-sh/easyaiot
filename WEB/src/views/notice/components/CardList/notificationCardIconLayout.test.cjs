const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'NoficeCardList.vue'), 'utf8')

function blockFor(selector) {
  const pattern = new RegExp(`${selector.replace('.', '\\.')}\\s*\\{([\\s\\S]*?)\\n\\s*\\}`)
  const match = source.match(pattern)

  assert.ok(match, `Expected ${selector} styles to exist`)

  return match[1]
}

test('notification provider icon stays in the card bottom right away from text', () => {
  const productInfo = blockFor('.product-info')
  const productImage = blockFor('.product-img')

  assert.match(productInfo, /max-width:\s*calc\(100%\s*-\s*128px\);/)
  assert.match(productImage, /right:\s*20px;/)
  assert.match(productImage, /bottom:\s*20px;/)
  assert.doesNotMatch(productImage, /top:\s*\d+px;/)
})
