# What to reply on PR #545, and where

Drafted 2026-09-07 after porting our engine onto Gene's `registerTask`/`startTask`. **Nothing here
has been posted.** Each item names the thread to reply into, so nothing opens a duplicate.

Order matters: he asked us three direct questions and is waiting on them. Answer those first, then
confirm what we verified, then raise the one thing we want changed.

---

## A. He asked us something - answer these first

### A1. `PrintStream` vs `PrintWriter`

**Reply in:** the `xExternalConsole.java:31` thread (r3933846869), where he wrote *"If you think it
would be better to accept PrintWriter, I'd be glad to change that."*

**Answer: yes, and here is why, measured rather than asserted.**

- A failed write cannot be reported. Writing to a stream whose `write` throws `IOException` does not
  propagate: `PrintStream` swallows it and sets a flag reachable only through `checkError()`, which
  nothing polls. A hosted run writing to a closed pipe goes quiet.
- `println` depends on the caller's auto-flush setting. `print` flushes explicitly in the current
  code; `println` does not. With `new PrintStream(out)` - `autoFlush` defaults to `false` - over a
  **buffered** sink, `println` leaves **0 bytes** at the sink until something flushes; with
  `autoFlush=true` it arrives immediately. `LspTest` passes a `ByteArrayOutputStream`, which is not
  buffered, which is why this has not bitten.
- The charset is whatever the caller happened to construct. `PrintWriter` takes the `char[]`
  directly, which is what `xTerminalConsole` already writes through.

`PrintWriter` fixes the last two and matches the parent. It does not fix the first - nothing in the
`Print*` family reports failure - but it stops adding a second encoding decision.

### A2. Whose job is console concurrency

**Reply in:** the `xExternalConsole.java:83` thread (r3933621812), where he wrote *"My assumption was
that the concurrency responsibility lies on the provided PrintStream."*

**That assumption is reasonable and we should say so** - a host-supplied sink is the host's to
synchronize. Worth adding only that it should be stated in the class doc, because the natural
reading of "per-run console" is that the runtime has made it safe.

### A3. `LspTest` in CI

**Reply in:** the `LspTest.java:45` thread (r3933723359), where he wrote *"I'm a bit hesitant to
include long running tests in a CI run, but would take your advice on this one."*

**It does not have to be all-or-nothing.** The cheap version is to annotate the five scenarios
`@Test`: they already assert by throwing, so a throwing test fails and that alone buys CI coverage
with no rewriting. If `testPoolGrows` and `testRunLatency` are the slow ones, tag or `assumeTrue`
just those. The only piece genuinely tied to `main` is the two path arguments, and
`DirRepositoryConcurrentScanTest.java:44-50` already shows the in-tree pattern for that.

---

## B. Confirm what he fixed - short, and worth saying

**Reply in each of the three threads where he wrote "implemented; please review".**

- **`runner.x` registry thread** - confirm. `unregisterTask^(id)` sits after the success/failure
  branch, so it runs on both paths. Worth saying explicitly that this is more complete than what we
  had: our engine forgot a task only when it completed normally, so a failed run leaked its
  container. We have deleted that code in favour of his.
- **`InterpreterControl.java:132` (the poll)** - confirm, and note *why* the shape is right: he does
  not hand out the raw `outcome` future but a separate `completion` assigned inside the same
  `whenComplete`, after the outcome is recorded. That is what removes the race a naive
  `waitForTask` would have had, and it is worth him knowing it was deliberate-looking from outside.
- **`runner.x:161` (H19)** - confirm the file-system half, and say plainly that we were wrong about
  the platform: we had concluded no `FileStore` could be rooted below `/`, having read the native
  `xOSFileStore` and its `File("/")`, and missed `ecstasy.fs.DirectoryFileStore` in `lib_ecstasy`,
  which is exactly the right tool and which he used.

---

## C. What we want changed - one thing, with an alternative

### C1. The `Cleaner` for task directories

**Reply in:** a new comment on `InterpreterControl.java`, on the `CLEANER.register(...)` line.

Task roots are deleted when the `Control` becomes phantom-reachable, and `retainStore` defaults to
`True` on the native path, so this is the only thing that deletes them. Four concerns, increasing:

1. **Non-deterministic** - roots accumulate until a GC happens to notice.
2. **Not run at JVM exit** - cleaners are not guaranteed to run on shutdown, so a process that exits
   normally can leave every root behind, with no deterministic path that removes them.
3. **It blocks a cleaner thread on the runtime** - `invokeAsync(...).join()` runs on the Cleaner's
   thread and calls into container zero.
4. **It can fire after the runtime is gone.** A host that closes its connector - which is what our
   `XtcEngine.close()` does - can have a cleanup fire afterwards, calling `invokeAsync` on a
   shut-down runtime from a daemon thread nobody watches. Best case a swallowed exception, worst
   case a `join()` that never returns.

**The alternative is already in his own code.** `submitTask` passes `retainStore=False` and the task
deletes its own root in `whenComplete` - deterministic, in Ecstasy, no Cleaner. We have taken that
route: our engine calls a `registerTransientTask` variant and never registers a cleaner. Suggest
making `Control` `AutoCloseable` and deleting on `close()`, keeping the Cleaner purely as a backstop
for callers who leak the Control.

### C2. Two small ones he said he would do and did not

**Reply in the existing threads**, briefly, without pressing:

- `runner.x:139` - `Boolean running;` on `Task` is still externally writable. He replied *"I'm
  changing all this rn"*; the surrounding code changed but this did not. `public/private` is the
  one-word version.
- `InterpreterControl.java:67` - he replied *"please re-review"*, but it is still
  `new InterpreterControl(...).start()`, so `taskId` and `consoleId` still cannot be final.

### C3. Where the casts went up, not down

**Reply in:** the `InterpreterControl.java:96` thread (r3933644114). He was right that three call
sites do not justify a typed wrapper. Worth noting only that the count went **up** with the new API -
`startTask` answers a tuple that is unwrapped by hand, `(TupleHandle)` then two more casts - so if a
typed `invokeAsync` ever happens, this is now where it pays.

---

## D. Do NOT raise

- **H4** (`containsKey` + `put`). He explained the contract: the caller guarantees uniqueness, and
  console ids come from an `AtomicLong`, so he is right for the current caller. `putIfAbsent` is
  still better but it is his call and pressing it costs goodwill for nothing.
- **H21** - withdrawn already, and `RepeatedRunSweepTest` now passes on his design.
- **The per-task root living in the process working directory.** Real - `@Inject Directory curDir`
  in container zero is the process cwd, so runs create `{moduleName}_{id}` wherever the host was
  started, which for an editor plug-in is the user's project. But it interacts with C1, and raising
  both at once muddles the ask. Hold it until the Cleaner conversation lands.

---

## E. Still ours, not his

Per-run **string injections** remain unsolved upstream: `TaskResourceProvider` extends
`BasicResourceProvider`, whose `String` case forwards to the parent, so two runs asking for the same
name both resolve against container zero. `LspSupport.run` still throws for `injections`. We have
implemented it on our side as two parallel arrays on `registerTask`, and `PerRunInjectionTest`
passes. **Offer it once the above is settled** - not in the same breath, since everything else here
is either agreement or one request.
