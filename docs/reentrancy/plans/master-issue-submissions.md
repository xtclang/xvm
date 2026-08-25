# Master Issue Submission Drafts

This is a filing-prep document only. Nothing here has been pushed to GitHub,
and no issue should be filed without manual review.

Baseline used for source references: local `origin/master`
`61e555a68cd82a866f82aea40a3bb97a424a3809`. The older red-on-master audit in
`../test-failure-evidence.md` used master
`145f12f51074bae5e073db6181b0d015414dda65`; re-run targeted tests before
filing if master has moved.

Scope decision: `plans/github-issue-breakdown.md` lists 19 Category A rows, but
the last row is already extracted as PR #539. This file prepares the 18 critical
master issues still intended for manual filing.

## Filing Order

1. jsondb rollback failure retention
2. Requested module load preserves corrupt-file cause
3. Method op assembly failure is terminal
4. Compiler codegen failure is terminal
5. Raw file submit observes queued write failures
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

## 1. jsondb rollback failure retention

**Issue title:** Do not swallow jsondb rollback failure after commit failure.

**Status/category:** Category A. Single-threaded master bug; any failed commit
whose compensating rollback also fails loses the only evidence that the
transaction disposition is unknown.

**Master evidence:** `origin/master:lib_jsondb/src/main/x/jsondb/Client.x:1427`
sets the result to `DatabaseError`; line 1430 has `catch (Exception ignore) {}`.

**Failure mode:** The client reports an ordinary database error after the commit
path failed, even if rollback also failed. A database health/recovery decision
cannot distinguish "clean rollback" from "commit failed and rollback failed".

**Minimal master-portable fix strategy:** Keep result, close, and `rootTx`
clearing unchanged. Log the rollback failure with the original commit failure as
context.

**Patch/diff section:** Verbatim branch seed: `a935bc553`.

```diff
diff --git a/lib_jsondb/src/main/x/jsondb/Client.x b/lib_jsondb/src/main/x/jsondb/Client.x
@@
-                    } catch (Exception ignore) {}
+                    } catch (Exception rollbackFailure) {
+                        log(rollbackFailure, $"Rollback after failed commit also failed: {e}");
+                    }
```

**Tests to add/run on master:** Add an XTC unit or service test that forces
commit failure and rollback failure. Behavioral red on master: rollback failure
is not observable. Run `./gradlew xdk:installDist` after the focused module
test, because the change is in XTC library code.

**Dependencies/order:** None. This is the smallest opener.

## 2. Requested module load preserves corrupt-file cause

**Issue title:** Preserve requested module load failures instead of returning
`null` for corrupt modules.

**Status/category:** Category A. Single-threaded master bug; a corrupt requested
`.xtc` file is indistinguishable from a missing module.

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
red on master. Run `./gradlew :javatools:test --tests org.xvm.asm.ModuleRepositoryLoadFailureTest`.

**Dependencies/order:** None, but the additive exception type is a tiny
prerequisite for the test to compile.

## 3. Method op assembly failure is terminal

**Issue title:** Do not serialize methods with empty op bytes after assembly
failure.

**Status/category:** Category A. Single-threaded master bug; a compiler defect
at emission can persist a corrupt module.

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
behavioral red on master. Run `./gradlew :javatools:test --tests org.xvm.asm.MethodStructureAssemblyFailureTest`.

**Dependencies/order:** None.

## 4. Compiler codegen failure is terminal

**Issue title:** Stop compiler retry loops after unchecked codegen failures.

**Status/category:** Category A. Single-threaded master bug; the compiler can
catch `Throwable`, print it, and continue mutating module state.

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
-                    out("Code generation failed: " + e);
-                    e.printStackTrace(System.err);
+                } catch (Throwable e) {
+                    throw new IllegalStateException("Code generation failed", e);
                 }
```

**Tests to add/run on master:** `CompilerCodegenFailureTest` is behavioral red
on master. Run `./gradlew :javatools:test --tests org.xvm.tool.CompilerCodegenFailureTest`.

**Dependencies/order:** None.

## 5. Raw file submit observes queued write failures

**Issue title:** Raw file submit must report queued write failure instead of
returning OK immediately.

**Status/category:** Category A. Single-service master bug; async I/O exists
without parallel containers.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java:231`
creates the write task; line 233 discards the `CompletableFuture`; line 235
returns OK.

