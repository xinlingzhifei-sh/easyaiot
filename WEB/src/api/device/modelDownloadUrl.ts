function getDefaultApiBase() {
  return import.meta.env?.VITE_GLOB_API_URL || '';
}

export function buildModelDownloadUrl(modelId: number | string, apiBase = getDefaultApiBase()) {
  const base = apiBase.replace(/\/+$/, '');

  return `${base}/model/${encodeURIComponent(String(modelId))}/download`;
}
