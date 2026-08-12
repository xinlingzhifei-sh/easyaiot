# Use Supervision Event Closure as the Product Core

yFeiEye already has device, video, AI task, alert, notification, evidence material, permission, and audit foundations. The next product layer should not expand `Alert` into a catch-all workflow table. We will keep `Alert` as an algorithm, device, or manual-report input and introduce `Supervision Event` as the authoritative lifecycle for judicial supervision incidents.

The supervision event closure model owns event level, responsibility chain, disposal tasks, recheck records, close checks, evidence chain, exception approval, and audit replay. Physiology emergencies, behavior events, drug-rehabilitation abnormalities, and device-availability incidents should all enter this same closure foundation instead of creating parallel closure semantics.

This decision makes the event closure loop the shared product core: an abnormal signal must be confirmed, assigned, handled, rechecked when required, closed by rule, preserved as evidence, and replayable by supervision or audit roles.
