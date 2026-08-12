import { defineComponent, h } from 'vue'

export default defineComponent({
  name: 'PlayerLeafStub',
  setup() {
    return () => h('div', { 'data-testid': 'alert-review-player-leaf-stub' })
  },
})
