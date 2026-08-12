# Dashboard Alert Read Permission Design

## Problem

Selecting the production GB28181 camera makes both dashboard alert queries return HTTP 401 and the global frontend interceptor redirects the operator to login. The VIDEO authorization audit records `authorization_service_rejected`; DEVICE logs show that the media-permission resolver queries `system_supervision_alert_review_item`, which is absent in the production database, so DEVICE returns HTTP 500 before it can return the configured camera grant.

## Decision

`alert_read` will use the existing authenticated action permission and the exact per-user configured camera grant as its complete authorization boundary. The resolver will intersect the requested camera IDs with that configured grant and return the intersection without querying alert-review history.

All other media actions retain the current persisted tenant-camera evidence requirement. Missing mappers, missing tables, empty review history, and cross-tenant review data therefore continue to fail closed for playback, snapshot, coverage, export, download, manifest verification, and record management.

## Data Flow

1. VIDEO forwards the authenticated token, tenant header, action `alert_read`, and one camera ID to DEVICE.
2. DEVICE confirms the user, tenant, supported action, non-empty camera ID, and required playback permission.
3. The resolver normalizes and intersects the requested camera with `yfeieye.review.camera-permission.users.<userId>`.
4. For `alert_read` only, the resolver returns that exact intersection without reading the optional review-history table.
5. VIDEO receives `granted`, constrains the alert query to the authenticated tenant and camera, and returns the result.

## Verification

- A focused resolver test must fail before the implementation because an `alert_read` request invokes a mapper that must not be called.
- The test must pass after the implementation and return only the configured requested camera.
- Existing resolver and controller tests must continue to prove that unconfigured cameras, missing permissions, other tenants, and sensitive actions fail closed.
- Production browser verification must select the real camera without logout, observe HTTP 200 for alert page/statistics, start or reuse one dashboard AI task with non-empty model IDs, and retain the management-backend entry.

## Non-goals

- Applying the full supervision schema migration set.
- Changing permissions for export, download, playback, recording, or management actions.
- Adding a general fail-open fallback for database errors.
