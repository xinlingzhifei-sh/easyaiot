const API_ENCRYPT_HEADER = 'X-Api-Encrypt'
const DISABLED_MESSAGE = '客户端字段加密已停用；请使用 HTTPS 与服务端会话鉴权'

/**
 * 保留旧调用接口，但不再把共享密钥或私钥编译进客户端。
 */
export class ApiEncrypt {
  static isEnabled(): boolean {
    return false
  }

  static getEncryptHeader(): string {
    return API_ENCRYPT_HEADER
  }

  static encryptRequest(_data: unknown): never {
    throw new Error(DISABLED_MESSAGE)
  }

  static decryptResponse(_encryptedData: string): never {
    throw new Error(DISABLED_MESSAGE)
  }
}
