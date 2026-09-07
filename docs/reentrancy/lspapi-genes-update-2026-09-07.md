# Where PR #545 stands after Gene's 2026-09-04 push

Base for comparison: `2568d6be4` (what I reviewed). New head: `96a147357`. Seven commits, and the
PR is **no longer a draft** - it is marked ready for review.

Gene replied to every comment. Three of them say "implemented; please review".

---

## 1. What he solved, and how

### H10 - the poll is gone, replaced properly

`runTask` is **deleted**. In its place a two-phase API:

```ecstasy
Int                registerTask(ModuleTemplate template, ModuleRepository repository, Int? consoleId)
Tuple<Int, String> startTask(Int id)      // returns (result, failure)
```

`Task.start` declares `@Future Tuple<Int, String> completion`, assigns it inside the same
`whenComplete` that records everything else, and returns it. So a Java caller gets **one future**
instead of 40 requests a second, and `Control.join()` is exposed on top.

This also answers the ordering question I raised and declined to guess at. I worried that a waiter
on the raw `outcome` future could be resumed before `Task`'s own continuation had recorded the
result. He avoided it by not handing out `outcome` at all: `completion` is a *separate* future
assigned after the recording, in the same continuation. That is the right shape and better than the
`waitForTask` I sketched.

### H5 - the registry leak, fixed more completely than in our branch

`whenComplete` ends with `TaskRegistry.unregisterTask^(id)`, placed **after** the success/failure
branch, so it runs on both paths.

**Ours is worse.** `XtcEngine.completeTask` calls `forgetTask` only when `taskFailure` returned
null - a failed run is never forgotten (recorded as M1 in the integration analysis). His does not
have that hole, and once we move onto his API the hole disappears with the code that had it.

### H19 - the file-system half, solved with facilities master already had

`TaskResourceProvider` now extends `BasicResourceProvider` and fabricates a complete per-run file
system:

```ecstasy
@Lazy FileStore store.calc() {
    @Inject Directory curDir;
    Directory taskDir = curDir.dirFor(taskDirectoryName(template.name, id)).ensure();
    return new ecstasy.fs.DirectoryFileStore(taskDir);
}
```

with `storage`, `rootDir`, `homeDir`, `curDir` and `tmpDir` all answered from it, and the console
answered from the run's own. That is exactly the "third shape" H19 said was missing - fabricate what
must be per-run, delegate the rest - and it needed no platform change.

**This is where I was wrong.** I concluded on 2026-09-07 that the runtime had no FileStore that
could be rooted below `/`, having read the native `xOSFileStore` and its `static final File ROOT =
new File("/")`. I did not look in `lib_ecstasy/fs`, where `DirectoryFileStore` has been all along,
documented as existing precisely so "the Container will not be able to see 'above' the level of the
injected FileStore's 'root' directory". E40 is withdrawn.

---

## 2. What he pushed back on, and whether he is right

**H4 - `containsKey` + `put` on a ConcurrentMap.** His reply: the method was private before, he
opened it up, and "the contract stays the same - the main container must ensure the uniqueness; the
assert is basically a documentation of that contract".

He is right about the current caller: console ids come from an `AtomicLong`, so uniqueness holds by
construction. `putIfAbsent` is still strictly better - it makes the check and the insert one
operation and lets the assert report a real collision rather than a silently overwritten supplier -
but this is a preference, not a defect, and it is his call. **Do not press it.**

**H8 - the unchecked `ObjectHandle` downcasts.** "There are just three call sites, so we don't save
much. But I don't disagree in principal." Fair. The count has since gone *up*, because `startTask`
returns a tuple that gets unwrapped by hand (`TupleHandle`, then two casts). Worth one follow-up
noting that, and nothing more.

**Part 5b - `LspTest` in CI.** "I'm a bit hesitant to include long running tests in a CI run, but
would take your advice on this one." That is a genuine question to us, and the answer is easy:
annotate the five scenarios `@Test` and let the two that are slow carry a tag or an assumption. It
does not have to be all-or-nothing.

---

## 3. Questions he has asked us, still unanswered

1. **`PrintStream` vs `PrintWriter`** (`xExternalConsole.java:31`): "If you think it would be better
   to accept PrintWriter, I'd be glad to change that." We have the measurements - `println` over a
   *buffered* sink delivers nothing until a flush, and a failed write is unreportable. **Answer: yes,
   `PrintWriter`**, and say why with the numbers.
2. **Console concurrency** (`xExternalConsole.java:83`): "My assumption was that the concurrency
   responsibility lies on the provided PrintStream. If you think it's incorrect or too tolling for
   the user I'd be willing to reconsider." That assumption is reasonable for a *host-supplied* sink.
   Worth confirming rather than arguing.
