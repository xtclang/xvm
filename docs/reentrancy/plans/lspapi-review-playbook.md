
---

## `lib_runner` audit, 2026-09-08 — ours vs PR #545 head (`361219c`)

**66 lines of divergence in `runner.x`, in four changes. All additive, all defaulted, and three of
them are our implementations of review threads that are still open on the PR.** This is not a fork
that needs reconciling so much as a set of contributions that have not been offered yet.

| # | change | status upstream | reconcilable? |
| --- | --- | --- | --- |
| 1 | `registerTask` gains `injectionNames`/`injectionValues` | absent | yes - defaulted `[]`, upstream's 3-arg calls still compile |
| 2 | `registerTransientTask` | absent, but `retainStore` **already exists internally** | yes - see below |
| 3 | `public/private Boolean running` | still plain `Boolean running` | yes - one keyword |
| 4 | `TaskResourceProvider` answers `case (String, _)` per run | absent | yes - additive case |

**#3 and #4 are literally our own review comments, unlanded.** The `runner.x:139` thread says
*"`public/private Boolean running;` is the whole fix"*; upstream still has the writable field. The
`runner.x:161` thread describes the injections problem — `BasicResourceProvider`'s `String` case
forwards to the parent, so two runs asking for the same name both resolve against container zero —
and offers *"happy to put it up separately if you want it"*. That offer has not been taken up, and
the code has been sitting here since.

**#2 is smaller than it looks.** `retainStore` is not ours: upstream already has it on
`TaskRegistry.registerTask` (defaulted `True`), on the `Task` service, and at the deletion site. It
is simply never exposed on the public API. `registerTransientTask` exposes an existing internal
capability, so reconciling it is a public-surface decision — a second named entry point, or a
`retainStore` parameter on `registerTask` — not a design change.

It also survives Gene's `AutoCloseable` rework for a reason that is now clearer: a `Control` is handed
to a caller who may inspect what the run produced, which is why `close()` and not completion. This
engine hands out no `Control` and exposes no task directory, so deletion at completion is right here
and wrong there. The two are complementary.

### Gaps this audit found

- **CORRECTION: the injections test did exist, and it could not fail.** This row first said no test
  existed; that was wrong - `PerRunInjectionTest` was there, and my search missed it because I
  grepped for `injectionNames`/`registerTransientTask` while it uses the public engine API. What it
  actually asserted was worse than absent: it ran ONE module twice and checked
  `label == "first" || label == "second"`, which passes when the second run sees the first's value.
  It could not detect the leak it was named for.

  The discriminator has to be outside the module - a module cannot tell which run it is - so the
  expectation has to be compiled into the source. It now uses two modules, each asserting its own
  value, plus a negative test proving a wrong value fails the run, which is what makes the positive
  one capable of failing. Transient tasks remain untested.
- **`registerTask`'s injection parameters are dead on this branch.** `XtcEngine` only ever calls
  `registerTransientTask`; nothing calls the five-argument `registerTask`. The surface is carried but
  unexercised, which is how it would rot.
- **`XtcEngine.run(..., Map<String, List<String>>)` - replaced by an `Injection` record.** The
  mutable collection was the smaller half of the problem. The bigger one was that the TYPE advertised
  something the runner cannot do: a `List` per name says multi-valued `String[]` injections are
  expressible, so every call had to be validated at run time and rejected with an
  `UnsupportedOperationException`. `Injection(String name, String value)` cannot express the
  unsupported case, so the check is gone with it.

### Recommendation

Offer #3 and #4 to Gene as their own PR against `cpurdy/LSPAPI` — they are his open threads, they are
small, and #4 is the one he asked about. Write the injections test first, since the offer already
claims it exists. Hold #2 until the `close()` thread settles, because whether upstream wants a second
entry point depends on whether `retainStore` becomes public for other reasons.