**Failure mode:** A queued write can fail after native `submit` has already
returned `0`, so callers lose I/O failure information.

**Minimal master-portable fix strategy:** Keep async scheduling, but wait for
the scheduled future through the existing frame I/O continuation path and
assign the written byte count or raise a path exception.

**Patch/diff section:** Branch seed: `8a45ba708`; master path is
`xRawOSFileChannel.java`.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java b/javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java
@@
-        frame.f_context.f_container.scheduleIO(task); // don't wait
-
-        return frame.assignValue(iReturn, xInt64.makeHandle(0)); // OK
+        CompletableFuture<Integer> cfWrite = frame.f_context.f_container.scheduleIO(task);
+
+        Frame.Continuation continuation = frameCaller -> {
+            try {
+                return frameCaller.assignValue(iReturn, xInt64.makeHandle(cfWrite.get()));
+            } catch (InterruptedException | ExecutionException e) {
+                return xOSFileNode.raisePathException(frameCaller, e, hChannel.f_path);
+            }
+        };
+        return frame.waitForIO(cfWrite, continuation);
```

**Tests to add/run on master:** `RawOSFileChannelSubmitTest` is behavioral red
on master. Run `./gradlew :javatools:test --tests org.xvm.runtime.template._native.fs.RawOSFileChannelSubmitTest`.

**Dependencies/order:** None.

## 6. Future.and uses both inputs and preserves async failure

**Issue title:** Fix `Future.and` fast-path double-read and assert-only async
failure path.

**Status/category:** Category A. Single-threaded master bug.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:466`
gets `cfThis`; line 472 extracts both results from `cfThis`; line 511 uses
`assert false` after async get failure.

**Failure mode:** Completed `futureA.and(futureB)` can use `futureA` twice. If
the async join path fails, assertions-disabled production can hide the defect.

**Minimal master-portable fix strategy:** Extract the second result from
`cfThat`. In the async continuation, raise or complete with the actual failure
instead of relying on `assert false`.

**Patch/diff section:** Branch seed: `6496f5303`.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java b/javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java
@@
-            ObjectHandle[] ahRThat = extractResult(frame, cfThis);
+            ObjectHandle[] ahRThat = extractResult(frame, cfThat);
@@
-                    } catch (Exception e) {
-                        assert false;
+                    } catch (Exception e) {
+                        return frameCaller.raiseException(xException.makeHandle(frameCaller,
+                                "Future.and completion failed", e));
                     }
```

**Tests to add/run on master:** `FutureCompletionSafetyTest` is behavioral red
on master. Run `./gradlew :javatools:test --tests org.xvm.runtime.template.annotations.FutureCompletionSafetyTest`.

**Dependencies/order:** None. The exact exception-construction helper may need
to match master APIs.

## 7. JIT generated exception and bridge reflection failures are not swallowed

**Issue title:** JIT must propagate generated XTC exceptions and unwrap bridge
reflection exceptions.

**Status/category:** Category A for current JIT/direct execution paths; broader
JIT owner statics remain parked.

**Master evidence:** `origin/master:javatools/src/main/java/org/xvm/javajit/JitConnector.java:142`
catches `InvocationTargetException`; line 152 catches `Throwable ignore`.
`origin/master:javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java:130`,
`:187`, and `:231` catch `IllegalAccessException | InvocationTargetException`
together.

**Failure mode:** Generated code can throw a natural XTC exception while the
connector reports success or hides rendering failure. `nType` reflection can
turn an invoked XTC `nException` into `$unsupported`.

**Minimal master-portable fix strategy:** In `JitConnector`, preserve non-zero
result when generated code throws. In `nType`, catch
`InvocationTargetException` separately, unwrap its cause, rethrow XTC
`nException`/`Error`, and keep `$unsupported` only for bridge reflection access
failures.

**Patch/diff section:** Branch seeds: `33323ffe1`, `f4744cb1e`.

```diff
diff --git a/javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java b/javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java
@@
-        } catch (IllegalAccessException | InvocationTargetException e) {
+        } catch (InvocationTargetException e) {
+            Throwable cause = e.getCause();
+            if (cause instanceof nException exception) {
+                throw exception;
+            }
+            if (cause instanceof Error error) {
+                throw error;
+            }
+            throw $unsupported();
+        } catch (IllegalAccessException e) {
             throw $unsupported();
         }
