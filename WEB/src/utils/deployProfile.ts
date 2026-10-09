import {
  deploymentProfileManifest,
  type DeploymentCapabilityId,
  type DeploymentProfileId,
} from '@/generated/deploymentProfileManifest';

export type DeployProfile = DeploymentProfileId;

interface ManifestProfileConfig {
  aliases?: readonly string[];
  capabilities?: readonly DeploymentCapabilityId[];
}

interface ManifestMenuAlias {
  capabilityId: DeploymentCapabilityId;
  componentNames?: readonly string[];
  routeNames?: readonly string[];
  pathPrefixes?: readonly string[];
  legacyTitles?: readonly string[];
}

interface DeploymentManifestView {
  defaultProfile?: DeploymentProfileId;
  profiles: Partial<
    Record<DeploymentProfileId, ManifestProfileConfig | readonly DeploymentCapabilityId[]>
  >;
  web?: {
    menuAliases?: readonly ManifestMenuAlias[];
  };
  menuAliases?: readonly ManifestMenuAlias[];
}

export interface DeployProfileMenuLike {
  name?: unknown;
  path?: unknown;
  componentName?: unknown;
  meta?: unknown;
}

const manifest = deploymentProfileManifest as unknown as DeploymentManifestView;

function normalizeText(value: unknown): string {
  return String(value ?? '').trim();
}

function normalizeIdentity(value: unknown): string {
  return normalizeText(value).toLowerCase();
}

