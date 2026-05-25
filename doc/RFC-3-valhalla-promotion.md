# RFC-3: Implement Valhalla promotion for value types

## Summary
Heavy structural work to emit value types as JVM value classes. Requires null incompatibility resolution, JVM version gating, and new `JitFlavor` variant.

## Impact
- 2 format switch sites
- New JitFlavor variant
- Nullability strategy resolution

## Status
Proposed for implementation - heavy structural changes required.