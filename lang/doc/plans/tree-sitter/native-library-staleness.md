# Native Library Staleness Verification

**Status**: COMPLETE (2026-01-31)

**Goal**: Verify the native library build system correctly detects when rebuild is needed.

---

## Behavior

The `ensureNativeLibraryUpToDate` task:

1. Computes SHA-256 hash of `grammar.js` + `scanner.c`
2. Compares to stored hash in `.inputs.sha256`
3. If match: logs version info and succeeds
4. If mismatch: **FAILS** with instructions to rebuild

This design avoids downloading Zig in CI (where libraries should always be up-to-date).

---

## Test Cases

### 1. Fail on hash mismatch

- Corrupt the `.inputs.sha256` file
- Run `ensureNativeLibraryUpToDate`
- Verify task FAILS with "STALE" message

### 2. Success when up-to-date

- Run `ensureNativeLibraryUpToDate` with correct hash
- Verify task succeeds and logs version info

---

## Manual Testing

```bash
# Verify current libraries are up-to-date
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate

# Simulate stale library (corrupt hash)
echo "0000" > lang/tree-sitter/src/main/resources/native/darwin-arm64/libtree-sitter-xtc.inputs.sha256
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate  # Should FAIL

# Rebuild to fix
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources
```