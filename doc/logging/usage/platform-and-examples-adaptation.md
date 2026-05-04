# How `lib_logging` would change `~/src/platform` and `~/src/examples`

This is a forward-looking doc surveying two real Ecstasy code bases that already exist
side by side with the XDK in this dev environment, and showing how each would adopt
`lib_logging` if it shipped as part of the XDK today. The point is to validate the API
choices against actual code, not invented snippets.

The repos surveyed:

- `~/src/platform/` — the XTC platform host (kernel, account/host managers, OAuth
  proxy, CLI tools).
- `~/src/examples/` — the demo apps (CardGame, welcome, shopping, chess-game).

## What they do today

Both repos use `@Inject Console console;` and write log-shaped lines manually. They
have invented their own ad-hoc logging vocabularies, and they do not all agree.

### `~/src/platform/`

The platform code logs heavily and has crystallised a convention: every log line is
prefixed `Info :`, `Warn :`, or `Error:` and timestamped with a helper from
`common.logTime`.

`platform/common/src/main/x/common.x:47`:

```ecstasy
static String logTime(Time? time = Null) { ... }
```

Used in `platform/kernel/src/main/x/kernel.x` ~13 times like this:

```ecstasy
console.print($"{common.logTime($)} Info : Starting the AccountManager...");
console.print($|{common.logTime($)} Warn : Failed to load the ProxyManager; new \
                 instance will be initialized
              );
```

`platform/auth/src/main/x/auth/OAuthProvider.x` has 12 such call sites:

```ecstasy
console.print($"Error: Authentication request to {provider} has timed out");
console.print("Error: Authentication request rejected: too many attempts");
console.print($"Info : Requesting authorization from {provider}");
```

`platform/host/src/main/x/host/HostManager.x` collects deferred messages into an
in-memory error list and replays them later:

```ecstasy
errors.add($|Info : Deployment "{webAppInfo.deployment}" is already active);
// ...
errors.reportAll(msg -> console.print(msg));
```

This is what every codebase that has *almost* invented a logging library looks like.

### `~/src/examples/`

The demo apps print to the console in the same way but without the `Info :`/`Warn :`
prefix. Each `Eights` game turn does:

```ecstasy
console.print($"{player.name} plays {card}");
console.print($"{player.name} cannot play and passes.");
console.print($"{player.name} draws {card}");
```

These aren't really log lines — they're program output. But they share the same shape
as the platform's `console.print` calls and would benefit from the same separation
between "intentional user-facing output" (`Console`) and "diagnostic event"
(`Logger`).

## What would change with `lib_logging`

Three categories of change, depending on what each `console.print` is actually for.

### 1. Diagnostic logging → `Logger`

This is the platform repo's bread and butter. The migration is mechanical:

**Before** (`platform/kernel/src/main/x/kernel.x:149`):

```ecstasy
@Inject Console console;

void start() {
    console.print($"{common.logTime($)} Info : Starting the AccountManager...");
    // ...
    if (!loaded) {
        console.print($|{common.logTime($)} Warn : Failed to load the ProxyManager; new \
                         instance will be initialized
                      );
    }
    if (errored) {
        errors.reportAll(msg -> console.print(msg));
    }
}
```

**After**:

```ecstasy
@Inject log.Logger rootLogger;
log.Logger logger = rootLogger.named("platform.kernel");

void start() {
    logger.info("Starting the AccountManager...");
    // ...
    if (!loaded) {
        logger.warn("Failed to load the ProxyManager; new instance will be initialized");
    }
    if (errored) {
        errors.reportAll(msg -> logger.error(msg));
    }
}
```

What we get:
- The timestamp goes away from the call site (the sink renders it).
- `Info :`/`Warn :`/`Error:` text disappears (the sink renders it).
- `common.logTime` becomes dead code; same for any duplicate of it elsewhere.
- The platform host can choose at deploy time whether to render plain text, JSON, or
  ship to a remote aggregator — *without changing any caller*.

### 2. The deferred-error pattern → markers + a memory sink

The pattern in `host/HostManager.x` — collect errors into a list, replay them later —
maps cleanly onto the `MemoryLogSink` shipped in `lib_logging`. Each `errors.add(...)`
becomes `auditLogger.error(...)` against a logger whose sink is a memory-backed buffer
that the host drains on completion.

```ecstasy
log.MemoryLogSink deploymentLog = new log.MemoryLogSink();
log.Logger        deployLogger  = new log.BasicLogger("host.deploy", deploymentLog);

// ... do the work ...

// On completion:
for (log.LogEvent event : deploymentLog.events) {
    console.print(event.message);
}
```

If categorical filtering matters ("show only audit events to ops"), markers do that:

```ecstasy
log.Marker AUDIT = markers.getMarker("AUDIT");
deployLogger.atInfo()
            .addMarker(AUDIT)
            .log("Deployment \"{}\" is already active", webAppInfo.deployment);
```

