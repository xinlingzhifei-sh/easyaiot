# Use Supervision Identity for Trusted person_id

Personal physiology events require a trusted supervision identity, not just an algorithmic match or camera-local association. We will use the supervision person registry or a business-confirmed detained-person identifier as the trusted `person_id`; face matching, bed or point binding, manual observation, and `correlation_id` remain identity candidates until business rules confirm them, so personal physiology events and baselines are not created from uncertain identity evidence.
