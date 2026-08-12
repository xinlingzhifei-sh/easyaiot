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
