# WebRTC NAT Production Design

## Goal

Make browser WebRTC playback reliable from public clients across LAN, mobile
hotspot, and cross-carrier networks.

## Required Capabilities

Media node config:

- STUN server.
- TURN server with credential rotation.
- Public candidate IP or domain rewrite.
- HTTPS/WSS endpoint for browser playback.
- Per-media-node public URL metadata exposed to the platform.

Platform config:

- Store STUN/TURN server config per environment.
- Store public media-node hostnames and candidate rewrite values.
- Surface WebRTC readiness and failure reasons in the device access state
  center.

Browser playback:

- Use HTTPS page origin.
- Use WSS signaling or media-node HTTPS APIs as required by the selected media
  server.
- Report candidate, ICE, and peer-connection failures with enough reason data
  for debugging.

## Acceptance

- HTTP-FLV playback works from a public browser over HTTPS.
- WebRTC playback works from:
  - same LAN
  - mobile hotspot
  - second carrier network
- ICE candidate list contains the expected public candidate, not private-only
  addresses.
- TURN relay is used when direct UDP is blocked.
- Failure state records whether the blocker is certificate, WSS, STUN, TURN,
  candidate rewrite, media-node offline, or stream offline.

## Verification

Run smoke checks from at least three networks:

- Office/LAN.
- Mobile hotspot.
- Cross-carrier remote client.

For each check capture:

- Page URL.
- Browser console result.
- ICE connection state.
- Selected candidate pair.
- Media node id.
- Device id.
- Timestamp.

## Implementation Status

Done in the current TDD slices:

- Browser playback loads platform WebRTC NAT config and injects STUN/TURN
  `iceServers` into `RTCPeerConnection`.
- Browser playback rewrites WebRTC play URLs to configured public host and
  secure protocol when HTTPS/WSS is required.
- Media-node deployment config can propagate public candidate IPs to SRS/ZLM.
- RTC playback now reports ICE candidate and offer-answer failures to the
  device access state center as WebRTC `error` states with source events.

Still required before calling WebRTC NAT production-ready:

- Target environment STUN/TURN credential rotation.
- HTTPS/WSS browser playback verification from same LAN, mobile hotspot, and a
  second carrier network.
- Captured ICE candidate pair evidence proving public candidate rewrite and
  TURN relay behavior.

## Non-Goals

- Replacing the Edge Agent outbound RTSP slice.
- Signed RTMP ingest enforcement.
- Full player UI redesign.
