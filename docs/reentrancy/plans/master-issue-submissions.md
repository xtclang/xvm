# Master Issue Submission Drafts

This is a filing-prep document only. Nothing here has been pushed to GitHub,
and no issue should be filed without manual review.

Baseline used for source references: local `origin/master`
`61e555a68cd82a866f82aea40a3bb97a424a3809`. The older red-on-master audit in
`../test-failure-evidence.md` used master
`145f12f51074bae5e073db6181b0d015414dda65`; re-run targeted tests before
filing if master has moved.

> **Master has moved (2026-08-27):** `origin/master` is now `82683bcd2` (PR #377
> LSP support, PR #539 `cc183520c` this-escape fixes). Every per-row "applies
> cleanly to `origin/master` at `61e555a68`" statement below was verified at that
> OLDER hash and has NOT been re-verified against `82683bcd2`; re-run the
> cherry-pick check before filing. PR #539 in particular reworked the owner-lazy
> concern (`Lazy.Bound`), so rows touching runtime lazy caches are most likely to
> have shifted.
>
> **Branch seed hashes are also stale (2026-08-28):** the rebase onto `82683bcd2`
> rewrote all 297 branch commits, so the "source-only clean from `<hash>`" cherry-pick
> SEEDS cited in the rows below no longer resolve on the branch. They are kept as
> identifiers; the authoritative reference is the commit SUBJECT, mapped for every
> cited hash in the [appendix table](#appendix-commit-hash-resolution-table) at the end
> of this document (all branch seeds verified to resolve). The pre-rebase hashes remain
> reachable via the `backup/pre-rebase-master` tag, so an existing scratch worktree
> still works.

Scope decision: `plans/github-issue-breakdown.md` lists 19 Category A rows, but
the last row is already extracted as PR #539. This file prepares the critical master issues still intended for manual filing
(originally 18; rows 19 and 20 were graduated 2026-08-25 from the audit waves).

## Filing Index

One row per issue, with what an agent needs to file or PR it on its own. **Independent** means the
change touches nothing another row touches and can be filed today; where a row does depend on
another, the dependency is named and must land first.

Status is as of this file's last update; check the PR before re-filing.

> **Status verified against GitHub 2026-09-01.** Merged: #547 (row 27), #548 (29), #550 (30),
> #556 (31), #558 (33), #560 (35), #563 (36), #564 (37+38). Still open: **#549** (row 28) and
> **#559** (row 32) and **#566** (row 19). Everything numbered 1-18 and 20-26 is still unfiled.
>
> **Row 34 is fixed on master but PR #557 was closed unmerged.** The maintainer's reason, verbatim:
> *"I don't think there is a reason to clutter the repo with such a trivial typo fix. I pushed it as
> a minimal change."* Verified: `IntersectionTypeConstant.mergeChildren` on `origin/master` now
> reads `info2 == null ? ListMap.EMPTY : info2.getChildInfosByName()`. **Read this as filing
> guidance**: a one-word fix on its own is not worth a PR to this maintainer. Bundle small fixes by
> theme, or hand the fix over directly.

| # | Issue | Status | Depends on | Scope |
| --- | --- | --- | --- | --- |
| 1 | jsondb rollback failure retention | unfiled | independent | `lib_jsondb`, one catch |
| 2 | Module load preserves corrupt-file cause | unfiled | independent | `ModuleRepository` |
| 3 | Method op assembly failure is terminal | unfiled | independent | `MethodStructure` |
| 4 | Compiler codegen failure is terminal | unfiled | independent | compiler backend |
| 5 | Raw file submit observes queued write failures | unfiled | **row 8** (worker-failure channel) | `RawOSFileChannel` |
| 6 | `Future.and` uses both inputs | unfiled | independent | `xFuture` |
| 7 | JIT/bridge reflection failures not swallowed | unfiled | independent | `javatools_jitbridge` |
| 8 | MainContainer startup preserves causes | unfiled | independent | `MainContainer` |
| 9 | Alarm callback registry is timer-thread safe | unfiled | independent | `xRTClock` |
| 10 | Native callback registration rolls back | unfiled | independent | `NativeContainer` |
| 9b | Exception escaping a Trigger kills the process-wide alarm Timer | unfiled | independent of 9/10, either order | `xLocalClock`, one guard |
| 9c | `callLater`'s completion handler loses non-`WrapperException` failures | unfiled | rides with 9b or 9/10 | `ServiceContext`, 2 lambdas |
| 11 | Hash/equality contracts (`Register`, `VersionTree`, `MethodBody`) | unfiled | independent | `asm` |
| 12 | Handle view lifecycle state shared or refused | unfiled | independent | `ObjectHandle` |
| 13 | `HandleConstant` does not serve live handles cross-container | unfiled | independent | `HandleConstant` |
| 14 | Reflection `Method.invoke` does not alias caller tuple | unfiled | independent | `xRTMethod` |
| 15 | Short-hand property override copies super `Parameter` | unfiled | independent | `asm` |
| 16 | `Contribution` body copies re-own outer component | unfiled | independent | `asm` |
| 17 | `MethodStructure.Source` copies re-own outer method | unfiled | independent | `asm` |
| 18 | `FullyBoundHandle.chain()` appends | unfiled | independent | `xRTFunction` |
| 19 | Implicit-identity cache written from service threads | **filed, OPEN** (#566) | independent | one line + test |
| 20-26 | (see the sections below; each states its own scope) | unfiled | independent unless stated | varies |
| 27 | DirRepository scan-cache data race | **merged** (#547, 2026-08-28) | - | - |
| 28 | MethodStructure native/code state publication | **filed, OPEN** (#549) | independent | `MethodStructure` |
| 29 | `FileStructure.getErrorListener()` NPE | **merged** (#548, 2026-08-31) | - | - |
| 30 | `Version.isSameAs()` indexes the wrong array | **merged** (#550, 2026-08-29) | - | - |
| 31 | `Op.toString()` throws on 16 opcodes | **merged** (#556, 2026-08-30) | - | - |
| 32 | `Format.TimeZone` rejected by the pool | **filed, OPEN** (#559) | independent | 7 sites + `TimeZone.x` |
| 33 | `AstNode.fieldsForNames` dead guard / null holes | **merged** (#558, 2026-08-31) | - | - |
| 34 | `IntersectionTypeConstant.mergeChildren` wrong guard | **fixed on master**; #557 CLOSED unmerged - see note below | - | - |
| 35 | String/Type index a long by its low 32 bits | **merged** (#560, 2026-09-01) | - | - |
| 36 | `deleteAll(range)` wrong elements / crash | **merged** (#563, 2026-08-31) | - | - |
| 37 | `&slice1 == &slice2` / `&view1 == &view2` crash with a Java `ClassCastException` | **merged** (#564, 2026-08-31) | - | - |
| 38 | `&a == &slice_of_a` crashes for every non-generic element type | **merged** (#564, 2026-08-31) | - | - |
| 39 | One-return service call completes a bare handle, reads it as an array | **latent, NOT filed** - no reproduction; see the section below | independent | `ServiceContext` |

### Filing a row as an issue or PR

Every row above is a behaviour defect with a reproduction in its section below. To file one:

1. Take the section's **red proof** - each has either a named failing test or a runnable snippet.
2. Branch from `master`, not from a campaign branch. None of these needs the typing work.
3. Add the test first and confirm it fails, then the fix, then confirm it passes. Rows 30-36 all
   carry a test written this way already.
4. Where the section names a "Not affected" case, keep it in the PR description - several of these
   sit next to code that looks identical and is correct.

## Reviewer Framing

These issues are not requests to adopt a house style. Each one is a concrete
master bug where Java had a type-safety or concurrency mechanism available, but
the code deferred the invariant to mutable shared state, raw arrays,
`ObjectHandle`/metadata casts, hidden ambient ownership, or print-and-continue
error handling. That makes the code behave more like a dynamically checked
runtime than a Java implementation: the compiler cannot prove the owner, shape,
or lifecycle state, so the failure appears later as data corruption, wrong
success, leaked callbacks, cross-container handles, or missing diagnostics.

The fixes are intentionally small. They do not require accepting the whole
reentrancy branch, but they do use the same principle: make invalid states
unrepresentable when Java can express them with typed exceptions, final shared
cells, owner-aware copies, concurrent containers, explicit clone refusal, or
stable identity keys. Several issues also have security implications because
same-VM isolation depends on not serving live handles, mutable arrays, or hidden
owner metadata across container and module boundaries.

## Filing Order

1. jsondb rollback failure retention
2. Requested module load preserves corrupt-file cause
3. Method op assembly failure is terminal
4. Compiler codegen failure is terminal
5. Raw file submit observes queued write failures (depends on row 8's
   worker-failure channel seed `796f13465`, or inlines a minimal recorder)
6. `Future.and` uses both inputs and preserves async failure
7. JIT generated exception and bridge reflection failures are not swallowed
8. MainContainer startup preserves failure causes
9. Alarm callback registry is timer-thread safe
10. Native callback registration rolls back on startup failure
11. Hash/equality contracts for `Register`, `VersionTree`, and `MethodBody`
12. Handle view lifecycle state is shared or refused
13. `HandleConstant` does not serve live handles to the wrong container
14. Reflection `Method.invoke` does not alias caller tuple storage
15. Short-hand property override copies super `Parameter` elements
16. `Contribution` body copies re-own the hidden outer component
17. `MethodStructure.Source` body copies re-own the hidden outer method
18. `FullyBoundHandle.chain()` appends instead of dropping linked finalizers

## Filing Readiness Matrix

This table is the one-page launch checklist. "Source-only clean" means the
runtime/compiler/test files from the listed seed apply to `origin/master`
`61e555a68cd82a866f82aea40a3bb97a424a3809`; full cherry-picks can still report
delete/modify conflicts on branch-only `docs/reentrancy` files, and those docs
must not be included in master filings.

Every row's cherry-pick result was re-verified 2026-08-25 in a scratch
worktree at `origin/master` (still `61e555a68` - no drift). "Merge-clean" is a
textual statement only; row 5 merges clean but does not compile on master
without its listed dependency.

| # | Patch material for master | Deterministic red proof | Filing readiness |
| --- | --- | --- | --- |
| 1 | Source-only clean from `a935bc553` (verified 2026-08-25); the test rides in `5b9d577da`, which also applies clean. | `org.xvm.lib.jsondb.JsondbClientRollbackFailureTest` fails on the swallowed rollback catch shape. | Ready after manual review. |
| 2 | Source-only clean from `979784a1a`. | `org.xvm.asm.ModuleRepositoryLoadFailureTest` fails on print/drop/null corrupt-module loads. | Ready after manual review. |
| 3 | Source-only clean from `536067f5e`. | `org.xvm.asm.MethodStructureAssemblyFailureTest` fails when `writeTo` serializes after op/AST assembly failure. | Ready after manual review. |
| 4 | Source-only clean from `b00654356`. | `org.xvm.tool.CompilerCodegenFailureTest` fails on catch-`Throwable`/continue codegen. | Ready after manual review. |
| 5 | Merge-clean from `8a45ba708`, but the fix calls branch-only APIs: `Container.recordRuntimeFailure(...)` (seed `796f13465`) plus `frame.container()`/`xInt64.makeHandle(frame, ...)` need master forms. | `org.xvm.runtime.template._native.fs.RawOSFileChannelSubmitTest` (source-shape) fails while `submit` discards the `scheduleIO` future. | File with/after the row 8 worker-failure channel (`796f13465`) or inline a minimal recorder; adapt fix and test literals to master APIs. |
| 6 | Extract from `6496f5303`; real source conflict in `xFuture.java` on master. | `org.xvm.runtime.template.annotations.FutureCompletionSafetyTest` fails on fast-path double-read and assert-only async failure. | Needs exact master hunk before filing. |
| 7 | Source-only clean from `33323ffe1` and `f4744cb1e` applied in that order (`f4744cb1e` edits the test `33323ffe1` adds). | `org.xvm.javajit.JitFailurePropagationTest` (source-shape) fails on swallowed generated exceptions and bridge reflection wrapping. | Ready after manual review. |
| 8 | Source-only clean from `3e09abc32`; the worker-failure channel seed `796f13465` is also source-clean when applied after it (its test hunks edit the test `3e09abc32` adds). | `org.xvm.runtime.RuntimeFailurePropagationTest` fails on detached startup/invocation causes. | Ready after manual review; include `796f13465` if row 5 is to depend on this issue. |
| 9 | Extract from `26ce54466`; real conflicts in `xLocalClock.java` and `xNanosTimer.java`; test patch needs master add-form. | `org.xvm.runtime.NativeCallbackRegistrationTest.callbackRegistryIsConcurrentTimerSafeAndLeakFree()` fails on lazy `HashMap`/timer-thread throw/cancel leak shape. | Needs exact master hunk before filing. |
| 10 | Extract from `5311da1ac`; `xRTServer.java` applies cleanly, timer files conflict with row 9. | `org.xvm.runtime.NativeCallbackRegistrationTest` fails on leaked keep-alive registration after schedule/bind failure. | Decided 2026-08-25: files WITH row 9 as one PR (launch-plan row L12). |
| 11 | Extract only `Register.java`, `VersionTree.java`, `ChildInfo.java` from `9456d6727`, plus `MethodBody.java` from `a11765c86`; all four merge clean individually (verified 2026-08-25) - the conflicts in those broad commits are in other files. | `org.xvm.asm.RegisterHashCodeTest`, `org.xvm.asm.VersionTest`, and `org.xvm.asm.constants.MethodInfoTest.methodInfoEqualityDoesNotRecurseThroughMethodTargets()`; `MethodInfoTest` needs a master add-form. | Needs filtered diff; do not cherry-pick broad lint/adoption commits. |
| 12 | Multi-seed extraction from `c5c40d443`, `d2165e4f8`, `f4df60ed1`, `7ce5662d1`; verified 2026-08-25: only `ObjectHandle.java` (in `d2165e4f8`) conflicts - the other three seeds are source-clean. | `AtomicViewSharingTest`, `FreezeViewSharingTest`, `ArrayViewGuardTest`, `RefViewGuardTest`. | Needs split or conflict-resolved master patch. |
| 13 | Extract from `632cac927`; real conflict in `HandleConstant.java`. | `HandleConstantOwnerGuardTest` and `HandleConstantAssembleTest`; owner-guard test may need master API adaptation. | Needs exact master hunk before filing. |
| 14 | Source-only clean from `ff8cc479a`. | `org.xvm.runtime.template._native.reflect.MethodInvokeArgumentAliasingTest` fails on tuple-storage aliasing. | Ready after manual review. |
| 15 | Source-only clean from `f835b3693`. | `org.xvm.asm.ComponentMethodParameterCopyTest` fails on shared super `Parameter` elements. | Ready after manual review. |
| 16 | Extract only contribution re-owner hunks from broad `0af827c72`; real conflicts in `Component.java` and `MethodStructure.java`. | `org.xvm.asm.ComponentBodyCopyTest.contributionsAreReOwnedByBodyCopies` fails on hidden outer owner. | Needs exact master hunk before filing. |
| 17 | Source-only clean from `25371b397`. | `org.xvm.asm.ComponentBodyCopyTest.methodBodyCopyRebindsSourceOuter` fails on hidden outer method. | Decided 2026-08-25: files WITH row 16 as one PR (launch-plan row L10). |
| 18 | Extract from `c621b1dca`; real conflict in `xRTFunction.java`. | `org.xvm.runtime.template._native.reflect.FinalizerChainTest.chainAppendsAtTailInsteadOfDroppingLinkedFinalizers()` fails under both `-ea` and `-da`. | Needs exact master hunk before filing. |
| 19 | Extract from `8077ad6c0`: the one-line `f_implicits` `ConcurrentHashMap` conversion plus `implicitIdentityCacheIsConcurrentSafe()`. | `org.xvm.asm.ConstantPoolDiagnosticsTest.implicitIdentityCacheIsConcurrentSafe()` fails on the plain-`HashMap` shape (verified by reverting the fix). | Ready after manual review; the test's master form drops the branch-only surrounding cases. |
| 20 | Extract from `5d9a5f395`: the detached-build factories, the copy-on-write publish primitives, and the reworked `ensure*Delegation` - WITHOUT the synthesis window (`openRuntimeSynthesisWindow` exists only because of this branch's publication marker; master has no marker). | `org.xvm.asm.DelegationSynthesisTest` concurrent hammer (probabilistic red: half-built method observed / null multimethod); the deterministic marker test is branch-only. | Needs a filtered master patch; the mechanics (detached build, `publishRuntimeChild`, `publishOrAdoptSynthesizedMethod`, volatile maps) are additive and portable. |
| 30 | Source-only clean; one-word fix in `Version.isSameAs`. | `org.xvm.asm.VersionTest.testIsSameAsAcrossDifferingPartCounts` fails with `ArrayIndexOutOfBoundsException` at `Version.java:492` on the unfixed source. | Ready after manual review. |
| 31 | Source-only clean; add 16 `case` labels to `Op.toName`. Fixed in-branch. | `Op.toName(0xDD)` throws `IllegalStateException: op=0xdd` while `toName(0x01)` returns `LINE_1`; 16 opcodes reachable via `instantiate` are absent from `toName`. | Ready after manual review. |
| 32 | Seven sites plus a `construct(String)` on `lib_ecstasy` `TimeZone`. Fixed in-branch, verified end to end. | `pool.ensureLiteralConstant(Format.TimeZone, "x")` throws `IllegalStateException: unsupported format: TimeZone` where `Date`/`Time`/`Duration` succeed. | Ready after manual review. |
| 33 | Source-only; two one-line fixes. Surfaced set for the guard measured empty (183/183 AST classes load clean). Fixed in-branch. | `fieldsForNames(AstNode.class, "noSuchFieldAnywhere")` returns `fields[0]=null` instead of throwing; `isInstance(AstNode.class)` observed `false` where `isAssignableFrom` is `true`. | Ready after manual review. |
| 34 | Source-only; one word (`info1` -> `info2`). Fixed in-branch. | Caller `mergeTypeInfo` treats both TypeInfos as independently nullable; with `info1` present and `info2` absent the guard passes and `info2.getChildInfosByName()` NPEs. | Ready after manual review. |
| 35 | Source-only; two methods, three lines each. Fixed in-branch, verified end to end. | `org.xvm.runtime.IndexNarrowingTest.noIndexedMethodRangeChecksANarrowedIndex` fails on the unfixed source naming both sites; at run time `"abcdefgh"[4294967300]` answers `'e'` where the same index on an `Int[]` raises. | Ready after manual review. |
| 36 | Source-only; one offset and one missing override. Fixed in PR #563 with tests. | `TestArray.testDeleteRange` fails on master: `Int8[1,2,3,4,5].deleteAll(1..2)` answers `[1, 3, 4]` where `[1, 4, 5]` is required, and `String[...].deleteAll(1..2)` throws `ClassCastException: StringArrayHandle cannot be cast to GenericArrayDelegate`. | Filed as PR #563. |
| 37 | Source-only; two added overrides, no change to existing code. Fixed on `lagergren/fix-slice-compare-identity`, verified red-on-master and green-after. | `TestArray.testSliceIdentity` dies on clean master `145f12f51` at `array.x:84` with `ClassCastException: SliceHandle cannot be cast to GenericArrayDelegate`; the view case dies the same way with `ByteBasedBitView$ViewHandle`. Both answer `True` after the fix, and the full `array.x` suite runs with 0 unhandled exceptions. | Filed as PR #564. |

## Reuse Exposure Categories

This is the filing sort the dev team will care about most. The first group
breaks even if nobody ever allows two different containers, runs, or compiler
requests to coexist in one JVM. Rows 9 and 10 are a subcase of that group:
they use ordinary Java Timer/server/IO concurrency that already exists inside a
single master container. The second group is the set that starts breaking, or
becomes immediately security-critical, once the process contains multiple
containers, sequential runs, or a resident compiler/LSP/debugger.

| Exposure | Rows | Why this is the right bucket |
| --- | --- | --- |
| **Breaks without multiple containers/runs/compilations** | 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 18 | These are ordinary wrong results, lost failures, or already-present Java thread races: swallowed jsondb rollback evidence, corrupt-module cause loss, zero-op serialization, compiler catch-and-continue, async write false success, `Future.and` wrong input, JIT failure misreporting, detached startup causes, timer callback map races, native keep-alive leaks, broken Java collection contracts, reflection tuple aliasing, library `Parameter` mutation, contribution hidden-outer owner, and dropped finalizers. None needs same-VM reuse to be a bug. |
| **Starts breaking immediately when one JVM hosts multiple containers/runs/compilations** | 12, 13, 17 | Handle view lifecycle state, live `HandleConstant` serving, and `MethodStructure.Source` hidden-outer ownership are owner-boundary bugs. Their most important impact is same-VM isolation: views of one object disagree about mutability/cells, a constant heap can hand container B a live handle from container A, and copied method source metadata can resolve through the wrong pool once copies cross owners. |

Rows 12 and 17 have single-run symptoms too, but they are best filed in the
reuse/security batch: row 12 contains atomic/injected and freeze-state bugs plus
fail-loud guards for latent view shapes; row 17 is structurally wrong on every
copy but becomes most observable when the copy crosses pools.

## 1. jsondb rollback failure retention

**Issue title:** Do not swallow jsondb rollback failure after commit failure.

**Status/category:** Category A. Single-threaded master bug; any failed commit
whose compensating rollback also fails loses the only evidence that the
transaction disposition is unknown.

**Explanation:** This is not a logging preference. A database commit failure is
already a boundary where the caller must assume storage state may be abnormal.
If the compensating rollback also fails, the client has crossed from "operation
failed cleanly" into "the transaction's final state is unknown". Silencing that
second exception removes the one piece of evidence recovery code and operators
need in order to decide whether to quarantine, retry, rebuild indexes, or
surface a stronger health signal.

The security angle is auditability and integrity. A storage layer that hides a
failed compensation path can make a partial write look like an ordinary request
error, which is exactly the kind of failure mode that later becomes data loss or
cross-request consistency damage. This fix keeps the runtime behavior stable but
ensures the critical evidence is not thrown away.

**Master evidence:** `origin/master:lib_jsondb/src/main/x/jsondb/Client.x:1427`
sets the result to `DatabaseError`; line 1430 has `catch (Exception ignore) {}`.

**Failure mode:** The commit path is already in an exceptional state when it
tries to compensate with `txManager.rollback(writeId_)`. If that rollback also
throws, jsondb is no longer merely reporting "commit failed"; it no longer knows
whether the write was committed, rolled back, or left for TxManager recovery.
That second failure is the signal a health check, recovery tool, or operator
needs in order to treat the transaction as unknown-disposition instead of a
cleanly rolled-back commit failure. Master deletes that signal with an empty
catch block, so the caller gets the same `DatabaseError` shape for both states.

**Minimal master-portable fix strategy:** Keep result, close, and `rootTx`
clearing unchanged. Log the rollback failure with the original commit failure as
context.

**Patch/diff section:** Verbatim branch seed: `a935bc553`.

```diff
diff --git a/lib_jsondb/src/main/x/jsondb/Client.x b/lib_jsondb/src/main/x/jsondb/Client.x
@@
-                    } catch (Exception ignore) {}
+                    } catch (Exception e2) {
+                        // a rollback failure after a commit failure means this client no longer
+                        // knows the transaction's disposition; the TxManager owns recovery, but
+                        // the secondary failure is exactly the evidence that health/recovery
+                        // needs, so it must not disappear into an ignored catch
+                        log($"Exception during rollback after failed commit of {this}: {e2} (commit failure: {e})");
+                    }
```

**Tests to add/run on master:** `JsondbClientRollbackFailureTest.commitFailureDoesNotSwallowRollbackFailure()`
is a deterministic source-shape regression test for this exact error-path
contract. It is red on the old/master shape because the rollback region contains
`catch (Exception ignore) {}` and no preserved commit/rollback context; it is
green after the fix. Run
`./gradlew :javatools:test --tests org.xvm.lib.jsondb.JsondbClientRollbackFailureTest --rerun-tasks --no-build-cache`
and then `./gradlew xdk:installDist --rerun-tasks --no-build-cache`, because the
behavioral fix is in XTC library source.

**Master dry-run status:** Source-only patch applies cleanly to
`origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`. A full
cherry-pick of branch seed `a935bc553` conflicts only because the same commit
also touched branch-only reentrancy docs that do not exist on master; file only
the `lib_jsondb` hunk plus the focused Java regression test.

**Dependencies/order:** None. This is the smallest opener.

## 2. Requested module load preserves corrupt-file cause

**Issue title:** Preserve requested module load failures instead of returning
`null` for corrupt modules.

**Status/category:** Category A. Single-threaded master bug; a corrupt requested
`.xtc` file is indistinguishable from a missing module.

**Explanation:** A requested module load is not a best-effort directory scan.
When a caller asks for a specific module and the repository finds a candidate
file, a parse/read failure means "this module exists but is broken", not "the
module was absent". Returning `null` erases that distinction and lets higher
layers keep searching, fall back, or report a generic missing dependency.

This matters to maintainers because module loading is a trust boundary. A
corrupt or tampered `.xtc` file should produce a typed failure with the path and
cause, not a console print and a null. The typed exception is also a use of
Java's type system to encode the contract instead of forcing every caller to
reverse-engineer state from nullable returns and side effects.

**Master evidence:** `../test-failure-evidence.md` records
`ModuleRepositoryLoadFailureTest`. Branch seed: `979784a1a`.

**Failure mode:** `FileRepository`, `DirRepository`, or `LinkedRepository`
encounters a malformed requested module, prints or drops the cause, and caller
continues as if the module was not found.

**Minimal master-portable fix strategy:** Add a tiny unchecked
`ModuleLoadException` carrying module name/path/cause. Throw it only when a
requested module file exists but cannot be loaded. Keep normal "not found"
lookup returning `null`.

**Patch/diff section:** Close patch sketch against `origin/master`.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/ModuleLoadException.java b/javatools/src/main/java/org/xvm/asm/ModuleLoadException.java
new file mode 100644
@@
+package org.xvm.asm;
+
+public class ModuleLoadException
+        extends RuntimeException {
+    public ModuleLoadException(String module, String source, Throwable cause) {
+        super("Failed to load requested module " + module + " from " + source, cause);
+    }
+}
diff --git a/javatools/src/main/java/org/xvm/asm/FileRepository.java b/javatools/src/main/java/org/xvm/asm/FileRepository.java
@@
-        } catch (IOException e) {
-            return null;
+        } catch (IOException | RuntimeException e) {
+            throw new ModuleLoadException(sModule, file.toString(), e);
         }
diff --git a/javatools/src/main/java/org/xvm/asm/LinkedRepository.java b/javatools/src/main/java/org/xvm/asm/LinkedRepository.java
@@
-            ModuleStructure module = repo.loadModule(sModule);
+            ModuleStructure module = repo.loadModule(sModule);
             if (module != null) {
                 return module;
             }
+        } catch (ModuleLoadException e) {
+            throw e;
         }
```

**Tests to add/run on master:** `ModuleRepositoryLoadFailureTest` is behavioral
red on master. Run
`./gradlew :javatools:test --tests org.xvm.asm.ModuleRepositoryLoadFailureTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Source/test files from branch seed `979784a1a`
apply cleanly to `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`.
A full cherry-pick conflicts only on branch-only reentrancy docs; file the
source/test slice, not the branch docs.

**Dependencies/order:** None, but the additive exception type is a tiny
prerequisite for the test to compile.

## 3. Method op assembly failure is terminal

**Issue title:** Do not serialize methods with empty op bytes after assembly
failure.

**Status/category:** Category A. Single-threaded master bug; a compiler defect
at emission can persist a corrupt module.

**Explanation:** Serialization is the last gate before a module becomes an
artifact other tools will trust. If method op or AST assembly fails, continuing
to write the file is equivalent to publishing a known-bad binary while recording
the real failure only on stderr. A later loader cannot reliably tell whether the
module was produced by a successful compiler run or by a compiler that swallowed
its own emission failure.

This is a concrete correctness and supply-chain issue. Build products must not
be written after an unchecked internal failure, because downstream tools,
caches, and users treat `.xtc` output as authoritative. Throwing a typed Java
failure with the method identity and cause makes the bad state terminal at the
point where the compiler still knows what went wrong.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/MethodStructure.java:2037`
prints an op assembly error to stderr; line 2058 does the same for AST assembly.

**Failure mode:** `FileStructure.writeTo(...)` completes even after method ops
cannot be encoded, and the persisted method can read back as if compilation
succeeded.

**Minimal master-portable fix strategy:** Replace print-and-continue with an
unchecked assembly exception that names the method and preserves the cause.

**Patch/diff section:** Branch seed: `536067f5e`.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/MethodStructure.java b/javatools/src/main/java/org/xvm/asm/MethodStructure.java
@@
-                System.err.println("Error in MethodStructure.assemble() of ops for "
-                        + getIdentityConstant().getValueString() + ": " + e);
+                throw new IllegalStateException("Error assembling ops for "
+                        + getIdentityConstant().getValueString(), e);
@@
-                System.err.println("Error in MethodStructure.assemble() of AST for "
-                        + getIdentityConstant().getValueString() + ": " + e);
+                throw new IllegalStateException("Error assembling AST for "
+                        + getIdentityConstant().getValueString(), e);
```

**Tests to add/run on master:** `MethodStructureAssemblyFailureTest` is
behavioral red on master. Run
`./gradlew :javatools:test --tests org.xvm.asm.MethodStructureAssemblyFailureTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Source/test files from branch seed `536067f5e`
apply cleanly to `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`.
A full cherry-pick conflicts only on branch-only reentrancy docs; file the
source/test slice, not the branch docs.

**Dependencies/order:** None.

## 4. Compiler codegen failure is terminal

**Issue title:** Stop compiler retry loops after unchecked codegen failures.

**Status/category:** Category A. Single-threaded master bug; the compiler can
catch `Throwable`, print it, and continue mutating module state.

**Explanation:** Catching `Throwable` in compiler codegen is far broader than
recovering from expected diagnostics. It catches unchecked compiler defects and
VM-level errors, prints them, and then lets the retry loop continue against data
structures that may already be partially mutated. That turns an internal
compiler failure into an ambiguous output state.

This is exactly where Java's exception type system should be used instead of a
runtime catch-all. Expected compile errors should stay in the diagnostic path;
unexpected defects should stop the compiler with their cause chain intact. That
is both easier to debug and safer for anyone consuming compiler output in an
automated build or toolchain.

This does not change the normal "collect diagnostics and keep checking" mode.
Typed compiler diagnostics still flow through `ErrorListener`, the compiler
phase loop still returns `false` for deferred work, and the launcher still
flushes accumulated node diagnostics after the phase. The terminal boundary is
only for a Java `RuntimeException` or `Error` escaping from code generation.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/tool/Compiler.java:471`
catches `Throwable`; line 473 prints to stderr.

**Failure mode:** A VM error or unchecked compiler bug during code generation
can be reported as console text while compilation retries or continues with
partially mutated structures.

**Minimal master-portable fix strategy:** Re-throw fatal errors and unchecked
compiler defects as terminal compiler failure. Preserve ordinary diagnostic
reporting for expected compilation errors.

**Patch/diff section:** Branch seed: `b00654356`.

```diff
diff --git a/javatools/src/main/java/org/xvm/tool/Compiler.java b/javatools/src/main/java/org/xvm/tool/Compiler.java
@@
-                } catch (Throwable e) {
-                    System.err.println("Failed to generate code for " + compiler);
-                    e.printStackTrace(System.err);
-                    log(ERROR, "Failed to generate code for {} due to exception: {}", compiler, e);
+                } catch (Error e) {
+                    throw e;
+                } catch (RuntimeException e) {
+                    // Code generation mutates module state. Continuing after an unchecked compiler
+                    // defect can persist corrupted bytecode or mask an ownership failure as a normal
+                    // diagnostic, so route it through the fatal launcher path with the cause intact.
+                    log(FATAL, e, "Failed to generate code for {}", compiler);
+                    throw e; // reachable only if error reporting is deliberately suspended
                 }
```

**Tests to add/run on master:** `CompilerCodegenFailureTest` is a deterministic
behavior/source-shape guard for the catch-`Throwable` retry loop. It is red on
master and green after the terminal failure change. Run
`./gradlew :javatools:test --tests org.xvm.tool.CompilerCodegenFailureTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Source/test files from branch seed `b00654356`
apply cleanly to `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`.
A full cherry-pick conflicts only on branch-only reentrancy docs; file the
source/test slice, not the branch docs.

**Dependencies/order:** None.

## 4b. Worker-thread VM defects are reported as success (analysis, 2026-09-01)

Not a new row - this is the **worker-failure channel** that row 5 already depends on and that row 8's
seed carries. Recorded here because the analysis behind it was done properly and the headline
consequence was not previously written down.

**Four real master defects, all cross-thread:**

| | site (master) | what happens |
|---|---|---|
| A | `Container.schedule:147-151` | `catch (Throwable)` -> print; the `finally` still decrements `f_pendingWorkCount`, so the container returns to idle **as if the work succeeded**. `Runtime.submitService` uses `f_executorXVM.submit(task)` and **discards the Future**, so an escaping exception would be swallowed by the `FutureTask` anyway. |
| B | `ServiceContext.drainWork:322-329` | `catch (Throwable)` -> print, then returns a normal scheduling verdict after a VM defect. |
| C | op loop `:554-557` | `catch (Throwable)` -> `raiseException("Run-time error: " + e)`, which builds the **base XTC `Exception`** - so a JVM `NullPointerException` inside an op is catchable by any user `catch (Exception e)`. |
| D | `InterpreterConnector.join():121-135` | busy-waits to idle, returns `m_containerMain.getResult()`; `m_nResult` is an `int` defaulting to 0 and set only on normal return. `Runner.java:242` returns it as the process exit code. **A worker-thread VM defect therefore exits 0.** |

**Why there is no smaller master-shaped fix.** A, B and D fail on the worker thread itself. There is
no caller on that stack to catch a rethrow, and `submit()` actively swallows into a dropped Future,
so "wrap and rethrow at the existing site" cannot work by construction. Any fix must record on the
worker and observe on the joiner - which is what a failure slot is. C is strictly downstream of B:
throwing from the op loop lands in `drainWork`'s `catch (Throwable)`, so fixing C alone just moves
the swallow.

**A hypothesis worth recording because it was disproved.** The op-loop catch was suspected to be
load-bearing for stack overflow. It is not: `Frame.ensureInitialized:358-360` guards `f_nDepth > 128`
and raises a properly-typed XTC `StackOverflow`. Confirmed by running deep XTC recursion - the
op-loop catch is never reached. C is therefore cleaner than it looks, but still needs B.

**Why this did NOT ride along on the rows 2/3/4/8 branch.** Same defect class, different risk tier.
Those rows preserve a cause on a path that **already fails**. These make paths that currently report
**success** start throwing - `join()` goes from total to partial, changing `Runner`'s exit-code
contract and every embedder's. That is a behaviour change for currently-working programs.

**The blocking prerequisite is proof, not code.** Master has no way to make a worker die on demand,
so a test today could only be source-shape assertions - exactly the weak proof the other rows avoided.
A follow-up branch needs, in order: a **fault-injection seam** so a worker failure can be provoked in
a real test; then the `Container` failure slot (`recordRuntimeFailure` / `getRuntimeFailure` /
`throwIfRuntimeFailed`, ~40 lines) with its two record sites and two `join()` check sites; a decision
on `Runner`'s exit code and what `xec` prints; and only then, separately, the op-loop split, which
changes what every running XTC program can catch and needs broad runtime testing.

## 5. Raw file submit observes queued write failures

**Issue title:** Raw file submit must observe queued write failure instead of
discarding the scheduled future.

**Status/category:** Category A. Single-service master bug; async I/O exists
without parallel containers.

**Explanation:** `RawChannel.submit()` is a deliberately non-blocking queue
operation, so returning OK immediately is the correct contract. The master bug
is that the `CompletableFuture` returned by `scheduleIO(task)` is discarded
with nothing observing it: an `IOException` thrown by the queued write on the
IO thread disappears completely, and `join()` can later report success after a
failed native write.

For storage and filesystem APIs, silently lost write failures are a security
and integrity problem. Callers may make authorization, cleanup, or durability
decisions based on a write that never happened. The fix keeps the non-blocking
contract - `submit` still assigns OK immediately - but attaches a
`whenComplete` observer to the scheduled write and records any failure through
the container's runtime-failure channel, so the failure becomes host-visible
at `join()` instead of dying on the IO thread.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java:231`
creates the write task; line 233 discards the `CompletableFuture` (`// don't
wait`); line 235 returns OK.

**Failure mode:** A queued write can fail after native `submit` has already
returned `0`; the exception is dropped on the IO thread and the host boundary
reports success.

**Minimal master-portable fix strategy:** Keep async scheduling and the
immediate OK result, but keep the scheduled future, observe its completion,
and record failure through a container-level runtime-failure channel that
`join()` consults. On master that channel (`Container.recordRuntimeFailure(...)`)
does not exist yet: file this together with (or after) the row 8
worker-failure channel from branch seed `796f13465`, or inline a minimal
failure recorder.

**Patch/diff section:** Branch seed: `8a45ba708`. This is the actual branch
hunk; the master form must use `frame.f_context.f_container` (master `Frame`
has no `container()` accessor) and `xInt64.makeHandle(0)` (master has no
frame-taking overload).

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java b/javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java
@@
         Callable<Integer> task = () -> hChannel.f_channel.write(buffer);

-        frame.f_context.f_container.scheduleIO(task); // don't wait
+        // RawChannel.submit() is a non-blocking queue operation, so preserve the immediate OK
+        // result. The scheduled write still needs an observer; otherwise an IOException on the IO
+        // thread disappears and join() can report success after a failed native write.
+        var container = frame.f_context.f_container;
+        var cfWrite   = container.scheduleIO(task);
+        cfWrite.whenComplete((_, ex) -> {
+            if (ex != null) {
+                container.recordRuntimeFailure(
+                        "Unexpected RawOSFileChannel write failure: " + hChannel.f_path, ex);
+            }
+        });

         return frame.assignValue(iReturn, xInt64.makeHandle(0)); // OK
```

**Tests to add/run on master:** `RawOSFileChannelSubmitTest` is a source-shape
test that fails while `submit` discards the scheduled future. Its assertion
literals pin the branch API shapes (`container.scheduleIO(task)`,
`xInt64.makeHandle(frame, 0)`); the master form of the test must pin the
master shapes instead. Run
`./gradlew :javatools:test --tests org.xvm.runtime.template._native.fs.RawOSFileChannelSubmitTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Verified 2026-08-25: the source/test files from
branch seed `8a45ba708` merge cleanly onto `origin/master` at
`61e555a68cd82a866f82aea40a3bb97a424a3809`, but the result does not compile
there - `Container.recordRuntimeFailure(...)` and `frame.container()` are
branch APIs. A full cherry-pick also conflicts on branch-only reentrancy docs;
file the source/test slice, not the branch docs.

**Dependencies/order:** Depends on the container runtime-failure channel from
branch seed `796f13465` (the row 8 family: `recordRuntimeFailure`,
`getRuntimeFailure`, `throwIfRuntimeFailed`, and the connector `join()`
wiring). Decided 2026-08-25: this fix files together with row 8 in one PR
(launch-plan row L7) as the channel's first consumer; it does not file
standalone.

## 6. Future.and uses both inputs and preserves async failure

**Issue title:** Fix `Future.and` fast-path double-read and assert-only async
failure path.

**Status/category:** Category A. Single-threaded master bug.

**Explanation:** The fast path reads the first completed future twice, so
`futureA.and(futureB)` can report a result pair built from `futureA` and
`futureA`. That is ordinary wrong-result behavior, independent of any stress
harness. The async path then compounds the problem by treating an unexpected
join failure as `assert false`, which production usually compiles out.

This is a good example of why stronger static structure matters. The bug is a
two-variable ownership/value mix-up that the code currently leaves to runtime
tests. Clearer typed helpers or sealed result states would make it much harder
to accidentally extract the wrong input or silently continue after an impossible
completion state.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:466`
gets `cfThis`; line 472 extracts both results from `cfThis`; line 511 uses
`assert false` after async get failure.

**Failure mode:** Completed `futureA.and(futureB)` can use `futureA` twice. If
the async join path fails, assertions-disabled production can hide the defect.

**Minimal master-portable fix strategy:** Extract the second result from
`cfThat`. In the async completion callback, move the argument extraction and
the `postRequest` dispatch inside the `try`, complete the result future with
the translated actual failure instead of `assert false`, and restore the
interrupt flag on `InterruptedException`.

**Patch/diff section:** Branch seed: `6496f5303`. This is the branch hunk in
master form: master's `Utils.translate(...)` takes only the throwable (the
branch signature is `Utils.translate(f_container, ...)`), and master's
fast-path null checks read `ahRThis[1] != xNullable.NULL` (context only). Add
the `java.util.concurrent.ExecutionException` import.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java b/javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java
@@
         if (cfThis.isDone() && cfThat.isDone()) {
             ObjectHandle[] ahRThis = extractResult(frame, cfThis);
-            ObjectHandle[] ahRThat = extractResult(frame, cfThis);
+            ObjectHandle[] ahRThat = extractResult(frame, cfThat);
@@
             CompletableFuture.allOf(cfThis, cfThat).whenComplete((_null, ex) -> {
                 if (ex == null) {
-                    ObjectHandle[] ahArg = new ObjectHandle[2];
                     try {
-                        ahArg[0] = cfThis.get();
-                        ahArg[1] = cfThat.get();
-                    } catch (Throwable e) {
-                        // must not happen
-                        assert false;
+                        ObjectHandle[] ahArg = {cfThis.get(), cfThat.get()};
+                        CompletableFuture<ObjectHandle> cfAnd =
+                                frame.f_context.postRequest(frame, hCombine, ahArg, 1);
+
+                        cfAnd.whenComplete((hNew, exTrans) ->
+                                hAnd.complete(hNew, Utils.translate(exTrans)));
+                    } catch (InterruptedException e) {
+                        Thread.currentThread().interrupt();
+                        hAnd.complete(null, Utils.translate(e));
+                    } catch (ExecutionException | RuntimeException e) {
+                        hAnd.complete(null, Utils.translate(e));
                     }
-
-                    CompletableFuture<ObjectHandle> cfAnd =
-                            frame.f_context.postRequest(frame, hCombine, ahArg, 1);
-
-                    cfAnd.whenComplete((hNew, exTrans) ->
-                            hAnd.complete(hNew, Utils.translate(exTrans)));
                 } else {
                     hAnd.complete(null, Utils.translate(ex));
```

**Tests to add/run on master:** `FutureCompletionSafetyTest` is behavioral red
on master. Run
`./gradlew :javatools:test --tests org.xvm.runtime.template.annotations.FutureCompletionSafetyTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Re-verified 2026-08-25: branch seed `6496f5303` has
a real source conflict in
`javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java`
against `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`
(`FutureCompletionSafetyTest` adds cleanly). The fast-path double-read
(`extractResult(frame, cfThis)` twice) and the `assert false` async path are
verbatim on master; use the master-form hunk above rather than resolving the
cherry-pick. Do not use branch docs or unrelated branch helpers.

**Dependencies/order:** None. The only API delta is `Utils.translate` arity,
already reflected in the hunk above.

## 7. JIT generated exception and bridge reflection failures are not swallowed

**Issue title:** JIT must propagate generated XTC exceptions and unwrap bridge
reflection exceptions.

**Status/category:** Category A for current JIT/direct execution paths; broader
JIT owner statics remain parked.

**Explanation:** The JIT boundary is a host/runtime trust boundary. Generated
code may throw an XTC exception, but the connector and bridge currently have
paths that either swallow the generated failure after printing diagnostics or
convert a real invoked exception into a generic unsupported-operation shape.
That means a host can receive "success" or the wrong exception after generated
code failed.

This has direct security relevance. JIT execution is allowed to run program
logic with native Java reflection and generated classes; if exceptions are
rewrapped incorrectly, callers cannot reliably distinguish language exceptions,
bridge defects, and VM failures. The minimal fix is deliberately not the broad
JIT owner-state rework: it only makes the boundary report the real failure
deterministically.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/javajit/JitConnector.java:142`
catches `InvocationTargetException`; line 152 catches `Throwable ignore`.
`origin/master:javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java:130`,
`:187`, and `:231` catch `IllegalAccessException | InvocationTargetException`
together.

**Failure mode:** `JitConnector` keeps its result in a stored `long result`
field; the natural-exception arm never assigns it, relying on the field's
initial value, so a result already stored by a successful invocation can
survive a later generated failure - and the diagnostic print wraps its
reflection in `catch (Throwable ignore) {}`, silently swallowing VM errors
during failure rendering. Separately, and single-run reachable, `nType`
reflection can turn an invoked XTC `nException` into `$unsupported`, changing
the natural exception type the program observes.

**Minimal master-portable fix strategy:** In `JitConnector`, explicitly pin
`this.result = 1` in the natural-exception arm and narrow the print fallback
catch to `ReflectiveOperationException | RuntimeException`. In `nType`, catch
`InvocationTargetException` separately, unwrap its cause, rethrow XTC
`nException`, rethrow `Error`, and keep `$unsupported` only for bridge
reflection access failures.

**Patch/diff section:** Branch seeds: `33323ffe1`, `f4744cb1e`. These are the
actual branch hunks. `nType` has three identical catch sites (`equals`,
`compare`, `hashCode$p`); the hunk below shows the `equals` site and the two
shared helpers - `compare` and `hashCode$p` change the same way.

```diff
diff --git a/javatools/src/main/java/org/xvm/javajit/JitConnector.java b/javatools/src/main/java/org/xvm/javajit/JitConnector.java
@@
             if (name.startsWith(TypeSystem.ClassfileShape.Exception.prefix) ||
                 name.charAt(0) == TypeSystem.NO_MOD) {

+                // Match the interpreter connector result contract: an unhandled natural exception
+                // keeps the default non-zero result. Printing the exception is diagnostic only; the
+                // host boundary must not report success after generated XTC code threw.
+                this.result = 1;
                 try {
                     // TODO: add the service info; see Utils.log()
                     System.out.println("\nUnhandled exception: " +
                         cause.getClass().getField("exception").get(cause));
-                } catch (Throwable ignore) {}
+                } catch (ReflectiveOperationException | RuntimeException _) {}
             } else {
diff --git a/javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java b/javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java
@@
             java.lang.Boolean result = (java.lang.Boolean)
                     equalsMethod.invoke($xvmClass(ctx), ctx, this, value1, value2);
             return result.booleanValue();
-        } catch (IllegalAccessException | InvocationTargetException e) {
-            throw Exception.$unsupported($ctx,
-                "Failed to invoke 'equals()` on class " + $dataType.getValueString());
+        } catch (IllegalAccessException e) {
+            throw invocationUnsupported("equals()");
+        } catch (InvocationTargetException e) {
+            throw invocationFailure("equals()", e);
         }
     }
@@
+    /**
+     * Reflection wraps exceptions thrown by generated XTC methods. Replacing a natural
+     * {@link nException} with Unsupported changes language semantics, so unwrap and rethrow it.
+     */
+    private nException invocationFailure(java.lang.String methodName, InvocationTargetException e) {
+        Throwable cause = e.getCause();
+        if (cause instanceof nException exception) {
+            return exception;
+        }
+        if (cause instanceof Error error) {
+            throw error;
+        }
+        return invocationUnsupported(methodName);
+    }
+
+    private nException invocationUnsupported(java.lang.String methodName) {
+        return Exception.$unsupported($ctx,
+                "Failed to invoke '" + methodName + "' on class " + $dataType.getValueString());
+    }
```

**Tests to add/run on master:** `JitFailurePropagationTest` is a source-shape
test (red on master: the `this.result = 1;` pin and the narrowed catch are
missing in `JitConnector`, and `nType` still catches
`IllegalAccessException | InvocationTargetException` together). Run
`./gradlew :javatools:test --tests org.xvm.javajit.JitFailurePropagationTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Verified 2026-08-25: source/test files from branch
seeds `33323ffe1` and `f4744cb1e` apply cleanly to `origin/master` at
`61e555a68cd82a866f82aea40a3bb97a424a3809` when applied in that order
(`f4744cb1e` edits the test that `33323ffe1` adds). A full cherry-pick
conflicts only on branch-only reentrancy docs; file the source/test slice, not
the branch docs.

**Dependencies/order:** None for this narrow failure boundary. Keep generated
owner-static fixes out of this issue.

## 8. MainContainer startup preserves failure causes

**Issue title:** Preserve startup/invocation cause chains at the host boundary.

**Status/category:** Category A. Single-run master bug; startup failure
diagnostics lose the real cause.

**Explanation:** Startup and invocation are the point where host tooling,
embedders, LSPs, test runners, and build scripts learn whether the runtime
worked. Replacing the original exception with a string containing
`e.getMessage()` destroys the Java cause chain, stack, suppressed exceptions,
module identity, and sometimes the only owner/context clue. That makes failures
harder to diagnose and easier to misclassify as user errors.

This also matters for same-VM use. An LSP or compiler process that runs many
modules sequentially and concurrently cannot rely on stderr prints or detached
messages; it needs exact failure objects so it can isolate a failed run and keep
the process usable. Preserving causes is the low-risk first step before any
broader runtime ownership work.

**Master evidence:** `../test-failure-evidence.md` records
`RuntimeFailurePropagationTest`; branch seed is `3e09abc32`. The old
`MainContainer.invoke0()` message built `". Cause: " + e.getMessage()`.

**Failure mode:** Module-load, startup, or invocation failure loses type,
stack, suppressed exceptions, and owner/module context. Host code sees a generic
runtime failure with the original cause detached.

**Minimal master-portable fix strategy:** When wrapping startup failure, chain
the original exception as the Java cause. Do not concatenate only its message.

**Patch/diff section:** Close patch sketch.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/MainContainer.java b/javatools/src/main/java/org/xvm/runtime/MainContainer.java
@@
-        } catch (Exception e) {
-            throw new RuntimeException("Failed to invoke " + method + ". Cause: " + e.getMessage());
+        } catch (Exception e) {
+            throw new RuntimeException("Failed to invoke " + method, e);
         }
```

**Tests to add/run on master:** `RuntimeFailurePropagationTest` is behavioral
red on master. Run
`./gradlew :javatools:test --tests org.xvm.runtime.RuntimeFailurePropagationTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Verified 2026-08-25: source/test files from branch
seed `3e09abc32` apply cleanly to `origin/master` at
`61e555a68cd82a866f82aea40a3bb97a424a3809`. The follow-on worker-failure
channel seed `796f13465` (`Container.recordRuntimeFailure(...)`,
`getRuntimeFailure()`, `throwIfRuntimeFailed()`, `InterpreterConnector`/
`ServiceContext` wiring, and extra `RuntimeFailurePropagationTest` cases) is
also source-clean when applied after `3e09abc32` - its test hunks edit the
test that `3e09abc32` adds. A full cherry-pick conflicts only on branch-only
reentrancy docs; file the source/test slice, not the branch docs.

**Dependencies/order:** None. Worker terminal-failure and op-loop defect
propagation can be separate issues if the patch becomes large - but row 5
depends on `796f13465`'s failure channel, so if that seed is not included
here, row 5 must inline its own minimal recorder.

## 9. Alarm callback registry is timer-thread safe

**Issue title:** Make alarm callback extraction safe against the shared Java
timer thread.

**Status/category:** Category A. Concurrent, but reachable in any timer-using
program because the service thread and the process-wide Java `Timer` thread
exist on master.

**Explanation:** This is not an exotic parallel-container bug. The Java timer
thread exists as soon as a program uses alarms, and it runs outside the service
thread that registers callbacks. A lazily-created plain `HashMap` shared between
those threads has no concurrency contract. A resize or remove racing a put can
corrupt the map, lose the callback, or throw from the timer task.

The worst failure mode is process-wide: an exception escaping a `TimerTask` can
kill the shared Java timer and silently disable future alarms. That is a denial
of service against every container in the same VM. The fix uses the standard
Java concurrent container that encodes the intended cross-thread access pattern
instead of relying on mutable shared state and timing luck.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java:232`
extracts callback state from a timer thread. `xNanosTimer.java:378` has the
same shape. `../state-inventory.md` records the non-concurrent callback map
sequence.

**Failure mode:** The timer thread and service thread can mutate the weak
callback registry concurrently. Worst case: a thrown timer task kills the
process-wide timer and disables every alarm.

**Minimal master-portable fix strategy:** Make the callback registry concurrent
and make `extractCallback()` atomic/null-safe for timer-thread use. Timer
callbacks should drop dead callbacks without throwing.

**Patch/diff section:** Branch seed: `26ce54466`; close patch sketch because
the exact registry class lives outside the timer files.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/WeakCallback.java b/javatools/src/main/java/org/xvm/runtime/WeakCallback.java
@@
-    private final Map<Object, Callback> callbacks = new HashMap<>();
+    private final ConcurrentMap<Object, Callback> callbacks = new ConcurrentHashMap<>();
@@
-    public Callback extractCallback() {
-        return callbacks.remove(key);
+    public Callback extractCallback() {
+        return callbacks.remove(key);
     }
diff --git a/javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java b/javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java
@@
-                    WeakCallback.Callback callback = f_refCallback.extractCallback();
-                    context.callLater(callback.frame(), callback.functionHandle(), Utils.OBJECTS_NONE);
+                    WeakCallback.Callback callback = f_refCallback.extractCallback();
+                    if (callback != null) {
+                        context.callLater(callback.frame(), callback.functionHandle(), Utils.OBJECTS_NONE);
+                    }
```

**Tests to add/run on master:** `NativeCallbackRegistrationTest.callbackRegistryIsConcurrentTimerSafeAndLeakFree()`
is behavioral/source-shape red on master. Run the focused test class:
`./gradlew :javatools:test --tests org.xvm.runtime.NativeCallbackRegistrationTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Branch seed `26ce54466` has real source conflicts in
`javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xLocalClock.java`
and
`javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xNanosTimer.java`
against `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`.
`ServiceContext.java` and `WeakCallback.java` apply cleanly. Extract the
concurrent-registry and null-safe timer-fire hunks manually; add
`NativeCallbackRegistrationTest` as a new master test file rather than relying
on the branch patch context.

**Dependencies/order:** Can land with issue 10 if review prefers one native
callback lifecycle PR; otherwise independent.

## 9b. An exception escaping a Trigger kills the process-wide alarm Timer

**Deliberately NOT part of rows 9/10.** Filed separately because it stands alone against unmodified
master and because its fix is structurally different: rows 9 and 10 remove *known* throwers, this one
says the timer thread must survive an *unknown* one.

### The defect, with exact sites on `origin/master`

`xLocalClock.java:282`
```java
public static Timer TIMER = new Timer("ecstasy:LocalClock", true);
```
**One process-wide static `java.util.Timer`** - one thread, shared by every alarm in every container
in the JVM.

`xLocalClock.java:265-268`, the only thing scheduled on it:
```java
protected static class Trigger extends TimerTask {
    @Override
    public void run() {
        f_alarm.run();          // no try/catch
    }
}
```

`java.util.Timer`'s own thread catches nothing. **An exception escaping `TimerTask.run()` terminates
the timer thread and cancels the Timer permanently.** Every later `TIMER.schedule(...)` - at
`:128`, `:226` and `:239` - then throws `IllegalStateException: Timer already cancelled`.

So one bad alarm silently disables **all** alarms, in **all** containers, for the rest of the process.

### Why it is reachable today, not theoretical

`Alarm.run()` (`:231-236`) does:
```java
WeakCallback.Callback callback = f_refCallback.extractCallback();
context.callLater(callback.frame(), callback.functionHandle(), Utils.OBJECTS_NONE);
```

and `WeakCallback.extractCallback()` on master (`WeakCallback.java:29-38`) ends in a bare
`throw new IllegalStateException();` whenever the context is gone or the callback id is already
removed - i.e. exactly the shutdown/cancel race a timer thread is most likely to hit. Row 9 removes
*that* thrower. It does not make the `Trigger` safe: `context.callLater(...)` reaches `postRequest`
and the fiber machinery on a possibly-shutting-down service, and any future thrower on this path has
the same process-wide blast radius.

### Why the symptom is so unpleasant

The failure does not surface where it happens. A dead `Timer` means alarms **silently never fire**,
so the observable behaviour is a hang - in an unrelated container, arbitrarily later. Nothing logs.

### The fix, and what it is NOT

Guard `Trigger.run()` so the timer thread survives, and record the failure rather than discarding it.
**Do not wrap `context.callLater(...)` in a blanket `catch`** - that swallows real errors at the one
place a diagnostic would be useful. The right shape is the same "record on the worker, observe on the
joiner" channel as §4b, so this may want to land after it.

### Ordering relative to rows 9/10

Independent in both directions; either can land first.

- Filed FIRST (against unmodified master), the red-on-master test can use master's own
  `extractCallback()` throw as the injection mechanism - schedule an alarm, drop the callback, watch
  `TIMER` die and a subsequent `schedule` throw `Timer already cancelled`.
- Filed AFTER row 9, that route is gone, so the test must inject a thrower directly (a `Trigger`
  subclass, or a callback whose `callLater` fails). Slightly more work, same defect.

## 9c. `callLater`'s completion handler loses any failure that is not a bare `WrapperException`

Found 2026-09-01 while checking whether the discarded `callLater` future in `Alarm.run()` mattered.
It does not - but this does. Both `ServiceContext.callLater` overloads carry the identical lambda:

```java
future.whenComplete((r, x) -> {
    if (x != null) {
        callUnhandledExceptionHandler(((WrapperException) x).getExceptionHandle());
    }
});
```

**Open question the fix depends on:** `whenComplete` on a *dependent* stage receives the failure
wrapped in `CompletionException`, not the original. Whether that reaches this lambda depends on how
`postRequest`'s future is completed and whether any stage is chained in between. If `x` is provably
always a bare `WrapperException`, this is a defensive hardening; if a `CompletionException` or a raw
`Error` can arrive, it is a live bug. **Trace it before writing the PR description** - the framing
differs completely.

### Why this belongs with §4b and not with a tidy-up

A blind cast normally announces itself. This one cannot: **`CompletableFuture` swallows anything
thrown inside `whenComplete`**. So the wrong cast produces no visible `ClassCastException` - it
produces the *absence* of `callUnhandledExceptionHandler` ever running. The type system was
disabled at precisely the point where the failure of that assumption becomes unobservable, and the
code being type-punned is the error-handling path itself. A wrong guess therefore converts a
**reported** failure into a **lost** one, which is strictly worse than an ordinary bad cast.

### For the PR description - the general argument, once the trace above settles the specifics

This is the concrete exhibit for the "Python in Java" case that E2 and E14 make in the enhancement
list: promoting a type distinction to a runtime cast means the compiler cannot prove the dispatch is
complete, so a case the author did not consider compiles cleanly and fails at run time. What makes
this instance worth putting in a PR description rather than an audit document is the extra turn -
here the failure is not merely deferred to run time, it is **silenced**, because the swallowing
happens in a completion stage. That is the sharpest available argument for typing the failure
channel rather than casting it, and it should be stated in whichever PR carries the fix.

### Placement

Not yet decided. `master-clock-callback-registry` already edits `ServiceContext.java` (no new
conflict surface); `master-alarm-timer-survives-callback-failure` has the closer story (its guard
covers the synchronous escape that kills the timer, this covers the asynchronous one that vanishes).
Counter-argument: `callLater` has many callers, so this is a general `ServiceContext` defect that
alarms merely hit. Fix both overloads or neither.

## 10. Native callback registration rolls back on startup failure

**Issue title:** Roll back native callback keep-alive registration when timer or
server startup fails.

**Status/category:** Category A. Single-threaded failure paths.

**Explanation:** Native callback registration is a lifecycle count that keeps a
container alive while Java-side timer/server work may call back into it. If
startup fails after the count is incremented, the container can be pinned
forever even though the native resource was never successfully installed. That
turns a local startup failure into a leak of process resources and runtime
liveness state.

This is also an ownership problem. The code mutates a shared keep-alive counter
without a transactional owner handoff: either the native resource is installed
and owns a callback, or the counter must be rolled back. Encoding that lifecycle
explicitly is safer than relying on scattered mutable fields and broad catch
blocks to happen in the right order.

**Master evidence:** `origin/master:xLocalClock.java:203` registers in the
alarm constructor. `origin/master:xRTServer.java:286` registers after server
start; lines 288-290 can still fail; catch at 293-295 terminates without
unregistering.

**Failure mode:** A failed alarm/server setup can leave the container's native
callback count positive forever, preventing idle termination or making `join()`
hang.

**Minimal master-portable fix strategy:** Register only after successful
scheduling or track the exact registered container and unregister on any
post-registration failure. For `xRTServer`, close partial Java server resources,
clear native handle fields, and unregister if bind startup fails after
keep-alive registration.

**Patch/diff section:** Branch seed: `5311da1ac`; close patch sketch.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java b/javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java
@@
-        try {
+        boolean registered = false;
+        try {
@@
             hServer.f_context.f_container.registerNativeCallback();
+            registered = true;
@@
         } catch (Exception e) {
+            if (registered) {
+                hServer.f_context.f_container.unregisterNativeCallback();
+            }
+            hServer.closePartialResources();
             frame.f_context.f_container.terminate(hServer.f_context);
             return frame.raiseException(xException.obscureIoException(frame, e.getMessage()));
         }
```

**Tests to add/run on master:** `NativeCallbackRegistrationTest` is red on the
old shapes. Run
`./gradlew :javatools:test --tests org.xvm.runtime.NativeCallbackRegistrationTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Branch seed `5311da1ac` has real source conflicts in
`xLocalClock.java` and `xNanosTimer.java` against `origin/master`
`61e555a68cd82a866f82aea40a3bb97a424a3809`; the `xRTServer.java` slice applies
cleanly. Either pair this with issue 9 and resolve timer-file lifecycle hunks
once, or file the `xRTServer` rollback slice separately and extract timer
rollback hunks later.

**Dependencies/order:** Can be paired with issue 9.

## 11. Hash/equality contracts for Register, VersionTree, and MethodBody

**Issue title:** Fix metadata equality/hash contracts and cycle-prone method
body equality.

**Status/category:** Category A. Single-threaded map/set misuse and cyclic
metadata graphs.

**Explanation:** Java collections assume `equals` and `hashCode` describe the
same identity. When metadata objects violate that contract, sets and maps can
hold duplicates, miss existing entries, or produce order-dependent behavior.
This is not a style issue; it breaks ordinary Java data structures used by the
compiler and runtime.

The `MethodBody` piece is the deeper type-safety problem. Equality walks through
owner-shaped metadata graphs instead of comparing a stable target identity, so a
legal cyclic graph can recurse or observe mutable owner state. This is where the
code should lean on explicit key types or sealed identity shapes rather than
deferring meaning to runtime graph walks and casts.

**Master evidence:** `origin/master:Register.java:430` and `:773` define
`equals(...)`; no matching `hashCode()` for those shapes. `origin/master:MethodBody.java:699`
hashes only `m_id`; lines 704-716 compare implementation, id, signature, and
`m_target` by graph equality. `VersionTest` covers `VersionTree`.

**Failure mode:** Equal metadata keys can occupy different hash buckets.
`MethodBody.equals(...)` can recurse through owner metadata graphs and overflow
or compare unstable owner state.

**Minimal master-portable fix strategy:** Add hash codes for every existing
equality field. For `MethodBody`, compare stable target identity shape instead
of walking owner metadata graphs.

**Patch/diff section:** Extract only the hash-contract hunks for
`Register.java`, `VersionTree.java`, and `ChildInfo.java` from `9456d6727`, plus
the cycle-safe `MethodBody` target-key hunk from `a11765c86`. Do not use
`0231a8771` for this issue; that commit is the later owned-copy self-target
regression and belongs to the branch XDK build-break chain, not this master
hash/equality filing. Close patch sketch.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/Register.java b/javatools/src/main/java/org/xvm/asm/Register.java
@@
+    @Override
+    public int hashCode() {
+        return Objects.hash(getIndex(), getType(), getName());
+    }
diff --git a/javatools/src/main/java/org/xvm/asm/constants/MethodBody.java b/javatools/src/main/java/org/xvm/asm/constants/MethodBody.java
@@
-            && Handy.equals(this.m_target, that.m_target);
+            && Handy.equals(stableTargetKey(this.m_target), stableTargetKey(that.m_target));
```

**Tests to add/run on master:** `RegisterHashCodeTest`, `VersionTest`, and
`MethodInfoTest.methodInfoEqualityDoesNotRecurseThroughMethodTargets()`.

**Master dry-run status:** Do not cherry-pick `9456d6727` wholesale: it is a
broad lint/constructor-escape/audit commit and conflicts through unrelated
runtime files. Build a filtered patch containing only `Register.java`,
`VersionTree.java`, `ChildInfo.java`, their tests, and the cycle-safe
`MethodBody.java`/`MethodInfoTest` hunk from `a11765c86`.

**Dependencies/order:** None. Keep unrelated metadata owner-copy APIs out.

## 12. Handle view lifecycle state is shared or refused

**Issue title:** Handle access views must not split atomic/injected/freeze
lifecycle state.

**Status/category:** Category A for atomic/injected first-install races and
freeze-state split; guards for register-bound refs are latent hardening. The
array axis was upgraded 2026-08-25 from latent to **proven reachable**: the
same-JVM stress harness hit `MOV_THIS_A #1, PRIVATE` on a live mutable
`Array<Char>` in TestArray, so access views of mutable arrays are ordinary
single-threaded interpreter behavior; on master's per-view fields, a
subsequent `clear()` through one view forks the delegate pointer from its
sibling and a freeze through one view leaves the sibling still claiming
mutability over frozen shared storage.

**Explanation:** A handle access view is supposed to be another view of the same
runtime object, not a fork of the object's lifecycle state. Master stores
mutability, atomic backing cells, injected values, and some register/array
state in per-view mutable fields. Once a view is cloned, one path can freeze,
assign, or inject while another path still sees a different state over the same
logical object.

This is exactly the kind of bug that runtime casts and mutable arrays hide until
state leaks across an isolation boundary. Atomic and injected values should be
constructor-final shared cells with clear owner semantics; views that cannot
share safely should be refused. Otherwise a same-VM compiler, LSP, debugger, or
container host can observe split object state that Java's type system could have
prevented with explicit state objects and sealed view categories.

**Master evidence:** `origin/master:ObjectHandle.java:49` stores mutable state
as per-handle `m_fMutable`; line 65 uses shallow `clone`; line 92 clears only
that view. `origin/master:xAtomicIntNumber.java:134` lazily installs
`m_atomicValue`; `xAtomicInt128.java:119` has the same shape.

**Failure mode:** Two services can race first assignment on one shared
`@Atomic` instance and lose an update. A view can freeze one handle while a
sibling view still reports mutable over the same field storage.

**Minimal master-portable fix strategy:** Introduce tiny shared cells for
state that is logically object-wide. Use constructor-final cells for atomic and
injected referents, and a CAS-installed freeze cell for `GenericHandle` views.
Refuse `cloneAs(...)` where master has no legitimate safe sharing model.

**Patch/diff section:** Branch seeds: `c5c40d443`, `d2165e4f8`, `f4df60ed1`,
`7ce5662d1`. Close patch sketch.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/ObjectHandle.java b/javatools/src/main/java/org/xvm/runtime/ObjectHandle.java
@@
-    protected boolean m_fMutable;
+    private final AtomicReference<FreezeCell> f_freeze = new AtomicReference<>();
@@
-        m_fMutable = false;
+        freezeCell().freeze();
@@
-        return m_fMutable;
+        return !freezeCell().isFrozen();
diff --git a/javatools/src/main/java/org/xvm/runtime/template/annotations/xAtomicIntNumber.java b/javatools/src/main/java/org/xvm/runtime/template/annotations/xAtomicIntNumber.java
@@
-        protected AtomicLong m_atomicValue;
+        protected final AtomicLong f_atomicValue = new AtomicLong(UNASSIGNED);
```

**Tests to add/run on master:** `AtomicViewSharingTest` and
`FreezeViewSharingTest` are red on the old shapes. `RefViewGuardTest` is a
source-shape/guard proof; `ArrayViewGuardTest` and `TupleViewGuardTest` are
now SHARING proofs (2026-08-25: the freeze cell was hoisted to ObjectHandle
and Array/Tuple/Function views share all lifecycle state; both tests are
red on the per-view shape, which is master's).

**Master dry-run status:** The four branch seeds are not one clean
master-cherry-pick. `c5c40d443` applies source-only cleanly for the
atomic/injected cells, but `d2165e4f8` conflicts in `ObjectHandle.java`; the
array/ref guard commits are smaller and should be checked after the freeze-cell
conflict is resolved. File this as split patches if the exact master hunk grows
beyond the two proven master bugs: atomic/injected cell sharing and freeze-state
sharing.

**Dependencies/order:** Keep this as one issue only if the patch stays small by
mechanism. Otherwise split into atomic/injected cell sharing and freeze cell.

## 13. HandleConstant does not serve live handles to the wrong container

**Issue title:** Guard `HandleConstant` against raw cross-container handle
serving.

**Status/category:** Category A when sibling/nested containers share module
constants containing live handles.

**Explanation:** `HandleConstant` stores a live runtime handle in a constant
pool. If another container resolves the same module constant and receives that
raw handle, it gets access to an object owned by a different container. That is
not serialization or pass-through; it is a direct capability leak across the
runtime isolation boundary.

The security implication is direct. Containers are used to isolate services,
native resources, and object graphs inside one JVM. Returning a live handle to
the wrong owner can bypass mask/proxy checks and hand one container authority
over another container's state. This is precisely where Java should use explicit
owner-typed APIs instead of runtime casts and nullable checks around an
unqualified `ObjectHandle`.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:34`
returns a live handle for the current `Frame`.

**Failure mode:** A constant heap can serve a handle created under container A
to container B. The receiving runtime obtains a capability into the wrong
owner graph.

**Minimal master-portable fix strategy:** Add an owner check at the
`HandleConstant` boundary. If the handle is already shared/pass-through for the
target container, allow it. Otherwise reject or reconstitute through the target
container if an existing master API supports that exact value.

**Patch/diff section:** Branch seed: `632cac927`; close patch sketch.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java b/javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java
@@
-    public ObjectHandle getHandle(Frame frame) {
-        return m_hValue;
+    public ObjectHandle getHandle(Frame frame) {
+        ObjectHandle handle = m_hValue;
+        Container container = frame.f_context.f_container;
+        if (!handle.isShared(container)) {
+            throw new IllegalStateException("HandleConstant belongs to a different container");
+        }
+        return handle;
     }
```

**Tests to add/run on master:** `HandleConstantOwnerGuardTest` is branch-API
based; `HandleConstantAssembleTest` covers persistence. Run focused
`./gradlew :javatools:test --tests org.xvm.runtime.HandleConstantOwnerGuardTest --tests org.xvm.asm.constants.HandleConstantAssembleTest --rerun-tasks --no-build-cache`
after adapting to master APIs.

**Master dry-run status:** Branch seed `632cac927` has a real source conflict in
`javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java` against
`origin/master` `61e555a68cd82a866f82aea40a3bb97a424a3809`. Extract the owner
guard and assemble-refusal hunks; avoid branch-only ownership diagnostic APIs in
the master issue.

**Dependencies/order:** Can land before broad adoption work. Keep this narrow;
do not backport all `OwnershipDiagnostics`.

## 14. Reflection Method.invoke does not alias caller tuple storage

**Issue title:** Clone reflection invoke argument arrays before frame reuse.

**Status/category:** Category A. Single-threaded master bug.

**Explanation:** Reflection invoke is allowed to build a callee frame from a
caller-supplied tuple. Master passes the tuple's backing array directly when no
extra registers are needed. That aliases caller storage into the callee register
file, so parameter assignment by the callee can mutate the caller's tuple. If
that tuple came from a const heap or shared runtime object, the mutation can
escape far beyond the reflective call.

This is the same class of problem as the broader array leak audit: arrays are
mutable references, not values. The code should make ownership transfer
explicit by cloning at the boundary. Relying on convention around `ObjectHandle[]`
turns Java into a dynamically checked language and postpones a preventable
ownership error until runtime corruption.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:206`
passes `hTuple.m_ahValue` directly. `xRTFunction.java:251` already clones in
the same no-extra-register case.

**Failure mode:** A reflective method call can use the caller tuple's storage
as the callee register file. Parameter reassignment then mutates the caller's
tuple, including immutable or const-heap-cached tuple storage.

**Minimal master-portable fix strategy:** Mirror `xRTFunction`: clone the tuple
array when it can be used directly, and only use `Utils.ensureSize(...)` on a
copy or when expansion is required.

**Patch/diff section:** Verbatim branch seed: `ff8cc479a`.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java b/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java
@@
-        ObjectHandle[] ahPass  = hTuple.m_ahValue;  // TODO GG+CP do we need to check these?
+        ObjectHandle[] ahPass  = hTuple.m_ahValue.clone();
```

**Tests to add/run on master:** `MethodInvokeArgumentAliasingTest` has a
source-shape red pin on master. The full execution test can ride with same-JVM
stress. Run
`./gradlew :javatools:test --tests org.xvm.runtime.template._native.reflect.MethodInvokeArgumentAliasingTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Source/test files from branch seed `ff8cc479a`
apply cleanly to `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`.

**Dependencies/order:** None. This should be one of the first filed issues.

## 15. Short-hand property override copies super Parameter elements

**Issue title:** Short-hand property methods must not share super method
`Parameter` objects across modules.

**Status/category:** Category A. Single-threaded compile bug.

**Explanation:** `Parameter` objects are not inert syntax. They carry constants
and owner-sensitive metadata that get registered during assembly. Sharing the
super method's `Parameter` elements into a user module's short-hand property
override lets the user module rewrite objects owned by the library/ecstasy
module.

This is a type-system failure in practical form. A generic or owner-typed copy
API would make it impossible to pass "borrowed library parameter" where "new
method-owned parameter" is required. The current mutable-object style makes the
wrong thing easy, then discovers the damage only when shared metadata is
registered into the wrong pool.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java:504`
reads `methodSuper.getReturn(i)`; line 532 creates a new method using shared
parameter elements. `Parameter.registerConstants(...)` rewrites element
constants during assembly.

**Failure mode:** A user module overriding a short-hand property method can
mutate `Parameter` objects owned by the loaded library/ecstasy module. The
library parameter's constants are adopted into the user pool.

**Minimal master-portable fix strategy:** Add a tiny owner-aware parameter
copy helper to `Parameter`/`Component` if not already present on master. Use it
only at the short-hand property override call site.

**Patch/diff section:** Branch seed: `f835b3693`; close patch sketch.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/Parameter.java b/javatools/src/main/java/org/xvm/asm/Parameter.java
@@
+    public Parameter copyFor(MethodStructure owner) {
+        Parameter copy = new Parameter(this);
+        copy.setContaining(owner);
+        copy.clearMethodOwnedState();
+        return copy;
+    }
diff --git a/javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java b/javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java
@@
-                        org.xvm.asm.Parameter param = methodSuper.getReturn(i);
+                        org.xvm.asm.Parameter param = methodSuper.getReturn(i).copyFor(method);
```

**Tests to add/run on master:** `ComponentMethodParameterCopyTest` is red on
master for cross-module copy and call-site shape. Run
`./gradlew :javatools:test --tests org.xvm.asm.ComponentMethodParameterCopyTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Source/test files from branch seed `f835b3693`
apply cleanly to `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`.

**Dependencies/order:** None if the helper stays tiny. Do not pull in the full
clone-free adoption API.

## Verdicts from the 2026-09-01 master-porting pass

Recorded because each one changes how the row must be argued, and re-deriving them is expensive.

**Row 15 (short-hand property override copies super `Parameter`) is LATENT, not observable.** The
sharing is **accidental**, not a deliberate optimisation: `MethodStructure`'s constructor does
`m_aParams = aParams` with no copy and no comment, `Parameter.equals` is by value, and nothing keys a
map on `Parameter` identity. "Nothing mutates a `Parameter`" is flatly false - `addAnnotation`,
`resolveAnnotations`, `markDefaultValue`, `setDefaultValue`, `markImplicitDeref`, `deref` and
`registerConstants` all mutate after construction. Master already knows a `Parameter` belongs to one
method (`cloneBody` copies each and calls `setContaining`); that discipline was never wired to the
*create* path.

But the corruption **self-heals**: assembling the user module rewrites the library element's type
constant into the user's pool, and the library's next `pool.register()` maps it back to the identical
interned object. The residual hazard is only the window - `xcc` accepts multiple source modules in one
JVM, so a read of the library's parameter type inside that window sees a constant whose
`getConstantPool()` is the wrong pool, and pool identity is what `indexOf` uses when writing a module.
That window could not be turned into an end-to-end failure. **Argue this row as a correctness and
ownership fix, not as a bug fix.**

*Cost, measured over a full 20-module XDK build:* 1153 short-hand override sites compiled, **1118**
super `Parameter` elements borrowed (595 cross-module), so the fix is 1118 small allocations for the
whole build - about 60 per module compile, at compile time only, zero runtime cost. That is the number
to answer "copying overhead" with.

*Not fixed, same defect:* the borrowed-parameter pattern also exists at `ClassStructure:2958` and
`:3009` (delegated-method factory) and `ClassTemplate:2053` and `:2211` (runtime synthesis).

**Row 14 (reflection `Method.invoke` aliases caller tuple storage) is a LIVE, observable bug**, proven
red-then-green at run time with ordinary single-threaded Ecstasy: a method that assigns to its own
parameters mutates the caller's tuple. An early probe wrongly appeared clean because its callee's
`maxVars` exceeded its parameter count, so `Utils.ensureSize` grew - and therefore copied - the array.

The same aliasing shape exists in 11 ISA ops (`Call_T*`, `Invoke_T*`, `Construct_T`, `New_T`,
`NewG_T`), and fixing them rides along - but **they are decode-only today**: `m_fTupleArg` is never
assigned anywhere in the repo, and `Construct_T` is an outright `UnsupportedOperationException`. So
that half cannot be exercised by a test, because the compiler cannot emit the bytecode. Source-shape
assertions are the only available guard there, and the PR should say so.

## 16. Contribution body copies re-own the hidden outer component

**Issue title:** Replace `Contribution.clone()` so copied contributions answer
the copied component owner.

**Status/category:** Category A. Single-threaded clone/copy bug.

**Explanation:** Java inner classes carry a hidden reference to their outer
instance. `super.clone()` copies that hidden reference. For `Contribution`, that
means a copied component can contain contribution metadata that still answers
questions through the source component. The visible fields look copied, but the
owner relationship remains wrong.

This is why the branch is retiring ad hoc `Cloneable` patterns. Runtime casts
and shallow clones cannot express ownership. An explicit copy constructor or
factory called on the target owner lets Java encode the invariant: this
contribution belongs to this component. Without that, module linking and
conditional body copying can operate on metadata that lies about its owner.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/Component.java:1973`
uses `super.clone()` in body copying. `Component.java:2751` returns the owning
component from `Contribution.getComponent()`. `Component.java:3150` clones
`Contribution`, preserving the hidden Java inner-class outer reference.

**Failure mode:** A copied component can contain contributions whose
`getComponent()` still returns the source component. Conditional bifurcation,
link/merge, or module surgery can observe lying owner metadata.

**Minimal master-portable fix strategy:** Replace inner-class `clone()` with an
explicit constructor/copy factory that creates the contribution from the target
component instance.

**Patch/diff section:** Branch seed: `0af827c72`.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/Component.java b/javatools/src/main/java/org/xvm/asm/Component.java
@@
-                return super.clone();
+                return Component.this.new Contribution(this);
@@
+        private Contribution(Contribution that) {
+            this.m_idContrib = that.m_idContrib;
+            this.m_aParams   = that.m_aParams == null ? null : that.m_aParams.clone();
+            this.m_condition = that.m_condition;
+        }
```

**Tests to add/run on master:** `ComponentBodyCopyTest.contributionsAreReOwnedByBodyCopies`
is behavioral red on master. Run
`./gradlew :javatools:test --tests org.xvm.asm.ComponentBodyCopyTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Branch seed `0af827c72` is the broad Cloneable
retirement commit and has real source conflicts in `Component.java` and
`MethodStructure.java` on master. Extract only the `Contribution` copy/re-owner
hunks and the matching `ComponentBodyCopyTest` method; do not file the full
structure-family modernization as this master bug.

**Dependencies/order:** Independent. Keep this separate from the broader
Cloneable-retirement modernization.

## 17. MethodStructure.Source body copies re-own the hidden outer method

**Issue title:** Replace `MethodStructure.Source.clone()` so copied source
metadata resolves through the copied method.

**Status/category:** Category A structurally; observable when copied methods
cross pools.

**Explanation:** `MethodStructure.Source` has the same hidden-outer problem as
`Contribution`, but the failure shows up through source metadata and constant
pool resolution. A cloned method can carry a `Source` object whose
`getConstantPool()` still routes through the original method because `clone()`
preserved the hidden outer reference.

This is relevant even when many same-pool copies appear to work. Same-pool tests
mask the bug because both owners answer the same pool; cross-pool adoption,
module surgery, and compiler transformations expose it. Explicit target-owned
copying is the small, type-safe fix.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/MethodStructure.java:2760`
defines inner `Source`; line 2954 returns `(Source) super.clone()`.

**Failure mode:** A cloned method's `Source` object can still answer
`getConstantPool()` through the source method because Java inner-class clone
preserves the hidden outer reference.

**Minimal master-portable fix strategy:** Replace `Source.clone()` with a
copy constructor/factory invoked on the target `MethodStructure` owner.

**Patch/diff section:** Branch seed: `25371b397`.

```diff
diff --git a/javatools/src/main/java/org/xvm/asm/MethodStructure.java b/javatools/src/main/java/org/xvm/asm/MethodStructure.java
@@
-        that.m_source = m_source == null ? null : m_source.clone();
+        that.m_source = m_source == null ? null : that.new Source(m_source);
@@
-                return (Source) super.clone();
+                return MethodStructure.this.new Source(this);
```

**Tests to add/run on master:** `ComponentBodyCopyTest.methodBodyCopyRebindsSourceOuter`
is red on master via the outer reference. Run the same focused test class as
issue 16.

**Master dry-run status:** Source/test files from branch seed `25371b397` apply
cleanly to `origin/master` at `61e555a68cd82a866f82aea40a3bb97a424a3809`. File
with issue 16 only if reviewers prefer one inner-class owner-copy PR; the source
hunk is independently clean.

**Dependencies/order:** Can be filed with issue 16 as "inner-class clone owner
fixes" if reviewers prefer, but the hunks are independent.

## 18. FullyBoundHandle.chain appends instead of dropping linked finalizers

**Issue title:** `FullyBoundHandle.chain()` must append finalizer chains
instead of overwriting `m_next`.

**Status/category:** Category A. Single-threaded master bug.

**Explanation:** Constructor finalizers form a chain of work that must run after
construction. Master assumes the head has no existing `m_next`, asserts that
under `-ea`, and overwrites under `-da`. That means debug/test runs can fail
while production silently drops an already-linked finalizer. Both outcomes prove
the chain operation has the wrong contract.

This is a runtime safety issue because finalizers here are not optional logging
callbacks; they complete object initialization semantics for annotation/mixin
constructor paths. Appending to the tail preserves all scheduled work and turns
the mutable linked-list operation into the contract the caller actually needs.

**Master evidence:** The bug lives in the inner class
`xRTFunction.FullyBoundHandle` (there is no separate `FullyBoundHandle` file):
`origin/master:javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:918`
is `assert m_next == null;` followed by the overwrite at line 919.
Assertions-on fails; assertions-off silently drops already-linked constructor
finalizers.

**Failure mode:** Annotation-mixin constructor finalizers delegating to super
constructor finalizers can prepopulate a finalizer link
(`Frame.chainFinalizer` links onto a frame's head finalizer before
`ClassTemplate`'s construction epilogue folds the per-frame finalizers
together). The epilogue's `chain(...)` then asserts (`-ea`) or overwrites
(`-da`) instead of appending, so linked finalizers are lost.

**Minimal master-portable fix strategy:** Change `chain(...)` from "must be
unlinked" to tail append. The seed also carries defensive null-padding guards
in `isMutable()`/`makeImmutable()`/`checkArgumentsPassThrough()` (`f_ahArg` is
the register-file-sized array with trailing null padding); the deterministic
test does not need them - include them as clearly-marked defensive riders or
drop them, but do not pull unrelated handle-view changes.

**Patch/diff section:** Branch seed: `c621b1dca`. This is the actual
tail-append hunk (the required part of the fix):

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java b/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java
@@
         public FullyBoundHandle chain(FullyBoundHandle handle) {
             if (!(handle instanceof NoOpHandle)) {
-                assert m_next == null;
-                m_next = handle;
+                // append at the tail: the old "assert m_next == null" was not a sound
+                // invariant - Frame.chainFinalizer can already have linked a next handle onto
+                // a frame's head finalizer before ClassTemplate's construction epilogue folds
+                // the per-frame finalizers together (reachable when an annotation-mixin
+                // constructor with a finalizer delegates to a super constructor that also has
+                // one), and overwriting would silently drop the linked finalizers
+                FullyBoundHandle tail = this;
+                while (tail.m_next != null) {
+                    tail = tail.m_next;
+                }
+                tail.m_next = handle;
             }
             return this;
         }
```

The optional defensive riders change `hArg.isMutable()` to
`hArg != null && hArg.isMutable()` (and the analogous null-skips in
`makeImmutable()` and `checkArgumentsPassThrough()`) in the same inner class.

**Tests to add/run on master:** `FinalizerChainTest.chainAppendsAtTailInsteadOfDroppingLinkedFinalizers`
is behavioral red on master under both `-ea` and `-da`: it chains three
`FullyBoundHandle`s and asserts the first link survives the second `chain()`
call. Master adaptation needed: the test boots the native container with the
branch factory `NativeContainer.create(runtime, repository)`; master uses the
public constructor `new NativeContainer(runtime, repository)`. The
`FullyBoundHandle(Container, FunctionHandle, ObjectHandle[])` constructor and
`Utils.OBJECTS_NONE` exist unchanged on master, and the test shares
`xRTFunction`'s package, so protected access works. Run
`./gradlew :javatools:test --tests org.xvm.runtime.template._native.reflect.FinalizerChainTest --rerun-tasks --no-build-cache`.

**Master dry-run status:** Re-verified 2026-08-25: branch seed `c621b1dca` has
a real content conflict in
`javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java`
against `origin/master` `61e555a68cd82a866f82aea40a3bb97a424a3809` (branch
context drift around the hunks; the `assert m_next == null` shape itself is
verbatim on master). Use the master-form hunk above rather than resolving the
cherry-pick.

**Dependencies/order:** None. File before broader handle lifecycle work if the
patch stays self-contained.

## 19. Implicit-identity cache is written from concurrent service threads

**Issue title:** `ConstantPool.f_implicits` must be a concurrent map.

**Status/category:** Category A. Concurrent, but reachable in ANY program whose
services resolve implicit names: all ServiceContexts of one container share one
pool over the shared multi-thread executor - the same exposure bucket as the
timer-callback rows 9/10, no parallel containers required.

**Explanation:** `getImplicitlyImportedIdentity(...)` lazily caches resolved
implicit identities in a plain `HashMap` on the shared pool. Runtime callers
hit it from service threads (e.g. `xService` resolving `Timeout` through
`owner.pool()`, TypeInfo builds resolving `Object`/`String`). A `put` resize
racing another thread's `get` can structurally corrupt the map - lost
unrelated entries, broken bins - not merely duplicate work. The cached values
themselves converge (identities are interned), so the map implementation is
the entire defect.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/ConstantPool.java:3984`
declares the plain `HashMap`; `:1207` reads and `:1242` writes it on the
lazy-miss path.

**Failure mode:** Concurrent implicit-name resolution corrupts the pool-wide
name cache; later lookups can miss or misresolve for every service of the
container.

**Minimal master-portable fix strategy:** Declare the field as
`ConcurrentHashMap`. One line; values are interned so racing writers publish
identical entries.

**Patch/diff section:** Branch seed: `8077ad6c0` (the `ConstantPool.java`
one-liner plus the test).

**Tests to add/run on master:**
`ConstantPoolDiagnosticsTest.implicitIdentityCacheIsConcurrentSafe()` - the
instance-type pin fails deterministically on the `HashMap` shape (verified on
this branch by reverting the fix); the parallel exercise is the behavioral
companion. The master form of the test stands alone (drop the branch-only
sibling cases).

**Master dry-run status:** The fix is a one-line field-initializer change plus
an additive test; no conflict surface beyond branch docs.

**Dependencies/order:** None.

## 20. Delegation synthesis publishes half-built method code

**Issue title:** Runtime-lazy delegation synthesis must assemble before it
publishes.

**Status/category:** Category A. Concurrent, but reachable in ANY program in
which two services dispatch a delegating class's member: the optimized-chain
build is lazy and per-TypeInfo-view, so two views race the same host
`ClassStructure` - single container, ordinary scheduling.

**Explanation:** `ensureMethodDelegation`/`ensurePropertyDelegation`
synthesize the delegating method on first dispatch, and master attaches the
method as a findable child BEFORE building and assembling its code. A
concurrent dispatcher whose `findMethod` sees the half-built method captures
a partial, unlinked op array (`Code.ensureOps` hands out the in-progress
list), and both threads then mutate the same `Code` in place - `code.add`
racing `forceAssembly`'s dead-code elimination and re-linking. The nested
`ensureMultiMethodStructure` check-then-create races the same way (the loser
observes null from the failed `addChild`). Corrupted decoded code executes,
or the dispatch NPEs.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/ClassStructure.java:2958`
(property accessor created and attached) with `forceAssembly` only at
`:2980`; `:3009` (method delegation created and attached) with
`forceAssembly` at `:3135`; no synchronization anywhere on the path; child
maps are plain `ListMap`s mutated in place under concurrent readers.

**Failure mode:** Concurrent first dispatch of a delegating member yields a
method with zero/partial ops, duplicate assembly of one `Code`, corrupted
child maps, or a null multimethod - nondeterministic by scheduling.

**Minimal master-portable fix strategy:** Build the synthetic method
DETACHED, assemble completely, then publish atomically first-wins with
copy-on-write child-map republication (volatile fields), so unsynchronized
readers only ever observe complete maps and complete methods; racing builders
discard their build and adopt the winner. No lock is held across TypeInfo
construction (synthesis can recurse cross-class).

**Patch/diff section:** Branch seed: `5d9a5f395`. Portable pieces: the
`MultiMethodStructure.createDetachedMethod*` factories,
`Component.publishRuntimeChild`/`ensureRuntimeMultiMethodStructure`,
`MultiMethodStructure.publishOrAdoptSynthesizedMethod`, the volatile
`m_childByName`/`m_methodByConstant` fields, and the reworked
`ensure*Delegation` bodies. NOT portable: `openRuntimeSynthesisWindow` - it
exists only because this branch's pool publication marker (itself a branch
guard) rejected the synthesis registrations; master has no marker and needs
no window.

**Tests to add/run on master:**
`DelegationSynthesisTest.concurrentSynthesisYieldsOneAssembledAccessor()` and
`synthesizedAccessorIsFindableAndStable()` (the deterministic marker test is
branch-only). The concurrent red is probabilistic on master's shape; on this
branch the pre-fix run reproduced the null-multimethod race directly.

**Master dry-run status:** Needs a filtered patch (the branch commit also
touches the branch-only window and marker interplay); the portable pieces are
additive plus two method-body rewrites.

**Dependencies/order:** None. File after row 19 if sequencing matters (both
touch `ConstantPool`-adjacent files, but they do not conflict).

## 21. JIT generated and bridge class initializers capture the first container

**Issue title:** JIT `<clinit>` state (`$scN` constants, `@Inject` statics,
`$INSTANCE` singletons, bridge enum singletons) freezes the first active
container into classloader-wide statics.

**Status/category:** Will be unsafe for concurrency in master; also breaks
sequential same-JVM container reuse. Latent today only because the JIT executes
single-threaded, one container per process. Graduated MUST FIX on the audit
board (row 180/181); fix parked for a JIT rebase because of upstream churn.

**Explanation:** Every generated class begins its `<clinit>` with `Ctx.get()`
and materializes per-container state into `static` fields: `$scN` constant
fields resolved through the ambient container's type system, `@Inject` constant
properties resolved through the ambient container's injector, and `$INSTANCE`
singletons owned by the defining classloader. The bridge classes do the same by
hand: enum singletons (`eBoolean`, `eNullable`, `eOrdered`, `eRounding`,
`Array.eMutability`) and constants (`Boolean.False/True`) read
`$ctx().container.typeSystem.pool()` inside constructors invoked from static
initializers. Since type systems deliberately share `ModuleLoader`s, whichever
container first triggers JVM class initialization permanently donates its
constants, injections, and singleton identities to every later container using
those loaders. This is the static-scope analogue of the interpreter's
this-escape/ambient-pool diseases fixed on this branch.

**Master evidence:** `CommonBuilder.assembleCLInit` (CommonBuilder.java:660-830):
`Ctx.get()` at :681, `$sc` fill via `ctx.getConstant` at :712-717, `@Inject`
`putstatic` at :740-750, `$INSTANCE` at :799-815. Bridge: eBoolean.java:13-17,
eNullable.java:10-14, eOrdered.java:10-14, Boolean.java:20-31,
FPNumber.java:521-526, Array.java:84-94/:156-160, TerminalConsole.java:24-30,
ContainerControl.java:17. Also: many bridge `$INSTANCE` fields are non-final
(Ordered.java:64/71/78, FPNumber.java:489-517, Array.java:126-147), and the
enum bridges return their backing `$values` arrays raw (eBoolean.java:30-32).
Merged-`<clinit>` mechanics: AugmentingBuilder.java:100-108.

**Failure mode:** Two containers in one process (parallel or sequential): the
second observes the first's interned constants, injected values, and singleton
identities; injection isolation and container ownership are silently broken.
On a thread without a bound `Ctx`, class initialization dies with
`NoSuchElementException` from `ScopedValue.get()`.

**Minimal master-portable fix strategy:** Move container-owned state out of
classloader statics: route `$scN`/injected/singleton access through
per-container state reached via `Ctx` (a container-indexed table or per-
container classloader), or make first-initialization owner capture an explicit
loud error. Make bridge `$INSTANCE` fields final and stop exposing raw
`$values` arrays.

**Tests to add/run on master:** None exist. The recommended red harness (two
containers in one Xvm, assert the second sees its own pool/injector; a
class-init-on-unbound-thread test) is specified in
`jit-global-owner-classification.md:149-171` but was never written. File the
issue with the harness as part of the ask.

**Dependencies/order:** Coordinate with active JIT branches (`origin/JIT`);
fix against a rebased snapshot. Related fixed-on-this-branch precedent:
`Xvm` boot-escape factory (`JitConstructorEscapeTest`).

## 22. JIT ambient-context API bakes per-invocation state into shared code

**Issue title:** `$ctx()`/`$xvm()`/`$owner()` ambient helpers, `nType`'s
captured `Ctx`, and link-time condition evaluation leak invocation state
across containers.

**Status/category:** Will be unsafe for concurrency in master; `$owner()`
cross-Xvm resolution and `OpCondJump` baking are wrong-owner bugs the moment
more than one Xvm/container exists. Classified must-fix-by-API on the audit
board (row 182), parked for a JIT bridge PR.

**Explanation:** The bridge root `nObject` resolves everything through the
ambient `Ctx`: `$owner()` decodes the object's numeric owner id against the
*current* ambient Xvm's registry, so a bridge object touched under another
Xvm's Ctx resolves the right id in the wrong world. `nType` captures its
creating `Ctx` - per-logical-thread mutable scratch state - in an instance
field and reuses it later; its lazy caches (`equalsMethod`, `compareMethod`,
`hashCodeMethod`, `xvmClass`) are unsynchronized non-volatile writes. And
`OpCondJump.buildUnary` evaluates conditional constants at bytecode-build time
via `Ctx.get().container`, permanently baking one container's answer into
classloader-shared generated code (latent only because `Container.isSpecified`
currently hardcodes debug/test to true).

**Master evidence:** nObject.java:26-64 (`$ctx`/`$xvm`/`$owner`);
nType.java:35-45 (`$ctx` capture + unsync caches), :109-112 (fresh-per-call
today; the `TODO: cache type -> nType` would make the capture cross-fiber);
OpCondJump.java:513-526 (`cond.evaluate(Ctx.get().container)` at :522);
Container.java:110-117 (the hardcoded `isSpecified`). `Ctx` has no owner
consistency check (`Ctx.java:29-32` does not assert
`xvm == container.xvm`).

**Failure mode:** With two Xvms or two containers: wrong-owner resolution in
`$owner()`, cross-fiber reuse of another invocation's `Ctx` scratch slots via
a cached `nType`, and generated conditional branches answering for the wrong
container. Unbound threads throw `NoSuchElementException` at arbitrary depths.

**Minimal master-portable fix strategy:** Pass `Ctx` explicitly through the
bridge API surface (the interpreter-side precedent is this branch's complete
`withPool` ambient-bridge deletion); until then, assert
`ctx.xvm == ctx.container.xvm` at construction, remove the `Ctx` instance
capture from `nType`, and move `OpCondJump` condition evaluation from build
time to run time (or key generated classes by container).

**Tests to add/run on master:** None exist; same missing two-container harness
as row 21.

**Dependencies/order:** Same JIT-rebase coordination as row 21.

## 23. JIT classloading and registry races

**Issue title:** JIT classloaders and Xvm registries race under concurrent
class loading and container creation.

**Status/category:** Will be unsafe for concurrency in master. Latent today
(single-threaded JIT); each window is a verified read of current source.

**Explanation:** Neither `TypeSystemLoader` nor `ModuleLoader` registers as
parallel-capable, and they delegate to each other by calling `findClass`
DIRECTLY, bypassing the JVM's per-name classloading locks. Two type systems
sharing the ecstasy `ModuleLoader` can both miss `findLoadedClass`, both run
`genClass` (duplicating codegen and concurrently mutating the shared pool),
and the `defineClass` loser dies with `LinkageError: duplicate class
definition`, while `ModuleLoader.loadedClasses` (plain `HashMap`) races.
`Xvm.ensureTypeSystem` synchronizes on a mutex keyed by module-shape string,
so two differently-shaped type systems sharing a first module name hold
different mutexes while probing the same candidate package name - the
`putIfAbsent` loser throws bare `IllegalStateException`. The
`moduleLoaders`/`packagesByModule` sparse arrays are mutated in place under a
writer lock that readers deliberately skip. `CommonBuilder`'s log-dedup sets
are process-global mutable `HashSet`s. `JitConnector` is single-use (mutable
unsynchronized lifecycle fields) and unconditionally deletes/rewrites a
cwd-relative `./jasm` dump tree on every invocation, so two connectors in one
process fight over the directory.

**Master evidence:** TypeSystemLoader.java:70-79 and ModuleLoader.java:85-107
(direct `findClass` delegation, `defineClass`, `loadedClasses.put`; no
`registerAsParallelCapable` anywhere); ModuleLoader.java:194 (`HashMap`);
Xvm.java:411/:443-448/:468-496/:710-742 (name-collision window),
:575-652 + :199-217 (writer-locked, reader-unlocked arrays);
CommonBuilder.java:4159-4160/:3969-3980 (`SKIP_SET`/`METHOD_SKIP_SET`);
JitConnector.java:218-233 (lifecycle fields), :162-184 (jasm dump).

**Failure mode:** First concurrent class load through shared loaders:
duplicate codegen, `LinkageError`, or corrupted `loadedClasses`. First
same-named-module concurrent link: spurious `IllegalStateException`. Parallel
builds corrupt the skip sets; parallel connectors corrupt `./jasm`.

**Minimal master-portable fix strategy:** `registerAsParallelCapable` plus
delegation through `loadClass` (or per-name lock objects); make
`loadedClasses` concurrent; key the type-system mutex by candidate package
name (or take a global lock around name generation + registration);
make the skip sets concurrent; document `JitConnector` as single-use or guard
its lifecycle; scope the jasm dump per-connector.

**Tests to add/run on master:** None exist. A parallel-load red test (two
threads loading one generated class through two sharing TypeSystemLoaders) is
the minimal harness and needs no full runtime.

**Dependencies/order:** Independent of rows 21/22; still best filed against a
rebased JIT snapshot.

## 24. JIT build mutates shared ASM structures at runtime

**Issue title:** Lazy JIT classfile generation mutates shared
`ConstantPool`/`FileStructure`/constant state outside any ownership discipline.

**Status/category:** Will be unsafe for concurrency in master; the
FileStructure splice is also a correctness hazard for two runtimes over one
`ModuleRepository`. Latent today for the same single-threaded reason.

**Explanation:** JIT class generation runs lazily inside JVM class loading,
and it writes into the shared compile-time model while doing so: `<clinit>`
assembly calls `pool.register(type)` and `type.ensureTypeInfo()` on the
module's shared pool; `Linker.link()` splices the shared ecstasy
`ModuleStructure` into the app's `FileStructure` by `removeChild`/`addChild`
(the app module comes straight from the shared repository cache, so two Xvms
over one repository would concurrently mutate one structure); and JIT name
caches are lazy non-volatile writes onto shared ASM constants
(`TypeConstant.m_sJitName`, `SignatureConstant` method-name cache) whose
uniquifying suffixes come from a per-Xvm counter - first Xvm wins, polluting
every later runtime that shares the constants. `ConstantPool.
m_setJitPrimitives` is a lazily built plain transient set. This is the same
disease family as the interpreter-side runtime-published pool mutation fixed
on this branch, but with no publication marker, no synthesis window, and no
first-wins publication discipline on the JIT side.

**Master evidence:** CommonBuilder.java:706 (`pool.register` during classgen)
and :725 (`ensureTypeInfo`); Linker.java:335/:367-391 (splice at :379-384);
JitConnector.java:43 (repository-cached module); TypeConstant.java:7111-7126
+ :8369 (`m_sJitName`), SignatureConstant.java:731-742/:988; Xvm.java:342-345
(per-Xvm suffix counter); ConstantPool.java:2398-2433/:4445
(`m_setJitPrimitives`).

**Failure mode:** Concurrent class loading = concurrent unsynchronized pool
registration and TypeInfo builds; two runtimes over one repository corrupt the
app FileStructure's child list; cross-Xvm cached JIT names collide or leak
another runtime's suffix numbering.

**Minimal master-portable fix strategy:** Adopt the interpreter-side pattern
proven on this branch: build detached, publish first-wins under the owning
structure's monitor, and put a publication/ownership marker on pools the JIT
mutates after runtime visibility; copy the app module per Xvm the way core
modules already are (`new FileStructure(module, true)`); make the JIT name
caches volatile-or-CAS and Xvm-scoped rather than constant-scoped.

**Tests to add/run on master:** None exist. `CloneCensusTest`/pool-guard
analogues on the interpreter side show the shape a red harness takes; the JIT
needs its own two-loader concurrent-generation test.

**Dependencies/order:** Overlaps row 21 (`<clinit>` is both the static-capture
and the pool-mutation site); file 21 first, reference it.

## 25. Native injection caches race duplicate service creation

**Issue title:** Racing first injections create duplicate native services
(Console, Clock, Random, OSStorage) via unsynchronized `ensure*` caches.

**Status/category:** Will be unsafe under concurrency in master; reachable
today with concurrent first injections from two service threads of one
container. Owner-consistent but divergent and non-idempotent - the failure is
duplicate service identity, not wrong-owner state.

**Explanation:** The native injection suppliers cache their service handles in
plain double-read lazy fields on SHARED receivers: `Container.getTemplate`
delegates shared types to the parent, so one template instance (the native
container's) serves every app container, and the `ensure*` supplier runs on
the injecting caller's thread. Each racing thread that misses the cache calls
`f_container.createServiceContext(...)`; the loser's duplicate native service
context stays registered forever (two Consoles, two Clocks, a second
OSStorage), and the plain write publishes the winner without a fence.

**Master evidence:** `xRTAlgorithms.m_hAlgorithms` (xRTAlgorithms.java:72-76),
`xLocalClock.m_hLocalClock`/`m_hUTCClock` (xLocalClock.java:141-164),
`xTerminalConsole.m_hConsole` (xTerminalConsole.java:70-77),
`xInjector.m_hInjector` (xInjector.java:38-44), `xRTRandom.m_hRandom`
(xRTRandom.java:223-228); the same shape on `NativeContainer` itself:
`ensureOSStorage` (NativeContainer.java:433-466, including a continuation
write from another fiber at :452-455), `ensureFileStore`/`ensureRootDir`/
`ensureHomeDir`/`ensureCurDir`/`ensureTmpDir` (:468-535),
`ensureSecureNetwork`/`ensureInsecureNetwork` (:647-666). All master-verbatim.

**Failure mode:** Two services concurrently requesting their first `@Inject
Console` (or Clock/Random/storage) each construct a native service; both stay
registered in the container; later injections observe whichever handle won the
plain write - or, on a weakly-ordered CPU, a partially published one.

**Minimal master-portable fix strategy:** Per-site CAS with loser cleanup
(unregister the losing ServiceContext). A plain `synchronized` wrapper is NOT
sufficient for the continuation-completing sites (`ensureOSStorage` completes
from another fiber's continuation).

**Additional defect found while fixing:** `ensureAlgorithms` and both
`ensureSecureNetwork`/`ensureInsecureNetwork` cached the result of an R_CALL
construction path directly - a frame-bound `DeferredCallHandle` - so a later
caller received a deferred tied to the FIRST caller's frame.

**Branch fix (2026-08-25):** per shape - DCL over volatile fields for the
synchronous sites (Console, LocalClock/UTCClock, Random, Injector);
first-wins publication with loser-context `Container.terminate` for the
construct-request sites, publishing only the RESOLVED handle from the
completion continuation (never a deferred); fs-derived property caches
publish first-wins convergent.

**Tests to add/run on master:**
`NativeInjectionSingletonTest.racingFirstInjectionsCreateExactlyOneService`
(branch): 8 latch-raced first injections observe one identity and the service
registry grows by exactly one; red-verified against the plain shape.

**Dependencies/order:** Independent of rows 1-24.

## 26. TypeInfo placeholder identity race strands types as "being built"

**Issue title:** `ConstantPool.infoPlaceholder()` is a racy lazy singleton but
`TypeConstant` clears placeholders by identity.

**Status/category:** Will be unsafe under concurrency in master; the racing
window is first concurrent TypeInfo builds on one pool - routine for a
container's service threads warming types. Found independently by two audits.

**Explanation:** The TypeInfo build machinery installs a per-pool placeholder
`TypeInfo` while a build is in flight, and
`TypeConstant.clearTypeInfoPlaceholder` removes it by CASing the type's info
slot against `pool.infoPlaceholder()` BY IDENTITY. The placeholder accessor is
a plain unsynchronized lazy write, so two racing first callers create two
placeholder instances; a type slot holding the loser's instance can never be
CAS-cleared - the type is permanently "being built", and
`ensureObjectTypeInfo` treats the stranded placeholder as an unfinished build
and reports the spurious "Failed to create TypeInfo for root Object" error.

**Master evidence:** `origin/master:ConstantPool.java:2422-2433` (plain lazy
create; field non-volatile), cleared-by-identity at the master equivalent of
`TypeConstant.clearTypeInfoPlaceholder` (branch :2026:
`s_typeinfo.compareAndSet(this, getConstantPool().infoPlaceholder(), null)`).

**Failure mode:** Concurrent first `infoPlaceholder()` calls -> two instances
-> stranded placeholder in a type's info slot -> that type never finishes
building; worst observable is the spurious root-Object TypeInfo failure.

**Minimal master-portable fix strategy:** Make the field volatile and the
create synchronized (double-checked); one small hunk, no API change.

**Tests to add/run on master:**
`ConstantPoolDiagnosticsTest.typeInfoPlaceholderIsIdentityStableUnderRacingFirstCalls`
(branch) - 25 rounds of 8 latch-started threads asserting one identity;
red-verified against the plain-lazy shape.

**Dependencies/order:** Independent; can ride any ConstantPool-adjacent PR.

## 27. DirRepository scan cache is not thread-safe under concurrent module lookup

**Issue title:** `DirRepository`'s directory-scan cache uses plain unsynchronized
`HashMap`/`TreeMap` rebuilt with `clear()`+`put()`, so concurrent module lookups
corrupt it.

**Status/category:** PROVEN red on master `82683bcd2`. **FILED as PR #547** (fix + test). The racing window is
concurrent first-access module lookups on one on-disk repository - routine when
two container-0 compiles (or two services) resolve modules from the same
`DirRepository`.

**Explanation:** The scan cache is a plain `HashMap modulesByFile` (a NON-final
field reassigned wholesale by `rebuildCache`) and a plain `TreeMap modulesByName`
(rebuilt with `clear()` then a `put()` loop). None of `loadModule`,
`getModuleNames`, `ensureCache`, or `rebuildCache` is synchronized. A fresh
repository has `lastScan == 0`, so `isCacheValid()` returns false and the FIRST
concurrent access runs `rebuildCache` on every thread at once: concurrent
`modulesByName.clear()`+`put()` on the shared `TreeMap`, a non-volatile
reassignment of the `modulesByFile` field, all racing `get()` and the live
`keySet()` view returned by `getModuleNames()`.

**Master evidence:** `origin/master:DirRepository.java:440-441` (plain
`HashMap`/`TreeMap`, `modulesByFile` non-final), `:147-184` (`rebuildCache`:
`modulesByName.clear()` + `put()` loop + `modulesByFile = newModulesByFile`, all
unsynchronized), `:65-69` (`getModuleNames` returns `unmodifiableSet(modulesByName
.keySet())`, a fail-fast live view).

**Failure mode:** PROVEN `java.util.ConcurrentModificationException` under a
concurrent scan (see test below). A corrupted `HashMap` can additionally spin
(infinite loop -> hung fiber) or silently drop entries (spurious "module not
found").

**Minimal master-portable fix strategy:** Coarse per-repository `synchronized` on
`loadModule`/`getModuleNames`/`storeModule` - the correct granularity for a lookup
cache (not fine-grained per-entry). Branch seed `ae5d7ad80`; one file.

**Tests to add/run on master:** `org.xvm.asm.DirRepositoryConcurrentScanTest`
(branch) - 8 latch-started threads x 80 iterations over a fresh repository.
RED-verified on master `82683bcd2` (`ConcurrentModificationException`) in a
scratch worktree with master-built modules parsed into the cache; GREEN on this
branch (synchronized). NOTE: the test must feed the repository modules the target
JVM can parse - master rejected the branch's `0.20260519` `.xtc` as
"Unsupported .xtc version"; use modules built by the same tree.

**Dependencies/order:** Independent; single-file fix.

## 28. MethodStructure native/code state is published without a happens-before edge

**Issue title:** `MethodStructure.m_fNative` and `m_code` are non-volatile and
`ensureCode()` lazy-initializes `m_code` unsynchronized, so a concurrent reader can
observe a torn native/code state.

**Status/category:** Data race provable by inspection (JMM), master-verbatim. **FILED as
PR #549** (the two `volatile` modifiers + a reflection regression pin). Not
reachable in a strictly single-threaded link-then-run sequence; reachable as soon as
any second thread touches a `MethodStructure` while another marks it native or forces
its code - i.e. concurrent compile/link, warm reuse, or a JIT/interpreter mix.

**Explanation:** `markNative()` performs a multi-step transition (`setAbstract(false)`,
`resetRuntimeInfo()`, then `m_fNative = true`, `m_fTransient = true`) over a plain
non-volatile field. `ensureCode()` reads `isNative()`, then lazily creates `m_code`
with a plain read/write pair. There is no happens-before edge on either field, so a
racing reader can (a) observe the stale `native == false` together with a `m_code`
that is still null and take the wrong branch, and (b) publish/observe a partially
constructed `Code` object through the non-volatile field. `getOps()` turns the null
case into a hard `IllegalStateException`.

**Master evidence:** `origin/master:MethodStructure.java:3097`
(`private transient Code m_code;` - non-volatile), `:3123`
(`private transient boolean m_fNative;` - non-volatile), `markNative()` `:1149-1157`,
`ensureCode()` `:593-602` (unsynchronized lazy init), `getOps()` `:637`.

**Failure mode:** intermittent, non-reproducible `IllegalStateException` from
`getOps()` ("Method ... has no code"), duplicate `Code` construction, or use of a
partially published `Code` - all timing-dependent, and all of the "single crash dump,
cannot reproduce" shape.

**Minimal master-portable fix strategy:** make BOTH fields `volatile`
(`m_fNative`, `m_code`); that alone gives the visibility edge and is behavior-neutral.

**Two corrections learned by implementing the branch-side half (2026-08-28):**

1. A writer-side guard on `markNative()` must NOT be phrased as "refuse once code exists".
   Replacing an Ecstasy body with a native implementation is the method's whole purpose -
   `xConst` marks `equals`/`compare`/`hashCode` native and those DO have code - so a
   has-code guard rejects the legitimate use. The hazard is not that code exists, it is
   that READERS exist, so the correct condition is runtime publication, the same one
   `forceAssembly` already uses.
2. That guard is therefore **not portable to master today**: `ConstantPool
   .isRuntimePublished()` / `isRuntimeSynthesisWindowOpen()` do not exist there (verified:
   zero occurrences), because master has no concept of a pool becoming published. Master's
   portable fix is the two `volatile` fields ONLY; the guard becomes expressible once
   enhancement E3's publication tracking lands. Recorded so a porter does not try to carry
   the guard across and find nothing to test against.
Optionally follow with a double-checked/synchronized `ensureCode()` to also remove the
duplicate-construction window. Note this branch already had `m_code` volatile and has
now made `m_fNative` volatile too; master needs both. Unrelated but adjacent: master's
`markNative()` still carries dead debug code (`int q= 0;` at `:1154`) that should go.

**Tests to add/run on master:** no deterministic red is available - the transition runs
at link time, so the racing window does not reliably overlap in a normal run. This row
is filed on JMM/inspection evidence (like rows 19 and 26), with the fix being
behavior-neutral and cheap. A probabilistic hammer (N threads calling `markNative()`
against N threads calling `getOps()` on the same structure) reproduces it only under
instrumentation/`-XX:+StressLCM`-style perturbation.

**Dependencies/order:** Independent; two-word fix, no API change.

## 29. FileStructure.getErrorListener() NPEs when no ambient pool is bound

**Issue title:** `FileStructure.getErrorListener()` dereferences the ambient
`ConstantPool.getCurrentPool()` without a null check.

**Status/category:** PROVEN red on master with a two-line repro. **FILED as PR #548**
(fix + test). Reachable from any thread that has not had a pool pushed onto it.

**Explanation:** the accessor falls back to the ambient "current pool" when no explicit
listener is set, and calls `poolCurrent.getErrorListener()` unconditionally.
`getCurrentPool()` is a thread-local that is simply null on any thread that did not go
through the code which pushes a pool - which is every thread driving the compiler or
runtime from ordinary Java code (an embedding host, a build tool, a test harness).
Because this is a DIAGNOSTIC accessor, the failure is an NPE thrown from the very code
meant to report problems, surfacing far from its cause.

**Master evidence:** `origin/master:FileStructure.java:1392-1401`.

**Failure mode:** `NullPointerException: Cannot invoke
"ConstantPool.getErrorListener()" because "poolCurrent" is null`, observed via
`TypeConstant.ensureTypeInfo` -> `XvmStructure.getErrorListener` -> here.

**Minimal master-portable fix strategy:** null-guard the ambient lookup and fall
through to `ErrorListener.RUNTIME`, which is what the method already does when nothing
else is available. Behaviour is unchanged when a pool IS bound. The DEEPER fix - and
the reason this bug exists - is that pool ownership is expressed as a thread-local
rather than a parameter; passing the pool explicitly (this branch's E3) makes the whole
family unrepresentable rather than guarded against.

**Tests to add/run on master:** `org.xvm.asm.FileStructureErrorListenerTest` - construct
a `FileStructure`, ask it for its `ErrorListener` from an ordinary thread. Verified to
fail on master and pass with the guard; a second case pins that an explicitly supplied
listener still wins.

**Dependencies/order:** Independent; one-line fix.

## 30. Version.isSameAs() indexes the wrong array and throws on the longer receiver

**Issue title:** `Version.isSameAs()` reads past the end of `thatInts` whenever the
receiver has more version parts than the argument.

**Status/category:** PROVEN red on master. Pure logic bug, no concurrency, no
container reuse - a one-word fix with a wrong answer AND a crash behind it.

**Explanation:** the method compares the shared prefix, then requires every part
beyond it to be zero, so that `1.2.0` and `1.2` are the same version. The remainder
loop selects the longer of the two arrays into `remaining` and iterates to ITS
length - then indexes `thatInts` anyway:

```java
int[] remaining = cThis > cThat ? thisInts : thatInts;
for (int i = cShared, c = remaining.length; i < c; ++i) {
    if (thatInts[i] != 0) {          // <-- should be remaining[i]
```

`remaining` is computed and never used. When the receiver is the longer of the two,
`c` is the receiver's length while the indexed array is the argument's, so the read
runs off the end. The reversed direction works only by accident, because there
`remaining` and `thatInts` are the same array.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/Version.java:489-495`
- byte-identical to the branch before the fix.

**Failure mode:** two distinct wrong behaviours, both observed:
- `new Version("1.2.3").isSameAs(new Version("1.2"))` throws
  `ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2` instead of
  returning `false`.
- `new Version("1.2.0").isSameAs(new Version("1.2"))` throws instead of returning
  `true` - so the "trailing zeros are ignorable" semantic the method exists to
  provide never works in that direction.

**Minimal master-portable fix strategy:** index `remaining[i]` instead of
`thatInts[i]`. One word. No signature, semantic, or performance change; the
already-correct direction is unaffected because `remaining == thatInts` there.

**Tests to add/run on master:**
`org.xvm.asm.VersionTest.testIsSameAsAcrossDifferingPartCounts` - covers both
directions, multiple trailing zeros, a non-zero part past the shared prefix, and
equal-length pairs as a control. Verified red on the unfixed source (AIOOBE at
`Version.java:492`) and green with the fix.

**Dependencies/order:** Independent; one-word fix.

**How it was found:** while surveying `Version.getIntArray()` as a `FrozenIntArray`
candidate - the accessor clones on all 13 of its call sites. Reading the consumers
to prove they were read-only surfaced the defect.

## 31. Op.toString() throws IllegalStateException on 16 live opcodes

**Issue title:** `Op.toName()` has drifted 16 opcodes behind `Op.instantiate()`, so
`Op.toString()` throws on every one of them - including inside error reporting.

**Status/category:** PROVEN red on master, empirically, not by inspection. **FIXED in this
branch** with a ratchet test.

**Explanation:** `Op.instantiate()` switches over 215 opcodes; `Op.toName()` over 201.
The 16 in the gap are all reachable - they have classes and constants and are
instantiated:

```
OP_IIP_AND OP_IIP_MOD OP_IIP_OR OP_IIP_SHL OP_IIP_SHR OP_IIP_USHR OP_IIP_XOR
OP_PIP_AND OP_PIP_DIV OP_PIP_MOD OP_PIP_MUL OP_PIP_OR OP_PIP_SHL OP_PIP_SHR
OP_PIP_USHR OP_PIP_XOR
```

`Op.toString()` is `toName(getOpCode())` and `toName`'s default throws, so
`toString()` on any of these throws. **`toString()` is called implicitly by
debuggers, loggers and string concatenation**, which makes this worse than an
ordinary missing case.

The sharper consequence is diagnostic masking. Fifteen sites build an error message
with `toName(getOpCode())`, e.g. `OpInPlaceAssign.java:224`:

```java
default -> throw new UnsupportedOperationException(toName(getOpCode()));
```

`OpInPlaceAssign` IS the `IIP_*` family. So on exactly the opcodes that reach that
line, constructing the intended `UnsupportedOperationException` instead throws
`IllegalStateException: op=0xdd`, and the real diagnostic is destroyed at the moment
it is needed. Same shape at `OpGeneral` (3), `OpVar` (2), `OpIndex` (2),
`OpCondJump`, `OpJump`, `OpSwitch`, `GP_DivRem`, `OpCallable` (2).

`OP_NEWC_T` and `OP_NEWCG_T` are the reverse drift: named by `toName`, never instantiated.
They are NOT dead code and must NOT be deleted - an earlier draft of this row said "delete or
implement", which was wrong. `_T` is a real family variant (`NEW_T` and `NEWG_T` are both
implemented), and `0x43`/`0x47` sit inside the contiguous `NEWC`/`NEWCG` numbering. Removing
the constants invites a renumbering that would break the binary format, so they stay as
reserved slots, now documented as such at the declaration.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/asm/Op.java` -
verified `instantiate=215 toName=201 missing=16`, identical to the branch.

**Failure mode:** observed directly -

```
Op.toName(0xDD)  ->  java.lang.IllegalStateException: op=0xdd
Op.toName(0x01)  ->  LINE_1
```

**Minimal master-portable fix strategy:** add the 16 missing `case` labels to `toName`, and
document `OP_NEWC_T`/`OP_NEWCG_T` as reserved rather than removing them (see above). The
durable fix is to stop maintaining two parallel 200-case switches over the same enum-shaped
domain - see `object-typing-static-safety-study.md`, which proposes moving opcode name and
factory onto one carrier so they cannot drift.

**Tests to add/run on master:** `org.xvm.asm.OpNameCoverageTest` - three cases. A static one
asserting `instantiate`'s case set is a SUBSET of `toName`'s (subset, not equality, so the two
reserved names remain legal); a dynamic one that actually calls `Op.toName` for every opcode
`instantiate` accepts, so a case that exists but falls through still fails; and a named spot
check on `IIP_AND`/`PIP_XOR` with `LINE_1` as a control. All three verified red on the unfixed
source - the dynamic one reporting `OP_IIP_AND -> IllegalStateException: op=0xdd` - and green
after.

**Dependencies/order:** Independent.

## 32. Format.TimeZone is produced by the compiler and rejected by the pool

**Issue title:** A `TimeZone:` literal lexes and parses, then dies in
`ConstantPool.ensureLiteralConstant` with an internal `IllegalStateException`.

**Status/category:** PROVEN red on master, empirically. A user-facing compiler crash,
not an internal-only hazard. **FIXED in this branch**, verified end to end by compiling AND
running `manualTests/.../literals.x`, with a six-stage ratchet test.

**Explanation:** the path is complete right up to the pool:

- `Lexer.java:1022` - `case "TimeZone": return eatTimeZone(lInitPos);`
- `LiteralExpression.java:365` - `pool.ensureLiteralConstant(Format.TimeZone, ...)`
- `ConstantPool.ensureLiteralConstant(Format, String, Object)` - its switch handles
  `IntLiteral, FPLiteral, Date, TimeOfDay, Time, Duration, Path, RegEx`. **`TimeZone`
  is absent**, so it reaches `default: throw new IllegalStateException(...)`.

`LiteralConstant.java` contains no reference to `TimeZone` at all. Its siblings
`Date`, `Time` and `Duration` are handled in both places; `TimeZone` was missed in
both, which is why the omission is invisible from either end.

**Master evidence:** `origin/master` - `ensureLiteralConstant` has zero `case TimeZone`
labels, identical to the branch.

**Failure mode:** observed directly -

```
Date      -> Date{value="x"}
Time      -> Time{value="x"}
Duration  -> Duration{value="x"}
TimeZone  -> java.lang.IllegalStateException: unsupported format: TimeZone
```

So a source file containing a `TimeZone:` literal fails compilation with an internal
exception rather than a diagnostic - or, if the intent is that the literal is not
supported, the Lexer should never have accepted it.

**Minimal master-portable fix strategy:** support the literal - but note the size, which
was twice underestimated here before an end-to-end run settled it. It is **SEVEN sites plus
a library change**, not the five the source inspection first suggested:

1. `ConstantPool.ensureLiteralConstant` - the construction switch (throws).
2. `ConstantPool.disassemble` - the read-back switch, a separate list.
3. `LiteralConstant` - the constructor validation switch.
4. `LiteralConstant` - the accepted-format switch.
5. `LiteralConstant.getType()` - whose fallback is `Constant.getType()`, which THROWS, so a
   missing mapping is a live crash rather than a silent default.
6. `xConst.createConstHandle` - the runtime literal switch, plus a `timeZoneConstruct` on its
   `ConstInfo` record. Missing this one meant the literal COMPILED and then died at run time
   with `Unexpected op execution failure ... op=VAR_IN`.
7. `NativeContainer.getConstType` - maps the constant to its implementing class; its default
   throws `LauncherException("No implementation for constant: TimeZone{value=\"Z\"}")`. This
   was the last one, and it still failed after 1-6 were all fixed.

Plus **`lib_ecstasy`**: `TimeZone` had no `construct(String)`. The runtime path calls exactly
that on the Ecstasy class, and `Date`, `Time`, `TimeOfDay`, `Duration`, `Version` and `Path`
each have one; `TimeZone` had only `TimeZone(Int64 picos)` and a conditional `of(String)`. So
this is not purely a Java plumbing fix - it adds a constructor to the standard library, which
is worth flagging to a reviewer even though it is the shape all six siblings already have.

**Tests to add/run on master:** `org.xvm.asm.LiteralFormatPlumbingTest` - a SIX-stage ratchet
over every literal format the compiler produces: the pool builds it, it maps to a type,
`disassemble` reads it back, `xConst` materialises it, the Ecstasy class has the
`construct(String)` that path calls, and `NativeContainer` knows its implementing type. It
started at three stages and grew to six, because each new stage was added only after an
end-to-end run proved the previous "fix" still did not work - which is itself the argument
for keeping all six. Note the round trip is checked by comparing case lists in source, NOT by
writing a `FileStructure`: a constant nothing references is pruned before it is written, so a
genuine round trip cannot observe it.

Also `manualTests/src/main/x/literals.x` gains `testTimeZones()`, beside the existing
`testDates`/`testTimes`/`testDurations`. This is the test that actually mattered: the unit
ratchet went green twice while the literal was still broken, and only compiling AND RUNNING
the module exposed sites 6 and 7. It now prints:

```
** testTimeZones()
utc=UTC or UTC
plus0130=+01:30 or +01:30
minus0500=-05:00 or -05:00
```

**Dependencies/order:** Independent; needs a decision on intended behaviour.

## 33. AstNode.fieldsForNames has a dead type guard and returns null holes

**Issue title:** `AstNode.fieldsForNames()` validates field types with a check that is
always false, and its not-found path tests the wrong variable, so a missing child
field becomes a null array slot instead of an error.

**Status/category:** PROVEN red on master, empirically. Two defects in one method;
neither is currently reachable as a crash, but both defeat the diagnostics they exist
to provide. **FIXED in this branch** with tests.

**Explanation:** the method builds the reflective child-field model used by the AST
(65 call sites).

**Defect A - the type guard never fires:**

```java
if (!field.getType().isInstance(AstNode.class) && field.getType().isInstance(List.class)) {
    throw new IllegalStateException("unsupported field type ...");
}
```

`Class.isInstance(x)` asks whether the OBJECT `x` is an instance of that class. Here
`x` is `AstNode.class`, a `Class` object, so the test asks whether the `Class` object
is an instance of the field's type - false for every realistic field type. Observed:
`AstNode.class.isInstance(AstNode.class)` is `false`, while the intended
`AstNode.class.isAssignableFrom(AstNode.class)` is `true`. The guard is dead in both
conjuncts, so an unsupported field type is silently accepted.

**Defect B - the not-found path tests the wrong variable:**

```java
clzTry = clzTry.getSuperclass();
if (clz == null) {                       // should be clzTry
    throw new IllegalStateException(eOrig);
}
```

`clz` is the method parameter and is never null there. When a named field exists
nowhere in the hierarchy, `clzTry` walks to null, the loop exits, and `fields[i]` is
left **null**. Observed:

```
fieldsForNames(AstNode.class, "noSuchFieldAnywhere") -> length=1, fields[0]=null
```

The saved `NoSuchFieldException` naming the missing field is discarded. A renamed or
deleted child field therefore surfaces later as an opaque failure at the point the
null slot is dereferenced, instead of immediately as "no such field <name>".

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:1889`
(guard) and `:1902` (null check) - identical to the branch.

**Minimal master-portable fix strategy:** replace both `isInstance` calls with
`isAssignableFrom` on the correct operand order, and fix `clz == null` to
`clzTry == null`.

The obvious risk - that the corrected guard starts firing on fields the dead version let
through - was measured rather than assumed, and **is empty**. Force-loading all 183 classes
in `org.xvm.compiler.ast` (which runs the static initialisers where `fieldsForNames` is
called) gives `loaded=183, guard violations=0`. So the guard can be corrected without a
prior review pass; the earlier caution on this row is withdrawn.

**Tests to add/run on master:** `org.xvm.compiler.ast.AstNodeFieldModelTest` - four cases:
a missing field must throw and name itself (red before), an unsupported field type must be
rejected (red before), and the two shapes the child model IS made of, a direct `AstNode`
field and a `List` field, must still be accepted, plus an inherited field to keep the
superclass walk covered (both green before and after, deliberately - they pin behaviour the
fix must not break).

**Dependencies/order:** Independent; both fixes can land together, since the surfaced set
for the guard fix is measured empty.

## 34. IntersectionTypeConstant.mergeChildren guards the wrong argument

**Issue title:** `mergeChildren` null-checks `info1` twice and then dereferences `info2`.

**Status/category:** PROVEN by inspection on master, with the caller establishing
reachability. **FIXED in this branch.** Found while retiring raw types, not while looking
for it.

**Explanation:** both `TypeInfo` arguments are independently nullable. The caller says so
itself - `RelationalTypeConstant.mergeTypeInfo` computes its `Progress` as
`info1 == null || info2 == null ? Incomplete : ...`. But the second line guards on the
wrong one:

```java
map1 = info1 == null ? ListMap.EMPTY : info1.getChildInfosByName();
map2 = info1 == null ? ListMap.EMPTY : info2.getChildInfosByName();   // info1, reads info2
```

With `info1` present and `info2` absent - one half of an intersection resolved, the other
not yet - the guard passes and `info2.getChildInfosByName()` throws
`NullPointerException` while building a TypeInfo, far from anything naming the cause.

A copy-paste slip that neither the type system nor a test could catch, and the reason it
is worth filing rather than just fixing: **the whole `merge*` family was audited for the
same shape and this is the only instance**, so it is a one-line fix, not a pattern.

**Master evidence:** `origin/master:.../IntersectionTypeConstant.java:576-577` - identical.

**Minimal master-portable fix strategy:** guard `map2` on `info2`. One word.

**Tests to add/run on master:** `org.xvm.asm.constants.IntersectionChildMergeTest` - the
both-absent control (which always worked, because the wrong guard is accidentally right
when the two arguments agree) plus a pin on the guard itself.

**Dependencies/order:** Independent.

## Findings from the locator-typing pass (2026-09-01) - filed, and why NEITHER gets a branch

Both were found while typing `ConstantPool`'s locator tables. Recorded so nobody re-derives them;
neither is worth a PR, for different reasons.

### A. `ensureLiteralConstant` advertises `Format.RegEx` and always throws

`ConstantPool.ensureLiteralConstant` (master `:365`) lists `case RegEx:` in its `LiteralConstant`
group, so the switch reads as though RegEx literals are supported. They are not:
`LiteralConstant`'s validating constructor has **no `case RegEx:`** and falls to
`default: throw new IllegalStateException("unsupported format: " + format)`. So the arm can only
ever throw.

**It is dead, not a collision.** An earlier reading called this "two producers claiming one
format+locator space" - `ensureRegExConstant` files a `RegExConstant` under `Format.RegEx` keyed by
the pattern string, and this arm would file a `LiteralConstant` under the same format and key.
That collision **cannot happen**: the second producer throws before it registers anything. The real
producer, `ensureRegExConstant`, is unaffected.

**Why no branch.** Red-on-master is trivial (`assertThrows` on
`pool.ensureLiteralConstant(Format.RegEx, "abc")`) - but there is no meaningful green. Deleting the
arm leaves the call throwing from `ConstantPool`'s own `default` instead of from `LiteralConstant`;
routing it to `ensureRegExConstant` would make the 2-arg overload's `(LiteralConstant)` cast throw a
`ClassCastException` instead, since `RegExConstant` is not a `LiteralConstant`. Which of those is
"correct" is a design call with no observable benefit either way, on an arm nothing reaches. This is
the shape of change that got PR #557 closed as clutter. **Fix it opportunistically inside a PR that
is already in this switch** - the TimeZone work (#559) is literally editing these lines.

### B. `LiteralExpression:365` passes `Format.TimeZone` to a switch with no TimeZone arm

**Already covered by PR #559** (row 32), which is open and adds `Format.TimeZone` end to end -
`ConstantPool`, `LiteralConstant`, `NativeContainer`, `xConst` and a `Destringable` `TimeZone.x`.
Currently unreachable anyway: `TimeZone:Z` is rejected earlier with `COMPILER-163: Illegal literal
value`, so the throw is never hit. Do not file it again; if #559 stalls, this is one of the symptoms
that argues for it.

## Items intentionally not in the 18

- Utility constructor this-escape fixes are listed in Category A but already
  extracted as PR #539.
- (`MethodStructure` native/code visibility was previously parked here as
  "candidate only"; it is now filed properly as row **28** below.)
- Issue #541 is a genuine master bug, but the docs explicitly say it is not a
  Category A quick extraction. It needs a wait-graph design, not a hunk.
- JIT generated owner-bearing statics are must-fix but parked for a JIT rebase.
- Runtime-published `ConstantPool` freezing and relation-cache key-shape issues
  are newly graduated/continuing audit work, not part of the already prepared
  18 master quick-file set. 2026-08-25: the reader-safety half landed on the
  branch as the volatile runtime read mirror (ledger row 58) - the master
  ArrayList index-read race is real on master too (master's runtime interns
  during execution with no gate at all), but the portable fix rides with the
  pool-publication infrastructure rather than as a standalone hunk.

## Review Notes Before Filing

- Re-run each targeted test against current master; some tests require a tiny
  additive helper type before they compile on master.
- For every patch sketch, prefer cherry-picking the named branch seed into a
  scratch master worktree first, then shrinking away branch-only helper APIs.
- Do not use branch-only `OwnershipDiagnostics`, `NativeTemplates`,
  `Lazy.Owner`, sealing, or broad clone-free adoption helpers unless the issue
  explicitly says the tiny helper is the prerequisite.

---

## Appendix: commit-hash resolution table

The 2026-08-28 rebase onto `origin/master` `82683bcd2` rewrote all 297 branch commits, so the
cherry-pick SEED hashes cited in the rows above no longer resolve on the branch. Commit
SUBJECTS survive a rebase, so resolve any seed with:

```
git log --oneline master..HEAD --fixed-strings --grep '<subject>'
```

The pre-rebase hashes remain reachable via the `backup/pre-rebase-master` tag, so an existing
scratch worktree still works. Hashes of MASTER commits are unaffected and still resolve.

| Cited hash | Kind | Commit subject (authoritative) |
|---|---|---|
| `0231a8771` | branch seed (rewritten) | Stop fabricating self targets in MethodBody owned copies |
| `0af827c72` | branch seed (rewritten) | Retire Cloneable from the structure family |
| `145f12f51074bae5e073db6181b0d015414dda65` | master (still valid) | Fix interpreter xConstrainedInteger toNibble conversion (#536) |
| `25371b397` | branch seed (rewritten) | Retire Cloneable from Token, Source, and MethodStructure.Source |
| `26ce54466` | branch seed (rewritten) | Fix alarm callback registry race and cancel leak |
| `33323ffe1` | branch seed (rewritten) | Report JIT unhandled exceptions as failures |
| `3e09abc32` | branch seed (rewritten) | Preserve main container startup failure causes |
| `5311da1ac` | branch seed (rewritten) | Rollback native callback registration failures |
| `536067f5e` | branch seed (rewritten) | Make method op-assembly failures terminal |
| `5b9d577da` | branch seed (rewritten) | Add jsondb rollback failure regression test |
| `5d9a5f395` | branch seed (rewritten) | Fix runtime-lazy delegation synthesis: assemble before publish, lock-free |
| `61e555a68` | master (still valid) | Fix keysAt() undercount in JsonMapStore when the last pending mod sorts before the final history key (#542) |
| `61e555a68cd82a866f82aea40a3bb97a424a3809` | master (still valid) | Fix keysAt() undercount in JsonMapStore when the last pending mod sorts before the final history key (#542) |
| `632cac927` | branch seed (rewritten) | Guard HandleConstant against raw cross-container serving |
| `6496f5303` | branch seed (rewritten) | Fix Future.and completion failure handling |
| `796f13465` | branch seed (rewritten) | Report worker runtime failures through join |
| `7ce5662d1` | branch seed (rewritten) | Refuse view cloning of register-bound refs |
| `8077ad6c0` | branch seed (rewritten) | Close the conditional must-audit rows; fix the graduated findings |
| `82683bcd2` | master (still valid) | Add 'xtc bundle': merge compiled modules into a single multi-module .xtc (#525) |
| `8a45ba708` | branch seed (rewritten) | Observe raw file submit write failures |
| `9456d6727` | branch seed (rewritten) | Tighten lint and constructor escape audit |
| `979784a1a` | branch seed (rewritten) | Preserve cause for requested module loads |
| `a11765c86` | branch seed (rewritten) | Harden runtime op publication |
| `a935bc553` | branch seed (rewritten) | Retain jsondb rollback failure after failed commit |
| `ae5d7ad80` | branch seed (rewritten) | Thread-safe DirRepository scan cache (concurrent-compile must-fix #8) |
| `b00654356` | branch seed (rewritten) | Make compiler codegen failures terminal |
| `c5c40d443` | branch seed (rewritten) | Share atomic and injected handle cells across views |
| `c621b1dca` | branch seed (rewritten) | Fix finalizer chain append and null-padding NPEs |
| `cc183520c` | master (still valid) | Fix concrete utility this-escape hazards (#539) |
| `d2165e4f8` | branch seed (rewritten) | Share freeze state across object views |
| `f4744cb1e` | branch seed (rewritten) | Preserve JIT bridge language exceptions |
| `f4df60ed1` | branch seed (rewritten) | Refuse view cloning of Mutable arrays |
| `f835b3693` | branch seed (rewritten) | Copy super parameters for short-hand property overrides |
| `ff8cc479a` | branch seed (rewritten) | Clone reflection invoke arguments before frame reuse |

## 35. String and Type index a long by its low 32 bits

`ArrayTemplate.extractArrayValue` receives the index as a `long`, and two implementations narrow it
before range-checking it:

```java
int nIx = (int) lIndex;

return nIx < 0 || nIx >= ach.length
        ? frame.raiseException(xException.outOfBounds(frame, lIndex, ach.length))
        : frame.assignValue(iReturn, xChar.makeHandle(ach[nIx]));
```

`(int)` keeps only the low 32 bits, so an index of 2<sup>32</sup>&nbsp;+&nbsp;4 becomes 4, satisfies
the guard, and reads element 4.

**Red proof, at run time.** On an eight-character String:

```
"abcdefgh"[4294967300]   ->  'e'          // should raise
[10,11,12,13,14,15,16,17][4294967300]  ->  raises "Index 4294967300 out of range 0..7"
```

The array gets it right because `xArray` checks the un-narrowed value, so the two containers
disagree about the same out-of-range index. The exception each broken site builds already reports
`lIndex` rather than the narrowed variable, which is the tell that the check was always intended to
be against the full value.

**Red proof, deterministic.** `org.xvm.runtime.IndexNarrowingTest` scans the runtime for a method
taking a `long` index that narrows it and then range-checks the narrowed name. On unfixed source it
fails naming both sites; on fixed source it passes. Two further tests guard the scan against
silently matching nothing. It scans source rather than invoking the method because reaching
`extractArrayValue` needs a live `Frame`, container and type composition.

**Affected**

| file | method |
| --- | --- |
| `javatools/.../runtime/template/text/xString.java` | `extractArrayValue` |
| `javatools/.../runtime/template/_native/reflect/xRTType.java` | `extractArrayValue` |

**Patch** - range-check before narrowing; generated against `origin/master` blobs, so it applies
as-is:

```diff
--- a/javatools/src/main/java/org/xvm/runtime/template/text/xString.java
+++ b/javatools/src/main/java/org/xvm/runtime/template/text/xString.java
@@ -188,11 +188,10 @@
     @Override
     public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
         char[] ach = ((StringHandle) hTarget).getValue();
-        int    nIx = (int) lIndex;
 
-        return nIx < 0 || nIx >= ach.length
+        return lIndex < 0 || lIndex >= ach.length
                 ? frame.raiseException(xException.outOfBounds(frame, lIndex, ach.length))
-                : frame.assignValue(iReturn, xChar.makeHandle(ach[nIx]));
+                : frame.assignValue(iReturn, xChar.makeHandle(ach[(int) lIndex]));
     }
--- a/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java
+++ b/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java
@@ -468,11 +468,11 @@
     @Override
     public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
-        TypeConstant type   = ((TypeHandle) hTarget).getDataType();
-        int          nIndex = (int) lIndex;
+        TypeConstant type = ((TypeHandle) hTarget).getDataType();
 
-        return nIndex >= 0 && nIndex < type.getParamsCount()
-            ? frame.assignValue(iReturn, type.getParamType(nIndex).ensureTypeHandle(frame.f_context.f_container))
+        return lIndex >= 0 && lIndex < type.getParamsCount()
+            ? frame.assignValue(iReturn,
+                    type.getParamType((int) lIndex).ensureTypeHandle(frame.f_context.f_container))
             : frame.raiseException(xException.outOfBounds(frame, lIndex, type.getParamsCount()));
     }
```

**Not affected:** `BaseInt128`'s shift operators also narrow with `(int)`, and that one is safe -
`LongLong.shl` masks the count with `& 0x7F`, and truncation preserves the low 32 bits, so the low
7 survive. Worth stating because the two sites look identical.

## 36. deleteAll(range) returns wrong elements on byte-backed arrays and crashes on String arrays

Array storage is per element type, and each delegate implements `deleteRangeImpl` itself. Two of
them are wrong, and only a MULTI-element range shows either - a single-element delete takes the
`deleteElementImpl` branch instead.

**Byte-backed arrays return the wrong elements, with no error.**

```
Int8  [1,2,3,4,5].deleteAll(1..2)  ->  [1, 3, 4]    expected [1, 4, 5]
UInt8 [1,2,3,4,5].deleteAll(1..2)  ->  0x010304     expected 0x010405
```

`ByteBasedDelegate.deleteRangeImpl` copies from `nIndex + 1` where the generic implementation uses
`nIndex + nDelete`. `+1` is correct in `deleteElementImpl` immediately above it, which is where it
appears to have been copied from.

**String arrays crash.**

```
ClassCastException: StringArrayHandle cannot be cast to GenericArrayDelegate
    at xRTDelegate.deleteRangeImpl(xRTDelegate.java:681)
    at xRTDelegate.deleteRange(xRTDelegate.java:457)
    at xArray.invokeDeleteAll(xArray.java:765)
```

`xRTStringDelegate` declares no `deleteRangeImpl`, so it inherits `xRTDelegate`'s - which is the
object-array implementation and casts to `GenericArrayDelegate`.

**Checked every element type with its own storage.** `Int64`, `Int16`, `Int128`, `Float64`, `Char`,
`Boolean`, `Bit`, `Nibble` and `Object` are correct. `Int8`, `UInt8` and `String` are not.

**Red proof.** `TestArray.testDeleteRange`, added in PR #563, run against an unfixed runtime:

```
IllegalState: "new Array<Int8>(Mutable, [1,2,3,4,5]).deleteAll(1..2) == [1, 4, 5]": ... = [1, 3, 4]
    at testDeleteRange() (array.x:390)
```

**Both are consequences of the untyped storage protocol.** Nine methods on `xRTDelegate` take a
`DelegateHandle`; twenty implementations open by casting it back to what they actually store. A
missing implementation is therefore invisible - `xRTStringDelegate` inherits one written for a
different storage type and nothing objects - and nothing relates the byte-backed implementation to
the generic one it was derived from. See E25 for the generification that would make the first of
those a compile error, and the split it depends on.

## 37. Comparing two array slices by reference crashes the runtime

Taking the reference of two slices and comparing them kills the program with a raw Java
`ClassCastException` escaping into Ecstasy, instead of answering the comparison.

```
Int[] a  = [1, 2, 3, 4, 5];
Int[] s1 = a[1..3];
Int[] s2 = a[1..3];
s1 == s2      // True - fine
&s1 == &s2    // Unhandled exception: Run-time error: java.lang.ClassCastException
```

Verified on clean master `145f12f51`:

```
java.lang.ClassCastException: class ...xRTSlicingDelegate$SliceHandle cannot be cast to
class ...xRTDelegate$GenericArrayDelegate
        at ...xRTDelegate.compareIdentity(xRTDelegate.java:266)
        at ...xArray.compareIdentity(xArray.java:555)
        at ...xRef$CompareReferents.doNext(xRef.java:1227)
```

**Cause.** `xRTDelegate` is the base of every array delegate, but its `compareIdentity` is not a
base implementation - it is the *object-array* implementation, and it opens with

```java
GenericArrayDelegate h1 = (GenericArrayDelegate) hValue1;
```

`xRTSlicingDelegate.SliceHandle` and `xRTView.ViewHandle` are `DelegateHandle`s with no relation to
`GenericArrayDelegate`: a slice holds `f_hSource`/`f_ofStart`, a view holds its source. Neither
overrides `compareIdentity`, so both inherit a cast that cannot succeed. The same is true of
`createDelegate`, `callEquals`, `insertElementImpl` and `deleteElementImpl` - those four happen to
be unreachable (`xArray` answers `callEquals` itself; Ecstasy rejects `insert`/`delete` on a
fixed-size slice before any delegate is asked), so `compareIdentity` is the one that escapes.

This is the type-system failure in the audit made concrete: the storage-specific implementation
lives in the shared base, so "a subclass forgot to implement this" is invisible to the compiler and
shows up as a `ClassCastException` in a user's program. Declaring the storage protocol `abstract`
on the base - the E25 split - turns exactly this into a compile error, which is how master bug 36
was found.

**Fix.** Give `xRTSlicingDelegate` and `xRTView` a `compareIdentity` that compares what they
actually hold: same mutability and size, same start offset and direction, and `compareIdentity` on
the source delegate. Structurally identical to the generic one, over the fields a slice/view owns.

**Regression test.** `&s1 == &s2` on two equal slices of one array must answer without raising, and
must stay `True` for slices over the same source and range.

## 38. Comparing arrays of the same element type by reference crashes when their storage differs

The same defect as bug 37, in the seven templates that bug 37's fix does not reach. Bug 37 fixed the
case where the *slice or view* is asked; this is the case where the concrete delegate is asked and
the **other** side is the slice.

```
Char[] a = ['a','b','c','d','e'];
Char[] s = a[1..3];

&s == &a      // bug 37 direction - dispatches to xRTSlicingDelegate
&a == &s      // THIS bug     - dispatches to xRTCharDelegate
```

Verified on clean master `145f12f51`:

```
java.lang.ClassCastException: class ...xRTSlicingDelegate$SliceHandle cannot be cast to
class ...xRTCharDelegate$CharArrayHandle
```

**Cause.** `xRef.CompareReferents` resolves the template from the *first* handle and passes both
handles to it. Every delegate's `compareIdentity` then casts **both** arguments to its own handle
type:

```java
CharArrayHandle h1 = (CharArrayHandle) hValue1;
CharArrayHandle h2 = (CharArrayHandle) hValue2;
```

Handle 1 is safe by construction - it selected the template. Handle 2 is arbitrary. Any pair of
arrays with the same element type but different storage crashes, in whichever direction puts the
concrete delegate first.

**Affected templates** (all seven have the identical two-cast opening): `xRTCharDelegate`,
`xRTStringDelegate`, `xRTFloat64Delegate`, `ByteBasedDelegate`, `LongBasedDelegate`,
`LongLongDelegate`, `xRTGenericDelegate`.

**Fix.** Test instead of cast; a mismatched representation is not the same referent:

```java
if (!(hValue1 instanceof CharArrayHandle h1) || !(hValue2 instanceof CharArrayHandle h2)) {
    return false;
}
```

Seven mechanical edits, same shape as bug 37's fix. `&a == &s` then answers `False` rather than
raising, and `array.x` / `numbers.x` run unchanged.

**Longer term** this whole family should stop being a cast at all - see E28, which moves the
comparison onto the handle so the operation is total and the signature stops promising to accept
pairs it cannot handle.

**Regression test.** `&a == &s` and `&s == &a` must both answer for arrays of every element type
whose delegate is not the generic one.

## Should-fix: invariants that lost their test when the source-shape tests went

These were asserted by reading `.java` as text - matching literal code, counting regex hits across
the tree - which passes when the code is spelled a particular way and proves nothing about what it
does. They were removed rather than kept as false coverage. Each is a real property; each needs a
different vehicle than reading source.

**The assertion-dependent wrapper idiom** (was `ReadOnlyViewContractTest`). No site should write
`assert (map = Collections.unmodifiableMap(map)) != null` - an assignment inside an assert, so the
map stops being protected under `-da`. `Contribution.getTypeParams()` still does exactly this.
Doing it properly: the pattern compiles to a store inside the region guarded by
`$assertionsDisabled`, so a class-file scan can find a `putfield`/`astore` there. Fiddly but
expressible, unlike the source regex it replaces.

**Narrowed indices reaching a range check** (was `IndexNarrowingTest`). An indexed method takes a
`long` and range-checks a value already narrowed to `int`. The old test matched method signatures
and extracted bodies with regex, which is parsing Java badly. Doing it properly: a small dataflow
check over the compiled method - does the value reaching the bounds check come from an `l2i` of the
parameter - or, better, a behavioural test that calls the method with a value above `Integer.MAX_VALUE`
and asserts it is rejected.

**Queued write failures reaching submit** (was `RawOSFileChannelSubmitTest`) and **future
completion on exceptional paths** (was `FutureCompletionSafetyTest`). Both are behaviour, and both
were asserted by looking at source. Doing it properly: drive the failure. Queue a write that fails
and assert `submit` observes it; complete a future exceptionally and assert both inputs are used.
Neither needs a scan of any kind - they need a test that runs the code.

**xRTFunction's defensive copy** (was `MethodInvokeArgumentAliasingTest`). That the function invoke
path copies the tuple's storage is pinned on the tuple-alias branch by a class-file scan for
`getfield m_ahValue`, which is the right vehicle. This branch does not carry that migration, so the
scan does not apply here yet.

For contrast, two invariants moved the other way and are now stronger than the source tests they
replaced: no template declares or reads an `INSTANCE` static, and `m_fMutable` is written only by a
constructor or the sanctioned transitions. Both read compiled classes, so neither can be fooled by
how the code is spelled.

### Second pass: the rest of the source-shape tests

Same treatment, same reason. Recorded here so the invariants are not lost with the tests.

**`Op.toName` throws for opcodes that are declared.** Converting the coverage test from parsing the
`instantiate` switch to reflection over `Op`'s own constants made it stricter, and it immediately
found eight: `OP_NEWV_T`, and the whole `OP_M_GET`/`OP_M_SET`/`OP_M_VAR`/`OP_M_REF` and
`OP_MIP_INC`/`OP_MIP_DEC`/`OP_MIP_INCA` family all raise `IllegalStateException`. `Op.toString()`
calls `toName`, so `toString()` on any of those still throws - which is the failure PR #556 set out
to remove. Worth someone deciding whether those opcodes should have names or whether `toName`
should answer for a declared-but-unimplemented opcode. The reflection test cannot be kept as-is
because it also covers opcodes that can never appear; scoping it correctly needs the instantiate
switch, which is why the original read source.

**Literal format plumbing** (was `LiteralFormatPlumbingTest`, four tests). That the pool can build
every literal format, that disassembly reads each back, that every runtime literal class has a
String constructor, and that the native container knows each one's type. All four enumerated the
formats by reading source. Doing it properly: enumerate `Constant.Format` and the runtime literal
classes by reflection, then exercise each - build, serialize, read back, materialise. That is a
behavioural round-trip and needs no source at all.

**The short-hand property override uses the copying factory** (was
`ComponentMethodParameterCopyTest`). Expressible as a class-file scan: `MethodDeclarationStatement`
should invoke `createMethodCopyingParameters`, not `createMethod`, on that path.

**Decoded ops do not cache runtime operands** (was `OpRuntimeCacheTest`) and **the semantic
current-pool lookup is bridge-only** (was `ConstantPoolDiagnosticsTest`). Both are "no site does X"
census claims and both are class-file scans: a `putfield` on a decoded op, and a call to the bridge
lookup from outside the bridge.

**Unsafe array escapes only shrink** (was `FrozenArrayEscapeRatchetTest`), **intersection child
merge does not throw when the first info is present and the second absent** (was
`IntersectionChildMergeTest`), **JIT failures set a non-zero result and rethrow natural exceptions**
(was `JitFailurePropagationTest`), and **finishConnect returns the masked handle** (was
`SocketHandleStateSharingTest`). Every one of these names a behaviour. Each needs a test that runs
the code and asserts the outcome, not one that checks the method is written a particular way.

**The tuple-argument copy** (was `MethodInvokeArgumentAliasingTest`, two tests) is already pinned
properly on the tuple-alias branch by a class-file scan for `getfield m_ahValue`, plus an Ecstasy
reproducer. This branch does not carry that migration, so neither applies here yet.

## 39. A one-return service call completes a bare handle and reads it as an array

**Classification: latent defect, no known reproduction.** Two sites get the shape wrong, so this
is not an E13-style "shape that permits a defect nobody commits" - the defect is committed in the
source. What is missing is a caller that reaches it, and until there is one this should not be
filed as a bug. Recorded here so the analysis is not lost, and so the enhancement that removes it
can cite what it removes.

### The defect, with exact sites on `origin/master`

`ServiceContext.Message.f_future` is a raw `CompletableFuture`, completed with either a single
`ObjectHandle` or an `ObjectHandle[]` depending on a `cReturns` count each consumer must re-read.
`Message.sendResponse` produces an array **only** in the `default:` arm, guarded by
`assert cReturns > 1`; cases `0`, `1` and `-1` all complete with a bare `ObjectHandle`.

Two consumers read the future as an array without establishing that:

- `ServiceContext.sendInvokeNRequest`, at `cReturns == 1`:
  ```java
  CompletableFuture<ObjectHandle> cfReturn = future.thenApply(ahResult -> ahResult[0]);
  ```
  The responder completed that future with `frame.f_ahVar[0]` - a bare handle - so `ahResult[0]`
  applies an array subscript to an `ObjectHandle`. `ClassCastException`, raised inside a
  `thenApply` on the completing fiber, far from the call that set it up.
- `ServiceContext.sendOpNRequest`, at `cRets <= 1`: hands `request.f_future` to
  `Frame.createWaitFrame(CompletableFuture<ObjectHandle[]>, int[])`. Same mismatch, same cause.

### Why there is no reproduction

Probed on this branch by instrumenting both arms and running the full `javatools` suite plus
`services.x`, `misc.x`, `queues.x` and `timeouts.x`: **zero hits on either.** The callers explain
it - `sendOpNRequest` has one caller (`xRTType:1221`) which passes a 2-element array, and
`sendInvokeNRequest` is reached through `xRTFunction.callN`, the N-ary path, where the compiler
emits `Call_N1` rather than `Call_NN` for a single return. So the arms look unreachable through
today's code generation. "Looks unreachable" is not "is unreachable", which is why the fix is
worth having and the bug report is not.

### The fix, and why it is an enhancement rather than a patch

Patching the two arms would leave the erased future that allowed them. `Message<T>` carrying its
own payload type removes the class: `OpRequest` splits into the two shapes it always had - single
for no return, one return and a tuple return; multi for more than one - each completing its own
future through a typed helper, so a single-valued response cannot be handed an array. Both
array-consuming sites then have to branch on `cReturns` instead of assuming, which is what makes
the two arms above correct rather than merely unvisited. The raw `(CompletableFuture)` cast in
`sendInvokeNRequest` disappears, and `Frame`'s already-typed overloads match without it.

Landed on `lagergren/lazy-instance` as `91a9727e0`. Verified on the Ecstasy side, where these
paths actually execute: `services.x` covers void calls, single returns, and an async two-value
return at `:106`; `tuple.x` covers the tuple shape; seven modules through `runner.x`, exit 0.
