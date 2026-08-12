import { defineComponent, h } from 'vue'

export default defineComponent({
  name: 'DeviceRegionDrawerStub',
  emits: ['save'],
  setup(_, { emit }) {
    const save = () => {
      emit('save', [{
        id: 801,
        device_id: 'cam-east-gate',
        region_name: 'gate-zone',
        region_type: 'polygon',
        points: [
          { x: 0.1, y: 0.1 },
          { x: 0.9, y: 0.1 },
          { x: 0.9, y: 0.9 },
          { x: 0.1, y: 0.9 },
        ],
        color: '#2f80ed',
        opacity: 0.35,
        is_enabled: true,
        sort_order: 1,
        minStaySeconds: 15,
        inertiaFrames: 3,
        loiteringSeconds: 20,
      }])
    }
    return () => h('div', { 'data-testid': 'alert-review-region-drawer-stub' }, [
      h('button', { 'data-testid': 'alert-review-region-drawer-save', onClick: save }, 'save'),
    ])
  },
})
