# Device Access State Center Design

## Goal

Create one shared device-access state vocabulary across GB28181, direct RTSP,
Edge RTSP, RTMP push, HTTP-FLV playback, WebRTC playback, and AI readiness.

The state center should make every device row answer the same questions:

- Is configuration complete?
- Has the device or Edge Agent registered?
- Is the upstream stream online?
- Is a user-playable URL ready?
- Is the AI input stream ready?
- If not, what exact reason blocks it?

## Shared States

Use these states exactly:

- `pending_config`
- `registering`
- `registered`
- `stream_online`
- `play_ready`
- `ai_ready`
- `error`

## Protocol Writers

Each protocol path must have one writer into the shared state model:

- GB28181: SIP register, catalog/channel online, media publish hooks, playback
  URL readiness, PTZ capability.
- Direct RTSP: source validation, same-network pull readiness, media-node pull
  status, playback URL readiness.
- Edge RTSP: Agent register/heartbeat, command queued/leased/running/result,
  media-node RTMP publish hook, playback URL readiness.
- RTMP push: signed ingest URL issued, publish hook accepted/rejected,
  stream-online hook, playback URL readiness.
- HTTP-FLV/WebRTC: playback probe or media-node hook confirms public playback
  readiness.
- AI: AI stream URL exists and the AI consumer can pull it.

## Data Shape

Store state transitions with enough context for UI and audit:

- `device_id`
- `protocol`
- `state`
- `reason_code`
- `reason_message`
- `source_event`
- `event_time`
- `stream_id`
- `node_id`
- `tenant_id`

The current state can be materialized for fast UI reads, but transitions should
remain queryable for debugging.

## Acceptance

- GB28181, direct RTSP, Edge RTSP, and RTMP push each update the same state
  vocabulary.
- A device list can show state, reason, play readiness, AI readiness, and last
  transition time without protocol-specific branching.
- Error states include actionable reasons such as invalid RTSP URL, Agent
  offline, command failed, publish signature rejected, media node offline, or
  WebRTC candidate failure.
- Existing direct Agent deployment remains separate from Edge RTSP state
  transitions unless explicitly used by a device path.

## Non-Goals

- Replacing protocol-specific tables in the first pass.
- Redesigning the camera onboarding UI.
- Implementing signed RTMP ingest or TURN/STUN rollout; those have separate
  specs.

## Implementation Status

Implemented in the first TDD slice:

- `VIDEO` now has `device_access_state_event` and `device_access_state_current`
  models plus an additive SQL schema file.
- `record_device_access_event()` writes one transition and materializes the
  current state using the shared state vocabulary.
- Edge RTSP command enqueue writes `edge_agent/registering` with an actionable
  `edge_command_queued` reason.
- SRS `on_publish` writes `stream_online`, preferring an existing
  `edge_agent` registration state and falling back to `rtmp`.
- Device list serialization includes an `access_state` summary with state,
  reason, play readiness, AI readiness, last transition time, and protocols.
- The camera table UI includes an access-state column that shows state,
  playback readiness, AI readiness, and the current reason.
- RTMP public push now has signed URL issuance, token rotation, SRS/ZLM
  publish-hook validation, audit rows, and `rtmp` state writes for
  `stream_online` and `error`.
- Direct RTSP manual onboarding now lets the user choose local forwarding or an
  Edge Agent outbound node. Edge mode enqueues `/ensure-edge-task`, which writes
  `edge_agent/registering` and lets the refreshed access-state column show the
  queued command status and later errors.
- The `VIDEO/sql` DDL files for access state and signed RTMP ingest are now
  registered in `video_schema_migration_service` and executed during
  `VIDEO/run.py` database startup.
- The full device-access E2E acceptance matrix is now executable through
  `VIDEO/scripts/device_access_e2e_acceptance.py`, with an example config at
  `VIDEO/config/device_access_e2e_acceptance.example.json` and a runbook at
  `docs/superpowers/specs/2026-06-13-device-access-e2e-acceptance-runbook.md`.

Still remaining:

- GB28181 SIP/catalog/PTZ writers into the shared state model.
- Direct RTSP source validation, same-network pull, and local media pull
  readiness writers.
- Full frontend detail UI for recent command status, lease/running result, and
  RTMP signature rejection history beyond the table summary column.
- HTTP-FLV/WebRTC playback probe writers and WebRTC NAT production rollout.
- Target-environment migration execution verification, monitoring, alerting,
  and a real-device E2E acceptance report with `status: passed` across
  public/cross-network scenarios.
