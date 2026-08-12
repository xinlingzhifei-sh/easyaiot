import { defineComponent, h } from 'vue'

export default defineComponent({
  name: 'AntDesignIconDefaultStub',
  setup() {
    return () => h('span', { class: 'ant-design-icon-stub' })
  },
})
