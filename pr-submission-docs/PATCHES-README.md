# Code Modernization Patches

This directory contains self-contained git patches for the code modernization work. Each patch focuses on a single transformation type and can be applied independently to `master`.

## Patch Files

| Patch | Lines | Files | Description | Status |
|-------|-------|-------|-------------|--------|
| `PR-01-collections-empty-singleton.patch` | 673 | 27 | `Collections.emptyList/singletonList()` → `List.of()` | Pending |
| `PR-02-arrays-aslist.patch` | 248 | 11 | `Arrays.asList()` → `List.of()` | Pending |
| `PR-03-stringbuilder-var.patch` | 2528 | 122 | `StringBuilder sb =` → `var sb =` | Pending |
| `PR-05-arraycopy.patch` | 92 | 4 | `System.arraycopy()` → `Arrays.copyOf()` | Pending |
| `PR-06-lazy-list.patch` | 445 | 10 | Lazy `List<X> x = null` → upfront `new ArrayList<>()` | Pending |
| `PR-07-loop-to-lambda.patch` | 226 | 5 | Traditional loops → Stream API | Pending |

## Submitted PRs (moved to done/)

| Patch | PR | Description |
|-------|-----|-------------|
| `done/PR-04-boolean-first-loop.patch` | [#376](https://github.com/xtclang/xvm/pull/376) | Boolean first loop → `Collectors.joining()` |

## How to Apply

Each patch can be applied independently from the `master` branch:

```bash
# Switch to master
git checkout master
git pull

# Create a branch for the PR
git checkout -b modernize/collections-factory-methods

# Apply the patch
git apply pr-submission-docs/PR-01-collections-empty-singleton.patch

# Verify and commit
./gradlew build
git add -A
git commit -m "Modernize: Collections.emptyList/singletonList() → List.of()

Replace Collections.emptyList() and Collections.singletonList(x) with
List.of() and List.of(x) respectively. Both return immutable lists -
this is a semantic-equivalent transformation.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

## Verification

Before submitting any PR, run the full test suite:

```bash
./gradlew clean
./gradlew build
./gradlew test
```

## Patch Independence

These patches are designed to be applied independently:

- **No dependencies**: Each patch can be applied to `master` without needing the others
- **No conflicts**: Applying multiple patches in any order should work (though some files may be touched by multiple patches)
- **Self-contained**: Each patch includes all necessary import changes

## Recommended Submission Order

1. **PR-03** (StringBuilder) - Largest but trivially mechanical, low risk
2. **PR-01** (Collections) - Clear pattern, familiar idiom
3. **PR-05** (arraycopy) - Smallest, quick review
4. **PR-07** (Loop-to-Lambda) - Small, localized
5. **PR-06** (Lazy List) - Requires understanding context
6. **PR-02** (Arrays.asList) - Review for immutability implications

~~7. **PR-04** (Boolean First) - SUBMITTED as PR #376~~

## Notes

- All patches were extracted from branch `lagergren/sb-simplify`
- Patches were filtered to contain only their specific transformation type
- Some files appear in multiple patches (different types of changes in the same file)