3. **`LspTest` in CI** - above.

---

## 4. What is STILL not solved

**Per-run string injections.** `TaskResourceProvider` extends `BasicResourceProvider`, whose
`String` case delegates to the parent - so `@Inject("label") String` inside a run resolves against
**container zero**, not the run. Two runs cannot be given different values. `LspSupport.run` still
throws `UnsupportedOperationException` for `injections`.

**A caller-chosen root directory.** The runner picks the root itself -
`curDir.dirFor("{moduleName}_{id}")` - and `LspSupport.run` still throws for `rootDir`. So a run gets
*a* file system of its own, but the caller cannot say *where*. For a host that wants a run rooted in
a workspace, or in a temp dir it controls, that is still the H19 gap, narrowed.

**H6** - `Boolean running;` on `Task` is still externally writable, despite "I'm changing all this rn".
**H12** - still `new InterpreterControl(interpreter, ...).start()`, so `taskId`/`consoleId` still
cannot be final, despite "please re-review". Both are small; neither was actually done.

---

## 5. New, and worth raising

### The `Cleaner` is the one I would push back on

```java
CLEANER.register(this, new TaskCleanup(connector, module.getSimpleName(), taskId));

private record TaskCleanup(InterpreterConnector connector, String moduleName, long taskId)
        implements Runnable {
    public void run() {
        connector.getMainContainer().invokeAsync("deleteTaskDirectory",
                xInt64.makeHandle(taskId), xString.makeHandle(moduleName)).join();
    }
}
```

A task's directory is deleted when its `Control` becomes phantom-reachable, and `retainStore`
defaults to `True` for the native path, so this is the *only* thing that deletes it. Four problems,
in increasing order of seriousness:

1. **Non-deterministic.** Directories accumulate until a GC happens to notice. A long-lived LSP
   server does many runs between collections.
2. **Not guaranteed at exit.** Cleaners are not run on JVM shutdown, so a process that exits
   normally can leave every directory behind. There is no deterministic path that removes them.
3. **It blocks a cleaner thread on the Ecstasy runtime.** `invokeAsync(...).join()` runs on the
   Cleaner's thread and calls into container zero.
4. **It can fire after the runtime is gone.** A host that closes its connector - which is exactly
   what `XtcEngine.close()` does - can have a cleanup fire afterwards, calling `invokeAsync` on a
   shut-down runtime from a daemon thread nobody is watching. Best case an exception is swallowed;
   worst case it blocks on a `join()` that never completes.

The deterministic alternative already exists in the shape of the code: `Control` is the thing whose
lifetime matters, so make it `AutoCloseable` and delete on `close()`, keeping the Cleaner only as a
backstop for callers who leak the Control.

### Task directories land in the process's working directory

`@Inject Directory curDir` inside `TaskRegistry`/`TaskResourceProvider` is **container zero's**
`curDir`, which is the process's cwd. So runs create `{moduleName}_{id}` directories wherever the
host was started - for an editor plug-in, that is the user's project directory. Worth asking whether
the root should be a temp directory by default.

### A failed run's buffered output is merged into the exception line

`BufferedConsole` accumulates a partial line and is flushed via `bufferedConsole?.flush()` only on
the success path. On failure the next call is `taskConsole.print($"Unhandled exception: {failure}")`,
which appends to the pending partial line and prints them concatenated. Nothing is lost, but a
half-written line and the exception text run together. One `flush()` before the print fixes it.

---

## 6. What this means for our branch

**Our `XtcEngine` integration is broken against his API.** We drive `runTask`, `taskRunning`,
`taskResult`, `taskFailure` and `forgetTask`; the first is renamed and re-shaped and the rest no
longer exist. Re-basing is not optional.

The good news is that re-basing **deletes** most of our own follow-up list:

| ours | after moving to his API |
| --- | --- |
| M1 - failed runs never forgotten | **gone** - he unregisters on both paths, in Ecstasy |
| S1 - the 5ms poll | **gone** - `startTask` returns one future |
| A2 - `forgetTask` result discarded | **gone** - no such call any more |
| M3 - per-run injections | **still ours to do**, and still the real gap |

**And one thing we did is now incompatible.** On 2026-09-07 I added `injectionNames`/
`injectionValues`/`rootDir` parameters to `runTask` and taught `TaskResourceProvider` to fabricate
string injections. `runTask` no longer exists. The *idea* survives - it is precisely the gap he has
not closed - but it has to be re-expressed as parameters on `registerTask`, and the file-system half
of what I added is now redundant because his provider does it better.

**Recommended order:** re-base the engine onto `registerTask`/`startTask` first, taking his file
system and his completion future as-is; then re-offer per-run string injections as a parameter on
`registerTask`, since that is the one piece of H19 still missing and we have a passing test for it.
