# Device Access E2E Acceptance Runbook

This runbook is the production evidence gate for the unified device access
work. It does not replace real devices or real public networks. A local unit
test proves only that the matrix and runner are executable.

## Command

Copy the example config and replace every URL, device id, tenant id, Edge node
id, and WebRTC ICE evidence with target-environment values:

```powershell
Copy-Item VIDEO/config/device_access_e2e_acceptance.example.json VIDEO/config/device_access_e2e_acceptance.local.json
python VIDEO/scripts/device_access_e2e_acceptance.py --config VIDEO/config/device_access_e2e_acceptance.local.json --output VIDEO/reports/device_access_e2e_acceptance.json
```

Use `--plan-only` only to inspect missing evidence before the real run:

```powershell
python VIDEO/scripts/device_access_e2e_acceptance.py --config VIDEO/config/device_access_e2e_acceptance.local.json --output VIDEO/reports/device_access_e2e_plan.json --plan-only
```

不能把 plan-only 或 blocked 报告当成验收通过。Only a report with top-level
`status: passed` proves this acceptance gate.

## Matrix

The report must include these scenario ids:

- `gb28181_public`: GB28181 public registration, live playback, and PTZ.
- `rtsp_same_lan`: same-network RTSP validation, pull, and playback.
- `rtsp_edge_outbound`: private RTSP through Edge Agent outbound push.
- `rtmp_public_push`: public RTMP push with signed ingest and publish hook.
- `http_flv_public_playback`: public HTTPS HTTP-FLV playback.
- `webrtc_public_playback`: public WebRTC playback across NAT.

## Evidence Rules

Each scenario needs both probes and evidence. Missing probes or evidence makes
the scenario `blocked`; HTTP probe failures make it `failed`.

For WebRTC, browser-side evidence must record:

- page URL
- network name
- HTTPS origin verified in the browser
- WSS signaling verified in the browser
- ICE connection state
- selected candidate pair
- public candidate observed
- TURN relay verified when direct UDP is blocked
- cross-carrier playback result
- mobile hotspot playback result
- weak network profile
- weak network playback result

For RTMP, the report must be paired with the platform audit row showing device,
tenant, token version, remote IP, and publish hook decision.

For GB28181, PTZ must be exercised against the same public registered channel
that was used for playback.

## Pass Criteria

The acceptance gate passes only when:

- every scenario status is `passed`
- the top-level report status is `passed`
- the device access state center shows the expected `stream_online`,
  `play_ready`, or `ai_ready` transitions for the tested devices
- rejected or blocked paths produce actionable `error` reason codes instead of
  silent timeouts
