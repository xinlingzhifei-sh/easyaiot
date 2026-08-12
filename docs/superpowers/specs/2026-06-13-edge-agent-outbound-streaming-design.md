# Edge Agent Outbound Streaming Design

## Goal

Make private RTSP camera access work without inbound firewall rules or port
mapping at the customer site.

The first deliverable is a real outbound Edge/Agent loop:

- Edge runs on site and pulls local `rtsp://...` camera streams.
- Edge registers and heartbeats to the platform.
- Platform commands are delivered through an outbound Agent channel, not by
  calling the site's Agent port `9100` from the public platform.
- Edge pushes video outward to platform-assigned SRS/ZLM media endpoints.

This design keeps later work in view: a unified device access state machine,
signed public RTMP/Edge ingest URLs, production WebRTC NAT settings, and
end-to-end acceptance.

## Current Context

The repo already has the pieces to reuse:

- `NODE/run_agent.py` registers and heartbeats to `/node/agent/register` and
  `/node/agent/heartbeat`.
- `NODE/agent_server.py` can execute local `/workload/deploy`,
  `/workload/stop`, `/media/deploy`, and `/media/stop` actions, but those are
  currently HTTP endpoints on the Agent.
- `DEVICE/iot-node/.../NodeCommandServiceImpl.java` deploys workloads by
  calling `http://{node.host}:{agentPort}/workload/deploy`.
- `DEVICE/iot-node/.../NodeMediaServiceImpl.java` deploys media stacks by
  calling `http://{node.host}:{agentPort}/media/deploy`.
- `DEVICE/iot-node/.../NodeMediaServiceImpl.java` already allocates
  per-device SRS/ZLM bindings and produces RTMP/HTTP playback URLs.
- `VIDEO/services/stream_forward_service/run_deploy.py` already pulls
  RTSP/RTMP inputs and pushes RTMP/FLV to SRS.

The NAT gap is therefore narrow but important: command delivery still assumes
the platform can reach the Agent's inbound HTTP port.

## Recommended Approach

Use a platform-backed long-polling command queue for the first phase.

Why this route:

- It preserves the current Python Agent and Java control-plane layout.
- It does not introduce MQTT, a tunnel server, or a WebSocket session manager.
- It gives durable command state, retries, and audit from day one.
- It works through ordinary outbound HTTPS from the customer network.

WebSocket can be added later when command latency or interactive control needs
justify the extra moving parts. MQTT or reverse tunneling should wait until
there is a broader edge-fleet management requirement.

## Platform Command Queue

Add a command model owned by `iot-node`.

Suggested table: `node_agent_command`.

Fields:

- `id`
- `node_id`
- `command_type`
- `command_key`
- `payload_json`
- `status`
- `attempt_count`
- `lease_until`
- `last_error`
- `result_json`
- `created_at`
- `updated_at`
- `acked_at`
- `finished_at`

Statuses:

- `pending`
- `leased`
- `running`
- `succeeded`
- `failed`
- `cancelled`
- `expired`

The command API is tenant-ignored like the existing Agent endpoints, but every
request must include `nodeId` and `agentToken` and validate them against
`compute_node.agent_token`.

Endpoints:

- `POST /node/agent/commands/poll`
  - Agent sends `nodeId`, `agentToken`, `capabilities`, and `maxCommands`.
  - Platform leases pending commands for that node and returns them.
- `POST /node/agent/commands/{commandId}/ack`
  - Agent marks the leased command as `running`.
- `POST /node/agent/commands/{commandId}/result`
  - Agent reports `succeeded` or `failed`, plus `resultJson` or `lastError`.
- `POST /node/agent/commands/{commandId}/heartbeat`
  - Optional for long-running commands; extends `lease_until`.

Idempotency:

- `command_key` prevents duplicate deployment commands for the same logical
  target, for example `stream_forward:{deviceId}` or `media_stack:{nodeId}`.
