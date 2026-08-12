import { defineComponent, h } from 'vue'

export const Icon = defineComponent({
  name: 'Icon',
  props: {
    icon: {
      type: String,
      default: '',
    },
  },
  setup(props) {
    return () => h('span', { class: 'icon-stub', 'data-icon': props.icon })
  },
})
