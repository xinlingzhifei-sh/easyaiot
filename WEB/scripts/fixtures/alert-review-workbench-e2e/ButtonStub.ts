import { defineComponent, h } from 'vue'

export const Button = defineComponent({
  name: 'Button',
  inheritAttrs: false,
  props: {
    disabled: Boolean,
    loading: Boolean,
    size: String,
    type: String,
    preIcon: String,
  },
  emits: ['click'],
  setup(props, { attrs, emit, slots }) {
    return () =>
      h(
        'button',
        {
          ...attrs,
          disabled: props.disabled || props.loading,
          type: 'button',
          onClick: (event: MouseEvent) => emit('click', event),
        },
        slots.default?.(),
      )
  },
})