- A new command with the same active key updates or replaces the pending
  command instead of creating a duplicate.

## Agent Execution Loop

`NODE/run_agent.py` keeps the existing heartbeat loop and adds a command poll
loop:

1. Register.
2. Heartbeat periodically with metrics.
3. Poll commands through the platform URL.
4. Ack each command.
5. Execute command locally.
6. Report result.
7. Retry after backoff when the platform is temporarily unavailable.

The Agent should not require inbound access for Edge mode. The existing
`agent_server.py` can remain for LAN/admin scenarios, but outbound mode should
call its underlying managers directly rather than requiring a local HTTP call.

Command types:

- `workload.deploy`
- `workload.stop`
- `media.deploy`
- `media.stop`
- `stream_forward.deploy`
- `stream_forward.stop`

For `stream_forward.deploy`, the payload contains:

- `deviceId`
- `rtspUrl`
- `rtmpPushUrl`
- `streamName`
- `transport`
- `heartbeatUrl`
- `logDir`
- optional `env`

The Agent starts a local FFmpeg-based pusher or reuses the existing stream
forward runner with a single-device config. It reports the local PID, log path,
and resolved push URL.

## Private RTSP Flow

1. User creates a camera with access mode `edge_rtsp`.
2. The device stores the local RTSP URL and selected `edgeNodeId`.
3. Platform allocates media with `NodeMediaService.allocate(...)`.
4. Platform creates a `stream_forward.deploy` command for the edge node.
5. Agent polls and executes the command locally.
6. Agent pulls local RTSP and pushes RTMP to the platform media endpoint.
7. Stream hooks or task heartbeats mark the device `stream_online`.
8. Playback URLs become `play_ready` when HTTP-FLV/WebRTC URLs are available.

This is the core no-port-mapping acceptance path.

## Direct Agent Calls During Migration

Keep existing direct calls for nodes where the platform can reach the Agent.
Add a dispatch policy:

- `direct`: keep current `http://host:agentPort/...` behavior.
- `outbound`: enqueue commands and wait for result, or return an accepted
  command response if the caller is asynchronous.
- `auto`: use `outbound` when the node declares `edge_outbound=true` or when
  direct health probing fails.

This avoids breaking current managed nodes while making the private RTSP path
explicitly outbound-only.

## Unified Device Access State

Introduce one platform-level access state vocabulary for all protocols:

- `pending_config`
- `registering`
- `registered`
- `stream_online`
- `play_ready`
- `ai_ready`
- `error`

Each state update should carry:

- `accessType`: `gb28181`, `direct_rtsp`, `edge_rtsp`, `rtmp_push`, `http_flv`,
  `webrtc`
- `deviceId`
- `status`
- `reason`
- `lastSeenAt`
- `streamId`
- `playUrls`

First implementation can be a narrow table or service around existing device
records. Do not redesign every protocol at once; normalize state writes from
the new Edge path first, then connect GB28181 and RTMP events.

## Signed RTMP and Edge Ingest

After the outbound Edge loop works, add signed ingest URLs.

Required semantics:

- Every device gets its own push URL.
- Signature includes tenant, device, stream, expiry, and token version.
- Token rotation invalidates older push URLs.
- SRS/ZLM publish hooks validate the signature before accepting a push.
- Publish allow/deny decisions are written to an audit table.

Example shape:

`rtmp://media.example.com/live/{deviceId}?tenant={tenantId}&exp={ts}&ver={tokenVersion}&sig={hmac}`

Edge and public RTMP devices use the same signing service.

## WebRTC Production NAT

After HTTP-FLV and RTMP ingest are stable, productionize WebRTC:

- Add STUN/TURN config to media-node deployment.
- Rewrite advertised candidates to public HTTPS/WSS-safe addresses.
- Require HTTPS/WSS for browser playback.
- Add smoke tests for public network, mobile network, cross-carrier access, and
  weak network behavior.

