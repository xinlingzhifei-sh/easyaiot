import { getCurrentInstance, nextTick } from 'vue'

interface ModalMethods {
  setModalProps: (props: Record<string, unknown> & { open?: boolean }) => void
  redoModalHeight?: () => void
}

const dataCallbacks = new Map<number, (data: unknown) => void | Promise<void>>()

export function useModal() {
  let modal: ModalMethods | undefined
  let uid = 0
  const register = (methods: ModalMethods, modalUid: number) => {
    modal = methods
    uid = modalUid
  }
  return [register, {
    openModal(open = true, data?: unknown) {
      modal?.setModalProps({ open })
      if (data)
        nextTick(() => dataCallbacks.get(uid)?.(data))
    },
    closeModal() {
      modal?.setModalProps({ open: false })
    },
  }] as const
}

export function useModalInner(callback?: (data: unknown) => void | Promise<void>) {
  const currentInstance = getCurrentInstance()
  let modal: ModalMethods | undefined
  const register = (methods: ModalMethods, uid: number) => {
    modal = methods
    if (callback)
      dataCallbacks.set(uid, callback)
    currentInstance?.emit('register', methods, uid)
  }
  return [register, {
    closeModal() {
      modal?.setModalProps({ open: false })
    },
    setModalProps(props: Record<string, unknown> & { open?: boolean }) {
      modal?.setModalProps(props)
    },
  }] as const
}
