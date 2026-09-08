
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

- **There is no test for any of it.** Nothing in the tree exercises per-run injections or transient
  tasks. The PR comment on `runner.x:161` told Gene *"with a test that runs one module twice with
  different values"* — that test does not exist here. Either it was never lifted in or it was lost;
  either way the claim on the PR is currently unsupported, which matters if he takes the offer up.
- **`registerTask`'s injection parameters are dead on this branch.** `XtcEngine` only ever calls
  `registerTransientTask`; nothing calls the five-argument `registerTask`. The surface is carried but
  unexercised, which is how it would rot.
- **`XtcEngine.run(..., Map<String, List<String>> mapInjections)` takes a mutable collection**, which
  is against this project's own API preference — the runner underneath takes two parallel
  `String[]`s, and the map exists only to be unpacked into them at the boundary. An immutable value
  record (name plus values) passed varargs would match both the preference and the shape it is
  converted to anyway.

### Recommendation

Offer #3 and #4 to Gene as their own PR against `cpurdy/LSPAPI` — they are his open threads, they are
small, and #4 is the one he asked about. Write the injections test first, since the offer already
claims it exists. Hold #2 until the `close()` thread settles, because whether upstream wants a second
entry point depends on whether `retainStore` becomes public for other reasons.
