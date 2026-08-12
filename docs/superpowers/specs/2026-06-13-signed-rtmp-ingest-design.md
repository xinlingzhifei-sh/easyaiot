# Signed RTMP Ingest Design

## Goal

Protect public RTMP push endpoints with per-device signed URLs so a device can
push from the public internet without allowing cross-tenant or expired stream
publishing.

## URL Format

Use HMAC-signed query parameters:

```text
rtmp://media.example.com/live/{deviceId}?tenant={tenantId}&exp={unixSeconds}&ver={tokenVersion}&sig={hmac}
```

Canonical string:

```text
tenant={tenantId}&device={deviceId}&app=live&stream={deviceId}&exp={unixSeconds}&ver={tokenVersion}
```

Signature:

```text
HMAC-SHA256(device_push_secret[tokenVersion], canonical_string)
```

## Responsibilities

Platform:

- Issue signed RTMP push URLs per device.
- Store token version and rotation metadata.
- Bind device, tenant, media node, app, stream, expiry, and signature version.
- Provide token rotation without invalidating still-valid URLs unless explicitly
  revoked.

Media publish hook:

- Validate tenant, device, app, stream, expiry, token version, and HMAC.
- Reject missing, expired, malformed, wrong-tenant, wrong-device, or revoked
  signatures.
- Record accepted and rejected publish attempts for audit.

UI/API:

- Show current push URL state and expiry.
- Allow explicit rotation.
- Avoid exposing raw secrets.

## Acceptance

- Missing `sig` is rejected.
- Expired `exp` is rejected.
- Wrong tenant is rejected.
- Wrong device or stream path is rejected.
- Old token version is rejected after forced rotation.
- Valid signature is accepted and moves the device access state to
  `stream_online`.
- Audit records include device, tenant, media node, reason, remote IP, and
  timestamp.

## Implementation Status

Done in the current slice:

- Added per-device, per-tenant RTMP ingest secrets with token version metadata.
- Added signed URL issuance with bounded expiry and no raw secret exposure.
- Added forced token rotation that invalidates the previous token version.
- Added SRS/ZLM media-cluster `on_publish` validation for tenant, app, stream,
  expiry, token version, and HMAC.
- Added accepted/rejected publish audit records.
- Added device access state writes: accepted publishes become `stream_online`;
  rejected publishes become `error` with normalized RTMP reason codes.
- Added camera API endpoints for issuing a signed URL and rotating the token.
- Added frontend API wrappers for issue/rotate actions.
- Added SQL DDL for the two RTMP ingest tables.
- Wired the SQL DDL into `video_schema_migration_service`, which runs during
  `VIDEO/run.py` database startup.

Still required before calling the public RTMP ingress production-ready:

- Run and verify the migration execution chain in the target environments.
- Cut over the real public SRS/ZLM hook configuration to the signed media-hook
  routes, while preserving local/internal stream-forward compatibility.
- Bind tenant authorization to the authenticated user/session, not only to a
  request body or header value.
- Add UI controls for viewing the signed URL state, expiry, rotation status,
  and recent rejection reasons.
- Run E2E public RTMP push validation against real SRS/ZLM nodes and verify the
  resulting audit/state rows.

## Non-Goals

- Implementing WebRTC playback authorization.
- Replacing GB28181 auth.
- Encrypting RTMP transport; TLS termination can be a separate deployment
  concern if RTMPS is needed.
