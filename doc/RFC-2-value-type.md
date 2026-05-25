# RFC-2: Implement value type as single-field identity-free wrapper

## Summary
Single-field identity-free wrapper that desugars to `const` with enforced single-field constraint. Candidate for JVM Project Valhalla flat storage. Uses a flag bit on `Format.CONST` rather than a new enum value.

## Impact
- 14 soft keyword sites
- 1 flag bit on Format.CONST

## Status
Proposed for implementation.