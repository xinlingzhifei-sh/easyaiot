declare global {
  interface Window {
    __alertReviewE2EApiCalls?: Array<{ name: string; payload?: unknown }>
  }
}

function record(name: string, payload?: unknown) {
  window.__alertReviewE2EApiCalls ||= []
  window.__alertReviewE2EApiCalls.push({ name, payload })
}

export async function getModelPage(params: unknown) {
  record('getModelPage', params)
  return {
    code: 0,
    msg: 'ok',
    data: [{
      id: 7,
      name: 'person-loitering',
      version: '1.0.0',
    }],
  }
}
