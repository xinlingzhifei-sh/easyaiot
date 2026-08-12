export function resolveWvpSipDeviceId(wvp: Record<string, any>): string {
  return String(
    wvp.deviceIdentification ?? wvp.deviceId ?? wvp.id ?? wvp.gbId ?? '',
  ).trim();
}

export function isValidGb28181SipDeviceId(deviceId: unknown): boolean {
  return /^\d{20}$/.test(String(deviceId ?? '').trim());
}

export function filterValidWvpDevices<T extends Record<string, any>>(devices: T[] | null | undefined): T[] {
  return (devices || []).filter((device) => isValidGb28181SipDeviceId(resolveWvpSipDeviceId(device)));
}
