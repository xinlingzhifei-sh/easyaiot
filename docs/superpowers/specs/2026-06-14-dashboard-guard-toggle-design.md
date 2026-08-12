# Dashboard Guard Toggle Design

## Goal

Add a dashboard guard switch that starts recognition for the selected device or device group and keeps it running after the user navigates away from the dashboard. The running backend task must be able to produce alert events.

## Scope

- Add a guard switch to the dashboard monitor sidebar.
- Let the switch target the currently selected direct camera, synced GB28181 channel, NVR group, GB device group, or directory group.
- Resolve a group into the synced playable leaf devices under that group.
- Create or reuse a dashboard-owned realtime algorithm task.
- Start the task when the switch is enabled.
- Stop only dashboard-owned guard tasks when the switch is disabled.
- Restore the switch state from backend task state when the dashboard is opened again.
- Keep the existing video playback AI toggle behavior separate from this backend guard switch.

## Out Of Scope

- No new model picker on the dashboard.
- No broad redesign of the monitor screen.
- No changes to manually created algorithm tasks except reading them as templates.
- No forced reassignment of devices already occupied by a running algorithm task.

## Architecture

The frontend owns the dashboard interaction and uses the existing algorithm-task API. A small dashboard guard helper will contain task naming, scope keys, device collection, template selection, conflict detection, and task start/stop orchestration. The monitor sidebar will remain responsible for tree selection and will call the helper when the user toggles guard recognition.

Dashboard-owned tasks use a stable name prefix:

```text
[Dashboard Guard]
```

The full task name includes the selected scope label. This gives the frontend a narrow way to find and stop only tasks it created, while leaving normal backend algorithm tasks alone.

## Data Flow

1. The user selects a device or group in the monitor tree.
2. The sidebar stores the selected guard scope separately from video playback selection.
3. When the user enables the switch, the helper resolves the selected scope to synced device IDs.
4. The helper reads realtime algorithm tasks and chooses the first existing realtime task with model IDs as the template.
5. The helper checks running non-dashboard realtime tasks for device overlap.
6. If there is no conflict, the helper creates a dashboard-owned realtime task with:
   - `task_type: "realtime"`
   - `device_ids` from the selected scope
   - `model_ids` copied from the template task
   - `alert_event_enabled: true`
   - `alert_notification_enabled` copied from the template task
   - `tracking_enabled`, suppress times, defense mode, and schedule copied from the template task when present
   - `schedule_policy` copied from the template task when present
7. The helper starts the task through the existing start endpoint.
8. When the page is reopened, the sidebar lists backend tasks and marks the switch enabled if a dashboard-owned task is enabled for the selected scope.

## Error Handling

- If no device or group is selected, show a warning and leave the switch off.
- If a group has no synced device IDs, show a warning and leave the switch off.
- If no realtime task has model IDs, show a warning asking the user to configure an algorithm task in the backend first.
- If selected devices are already used by a running non-dashboard task, show a conflict warning and leave the switch off.
- If create, start, or stop fails, restore the previous switch state and show the backend error message when available.

## Testing

Add focused Node tests in `WEB/tests/dashboardGuardToggle.test.ts` that inspect the source files for the critical behavior:

- The sidebar renders a guard switch and labels it distinctly from the existing playback AI checkbox.
- Directory and group nodes can become the guard scope without triggering video playback.
- The guard helper creates realtime tasks with `alert_event_enabled: true`.
- The helper starts created tasks and stops only dashboard-owned tasks.
- The helper refuses to create tasks without a template model configuration.
- The helper detects conflicts with running non-dashboard tasks.

Run the new test plus the existing dashboard video startup test before committing implementation.