diff --git a/javatools/src/main/java/org/xvm/javajit/JitConnector.java b/javatools/src/main/java/org/xvm/javajit/JitConnector.java
@@
-        } catch (InvocationTargetException e) {
-            try {
-                ...
-            } catch (Throwable ignore) {}
+        } catch (InvocationTargetException e) {
+            Throwable cause = e.getCause();
+            reportUnhandled(cause);
+            result = result == 0 ? 1 : result;
         }
```

**Tests to add/run on master:** `JitFailurePropagationTest` is behavioral red
on master. Run `./gradlew :javatools:test --tests org.xvm.javajit.JitFailurePropagationTest`.

**Dependencies/order:** None for this narrow failure boundary. Keep generated
owner-static fixes out of this issue.

## 8. MainContainer startup preserves failure causes

**Issue title:** Preserve startup/invocation cause chains at the host boundary.

**Status/category:** Category A. Single-run master bug; startup failure
diagnostics lose the real cause.

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
red on master. Run `./gradlew :javatools:test --tests org.xvm.runtime.RuntimeFailurePropagationTest`.

**Dependencies/order:** None. Worker terminal-failure and op-loop defect
propagation can be separate issues if the patch becomes large.

## 9. Alarm callback registry is timer-thread safe

**Issue title:** Make alarm callback extraction safe against the shared Java
timer thread.

**Status/category:** Category A. Concurrent, but reachable in any timer-using
program because the service thread and the process-wide Java `Timer` thread
exist on master.

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
`./gradlew :javatools:test --tests org.xvm.runtime.NativeCallbackRegistrationTest`.

**Dependencies/order:** Can land with issue 10 if review prefers one native
callback lifecycle PR; otherwise independent.

## 10. Native callback registration rolls back on startup failure

**Issue title:** Roll back native callback keep-alive registration when timer or
server startup fails.

**Status/category:** Category A. Single-threaded failure paths.

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
old shapes. Run `./gradlew :javatools:test --tests org.xvm.runtime.NativeCallbackRegistrationTest`.

**Dependencies/order:** Can be paired with issue 9.

## 11. Hash/equality contracts for Register, VersionTree, and MethodBody

**Issue title:** Fix metadata equality/hash contracts and cycle-prone method
body equality.

**Status/category:** Category A. Single-threaded map/set misuse and cyclic
metadata graphs.

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

**Patch/diff section:** Branch seeds: hash-contract wave plus `0231a8771` for
MethodBody target identity. Close patch sketch.

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

**Dependencies/order:** None. Keep unrelated metadata owner-copy APIs out.

## 12. Handle view lifecycle state is shared or refused

**Issue title:** Handle access views must not split atomic/injected/freeze
lifecycle state.

**Status/category:** Category A for atomic/injected first-install races and
freeze-state split; guards for register-bound refs and mutable arrays are
latent hardening.

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
`FreezeViewSharingTest` are red on the old shapes. `RefViewGuardTest` and
`ArrayViewGuardTest` are source-shape/guard proofs.

**Dependencies/order:** Keep this as one issue only if the patch stays small by
mechanism. Otherwise split into atomic/injected cell sharing and freeze cell.

## 13. HandleConstant does not serve live handles to the wrong container

**Issue title:** Guard `HandleConstant` against raw cross-container handle
serving.

**Status/category:** Category A when sibling/nested containers share module
constants containing live handles.

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
`javatools:test` after adapting to master APIs.

**Dependencies/order:** Can land before broad adoption work. Keep this narrow;
do not backport all `OwnershipDiagnostics`.

## 14. Reflection Method.invoke does not alias caller tuple storage

**Issue title:** Clone reflection invoke argument arrays before frame reuse.

**Status/category:** Category A. Single-threaded master bug.

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
stress. Run `./gradlew :javatools:test --tests org.xvm.runtime.template._native.reflect.MethodInvokeArgumentAliasingTest`.

**Dependencies/order:** None. This should be one of the first filed issues.

## 15. Short-hand property override copies super Parameter elements

**Issue title:** Short-hand property methods must not share super method
`Parameter` objects across modules.

**Status/category:** Category A. Single-threaded compile bug.

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
`./gradlew :javatools:test --tests org.xvm.asm.ComponentMethodParameterCopyTest`.

**Dependencies/order:** None if the helper stays tiny. Do not pull in the full
clone-free adoption API.

## 16. Contribution body copies re-own the hidden outer component

**Issue title:** Replace `Contribution.clone()` so copied contributions answer
the copied component owner.

**Status/category:** Category A. Single-threaded clone/copy bug.

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
is behavioral red on master. Run `./gradlew :javatools:test --tests org.xvm.asm.ComponentBodyCopyTest`.

**Dependencies/order:** Independent. Keep this separate from the broader
Cloneable-retirement modernization.

## 17. MethodStructure.Source body copies re-own the hidden outer method

**Issue title:** Replace `MethodStructure.Source.clone()` so copied source
metadata resolves through the copied method.

**Status/category:** Category A structurally; observable when copied methods
cross pools.

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

**Dependencies/order:** Can be filed with issue 16 as "inner-class clone owner
fixes" if reviewers prefer, but the hunks are independent.

## 18. FullyBoundHandle.chain appends instead of dropping linked finalizers

**Issue title:** `FullyBoundHandle.chain()` must append finalizer chains
instead of overwriting `m_next`.

**Status/category:** Category A. Single-threaded master bug.

**Master evidence:** Category A ledger row cites branch seed `c621b1dca`.
Master `FullyBoundHandle.chain()` asserted `m_next == null`; assertions-on
fails, assertions-off silently drops already-linked constructor finalizers.

**Failure mode:** Annotation-mixin constructor finalizers delegating to super
constructor finalizers can prepopulate a finalizer link. The construction
epilogue then overwrites or asserts instead of appending, so one finalizer is
lost.

**Minimal master-portable fix strategy:** Change `chain(...)` from "must be
unlinked" to tail append. Keep null-padding defensive fixes if the same test
needs them, but do not pull unrelated handle-view changes.

**Patch/diff section:** Branch seed: `c621b1dca`; close patch sketch because
the exact class location should be confirmed during cherry-pick.

```diff
diff --git a/javatools/src/main/java/org/xvm/runtime/ObjectHandle.java b/javatools/src/main/java/org/xvm/runtime/ObjectHandle.java
@@
-        assert m_next == null;
-        m_next = next;
+        FullyBoundHandle tail = this;
+        while (tail.m_next != null) {
+            tail = tail.m_next;
+        }
+        tail.m_next = next;
         return this;
