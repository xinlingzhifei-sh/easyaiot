import { defineComponent, h } from 'vue'

function icon(name: string) {
  return defineComponent({
    name,
    setup() {
      return () => h('span', { class: 'ant-design-icon-stub', 'data-icon': name })
    },
  })
}

export const CameraOutlined = icon('CameraOutlined')
export const ClearOutlined = icon('ClearOutlined')
export const SaveOutlined = icon('SaveOutlined')
export const DeleteOutlined = icon('DeleteOutlined')
export const ReloadOutlined = icon('ReloadOutlined')
