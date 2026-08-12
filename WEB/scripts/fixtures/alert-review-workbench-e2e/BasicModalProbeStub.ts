import { defineComponent, getCurrentInstance, h } from 'vue'

export default defineComponent({
  name: 'BasicModalProbeStub',
  emits: ['register'],
  setup(_, { emit, slots }) {
    const instance = getCurrentInstance()
    const methods = {
      emitOpen: undefined as undefined | ((open: boolean, uid: number) => void),
      setModalProps(props: Record<string, unknown> & { open?: boolean }) {
        if (typeof props.open === 'boolean' && instance)
          methods.emitOpen?.(props.open, instance.uid)
      },
      redoModalHeight() {},
    }

    if (instance)
      emit('register', methods, instance.uid)

    return () => h('section', { 'data-testid': 'alert-review-player-modal-probe' }, slots.default?.())
  },
})
