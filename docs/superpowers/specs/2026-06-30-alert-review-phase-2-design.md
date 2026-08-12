# Alert Review Phase 2 Design

## Goal

Upgrade the alert review workbench from "can convert a clue to an event" into a review hub that connects alert evidence, region rule context, and the supervision event lifecycle.

## User Confirmed Boundary

The automatic record evidence flow is backend-led. The backend review service attempts evidence backfill and exposes status plus retry. The frontend shows status and triggers retry, but does not own the evidence search logic.

## Frigate Reference Points

- Frigate review items group related alerts into one review unit.
- Frigate snapshots and recordings are evidence attached to that review unit.
- Frigate zones and masks provide rule context for why an object matters.
- Frigate history/review views keep the user in one evidence review surface.

yFeiEye should reuse those product ideas, not Frigate's NVR, Home Assistant, or Frigate+ implementation.

## Approaches Considered

### Option A: Minimal Review-Orchestrated Slice

The review domain owns evidence status, rule context, and event status projection. VIDEO remains the source for record lookup. Existing device region APIs remain the source for polygon drawing.

Pros:
- Smallest change that completes the review hub.
- Keeps yFeiEye's supervision event model as the lifecycle source of truth.
- Easy to cover with focused service tests.

Cons:
- Automatic backfill is request-time/on-demand first, not a broad background reconciliation job.

### Option B: Backend Scheduler and Deep Sync

Add a scheduled job to continuously backfill records and mirror event status into review rows.

Pros:
- More autonomous after deployment.
- Better for delayed DVR uploads.

Cons:
- Larger blast radius.
- Requires scheduling, retry limits, and operational monitoring.
- Risks duplicating event lifecycle state.

### Option C: UI-First Visual Upgrade

Focus first on a rich workbench UI with region editing and evidence controls, using existing frontend VIDEO query utilities for playback.

Pros:
- Fast visible improvement.
- Lower backend integration cost.

Cons:
- Evidence state remains browser-local.
- Does not make the workbench a reliable review hub.

## Recommendation

Use Option A for phase 2. It best matches the user's confirmed boundary and the current yFeiEye architecture: review service coordinates, VIDEO supplies record material, device region APIs supply visual rules, and supervision events remain the closure source of truth.

## Scope

### 1. Automatic Record Evidence Backfill

Add review-level evidence status fields:

- `record_evidence_status`: `not_required`, `pending`, `found`, `missing`, `failed`
- `record_evidence_checked_at`
- `record_evidence_message`

When a clue is ingested without `recordUri`, the review service asks a narrow `AlertRecordEvidenceResolver` for a record URI using:

- `sourceAlertId`
- `deviceId`
- `cameraId`
- `alertTime`

If a record URI is found, append a `record` evidence item. If no record is found, keep the review item visible with `record_evidence_status = missing`. Add a retry endpoint:

```text
POST /system/supervision/alert-review/items/{reviewItemId}/record-evidence/retry
```

The first implementation uses a pluggable resolver interface. A default no-op resolver is acceptable when VIDEO HTTP integration is not configured, because the behavior is still testable and the UI can show "missing" rather than hiding the clue.

### 2. Region Rule Visual Configuration

Keep the existing device region drawing APIs and `DeviceRegionDrawer` as the visual source for polygons. The review workbench adds a rule configuration entry that:

- opens the region drawer for the selected `deviceId` or `cameraId`;
- lets the reviewer select a region and bind it to review rule fields;
- saves `ruleCode`, `ruleName`, `sourceSystem`, `cameraId`, `zoneCode`, `objectLabel`, `minStaySeconds`, `enabled`.

The backend rule model stays simple. It stores the rule condition, not the polygon geometry. Geometry remains in VIDEO/device-region tables.

### 3. Reverse Status Link After Conversion

After a review item is converted to a supervision event, the workbench should show the current event projection:

- `eventStatus`
- `closeCheckStatus`
- `evidenceStatus`

The projection is read from `system_supervision_event` by `event_id`. The review item does not drive event transitions after conversion. It only displays and filters the state. When the event reaches `closed`, the workbench shows the clue as closed through the linked event status while keeping `reviewStatus = converted`.

## Out Of Scope

- No Frigate NVR import.
- No Home Assistant integration.
- No Frigate+ training workflow.
- No background scheduler in phase 2.
- No new polygon storage table in the review domain.
- No duplicated event lifecycle transitions in the review service.

## Backend Design

Extend `SupervisionAlertReviewService` with:

- `retryRecordEvidence(Long reviewItemId)`
- event projection fields in `ReviewItemAggregate`
- evidence backfill status fields in `ReviewItemAggregate`
- a `RecordEvidenceResolver` interface
- an `EventProjectionStore` interface

Extend mapper store with:

- insert evidence only if the same `review_item_id + source_alert_id + material_type + material_uri` does not already exist
- update record evidence status
- left-load event projection for workbench rows

## Frontend Design

Extend `AlertReviewWorkbench.vue` with:

- evidence status badge in the row and timeline header
- "补证" retry action when record evidence is `missing` or `failed`
- linked event status pill when `eventId` exists
- rule configuration drawer entry that reuses `DeviceRegionDrawer`

The frontend should keep playback behavior unchanged: evidence `record` items still emit `viewVideo`.

## Error Handling

- Missing device/time: mark record evidence as `failed` with a short message.
- Resolver no match: mark as `missing`.
- Resolver exception: mark as `failed`.
- Retry on converted clues is allowed because evidence can still be useful after conversion.
- Event projection not found: keep `eventId` visible and leave projection fields blank.

## Testing

Backend focused tests:

- ingest without `recordUri` calls resolver and appends `record` evidence when found.
- ingest without `recordUri` marks missing when resolver has no match.
- retry record evidence appends only one record evidence item when called repeatedly.
- converted review item includes event status projection from the event store.
- SQL schema includes record evidence status fields and indexes.

Frontend checks:

- API wrapper exposes retry endpoint.
- workbench renders evidence status and retry action.
- workbench renders linked event status when present.
- workbench uses the existing region drawer for rule configuration.

## Success Criteria

- A clue without a record can still enter the workbench and display its backfill status.
- A later retry can attach the missing record evidence without duplicating evidence.
- Review rules can be configured from visible camera regions.
- A converted clue shows the current supervision event status and close-check/evidence projection.
- Existing phase 1 service tests continue to pass.
