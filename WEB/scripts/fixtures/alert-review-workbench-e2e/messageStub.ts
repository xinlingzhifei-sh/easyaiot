type ConfirmOptions = {
  onOk?: () => void | Promise<void>
}

const messages: Array<{ type: string; message: string }> = []

function push(type: string, message: string) {
  messages.push({ type, message })
}

function messageText(message: string | { content?: string }) {
  return typeof message === 'string' ? message : String(message.content ?? '')
}

export function useMessage() {
  return {
    createMessage: {
      error: (message: string) => push('error', message),
      info: (message: string) => push('info', message),
      loading: (message: string | { content?: string }) => {
        push('loading', messageText(message))
        return () => undefined
      },
      warn: (message: string) => push('warn', message),
      warning: (message: string) => push('warning', message),
      success: (message: string) => push('success', message),
      destroy: () => undefined,
    },
    createConfirm: (options: ConfirmOptions) => {
      void options.onOk?.()
    },
  }
}

export function getAlertReviewE2EMessages() {
  return messages
}
