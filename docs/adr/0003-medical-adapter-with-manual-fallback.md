# Prefer Real Medical Adapter with Manual Fallback

The V1 physiology loop needs a medical emergency channel, but customer sites may not have a ready medical-system integration on day one. We will prefer a real medical adapter and require reports to distinguish `real_success`, `manual_completed`, `manual_signed`, `simulated`, `blocked_by_customer`, and `skipped_not_ready`; manual fallback can prove business handling, but it must not be counted as real adapter success.
