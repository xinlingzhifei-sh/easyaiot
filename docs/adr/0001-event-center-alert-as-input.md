# Use Event Center for Supervision Events

The physiology monitoring V1 loop needs state, responsibility, medical recheck, evidence, readiness, and closure semantics that the existing `Alert` record does not own. We will create a unified `Event` center for behavior, physiology, fusion, and manual supervision events, and keep `Alert` as an algorithm or device alert input and evidence source so alert delivery is not confused with event handling.