### 3. Genuine user-facing output → stays on `Console`

Examples like `~/src/examples/CardGame/src/main/x/cardGame/Eights.x`:

```ecstasy
console.print($"{player.name} plays {card}");
```

This is *user output*, not a log event. It stays on `Console`. The split is healthy:
`Console` is for "things the user is intentionally meant to see on stdout"; `Logger` is
for "diagnostic events the operator might be interested in". A logging library that is
shaped like SLF4J makes that distinction obvious.

Mixed cases — say, a CLI that wants progress output during normal runs but verbose
diagnostics under `--verbose` — naturally split into "console for the progress lines,
logger for the diagnostics". CLI tools at any scale benefit from the split.

## Worked migration: `auth/OAuthProvider.x`

This is the worst-offending file in the platform repo by call-site count. Twelve
`console.print` lines that all begin with `Info :` / `Error:`. After migration:

```ecstasy
service OAuthProvider {
    @Inject log.Logger rootLogger;
    log.Logger logger = rootLogger.named("platform.auth.oauth");

    void timeout(String provider) {
        logger.error("Authentication request to {} has timed out", [provider]);
    }

    void rejected() {
        logger.error("Authentication request rejected: too many attempts");
    }

    void requesting(String provider) {
        logger.info("Requesting authorization from {}", [provider]);
    }

    void crossSiteForgery() {
        logger.error("Cross-site forgery detected");
    }

    // ...
}
```

The lines are shorter, the level is now structured (a sink can filter on it), the file
no longer threads `Console` through every method, and the operator gets to pick
whether the output goes to stdout, a file, or a remote sink.

## What lands in the platform repo's `common.x`

Today `common.x` exports `logTime`. Post-migration, that helper goes away, but the
package gains a small canonical `Logger` per subsystem:

```ecstasy
package common {
    package log import logging.xtclang.org;

    @Inject log.Logger rootLogger;

    log.Logger kernelLogger = rootLogger.named("platform.kernel");
    log.Logger authLogger   = rootLogger.named("platform.auth");
    log.Logger hostLogger   = rootLogger.named("platform.host");
    log.Logger proxyLogger  = rootLogger.named("platform.proxy");
    log.Logger cliLogger    = rootLogger.named("platform.cli");
}
```

…or each subsystem injects its own logger directly without going through `common`.
Either is fine; the point is that *every* subsystem now has a named logger and there
is no string-prefix convention to maintain.

## What this means concretely for each repo

### Platform repo

- **Files touched:** roughly 10 — anywhere a `console.print($"... Info :")` /
  `console.print($"... Error:")` exists.
- **Net code change:** roughly -50 lines (no more timestamp prefix, no more level
  string, often no more `console` injection in files that only used it for logging).
- **Configuration model:** until `lib_logging_logback` ships, the default
  `ConsoleLogSink` keeps existing operator dashboards working (it produces line-
  oriented output). When it ships, the platform gets per-subsystem level control,
  rolling files, JSON output for log shippers — none of which it has today.
- **Migration risk:** very low. Each call-site is independent; migrate file by file.
- **Side benefit:** `MemoryLogSink` replaces every ad-hoc `errors.add(...)` /
  `errors.reportAll(...)` pattern with one library helper.

### Examples repo

- **Files touched:** few. Most `console.print` calls are genuine user output; only
  the *non-game-output* messages (test setup, error reporting from the CardGame
  shuffler, validation messages in `shopping/cart_api.x`) become logger calls.
- **Net code change:** roughly neutral. The intent is clarity, not LOC.
- **Educational value:** the examples become canonical demonstrations of the API/impl
  split — `welcome` could ship with `ConsoleLogSink` by default and `MemoryLogSink`
  in tests, side by side. That's a useful lesson for a "new to Ecstasy" reader.

## Open question

`lib_logging` is itself an XDK lib (`logging.xtclang.org`). The platform and examples
repos consume the XDK as a published artifact. To use `lib_logging` they need:

- The next XDK release to ship with the new module included (already done in this
  branch — see `xdk/build.gradle.kts` and `xdk/settings.gradle.kts`).
- Each repo's `build.gradle.kts` to add no new dependencies (the XDK already includes
  it transitively) — they just `package log import logging.xtclang.org;` and use it.

That's it. There is no separate Maven coordinate or jar to add. This is the upside of
shipping logging as part of the XDK: every Ecstasy program everywhere gets it for
free.

## Recommendation

When `lib_logging` v0 lands and the runtime injection wiring is in place, do a single
PR against the platform repo migrating `kernel.x`, `auth/OAuthProvider.x`, and
`host/HostManager.x` to `Logger`. That covers ~80% of the platform's logging surface
in one chunk and validates the API end-to-end on real code. The examples repo can
follow opportunistically, file by file — there is no urgency, and the gains there are
educational rather than operational.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