This is deliberately a later phase because WebRTC NAT depends on media-node
network topology and certificates.

## Testing Strategy

Follow TDD for implementation.

First red tests:

- Java service test: polling leases only commands for the authenticated node.
- Java service test: reporting a result moves a command to `succeeded` or
  `failed` and stores result/error fields.
- Python Agent test: command loop executes `stream_forward.deploy` by calling a
  local executor and reports result without contacting local port `9100`.
- Python Agent test: transient poll failure backs off and keeps heartbeat
  behavior alive.

Integration checks:

- Create an `edge_rtsp` device and confirm the platform creates a
  `stream_forward.deploy` command.
- Run Agent with outbound-only network assumptions and confirm it starts the
  local RTSP-to-RTMP pusher.
- Confirm device access state reaches `stream_online` and `play_ready`.
- Confirm no platform call to customer-site `agentPort` is required on the
  Edge path.

End-to-end acceptance:

- GB28181 public registration, live play, playback, and PTZ.
- Direct RTSP on the same network.
- Private RTSP through Edge without port mapping.
- Public RTMP device push.
- HTTP-FLV and WebRTC playback from public clients.

## Non-Goals For The First Slice

- Replacing all direct Agent deployments.
- Building MQTT or reverse tunnel infrastructure.
- Full UI redesign of the camera onboarding flow.
- Full WebRTC TURN rollout.
- Complete RTMP publish-hook enforcement.

Those are follow-on slices. The first slice proves the Edge path no longer
needs inbound access to Agent `9100`.

## Implementation Status

First Edge RTSP outbound slice implemented:

- `iot-node` has the durable `node_agent_command` queue schema, mapper, service,
  and `/node/agent/commands/*` API for enqueue, poll, ack, and result.
- Command polling now stops re-leasing commands after three attempts, marks
  them `failed`, and records `agent_command_retry_exhausted`.
- A scheduled command reclaimer marks stale `running` commands whose lease has
  expired as `failed` with `agent_command_running_timeout`.
- The Java command API exposes `/commands/{commandId}/heartbeat`, and the
  Python Agent client can call it to extend a running command lease.
- Python Agent polls commands outbound, acknowledges them, runs local command
  executors, and reports success or failure.
- Python Agent includes a local `stream_forward.deploy` executor that starts an
  ffmpeg RTSP-to-RTMP pusher.
- VIDEO exposes `/stream-forward/device/{device_id}/ensure-edge-task`, allocates
  media URLs, and enqueues `stream_forward.deploy` instead of requiring the
  platform to reach the customer-site Agent `9100`.

Verified on 2026-06-13:

- Java: `mvn -pl iot-node/iot-node-biz -am "-Dtest=NodeAgentCommandSchemaSqlTest,NodeAgentCommandServiceImplTest" -DfailIfNoTests=false test`
- Agent: `python -m unittest NODE.tests.test_agent_commands NODE.tests.test_stream_forward_executor`
- VIDEO: `python -m unittest VIDEO.tests.test_edge_stream_forward_service`
- Invariant scan: Edge path contains `commands/poll`, `ensure-edge-task`, and
  `stream_forward.deploy`; existing `/workload/deploy`, `agentPort`, and `9100`
  matches remain in the direct managed-node Agent path.

Verified on 2026-06-14:

- Java retry budget and running-timeout reclaim:
  `mvn -pl iot-node/iot-node-biz -am -Dtest=NodeAgentCommandServiceImplTest -DfailIfNoTests=false test`
- Agent heartbeat client: `python -m unittest tests.test_agent_commands`

Remaining follow-on slices:

- Automatic periodic heartbeat during blocking long-running executors and
  command-status audit/monitoring.
- Public SRS/ZLM signed RTMP hook cutover and target-environment validation.
- Unified access-center state integration across GB28181, RTSP, RTMP,
  HTTP-FLV/WebRTC, and Edge Agent.
- Production WebRTC TURN/STUN validation.
