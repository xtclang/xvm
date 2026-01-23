# PR Submission Strategy for Code Modernization

## Overview

This document outlines the strategy for submitting the code modernization changes in reviewable, focused PRs. The total work spans **149 files** with **~1542 insertions and ~1168 deletions**.

## ⚠️ CRITICAL: Commit Mixing Problem

The existing commits on `lagergren/sb-simplify` branch are **NOT cleanly separated by category**. Several commits mix multiple types of changes:

| Commit | Problem |
|--------|---------|
| `7278a9e54` | **MIXED**: Collections factory methods + Boolean First patterns |
| `e0dca6acc` | **MIXED**: Arrays.asList() changes + System.arraycopy() changes |

**Cherry-picking commits will NOT produce clean single-purpose PRs.**

## Strategy: Extract Single-Purpose Changes

To create PRs that each do **one thing only**:

1. **Start fresh from `master`** for each PR
2. **Use `git diff master..lagergren/sb-simplify`** as reference
3. **Extract only the specific pattern** using grep/search
4. **Apply changes manually** based on the detailed file lists below

## PR Organization (7 Single-Purpose PRs)

| PR # | Single Purpose | Changes | Files | Cherry-pickable? |
|------|----------------|---------|-------|------------------|
| 1 | `Collections.emptyList/singletonList()` → `List.of()` | ~71 | ~32 | ❌ Extract from mixed |
| 2 | `Arrays.asList()` → `List.of()` | ~19 | ~12 | ⚠️ Partial |
| 3 | `StringBuilder` → `var` | ~217 | ~126 | ✅ Yes |
| 4 | `boolean first` loop → `Collectors.joining()` | ~39 | ~19 | ⚠️ Partial |
| 5 | `System.arraycopy()` → `Arrays.copyOf()` | 5 | 5 | ❌ Extract from mixed |
| 6 | Lazy list `null` → upfront allocation | ~21 | ~11 | ✅ Yes |
| 7 | Loop → Stream API | ~9 | ~5 | ✅ Yes |

## Pure Commits (Safe to Cherry-pick)

These commits do ONE thing only:

```
✅ StringBuilder → var:
   21b1a2389, 6f0dda0df

✅ Lazy List:
   c53d6ecde, 1e1561967

✅ Loop-to-Lambda:
   8530f5b56, 026695f31, 6a539dbe4

✅ Boolean First Loop (partial):
   0b043626d, 87daf8b66
```

## Mixed Commits (Need Manual Extraction)

```
❌ 7278a9e54 - Contains BOTH:
   - Collections.emptyList/singletonList → List.of()
   - Boolean First Loop → Collectors.joining()

❌ e0dca6acc - Contains BOTH:
   - Arrays.asList() → List.of()
   - System.arraycopy() → Arrays.copyOf()
```

## Extraction Patterns

### For Collections.emptyList/singletonList (PR 1)

```bash
# Find all emptyList/singletonList changes
git diff master..lagergren/sb-simplify -- '*.java' | \
  grep -B3 -A3 "Collections\.emptyList\|Collections\.singletonList"
```

Changes to look for:
```diff
-Collections.emptyList()
+List.of()

-Collections.singletonList(x)
+List.of(x)
```

### For Arrays.asList (PR 2)

```bash
# Find all Arrays.asList changes
git diff master..lagergren/sb-simplify -- '*.java' | \
  grep -B3 -A3 "Arrays\.asList"
```

### For System.arraycopy (PR 5)

```bash
# Find all Arrays.copyOf additions
git diff master..lagergren/sb-simplify -- '*.java' | \
  grep -B5 -A5 "Arrays\.copyOf"
```

### For Boolean First Loop (PR 4)

```bash
# Find all Collectors.joining additions
git diff master..lagergren/sb-simplify -- '*.java' | \
  grep -B5 -A5 "Collectors\.joining"
```

## Workflow: Creating Each PR

### For PRs with Pure Commits (PR 3, 6, 7)

```bash
# Example: StringBuilder PR
git checkout master
git checkout -b modernize/stringbuilder-var
git cherry-pick 21b1a2389
git cherry-pick 6f0dda0df
./gradlew build test
# Submit PR
```

### For PRs Needing Extraction (PR 1, 2, 4, 5)

```bash
# Example: Collections.emptyList/singletonList PR
git checkout master
git checkout -b modernize/collections-empty-singleton

# Use the detailed file lists in pr-submission-docs/01-*.md
# Apply changes manually file by file

./gradlew build test
# Submit PR
```

## Detailed Documentation

Each PR has a dedicated file with exact file lists and diff examples:

- [PR 1: Collections.emptyList/singletonList](./01-pr-collections-empty-singleton.md)
- [PR 2: Arrays.asList](./02-pr-arrays-aslist.md)
- [PR 3: StringBuilder](./03-pr-stringbuilder-var.md)
- [PR 4: Boolean First Loop](./04-pr-boolean-first-loop.md)
- [PR 5: System.arraycopy](./05-pr-arraycopy.md)
- [PR 6: Lazy List](./06-pr-lazy-list.md)
- [PR 7: Loop-to-Lambda](./07-pr-loop-to-lambda.md)

## Recommended Submission Order

Start with PRs that have pure commits (easiest):

1. **PR 3 (StringBuilder)** - Pure commits, largest volume, trivially mechanical
2. **PR 6 (Lazy List)** - Pure commits
3. **PR 7 (Loop-to-Lambda)** - Pure commits
4. **PR 4 (Boolean First)** - Mostly pure + small extraction from `7278a9e54`
5. **PR 5 (arraycopy)** - Extract from `e0dca6acc` (only 5 changes)
6. **PR 1 (Collections empty/singleton)** - Extract from `7278a9e54`
7. **PR 2 (Arrays.asList)** - Extract from `868a7ca15` + `e0dca6acc`

## Verification

Before submitting any PR:

```bash
./gradlew clean
./gradlew build
./gradlew test
```

## Commit Message Template

```
Modernize: <pattern> → <replacement>

Replace <old pattern> with <new pattern> across <N> files.
<Brief rationale>

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```