```

**Tests to add/run on master:** `FinalizerChainTest.chainAppendsAtTailInsteadOfDroppingLinkedFinalizers`
is red on master under both `-ea` and `-da`. Run
`./gradlew :javatools:test --tests org.xvm.runtime.FinalizerChainTest`.

**Dependencies/order:** None. File before broader handle lifecycle work if the
patch stays self-contained.

## Items intentionally not in the 18

- Utility constructor this-escape fixes are listed in Category A but already
  extracted as PR #539.
- Issue #541 is a genuine master bug, but the docs explicitly say it is not a
  Category A quick extraction. It needs a wait-graph design, not a hunk.
- JIT generated owner-bearing statics are must-fix but parked for a JIT rebase.
- Runtime-published `ConstantPool` freezing and relation-cache key-shape issues
  are newly graduated/continuing audit work, not part of the already prepared
  18 master quick-file set.

## Review Notes Before Filing

- Re-run each targeted test against current master; some tests require a tiny
  additive helper type before they compile on master.
- For every patch sketch, prefer cherry-picking the named branch seed into a
  scratch master worktree first, then shrinking away branch-only helper APIs.
- Do not use branch-only `OwnershipDiagnostics`, `NativeTemplates`,
  `Lazy.Owner`, sealing, or broad clone-free adoption helpers unless the issue
  explicitly says the tiny helper is the prerequisite.
