import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'

type RetryRequestConfig = InternalAxiosRequestConfig & {
  requestOptions?: {
    retryRequest?: {
      waitTime?: number
      count?: number
    }
  }
  __retryCount?: number
}

/**
 *  请求重试机制
 */

export class AxiosRetry {
  /**
   * 重试
   */
  retry(axiosInstance: AxiosInstance, error: AxiosError) {
    // 网络错误/超时/请求被取消时不存在 error.response，需从 error.config 取配置，
    // 否则会抛出 “TypeError: error.response is undefined”，反而吞掉真正的错误。
    const config = (error.response?.config ?? error.config) as RetryRequestConfig | undefined
    if (!config)
      return Promise.reject(error)
    const { waitTime, count } = config?.requestOptions?.retryRequest ?? {}
    if (!count)
      return Promise.reject(error)
    config.__retryCount = config.__retryCount || 0
    if (config.__retryCount >= count)
      return Promise.reject(error)

    config.__retryCount += 1
    // 请求返回后config的header不正确造成重试请求失败,删除返回headers采用默认headers
    delete (config as { headers?: unknown }).headers
    return this.delay(waitTime ?? 0).then(() => axiosInstance(config))
  }

  /**
   * 延迟
   */
  private delay(waitTime: number) {
    return new Promise(resolve => setTimeout(resolve, waitTime))
  }
}