function normalizeMenuPath(value: unknown): string {
  const raw = normalizeIdentity(value).split(/[?#]/, 1)[0];
  if (!raw) return '';
  const withLeadingSlash = raw.startsWith('/') ? raw : `/${raw}`;
  const normalized = withLeadingSlash.replace(/\/{2,}/g, '/');
  return normalized.length > 1 ? normalized.replace(/\/$/, '') : normalized;
}

function isRecord(value: unknown): value is Record<PropertyKey, unknown> {
  return typeof value === 'object' && value !== null;
}

function hasProfile(profile: string): profile is DeploymentProfileId {
  return Object.prototype.hasOwnProperty.call(manifest.profiles, profile);
}

function getProfileConfig(profile: DeploymentProfileId): ManifestProfileConfig {
  const config = manifest.profiles[profile];
  if (!config) return {};
  if (Array.isArray(config)) {
    return { capabilities: config as readonly DeploymentCapabilityId[] };
  }
  return config as ManifestProfileConfig;
}

function getDefaultProfile(): DeploymentProfileId {
  if (manifest.defaultProfile && hasProfile(manifest.defaultProfile))
    return manifest.defaultProfile;
  if (hasProfile('full')) return 'full';
  return Object.keys(manifest.profiles)[0] as DeploymentProfileId;
}

function normalizeDeployProfile(raw: string | undefined): DeployProfile {
  const normalized = normalizeIdentity(raw || getDefaultProfile());
  for (const profile of Object.keys(manifest.profiles) as DeploymentProfileId[]) {
    if (normalizeIdentity(profile) === normalized)
      return profile;
    const aliases = getProfileConfig(profile).aliases ?? [];
    if (aliases.some(alias => normalizeIdentity(alias) === normalized))
      return profile;
  }
  return getDefaultProfile();
}

function isEnabledFlag(raw: unknown): boolean {
  return normalizeIdentity(raw) === 'true';
}

/** 当前前端构建时的有效部署形态（默认 full；edge 兼容旧的独立布尔标识）。 */
export function getDeployProfile(): DeployProfile {
  if (isEnabledFlag(import.meta.env.VITE_GLOB_EDGE_STANDALONE) && hasProfile('edge'))
    return 'edge';
  return normalizeDeployProfile(import.meta.env.VITE_GLOB_DEPLOY_PROFILE);
}

/** 查询当前（或指定）部署形态是否声明了稳定能力 ID。 */
export function isDeployCapabilityEnabled(
  capabilityId: DeploymentCapabilityId,
  profile: DeployProfile = getDeployProfile(),
): boolean {
  return (getProfileConfig(profile).capabilities ?? []).includes(capabilityId);
}

function getMenuAliases(): readonly ManifestMenuAlias[] {
  return manifest.web?.menuAliases ?? manifest.menuAliases ?? [];
}

function isKnownCapabilityId(value: string): value is DeploymentCapabilityId {
  if (getMenuAliases().some(alias => alias.capabilityId === value))
    return true;
  return (Object.keys(manifest.profiles) as DeploymentProfileId[]).some(profile =>
    (getProfileConfig(profile).capabilities ?? []).includes(value as DeploymentCapabilityId),
  );
}

function findAliasByIdentity(
  field: 'componentNames' | 'routeNames' | 'legacyTitles',
  value: unknown,
): DeploymentCapabilityId | undefined {
  const normalized = normalizeIdentity(value);
  if (!normalized) return undefined;
  return getMenuAliases().find(alias =>
    (alias[field] ?? []).some(candidate => normalizeIdentity(candidate) === normalized),
  )?.capabilityId;
}

function findAliasByPath(value: unknown): DeploymentCapabilityId | undefined {
  const path = normalizeMenuPath(value);
  if (!path) return undefined;
  return getMenuAliases().find(alias =>
    (alias.pathPrefixes ?? []).some((candidate) => {
      const prefix = normalizeMenuPath(candidate);
      return !!prefix && (path === prefix || path.startsWith(`${prefix}/`));
    }),
  )?.capabilityId;
}

/**
 * 将路由/菜单解析为稳定能力 ID。优先级：显式 meta、componentName、route name、路径、旧标题。
 * 未识别项返回 undefined，由调用方保持可见。
 */
export function resolveMenuCapability(
  menu: DeployProfileMenuLike | string | null | undefined,
): DeploymentCapabilityId | undefined {
  const candidate: DeployProfileMenuLike = typeof menu === 'string' ? { name: menu } : (menu ?? {});
  const meta = isRecord(candidate.meta) ? candidate.meta : {};
  const explicitCapability = normalizeText(meta.capabilityId);
  if (explicitCapability)
    return isKnownCapabilityId(explicitCapability) ? explicitCapability : undefined;

  const componentCapability = findAliasByIdentity(
    'componentNames',
    candidate.componentName ?? meta.componentName,
  );
  if (componentCapability) return componentCapability;

  const routeCapability = findAliasByIdentity('routeNames', candidate.name);
  if (routeCapability) return routeCapability;

  const pathCapability = findAliasByPath(candidate.path);
  if (pathCapability) return pathCapability;

  return findAliasByIdentity('legacyTitles', meta.title ?? candidate.name);
}

export function isMiniDeployProfile(): boolean {
  const profile = getDeployProfile();
  return profile === 'mini' || profile === 'edge';
}

/** edge 单机合装：登录无租户、无滑块验证码（VITE 编译时关闭） */
export function isLoginTenantEnabled(): boolean {
  return String(import.meta.env.VITE_GLOB_APP_TENANT_ENABLE ?? 'true').trim().toLowerCase() !== 'false';
}

export function isLoginCaptchaEnabled(): boolean {
  return String(import.meta.env.VITE_GLOB_APP_CAPTCHA_ENABLE ?? 'true').trim().toLowerCase() !== 'false';
}

/** edge 单机合装（与 mini 共用前端裁剪，但零 DEVICE / 无集群 Tab） */
export function isEdgeStandaloneDeployProfile(): boolean {
  return getDeployProfile() === 'edge';
}

/** 仅在部署形态启用 NFS 集群能力时展示相关 Tab。 */
const NFS_CAMERA_TAB_KEYS = new Set([
  '20', // NFS 集群管理
  '24', // NFS 集群拓扑
  '22', // NFS 节点部署
  '23', // NFS 文件目录
]);

export function isEdgeCameraTabVisible(tabKey: string): boolean {
  if (!NFS_CAMERA_TAB_KEYS.has(String(tabKey)))
    return true;
  return isDeployCapabilityEnabled('media.nfs-cluster');
}

/** 大屏「管理后台」默认落地页：有集群能力时进入节点管理，否则进入流媒体。 */
export function getAdminHomeRoute(): { path: string; query?: Record<string, string> } {
  return isDeployCapabilityEnabled('node.cluster')
    ? { path: '/node/index' }
    : { path: '/camera/index', query: { tab: '1' } };
}

/** 当前形态是否部署国标接入能力。 */
export function isGb28181Enabled(): boolean {
  return isDeployCapabilityEnabled('video.gb28181');
}

/** 当前形态是否部署 RTC 接入能力。 */
export function isRtcEnabled(): boolean {
  return isDeployCapabilityEnabled('video.rtc');
}

/** 当前形态是否部署训练/导出/部署等高级训练能力。 */
export function isTrainAdvancedEnabled(): boolean {
  return isDeployCapabilityEnabled('ai.training');
}

/** 当前形态是否部署身份库能力。 */
export function isFacePlateLibraryEnabled(): boolean {
  return isDeployCapabilityEnabled('video.identity-library');
}

/** 当前形态是否部署工作流能力。 */
export function isFlowTicketEnabled(): boolean {
  return isDeployCapabilityEnabled('workflow.flow');
}

export function isScenarioPoseLibraryEnabled(): boolean {
  return isDeployCapabilityEnabled('video.identity-library');
}

/** EDGE 联邦入口已移除；节点集群能力由 node.cluster 菜单单独控制。 */
export function isEdgeNodeEnabled(): boolean {
  return false;
}

/** 当前形态是否部署可视化能力。 */
export function isVisualizeEnabled(): boolean {
  return isDeployCapabilityEnabled('visualize');
}

/** 当前形态是否部署系统对接能力。 */
export function isTransformEnabled(): boolean {
  return isDeployCapabilityEnabled('transform');
}

/** HARNESS 仅在 manifest 显式声明时启用；生产 profile 默认关闭。 */
export function isHarnessEnabled(): boolean {
  return isDeployCapabilityEnabled('assistant.harness');
}

/** 当前部署形态下是否应隐藏该路由/菜单；未知项默认可见。 */
export function isMenuHiddenByDeployProfile(
  menu: DeployProfileMenuLike | string | null | undefined,
): boolean {
  const capabilityId = resolveMenuCapability(menu);
  return capabilityId ? !isDeployCapabilityEnabled(capabilityId) : false;
}
