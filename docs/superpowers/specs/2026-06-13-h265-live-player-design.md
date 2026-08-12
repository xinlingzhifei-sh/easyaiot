# H265 Live Player Design

## Goal

Support GB28181 live playback for common camera video codecs, especially H265/HEVC, without changing the server-side stream format or requiring camera encoding changes.

## Current Context

The frontend already loads `EasyWasmPlayer.js` and ships `libDecoder.wasm`. Existing live views mostly render `components/Player/module/jessibuca.vue`, while the dialog player uses that component through `DialogPlayer.vue`. The WVP/ZLMediaKit play response includes the stream URL and may include codec metadata such as `videoCodec=H265`.

## Approach

Add a small codec-to-player strategy utility:

- Route H265/HEVC, H264/AVC, MPEG4, MJPEG/JPEG live URLs to the existing EasyWasm player.
- Keep Jessibuca as the fallback for unknown streams and existing non-codec-specific paths.
- Preserve the existing WVP URL preference: HTTPS pages prefer secure FLV/FMP4/WS URLs.

Update the existing Jessibuca wrapper so current call sites do not need broad rewrites. When the selected engine is EasyWasm, the wrapper renders `EasyPlayer`; otherwise it keeps the current Jessibuca behavior.

## Boundaries

This is frontend playback routing, not server-side transcoding. If a future camera uses a codec neither Jessibuca nor EasyWasm can decode, the correct next step is server-side transcoding or a decoder upgrade.

## Testing

Use focused Node/TypeScript tests for codec normalization, URL codec detection, WVP source selection, and engine selection. Then run the web build to verify Vue integration.
