import { defineComponent, h } from 'vue'

function passthrough(name: string) {
  return defineComponent({
    name,
    setup(_, { slots }) {
      return () => h('div', slots.default?.())
    },
  })
}

export const Select = passthrough('SelectStub')
export const TabPane = passthrough('TabPaneStub')
export const Tabs = passthrough('TabsStub')
export const Button = passthrough('ButtonStub')
export const Checkbox = passthrough('CheckboxStub')
export const Slider = passthrough('SliderStub')
export const Spin = passthrough('SpinStub')
export const Switch = passthrough('SwitchStub')
export const Tooltip = passthrough('TooltipStub')
export const Empty = passthrough('EmptyStub')
export const Input = passthrough('InputStub')
export const Popconfirm = passthrough('PopconfirmStub')
export const RadioGroup = passthrough('RadioGroupStub')
export const RadioButton = passthrough('RadioButtonStub')
export const Badge = defineComponent({
  props: { count: Number },
  setup: props => () => h('sup', String(props.count)),
})
export const InputNumber = defineComponent({
  inheritAttrs: false,
  props: { value: Number },
  emits: ['update:value', 'change'],
  setup(props, { attrs, emit }) {
    return () => h('label', attrs, [
      h('input', {
        type: 'number',
        value: props.value,
        onInput(event: Event) {
          const value = Number((event.target as HTMLInputElement).value)
          emit('update:value', value)
          emit('change', value)
        },
      }),
    ])
  },
})
