# Remaining work

Everything still open on `lagergren/errs`, as one list to tick off. Section 4 was rechecked on
2026-11-23 against LSP4IJ 0.30.1, which errs builds against since 2026-11-19. Checked on 2026-11-04
against [the integration plan](errs-integration-plan.md),
[the execution plan](plan-embedded-execution.md),
[the upstream register](errs-upstream-issues.md), [the PR roadmap](pr-roadmap.md), and the live
state of every upstream issue and pull request named here. When you tick an item, add the commit,
PR or run that closes it.

Closed and not repeated here: L1–L72 (L61–L67 within their bounded scope), L74–L76, L80, L81,
L83, UI1–UI7, AST1–AST5, PLAT1–PLAT3, R1–R5. R6–R13 are closed too; their sections below
stay as records. Cases those closures refuse on purpose are documented refusals, not open tasks;
the few worth revisiting are under [beyond the current scope](#beyond-the-current-scope).

## 1. Stale documentation

- [x] `doc/errs/plan-embedded-execution.md`: R3 ticked. Its last condition, a `javatools`
  test of the runtime part, is `EmbeddingSupportTest`, merged with #703 (`bdb674af5`); the
  integration plan closed R3 on 2026-10-19.
- [x] `errs-upstream-issues.md`: LSP4J #999 (Gradle 9.8.1) recorded as merged on 2026-10-20
  (`52acfd58`).
- [x] `errs-upstream-issues.md`: LSP4IJ #1744 (UP25) recorded as closed on 2026-10-20.
- [x] `errs-integration-plan.md`: a 2026-10-21 Marketplace follow-up after the 2026-10-20
  paragraph: #711, the two publishers, #710 assigned to Cam, and the vsce 4.0.0 `--oidc` caveat.
- [x] `errs-integration-plan.md`, the R1–R8 section: the inventory is now the current catalog (299
  cases then; 296 since X273–X275 were retired with the color prototype), with the October 7 284-case
  run as the latest full-catalog run.
- [x] One reference for the lang tooling's configuration: `lang/doc/configuration.md` (2026-11-11), every
  editor setting, server property, environment variable, path and build, test and container property,
  each with the line that reads it; linked from `lang/README.md`, `lang/doc/README.md` and the READMEs
  with partial lists. On errs and #693. AGENTS.md said the lang build flags default to `false`, which
  only master's `gradle.properties` does; corrected on both branches.
- [x] Six statements in other docs that the code no longer bears out, listed in the reference's
  "Stale in other docs" section: `XTC_JAVA_HOME` in `lang/doc/manual-test-plan.md`, `-Dxtc.debug.lsp` in
  `lang/intellij-plugin/testing.md`, `xtc.formatting.tabSize` in the VS Code README, the
  `includeBuildLang` defaults in `lang/lsp-server/README.md` and `lang/README.md` (right for master),
  the launcher's `--socket` comment, and the DAP server's `logback.xml` comment. Fix each, or remove it.
  All six corrected on 2026-11-11 (`4572f154e2`), on errs and #693; the reference's section now lists none.

## 2. LSP features

### L73: server commands and edit application

- [x] Closed (decided 2026-10-27), with nothing to build:
  - Run reaches the R2–R5 build/run service without a command line or a second compile: the module
    lens names the editors' own `xtc.runModule`, which in embedded mode sends `xtc/run` to the
    service (X280–X303 in both editors).
  - The one server command that edits, the legacy code action's apply, is advertised only to a
    client with `workspace/applyEdit` and without code-action literals. A refusal or a 10-second
    wait fails the request with the client's reason (`CapabilityNegotiationTest`).
  - Not built: a server-run Run command for LSP clients without their own `xtc.runModule`, such as
    Neovim, Helix or Zed, whose lens has no command they can run. It waits until one is needed.
- Rule kept: no generic command registry or placeholder handler without a named user action.

### L77: colors (removed)

- [x] Decided: no Ecstasy color type is coming for now, so document colors are out of the editor
  target. They put swatches and a picker beside color values; they never colored the code.
- [x] Removed the prototype, its flag, X273–X275 and their docs; the theme list and X277's theme
  screenshot moved to the highlighting cases, and `b7cb7df88`'s protocol regression moved to
  `FileEventDuringAnalysisTest` (2026-10-21).
- [x] Mirrored into #693 as `40f2c385e`, with the R3 tick as `8bb4a7d10` (2026-10-21).
- [x] Removed the prototype bullet from #693's "Known follow-ups" (2026-10-21).

### L78: notebooks (deferred)

- [ ] Deferred by decision on 2026-10-05. If taken up: define what a cell is in terms of XTC modules,
  then implement `notebookDocument/didOpen`, `didChange`, `didSave`, `didClose` and the notebook
  synchronization capabilities.

### L79: debug inline values (after R7)

- [x] `textDocument/inlineValue` (2026-10-27; shared X331): the parameters and locals of the method or
  lambda where the program is stopped, at each occurrence up to the stopped line, as lookups by name
  that the editor makes in the stopped frame; not fields, nor variables declared further on.
- [x] `workspace/inlineValue/refresh`, after a compile, to a client that takes it.
- [x] A shared acceptance case: X331 passes in VS Code, which shows no values for Ecstasy without it
  (`debug.inlineValues` on `auto`). IntelliJ's is not implemented: released LSP4IJ draws no inline
  values for Ecstasy (UP50); with our LSP4IJ #1781, merged 2026-10-28, it passes.

### Navigation inside the XDK's library sources

- [x] Compile each bundled XDK library as a whole, so that a library source's read-only copy answers
  for every name in it. Before, the copy was compiled on its own, which cannot resolve the library's
  names; Go to Definition resolved only an import's and a type's names, in the library's binary
  (`LibraryReferences.kt`), which stays for when the setting is off. Done 2026-11-10 (local, errs):
  an opened copy's scope is its library's module file, with every source of the library and the
  resources its sources name written beside it, and the library compiles in a library mode that
  skips implementation and declaration targets, which ran out of memory for the core library. The
  setting is "Compile an XDK library as a whole" in IntelliJ's Ecstasy Server Runtime page and
  `xtc.server.compileLibraries` in VS Code, on by default (`-Dxtc.lsp.compileLibraries`).
  `XdkLibraryDocumentsTest` covers both modes. Seen in both editors on 2026-11-11 as X350
  (`642f6c83c8`): from a project file into `String.x`, then from `add` in a method body into
  `StringBuffer.x`, and its hover. It found that VS Code never had it: VS Code opens a library
  source as the server's `ecstasy-library:` content, which the server took for a source of no
  library, so its whole-library compile never ran there; fixed in `42f212eba0`, whose
  `LibraryContentProtocolTest` case fails without it. X350 passed in the container in VS Code
  (`run-urcrr6`, 11.8 s, the library compiling in 10.1 s) and in IntelliJ (`run-14498740152730139212`
  with 14g, `run-2367797182219195798` with 7g); before the fix VS Code failed at its second step
  (`run-BmA5Wq`). Neither playbook sets `-Xmx`, so the server takes a quarter of the container,
  1.75 GB at 7g. One IntelliJ run at 7g was killed out of memory: the container had just built the
  project cold, and the library's compile came on top. 14g has room.
  Measured the same day, with nothing else running: nothing compiles until a copy opens; ecstasy's
  compile takes 8.6 s with a 2 GB heap and 10.6 s with 512 MB, its live data peaks at about 450 MB
  (heap histograms: mostly the compiler's TypeInfos, its MethodInfos and MethodBodies and their
  maps; the AST is about 15 MB and the LSP model 20 MB), and its analysis holds about 330 MB while
  a copy stays open. With 384 MB it runs out of memory. Fixed in `3b9bb74e87`, which #693's commit
  282, `5f07a72eba`, carries together with `4ef6532ab0` (the eight files it changes are the same on
  both branches; CI run 42904322639 passed on it):
  - each copy that opened recompiled the library (7 s more for a second file); one compile now
    serves every open copy of the library, and goes, with its memory, when the last one closes;
  - it held up the project's compiles on the one compiler worker (EmbeddingSupport asks a host to
    serialize compiles that share its repository, so no second worker); a project file's compile,
    cursor query, rename or build now preempts it, and it compiles again after that work; project
    queries such as references and code lenses still wait;
  - with a maximum heap under 1 GB a copy compiles on its own, and the server logs why once; the
    server's tests run with 1 GB (Gradle's default for a test JVM is 512 MB).

### A workspace's own modules replace bundled libraries

- [x] Analyze the XDK's libraries from the XVM repository's sources, not the bundled XDK. Done
  2026-11-12 (`ba3bb43353`, `f670e99225` on errs; `afb56f6538`, `9c9a6053c0` on #693; pushed): a
  workspace whose modules declare `ecstasy.xtclang.org` keeps its library modules in the graph, each
  module depending on the workspace's `ecstasy`; source-built libraries replace the bundled ones in
  the compiler's repository, so `json` compiles against the repository's `ecstasy`, unsaved edits
  included, and Go to Definition opens the repository's files. `mack` and `_native` stay bundled.
  Before it, the same day, discovered modules that share a name (seven in the repository) failed
  every file with "Duplicate source module name" (`6cad3509aa`).
- [x] The XDK as a general rule rather than a detected case, and Gradle as its build mode. Done
  2026-11-12: any workspace module named as a bundled library replaces it (`XdkLibraryReplacement`),
  in all three ways a project is configured: discovery, Gradle's model (`.gradle/xtc/lsp-model.json`,
  which before refused the XDK's library names) and explicit `sourceModules`. A replaced `ecstasy` is
  a dependency of every other module; `mack` and `_native` are never replaced.
  `-Dxtc.lsp.replaceLibraries=false`, or a heap under ecstasy's whole compile, keeps the bundle. The
  server tells the editors which libraries the workspace replaces (`xtc/replacedLibraries`); while
  there are any, both editors build, run and debug with Gradle whatever the chosen mode, and say so
  once, as the server builds and runs only with the bundled XDK and refuses embedded builds there.
  Since 2026-11-24 a replacement reaches only its own workspace folder: in a VS Code window with the
  XVM repository and another project, the other project imports the bundled libraries and keeps the
  embedded mode (`xtc/replacedLibraries` names the folders; `xtc/build` takes a folder).
  The debug agent's handshake already carries the protocol version, and a session refuses an agent
  of another (`AttachedProgram.kt:185`). `XdkRepositoryWorkspaceTest` (heavy),
  `XdkWorkspaceDiscoveryTest`, `XdkBuildModelTest`, `CompilerConfigurationTest`,
  `XdkProjectServerTest` and `EmbeddedExecutionTest` cover it.
- [x] Run and Debug in Gradle mode do not resolve in a composite build such as the XVM repository:
  both editors ran `gradlew -q runXtc` (or `testXtc`) `--module=<m>` from the project's root, and
  the XVM root only aggregates its included builds, so Gradle answered "Task 'testXtc' not found in
  root project 'xvm'"; the full path resolves with the task's options. Done 2026-11-23 (#774): the
  plugin's model export names each source set's project by its build-tree path (`buildTreePath`,
  `Project.getBuildTreePath()`, `:xdk:lib-json`); the editors' import already merges every included
  build's model into the root's (`lang/gradle/compiler-model.init.gradle`), so no root aggregator
  was needed; the server tells the client each module's project from the imported model
  (`xtc/gradleProjects`); and both editors run a module's `runXtc` and `testXtc` in it, `:runXtc`
  for a root project, every known project's `testXtc` for all test modules, and the bare name where
  no model names the module's project. Checked in the XVM repository: its import gives all 50 source
  sets a path, json's `:xdk:lib-json`, which resolves. Limits: the path needs an imported model
  (IntelliJ: **Refresh Gradle model** on the Ecstasy Compiler page, or a sync of a linked Gradle
  project; VS Code: **Ecstasy: Refresh Gradle Compiler Model**); without one, a run that Gradle
  answers with "Task '…Xtc' not found" says once to import the build, in IntelliJ for every Gradle
  run and in VS Code for tests only, as a VS Code task's terminal output is not the extension's to
  read. Not yet tried in the editors on the XVM repository itself (manual 18a.20).
- [ ] Embedded builds, runs and debug sessions with the workspace's own toolchain instead of the
  bundled XDK, for a project on another XDK version as much as for the XVM repository: run workers
  started from the toolchain the build model names, which compile and run with it
  (`RunWorkerProcess.kt:523` starts them from the server's classpath today; their identity check
  takes only the bundled XDK). Not needed for the XVM repository, whose core developers change
  javatools with the libraries and build, run and debug with Gradle.
- [x] Renames of an XDK library's symbols in such a workspace are still proven against the bundled
  library (`CompilerRenameFacts.kt:358`), so they treat the repository's `ecstasy` as the bundle.
  Done 2026-11-24: the rename facts prove a replaced library as sources (`XdkReplacedLibraryTest`),
  and a rename in the workspace's own `ecstasy` is proven (`XdkRepositoryWorkspaceTest`, heavy, over
  copies of `lib_ecstasy` and `lib_json`; also checked over all 21 libraries). The last obstacle was
  not the library: the proof refused on any binding it could not represent, and `ecstasy`'s ninety
  thousand bindings hold 358 that the model leaves unbound and about 200 through unproven identities.
  The proof now compares those by their spelling and refuses only where one spells the old or new
  name (`XdkRename.equivalent`); its comparison went from 4½ minutes to seconds (a line index per
  text). Such a rename needs a 2 GB heap, below which it is refused with a message
  (`XdkLibrarySources.PROOF_HEAP_BYTES`).
- [x] A rename compiled the whole workspace twice, before and after the edit, with no reuse: in the
  XVM repository every rename but a local's compiled all its modules twice, 50 s with `ecstasy`'s
  (`compiles=44`). Done 2026-11-24 (`lagergren/rename-speed`): a rename's proof starts from the
  revision's compile that code actions share (`XdkCodeActionCache`), which `prepareRename` starts,
  and after the edit compiles only the modules whose sources it changes and those that depend on
  them. A move or a renamed module still compiles every module after the edit, and a revision's
  compile in which a module did not compile is not used: the proof compiles the sources itself, and
  is refused as before. The log says so in both cases. From the server's log, over all 21 libraries:
  a rename of a method of `String`, 53.1 s and `compiles=44` before, is now 28.5 s and
  `compiles=22`, after 31.3 s of `prepareRename`'s compile (`compiles=22`), which overlaps the
  typing of the new name, or is cached already by code actions; one of a method of `json`, 50.9 s
  and `compiles=44`, is now 10.4 s and `compiles=12`, json and the modules that depend on it. Over
  copies of `ecstasy` and `json` (`XdkRepositoryWorkspaceTest`, heavy): 32.3 s and `compiles=4`
  before, 15.6 s and `compiles=2` after 17.5 s of `prepareRename`; json's method 27.6 s and
  `compiles=4` before, 2.1 s and `compiles=1` after. A rename with nothing cached and no
  `prepareRename` costs what it did, over the two copies 33.1 s and `compiles=4`; one sent right
  after `prepareRename`, with nothing cached, waits for both, 59.7 s over all libraries where it was
  53.1 s. Correction: a local, also one in `json`, was never a project rename; its proof compiles
  its module alone, twice, 2.4 s in `json`, before and after. Found on the way: the API of the
  repository's libraries other than `ecstasy` cannot be read alone (next item), which failed every
  compile that started from an earlier one, code actions' too, with "missing class for constant";
  such an API now counts as changed.
- [x] The API fingerprint of an XVM repository library's artifact, other than `ecstasy`'s, could not
  be read alone (`XdkDependency.api`), so a module that depended on one was compiled again whenever
  it was. The fingerprint described each declaration with `Constant.toString()`, whose description
  of a signature asks whether a parameterized type is a function's: a type relation over classes of
  the workspace's `ecstasy` (`Number`, through `Array`'s conditional `NumberArray`; `Object`,
  `Nullable`, `ConditionalTuple`) that the artifact read alone holds only as fingerprints, without
  their structures, "missing class for constant". Done 2026-11-24 (`lagergren/json-rename-api`): the
  fingerprint names each constant by its index in the stripped pool instead, which the stripped
  bytes that it digests fix, so it resolves nothing and links nothing, and every library's reads.
  Over all 21 libraries, the next revision's compile after an edit in a method body of `json`, which
  code actions make, compiles 2 modules, 6.1 s, where it compiled 12, 19.9 s
  (`XdkRepositoryWorkspaceTest`, heavy: 1 where it was 2). Correction: this did not make a rename of
  a `json` method faster, which still compiles 12 modules: such a rename changes json's API, and
  every module that depends on json, directly or not, is compiled again by design, as it can reach
  json's members through another module's types, and the proof needs its bindings after the edit
  (next item).
- [x] Compile after a rename's edit only the modules that it reaches, the edited ones among them,
  and keep every other module's compile, even one that depends on an edited module (queued
  2026-11-24). Done 2026-11-24 (`lagergren/rename-name-scope`, `XdkRenameReach`). Writing the old or
  the new name is not enough to prove: Ecstasy matches a class with an interface by its members
  (duck typing), so a module that writes neither name can break or bind elsewhere, through an
  assignment of the renamed method's class to an interface with the old name, either way round; a
  class of its own that extends that class and so gets the new name's default from an interface; or
  the same through a typedef, a property, a generic inference, an import alias or a conditional
  return. A module is compiled after the edit when it writes either name; when its compile's
  constants name a type whose members the rename changes, which are the edited modules' types with a
  member of either name and, to a fixpoint, every type, typedef, method or property whose
  declaration names one; when its facts name a declaration on an edited line, whose place the edit
  moves, as keeping it refused a valid rename; or when its facts name a module compiled again by its
  artifact's revision. The others keep their compile, as everything they read of the edited modules
  is as it was. A name that is not a plain identifier, a move, or a compile that cannot be read
  compiles them all, and the log says why. `XdkRenameReachTest` has a case of each, with the same
  outcomes when every dependent is compiled; with the spelling alone, eight of its renames were
  proven where a module breaks or binds elsewhere, and the edited-line one refused. Over all 21
  libraries, a rename of a method of `json` compiles 2 modules, json and jsondb, 6.0 s, where it
  compiled 12; one of `String`, all 22 still, as every module uses `String`;
  `XdkRepositoryWorkspaceTest`, 3 and 1 where they were 3 and 2. Superseded 2026-11-24 by the
  member-use tracker (`lagergren/member-use-tracker`, the item after next), which serves every edit
  and compiles the same modules or fewer: `XdkRenameReach` is removed, and `XdkRenameReachTest`'s cases
  keep their outcomes and modules; its rename to a name that is not a plain identifier compiles 2
  modules where it compiled 4, as the tracker reads a name of any letters.
- [x] Found 2026-11-24: what a rename's proof cannot see. It proves that every name binds to the same
  declaration in compiled code, as compiling every dependent would; behavior that no binding carries is outside
  it, for any rename: a method or property found by a string at runtime (reflection by name), a runtime check
  against an interface whose matching depends on member names (`x.is(Duck)`, which the compiler checks only where
  it is static, as in an assignment), and the JSON keys of a renamed property's values. Today a rename neither
  changes nor reports a string literal or comment that spells the old name. To do: (1) the rename's note, as
  K6b's, says how many string literals and comments of the modules it reaches spell the old name, and where, and
  that they are not changed, as IntelliJ's search in comments and strings does (half a day to a day); (2) the user
  guide's known limitations say what a rename does not change: names in strings or computed at runtime, runtime
  type checks by member name, and serialized property names. (2) is done 2026-11-24, in the guide's known
  limitations. (1) is done 2026-11-26 (`lagergren/reflow-and-rename-note`, `XdkRenameSpellings`): a rename's
  answer shows, after K6b's note, how many comments and string literals of the modules whose sources it
  changes spell the old name as a word, outside a template's expressions and its own edits, and the first
  three by file and line, "and N more", saying they are not changed; `XdkRenameSpellingsTest`, a case of
  `XdkRenameServerTest`, and playbook X109 in both editors. Not for a move, a file rename, a safe delete
  or a local's rename.
- [x] Track the members each module uses from its dependencies, and compile a dependent again only when
  something it uses changes, not on any change of its dependency's API (queued 2026-11-24). This is the
  generic case, which subsumes the rename reach rule (`XdkRenameReach`, 28d1c73e51) and the whole-API rule.
  Done 2026-11-24 (`lagergren/member-use-tracker`, `XdkMemberUseTracker`), for the compile that code
  actions and renames start from, and each proof's compile from it; `XdkRenameReach` and the
  `alike`/`privatelyMoved` rule are removed with `XdkDependency.api` and `accessibleApi`; the whole-API
  digest (`XdkModuleApi.all`, what `api` was) stays as the oracle for a digest gap and the rule for a
  module that did not compile. javatools' `DeclarationDigests` digests each declaration's own API
  apart, naming the constants it refers to by their content, not their pool index (`XdkModuleApi`); a
  dependent's uses come from its artifact's constants (`XdkModuleUses`). A module whose sources are as
  they were keeps its compile when, of each module it depends on that compiled to another artifact, its
  facts name nothing by revision and no declaration place they name moved; no changed, added or
  removed declaration has a name it writes or its constants name; and none is seen through a type it
  uses as a whole, a type its constants name or its classes extend. A change is seen through what
  holds it, past a virtual child to the class that holds it (whose type a module writes as the
  holder's and the child's name), and, to a fixpoint, through each declaration that names a type it is
  seen through, a signature, a property's type or a contribution, as the compiler matches types with
  interfaces by their members and their members' types, checks abstractness and enum switches against
  all members, and resolves operators by annotation (the reach rule's fixpoint, for every change).
  Every module uses each type of ecstasy and the turtle as a whole, but for their private members and
  their namespaces' members, which a module finds by name. Proof: `XdkMemberUseTrackerTest`, 30 cases
  whose kept modules compile to the same facts, artifact and errors as from nothing; each rule but one
  has a case that fails without it, some of them renames that must be refused (`XdkRenameReachTest`,
  12 cases) and are proven without it. The exception, the private members of the types a module's
  classes extend, which no case shows another module to see, is kept as a conservative rule; a changed
  default value, which a call leaves to the callee, is carried by the modules its case compiles alone.
  `XdkIncrementalBaseTest` compares whole facts now; heavy `XdkMemberUseTrackerLibrariesTest`, over
  the 22 modules of the 21 libraries, renames among its edits. Over all 21 libraries, the modules that
  the next revision's compile compiles after an edit, before and after: a method body of `String`, 3 and
  3 (ecstasy, and json and xenia, whose facts name ecstasy's members by artifact); a private method added
  to `String`, 22 and 3; a public method added to json's `Parser`, 12 and 4; a comment line that moves
  `Parser`'s declarations, 12 and 4. A rename's proof after the edit: of `Parser.expectString`, 4 and 4,
  as the reach rule; of a `String` method, 22 and 22 (`splitMap`'s is refused before and after). Not
  applied to the diagnostics and navigation builds, keyed by artifact revisions, whose navigation names
  other modules' declarations by revision and constant index. A rename of a `String` method still
  compiles every module, as every module uses ecstasy's types as a whole; getting below that would need
  the compiler to record which whole-type consults each module makes, which is deliberately not pursued.
- [x] A rename that the client cancelled ran its proof to the end: `textDocument/rename` maps a
  refusal with `exceptionallyCompose` (`856d5ef01b`), whose new future the cancellation cancelled
  without reaching the adapter's, so the proof, half a minute over the XVM repository's libraries,
  kept the compiler worker, and a rename asked again waited for it. `XdkCursorServerTest`'s two
  RENAME cases, which only `-PlspTests=all` runs, failed on it (found 2026-11-24 on
  `lagergren/rename-speed`, and on its base `f51064654e`). Done 2026-11-24
  (`lagergren/rename-cancel`): the refusal is mapped by `mapFailureCancellable`, which asks the
  adapter's future of every cancellation of the one it returns, and a cancelled rename's proof stops
  after the compile it is in (`XdkRenameServerTest`: `compiles=1`, `outcome=cancelled`, where it
  went on to 4). Two requests had the same gap: `xtc/rename` without the compiler's adapter answered
  with `thenApply` over `textDocument/rename`, and `xtc/build` with `thenApply` over the build, so
  VS Code's build terminal, which cancels the request as it closes, did not stop the build; both map
  with `mapCancellable` now (`XdkCursorServerTest`, `XdkProjectServerTest`). `xtc/run` stops a
  building run with `xtc/stopRun`, not the request's cancellation, and is unchanged.
- [x] The semantic model leaves some occurrences unbound that the rename's proof now tolerates, which
  navigation still misses, in order of value: a local used for its formal type (`t1.DataType`, about
  50 in `ecstasy`: Find References and highlighting miss them, and a rename of such a local is refused);
  the `_` placeholder, which binds nothing and should be no occurrence (75); the names of a method's
  multiple return values (the largest group: hover and their rename); `this` and `super` in a lazy
  property's `calc()` and in a method reference (27); and the unproven identities (about 200, such as
  `DataType` itself), only if a wanted rename is refused for one. The 358 are those of `ecstasy` (293)
  and `json` (65), the two libraries `XdkRepositoryWorkspaceTest` copies; over both, as counted
  2026-11-24 by a temporary census test (each library compiled from the repository's sources, its
  rename facts' occurrences without a symbol sorted by the construct that wrote them; all 21 libraries
  in parentheses): a local or parameter used for its formal type 84 (124), `_` 75 (145), a return
  value's name 168 (389), `this` and `super` 27 (53), others 4 (14); and 190 bindings through unproven
  identities (261), 134 of them in `ecstasy`. Done 2026-11-24 (`lagergren/semantic-gaps`), one commit
  each, 358 (725) unbound before, 4 (14) after:
  - A local or parameter used for its formal type binds to its declaration: the compiler validates the
    names of such a dynamic type as a chain of name expressions, which is not a child of the type's
    node (`NamedTypeExpression.getDynamicExpression`). 84 (124) unbound before, 0 after. Find
    References, highlighting, hover and the rename of the local or parameter cover it
    (`XdkOccurrenceBindingTest`).
  - The `_` placeholder is no occurrence. 75 (145) before, 0 after. Found on the way: every proof but a
    rename's refuses an occurrence without a symbol in the modules it compiles, so each of these
    constructs refused a safe delete, an inline, a removal, an extraction or a member's addition
    anywhere in its module; a safe delete in a module with a `_` is proven now
    (`XdkOccurrenceBindingTest`).
  - A name of a method's return values declares the value it names, a parameter symbol of its type
    (`MethodDeclarationStatement.getReturns`, the structure's returns after a conditional's
    `Boolean`), which hover shows and a local rename changes. 168 (389) before, 0 after. The names of
    a function type's return values, as in `function (Int count, String label) ()`, stay unbound
    (others), and `XdkRenameUnprovableTest` takes them as its example.
  - `this` in a property's accessor, such as a lazy property's `calc()`, is the instance that holds
    the property: the compiler reaches it as an outer this that names its class, and it binds to a
    receiver symbol per method, which a proof knows as that class's receiver, as it knows a method's
    `this`; Find References joins it with every `this` of the class, as it does a method's. `super`
    in a method reference, as in `&super(_)`, binds to the method it dispatches to, as a call of
    `super` does (`InvocationExpression.getResolvedMethod`), and in a mixin, whose `super` has no such
    method, to the method's super register (`getBoundRegister`), as a call there does; a local
    function bound the same way, as in `&add(_, 1)`, which no library has, binds to the local too. 27
    (53) before, 0 after. Two of the 53, in an anonymous class of `jsondb`, bind through an unproven
    identity, as a method's `this` there does.
- [ ] The semantic model's remaining unbound occurrences and unproven identities (census of all 21
  libraries). On 2026-11-24 there were 14 unbound occurrences and 263 bindings through unproven
  identities (205 occurrences, 58 calls). A rename of a declaration that one of them spelled was
  refused: in user code, `class Derived(Int seed) extends Base(seed)` could not rename `seed`. Fixed
  2026-11-24, one commit per construct, each with an `XdkOccurrenceBindingTest` case that fails
  without it:
  - [x] a primary constructor's parameter passed in an extends or incorporates clause binds to the
    property it declares (f24b6bba06);
  - [x] a property declared in a loop's body, such as the static local `MAX = MaxValue / 10 - 1`,
    keeps its folded initializer's facts: the loop validates a clone of its body (a822025209);
  - [x] a local or parameter used as a type, as `type` in `new NarrowingFormat<Doc, type>(this)`,
    binds to its declaration (ad0a7adcb7);
  - [x] the names of a function type's return values have symbols of their own (b12d0a55e8);
  - [x] `this:class` in a property's accessor, as in a method (9e88b156fe);
  - [x] `this.Outer` in a virtual child binds to ecstasy's `Outer` (19db309dd2).

  Left: 8 unbound occurrences and 138 unproven bindings (80 occurrences, 58 calls), down from 205
  unproven occurrences. Through ad0a7adcb7, `DataType` in `t1.DataType` (87) and other dynamic names
  now bind through their validated expressions; their hover, definition and references are the same.
  The largest unproven groups left are `Element` (23), `this` in anonymous classes (17),
  generated methods such as `appendTo` (15), and `Key` and `Value`.

  2026-11-25 (`lagergren/semantic-gaps-2`): the census is a heavy test now, `SemanticModelCensusTest`
  (`./gradlew :lang:lsp-server:test --tests '*SemanticModelCensusTest' -PlspTests=heavy
  -PincludeBuildLang=true -PincludeBuildAttachLang=true`; it also runs with `-PlspTests=all`). It
  compiles the 22 modules from source, fails on an unbound occurrence or on a binding that no proof
  can place (a symbol with no identity and no declaration in the module's sources, or a call's method
  with no symbol, which refuse every proof, a rename's too), and writes the unproven ones, by
  construct and by library with examples, to `lang/lsp-server/build/semantic-model-census.txt`. It
  also counts the dispatch chains of methods and properties that hold an unproven identity, which
  the first census missed: every proof but a rename's compares them exactly, so one refuses a safe
  delete, inline, removal, extraction or member addition anywhere in its module. At errs'
  4f3b5222c2: 0 unbound,
  0 unplaceable, 1864 unproven (80 occurrences, 58 calls, 1726 chains). Fixed, one commit each, each
  with an `XdkOccurrenceBindingTest` case that fails without it, all in `lang/`, no compiler change:
  - [x] an anonymous class is proven by the span of the type it constructs: its `this`, the call of
    the constructor that the compiler writes for it (39), `this.Map` naming it, and most of the
    chains, which an anonymous class owned; each such constructor is identified by the one it passes
    its arguments to, so a named argument binds to the overload it reaches. A rename of a type that an
    anonymous class implements was refused, and of a constructor's parameter that a named argument of
    an anonymous construction names (01997d642c);
  - [x] a formal type's child, `Element` in `CompileType.Element` (23, `Key` 8, `Value` 7), binds to
    the formal type of its name that its constraint has, declared or from a contribution: definition
    and references go to `List`'s `Element`, and `T.Item` for a `Crate<E> extends Box<E>` to `Box`'s
    `Item`, not to `E` (b1ac10cad5);
  - [x] a call on a union receiver through a method that the runtime implements, `appendTo` of a
    const or a `String` (18 sites), takes the native body as the contract it implements in a call's
    route, not in a method's own: a rename of `Stringable.appendTo`, or of an interface method that a
    const takes from the runtime, stays refused (41c076bbe1);
  - [x] a call on a union whose legs share one route, `toString` on a `Point?` (4b17328400);
  - [x] the formal type that the compiler adds to a mixin or annotation whose contribution names a
    generic class without its parameters, `Inner` in `annotation Cap into Holder`, binds to the
    contribution's, in a binding and in the mixin's property chain (3acb3699c1);
  - [x] the methods that the compiler merges over a union, of an annotation into `Class | Method`,
    are proven by the union's route through each leg, and a proof places those routes where an edit
    moves their legs' declarations; a rename that takes a method out of the union's stays refused
    (aa64257383).

  Left: 213 (4 occurrences, 0 calls, 209 chains), not worth it now or complex:
  - 173 property chains in ecstasy alone, each of a `Referent` whose other members are `Ref`'s and
    `Var`'s own, through one member that no source declares. A chain is recorded only where the
    module declares one of its bodies, and only ecstasy declares `Ref`'s and `Var`'s `Referent` (a
    property with its own accessors or annotations in user code records none), so they touch only a
    workspace that holds the repository's ecstasy, and only its proofs other than a rename's. Medium:
    that member would need the identity of the formal it stands for, as `implicitFormal` gives a
    mixin's.
  - 28 method and 8 property chains in json, of annotations into a union of generic virtual
    children, `PointerAwareElementOutput into ElementOutputStream | ArrayOutputStream`, whose legs'
    formal types the compiler adds to the annotation from each leg: `implicitFormal` would have to
    follow a union contribution and the virtual children's parents. Medium, for one library's
    construct.
  - `OuterType` in `PublicType.OuterType` (3, ecstasy's `Class`): a formal type's child that no
    constraint declares, which the compiler resolves by its name alone, so there is no declaration to
    prove it by; a structural identity (its parent and name) would need a new proof identity in every
    comparison. `XdkRenameUnprovableTest` takes it as its unprovable example.
  - `httpMethod` of `method.is(Observe|Intercept)` (1, xenia): a property on a union, which the
    compiler makes a property of a class named for the union, `Observe | Intercept`, that no source
    declares; proving it needs the legs' properties, as a union's methods take their legs' routes,
    which the facts keep for calls only. Medium, for a rare construct.

  2026-11-26 (`lagergren/semantic-gaps-3`): the census confirmed 213 at errs' f835e4003f, and it takes
  an editor's census too, from the same compiles, of what hover, Go to Definition, Find References and
  highlights miss at a name, which no proof judges: an identifier that no occurrence holds
  (unrecorded), a reference whose symbol has no declaration (undeclared), a value without a type
  (untyped), a reference that the workspace's navigation joins apart from its declaration (unjoined),
  and one declared in no source of the workspace (elsewhere). It counts only, by construct and by
  library, in `lang/lsp-server/build/semantic-model-editor-census.txt` (summary lines start with
  `editor`). Its elsewhere count found the census compiling 5 libraries against the bundled ecstasy,
  and 3 against those: it scanned each library as a workspace folder of its own, under which no module
  depends on the workspace's ecstasy; it scans them as one folder now, as an editor that opens the
  repository does (e044853262). At f835e4003f: unrecorded 2798, undeclared 588, untyped 1, unjoined 0, elsewhere 0.
  Fixed, one commit each, each with an `XdkOccurrenceBindingTest` case that fails without it and a
  rename that compiles and stays refused, all in `lang/`, no compiler change:
  - [x] the parameters of a constructor with a `finally` block bind to their declarations in its body
    and in a call's named argument: the block is a method of its own over the same written parameters,
    whose nodes keep the block's registers, so Find References and highlights of such a parameter missed
    its uses in the body, and its rename was refused, as `ListMap`'s `inPlace` (185 locals, 6 named
    arguments; 2f1c4dcdb7);
  - [x] a label declares a symbol, which `break`, `continue` and its variable, `Loop` in `Loop.first`,
    refer to: hover, highlights, references, definition and rename of a label (287 unrecorded, 145
    undeclared; 3b2c0482dd);
  - [x] the `Referent` that the compiler adds to a property with code or a Ref annotation, which it
    treats as a class of its own, is proven by its property, `Generated(property)`: its 173 chains in
    ecstasy refused every proof but a rename's there, and in a module of an annotation into `Var`
    (f7bafb8809).

  After: rename facts 0 unbound, 0 unplaceable, 40 unproven (4 occurrences, 36 chains); editor
  unrecorded 2511, undeclared 251, untyped 5, unjoined 0, elsewhere 0. Left, not worth it now:
  - json's 36 chains, `OuterType` (3) and `httpMethod` (1), as above: the json chains touch only a
    workspace that holds the repository's json.
  - 2414 unrecorded package and module segments of import paths, `ecstasy` and `collections` in
    `import ecstasy.collections.HashMap;`: low value, as most packages are directories with nothing to
    go to, and an occurrence there would join the renames of packages and package aliases. Also 44
    segments of module names (`xtclang`, `org`), 19 `Path:` literal prefixes, and 34 named arguments of
    an annotation or contribution clause, `@Inject(resourceName=...)`: small, rare.
  - 119 undeclared named arguments of other modules' methods, `freeze(inPlace=True)`: Go to Definition
    goes nowhere (hover has the type); a dependency's declarations and the bundled libraries' index keep
    no parameter locations, so both would need them. Medium to large.
  - 61 undeclared method formal types named in a lambda, `To` in `n -> n.toIntN().as(To)`: the lambda's
    captured formal type has no declaration, so Go to Definition, references and highlights of the
    method's formal type miss it; it would bind to its method's. Small to medium, mostly in ecstasy.
  - 37 calls of a primary constructor, `construct Point(x, y)`, and 13 methods that the compiler adds:
    no declaration to go to. 16 others, as xenia's `param.ParamType` (9) and `this.List` (3).
    5 untyped: 4 labels of modules that name no label's variable, whose compile has no `Label` type,
    and one property.

  2026-11-26 (`lagergren/semantic-round`): two of the groups left, and the round's two limits, at errs'
  ce95d17c6c (editor unrecorded 2511, undeclared 251, untyped 5, unjoined 0, elsewhere 0). Fixed, one
  commit each, each with a case that fails without it, all in `lang/`, no compiler change:
  - [x] a named argument of another module's method, `freeze(inPlace=True)`, goes to its parameter in
    that module's source, of the overload its call selects: a dependency compiled from source, a
    binary's attached source and the bundled libraries' index keep each method's parameter places, by
    method and name, which the artifact's revision and the member-use tracker's places cover, so a
    parameter that moves alone compiles the modules whose named arguments go to it. Definition only:
    references and highlights hold the workspace's sources, and a library's source is read-only (119
    undeclared; 6fade151a2);
  - [x] a formal type named in a lambda, `To` in `n -> n.toIntN().as(To)`, binds to the method's or the
    class's formal type that the lambda's own type parameter captures, through each lambda around it:
    hover, definition, references and highlights as outside a lambda (61 undeclared, and `Element` as a
    value in `Service.passableAs(e, Element)`; fd5516b81c);
  - [x] a parameter of a constructor with a `finally` block that only lambdas in its body name binds to
    its declaration, through each capture's origin (f5b40ff2b4), and a label written with `\u` escapes,
    `L\u006Fop`, spans what is written in its declaration, a `break` and a `continue` (6a5e3e96c8); no
    library has either.

  After: rename facts unchanged, 0 unbound, 0 unplaceable, 40 unproven; editor unrecorded 2511,
  undeclared 70, untyped 5, unjoined 0, elsewhere 0. Left, as above, not worth it now: json's chains,
  `OuterType` and `httpMethod`; the import paths' segments and the other unrecorded names; 37 calls of
  a primary constructor and 13 methods that the compiler adds, with no declaration to go to, and 16
  others; the 5 untyped. Still not done: a rename of a formal type, in a lambda or out of one, which no
  rename offers for a type parameter, and of a name written with escapes, which a rename edits only
  where it is written as it is spelled.
- [x] The 8 unbound occurrences left are code the compiler never compiles, found 2026-11-24:
  - 6 extends arguments, in `OrderedMapSet`, `JsonLogStoreBase`, `JsonProcessorStore` and
    `KeyCredential`. Each class's explicit constructors call the super constructor themselves, so no
    synthetic constructor uses the arguments, and the compiler never checks them:
    `class C extends Base(noSuchName) { construct(Int n) { construct Base(n); } }` compiles without
    an error. COMPILER-188 reports unused contribution arguments only beside an explicit default or
    shorthand constructor.
  - 2 primary-constructor defaults, `ConsoleAppender`'s `False` and `Instruction`'s `Null`, which an
    explicit constructor of the same signature overrides with its own.

  A rename of a declaration that such a name spells is refused, for example renaming
  `OrderedMapSet.contents`. Done 2026-11-24 as a compiler change on master, xvm #802: COMPILER-188
  now reports every contribution argument that no synthetic constructor passes, and the new
  COMPILER-210 reports a shorthand default that an explicit constructor replaces when the class
  declares the property itself. The checks found two more dead sites, `PseudoRandom`'s `= 0` and the
  native `OSFileChannel`'s `(rawChannel)`; all ten are removed. Mirrored to errs and #693.
  XdkRenameUnprovableTest now proves its refusal on `CompileType.Element`, a binding the proof cannot
  prove, since no compiling construct is known to leave a name unbound (on `Public.OuterType` since
  2026-11-25, as `Element` is proven).
- [x] Import suggestions (`XdkAutoImports`) and the libraries' hover documentation
  (`XdkLibrarySources`) still read the bundle, and may offer its older names. Done 2026-11-23/24
  (decided: index the workspace's): a source artifact of a library's name records its importable
  declarations, and a replaced library's candidates come from it, the bundle's skipped; in a workspace
  whose own `json` replaces the bundled one, `WorkspaceOnly` is offered from it and the bundled
  `json`'s `Parser` is not. Hover takes a symbol's declaration from the dependencies before the bundle
  (`SemanticModelBuilder.kt`, `dependencies[it] ?: …`). Correction (2026-11-24): an earlier note here,
  and the commit `ada6b74f70`, said that no candidate survived its proof in such a workspace; that came
  from the tests, which left a second unresolved name, or a second broken module, under the proof's
  whole-graph compile. With one, the proofs pass.
- [x] The server reads the repository's `ecstasy` with its own compiler, the plugin's, so a compiler
  change newer than the plugin's build is not understood. Done 2026-11-23, by saying so (decided):
  a replaced library with errors gets an information diagnostic on its root (`REPLACED-LIBRARY`),
  that the server's compiler may be older and a plugin built from the repository matches. Alternatives: build the plugin from the
  repository, as Gridman does (works now, by hand); have the server load the repository's built
  javatools for such a workspace, in a class loader of its own as the Gradle plugin's in-process mode
  does (large: the server's adapter is compiled against one javatools); or at least say plainly, when
  the workspace's `ecstasy` fails to compile, that the plugin may be older than the compiler.
- [x] An editor setting for `xtc.lsp.replaceLibraries`, as `compileLibraries` has one in both
  editors. Done 2026-11-23: IntelliJ's check box on the Ecstasy Server Runtime and Logs page, VS
  Code's `xtc.server.replaceLibraries`, both machine-local and on by default.

### L82: completion evidence and release gates

- [ ] Agree response-time and memory budgets per supported hardware and platform. The current
  receipts define provisional local budgets only.
  Deferred until #693 lands (decided 2026-11-07): the provisional budgets stay documented targets,
  not gates, until then.
- [ ] Prolonged-session acceptance (editing, restart, process leaks) on each supported platform.
- [ ] Cross-platform and remote acceptance: Windows, Linux, and restricted or remote workspaces.
- [x] An uninterrupted full-catalog VS Code run: `run-Pc161s` (2026-10-28, `5e6a0f2391`) ran all
  333 cases in one window, 332 passed, and X218 failed on UP23, the exception already accepted. On
  `vscode-languageclient` 11.0.0 (2026-10-29) the full catalog passed 332 again, X218 failing, in
  three windows: keys typed by hand reached the test window in X167 and X222, which then timed out
  and passed when run again (`run-l2txtQ`, `run-gvT2Bt`, `run-CirjOs`).
- [ ] For each host limitation, either a released fix with its acceptance passing, or a recorded
  release exception: UP16, UP23 (already accepted as an exception), UP25, UP26, UP17.
- [ ] IntelliJ bulk replacement in a heavily decorated large file (UP17): a recorded comparison of
  the large-file probe between IntelliJ 2026.2.3, which lacks the platform fix, and the first build
  with intellij-community `5079f25b88`. Its decorated case opens a 40,000-method module, waits until
  all its 120,003 semantic highlighters are installed and replaces its second half in one `setText`;
  its controls replace three quarters of plain documents with 20,000 to 80,000 markers. Each record
  gives the time and `invalidBelowValid`, the most highlighters or markers the edit invalidates
  below one that it keeps, which sets the cost without the fix (why, and why the 20,000-method
  replacement that kept a quarter stopped freezing: the UP17 items under
  [our fixes](#intellij-platform-and-vs-code-our-fixes-filed-2026-10-28)). Without the fix, in the
  container on 2026-11-11 (`run-10918673005354525358`, `run-6810812479400253317`, their receipts kept in
  `~/shared/xtc-up17-gate-2026-11-11/`, as the container prunes old runs): 8,161 and
  7,862 ms with 54,468 below a kept highlighter, each reported by the IDE as a UI freeze in
  `IntervalTreeImpl.maxEndOf`, and 4.5 and 5.3 s for 80,000 plain markers with 59,521. The gate
  passes when the fixed build's receipt starts fully decorated, with the same `invalidBelowValid`,
  its replacement and controls take only the edit's own work instead of seconds, of the order of
  the 276 ms that keeping a quarter of 20,000 methods took with 1,024 below a kept highlighter
  (`run-7878288516979224096`), and the probe's marker checks hold with no IDE errors. Nothing
  asserts a time: the two receipts are compared. No build with the fix could be had on
  2026-11-11: 2026.2.4, 2026.3 and its 263 EAP candidates lack it, and the `master` (271) builds
  that have it are only in JetBrains' nightly repository, which is internal. Once a 2027.1 EAP or a
  backport has it, set `lang-intellij-ide` in `gradle/libs.versions.toml` to it, which the plugin
  then also compiles against (an EAP needs Starter's `useEAP(<build>)` in `PlaybookIde` instead of
  `withVersion`), and run `lang/intellij-plugin/container/playbook.sh gradle
  :lang:intellij-plugin:testCompilerPlaybook --tests '*CompilerPlaybookTest.largeFileEditing'
  -PintellijLargeFileProbe=true -Plsp.adapter=compiler -PincludeBuildLang=true
  -PincludeBuildAttachLang=true`.
- [ ] At closure, update together: this list, the integration plan's checklist, the capability
  matrix, the playbook, the AST/API ownership record and the extraction map.

### Test harness stability

- [x] Found 2026-11-24 with K6b: the VS Code playbook's CFG2 replaces the client's `window/showMessage` handler
  and disposes it without restarting the server, so the server's messages no longer show for the rest of that
  session. K6b's cases that watch the message restart the server after watching; CFG2 should too, or put the
  client's own handler back. Done on `lagergren/k6b-gaps`: CFG2 puts the shared graph back, as the server would
  refuse to start with its cyclic one, and restarts the server, whose new client has its own handler again;
  CFG1–CFG3, 7a.8 and 7a.9 pass in the container (run-s7sjBd).
- [x] Found 2026-11-24: after a restart of the server, IntelliJ's tour now and then misses the error of an edit.
  In the Settings and the server section, a check restarts the server (from LSP4IJ's Language Servers window, or by
  killing its process), hovers in Eights.x, which answers, then types `popCard` → `popCrd` and waits for the error,
  which never comes while the server is idle ("No error with popCrd in Eights.x … idle"); the checks after it then
  find the file still edited. Seen in `run-tour-9835569065365967505`, the full tour, and before in
  `run-tour-7164529137914405705`, that section alone; the section's other recorded runs of 2026-11-23 and
  2026-11-24, about a dozen, did not show it. The restart checks' other intermittent failure, a first hover that
  answered with an error, was UP82's. Two causes, one in each run (2026-11-25, `lagergren/tour-restart-flake`):
  - `run-tour-7164529137914405705` (2026-11-22) was UP83's, before its bridge: the tour edited Eights.x 114 ms after
    LSP4IJ started the server that replaced the killed one (idea.log 23:20:13.927 local, the process at 23:20:13.813),
    the server opened the file with the text from before the edit (didOpen v1 at 22:20:14.620Z, compiled with no
    error) and got no change of it until the check had given up. OpenedTextRepair, since 2026-11-23, sends the text
    again: the tour runs since log it doing so up to three times a run, and none of them lost the edit.
  - `run-tour-9835569065365967505` was the server's. The Debugging section's Force Step Into had left Array.x open,
    the server's read-only copy in its folder of copies, `/tmp/xtc-library-sources-<n>`, which LSP4IJ opened again at
    the restarted server while the first server's exit deleted that folder. The new server took the copy for a file of
    a module of its own, rooted at the folder's `ecstasy.x`, whose compile read the folder as it went and did not find
    `ecstasy.x`; and since a pull joins the analysis of each open document, every diagnostics pull of the server failed
    with it, 43 in its log, Eights.x's among them. It showed only when the folder was still there as the server opened
    the copy and gone once the compile read it; the servers after it, with the folder gone, compiled the copy alone and
    marked an error in it. Fixed in `a82d1d3d7a`: a file in another server process's folder of copies is this server's
    copy of the same bundled source, or, when this server has no such source, an uncompiled library document; and in
    `2ac401bc88`: a pull reports a failed analysis as the error that its publication gives, and fails no other
    document's pull. `XdkPullDiagnosticsTest` has both, each failing without its fix.
  - The section alone opens no file copy on LSP4IJ 0.30.1, whose Go to declaration opens ecstasy-library content,
    which the restarts did not open again at the new servers (`run-tour-8880248059079362867`): only the debugger
    opens a copy, so only a run with the Debugging section before it meets the second cause. In the container the
    whole tour ran 7 times with both fixes: in each, LSP4IJ opened the first server's Array.x again at the server
    after the first restart, in two runs at the next two as well, one of them tree-sitter's; each compiler server of
    them compiled its own copy of ecstasy for it, and no server logged an error. The Settings and the server section
    passed in all 7, and the section alone once; 5 of the 7 passed every check. The first failed 5 Debugging checks,
    as the container's `xdk/build/install/xdk/lib` was empty and xtc did not run (`playbook.sh build` filled it); the
    second 2 Diagnostics checks (the item below).
- [x] Found 2026-11-25 in `run-tour-11649425361568010670`, the whole tour in the container, not seen in the runs
  kept before: two Diagnostics checks waited out their 10 minutes. Warnings' Ctrl+F1 popup never showed; and after
  the changed-signature check put the signature back, PlayerTest.x kept "Dependencies unavailable: cardGame",
  although the server compiled it clean a second later (00:12:33.325 local, no diagnostics) and LSP4IJ pulled it
  twice after that (00:12:34.212 and 00:12:38.512). The server logged no error and no library copy was open. Two
  causes, neither the server's (2026-11-25, `ca776cfa2d`, `0968b2a404`, `3278a17df5`, `82c4105da5`):
  - The mark was LSP4IJ's (UP86, filed as redhat-developer/lsp4ij#2231). The editor marked the error within 95 ms of
    PlayerTest.x opening, before any pull of it was answered: the workspace reports asked while the file was closed
    and the signature broken reached LSP4IJ as the editor opened and before it connected the document, so LSP4IJ kept
    the error in a document without a connection and marked it. The connection replaced that document with an empty
    one; the signature's fix came 150 ms later and rejected the 11 pulls in flight, so no report gave the connected
    document the error, and its reports, all empty, changed nothing. LSP4IJ marks an editor again as its document's
    diagnostics change, or as a server stops or refreshes all its features, and makes no diagnostics pass in an
    editor. In the runs that passed, cardGame's pulls gave the connected document the error first, as related
    reports, and took it back after the fix (the trace of `run-tour-11590639236857448344`). The fix does not race the
    dependent's compile in the server: PlayerTest.x's analysis after it was clean, and in that trace every report
    after the fix, the file's own and the related ones, was clean. The bridge refreshes the editor's marks from the
    connected document once it holds the items of its own pull's report for its text: a first version refreshed
    them as the document connected, when it has none yet, and a second at its first update, which can be another
    document's report or one that LSP4IJ rejects; each took the marks of an error still there away until the
    document's own report. `ClosedFileProblemsTest` makes the order certain at LSP4IJ's registration seam: with the
    error fixed, the mark goes with the connected document's report, and without the bridge it stays; with the error
    still there, the mark stays all the while, also when a rejected update or another document's report comes first,
    and a refresh as the document connects, or at its first update, takes it away.
  - The popup closed. The edit's code lenses come with a compile of the whole project: in each of the 9 kept runs
    whose popup never showed they came 1.6 to 3.4 s after the press, in 18 of the 20 that passed 0.4 to 0.6 s (2.2 s
    in the other two). As they arrive above the caret the editor scrolls, and IntelliJ closes an editor's hover
    popup when its visible area moves: in `run-tour-15291386358089028646`, between the end of the step's act and the
    end of its wait, the selected editor scrolled once and the press itself was the only action, with no switch to
    another application; those counts did not say which came first, which the step now lists in order. The step
    now presses once the edit's lenses are in place, none is pending, and nothing else is in progress.
  - For the next failure: each server's whole trace is kept in playbook runs (about 42 MB for the sections through
    Diagnostics, 264 MB for the IntelliJ playbook's features session), with what each pull answered at DEBUG, by
    result IDs and item codes, related documents too; a failing diagnostics wait says what LSP4IJ holds for the file
    and what its own pull last reported, and a popup that does not show says what could have closed it, in order.
  - Container runs of the sections through Diagnostics (`XTC_CONTAINER_MEMORY=14g`, a 3-minute bound): without the
    fixes the popup failed in 1 of 4, with the UP86 bridge alone in 3 of 4, with the first versions of both fixes in
    none of 7, with the second in none of 7, whose 39 steps and 27 checks all passed, and with both as they are in
    none of 3, whose Diagnostics sections passed; one of the 3 failed Reading code's Parameter info popup (an item
    below). The stale mark came back in none of them, with the bridge or without; `ClosedFileProblemsTest` is its
    reproduction. The IntelliJ playbook's features session, all cases: with the second versions 342 passed, 8 partial
    and 2 not implemented as listed, no IDE error (`run-6036632534047447082`), after a run whose X71 hung (an item
    below); with both as they are the same (`run-13646449048832034797`).
- [x] Found 2026-11-25 in `run-4165373714699057152`, the IntelliJ playbook's features session in the container, not
  in the run of the same cases two hours before: X71 waited out its 10 minutes for Advanced.x's diagnostics after its
  last variant ("Expected []; editor diagnostics: []"), with no mark in the editor and no pull after the last ones,
  which the server answered as unchanged. The playbook's record of each document's latest own report
  (`DiagnosticResultMessages`) took the full report of the last variant's text, with no items, as stale, as the text
  changed again while it came, while LSP4IJ applied it: the next pulls named its result ID. The record then counted
  the items of those unchanged reports from the report it had kept, of an earlier variant with a parser error, so
  the wait never saw none. The UP86 bridge, which decides from the record when the connected document holds its own
  report (`currentReport`), had the same gap: when LSP4IJ applied a report for the old text that the record dropped,
  and the pulls after it were unchanged, it never fired, and a stale UP86 mark stayed until the file's diagnostics
  changed (found in the bridge's re-review; it needs an edit of a newly opened file as its first report arrives,
  which the tour never makes). The tour's `heldDiagnostics` took its count from the same record
  (`currentDiagnosticItems`). Fixed (2026-11-25): the record keeps each full report's items by its result
  ID, for the document's connection, also when the text changed while it came, and an unchanged report counts the
  items of the report it names; one that names none it knows records nothing. The bridge now also looks at each own
  report recorded for the document's text as it is, as an unchanged report updates nothing in LSP4IJ.
  `DiagnosticResultMessagesTest` has X71's sequence, and `ClosedFileProblemsTest` the bridge's, with the record's
  snapshot changed while the first report comes, as an edit that LSP4IJ sends only once the document is committed
  changes it; both fail without the fix.
- [x] Found 2026-11-25 in a full run of the server's tests, whose classes run in several JVMs at once, not when
  `XdkPullDiagnosticsTest` runs alone (2 of 2 passed): its two library source cases failed, one reading
  `TypeTemplate.x` from its JVM's `xtc-library-sources-<n>` folder, which was gone (FileNotFoundException), the other
  with a canceled pull. A case there makes a second folder of that kind in the same JVM, as another server's, and
  making it asks about the other folders of the kind (`ProcessDirectories.deleteAbandoned`): its look at the first
  folder's lock opened and closed a channel to the lock file, which on Linux and macOS releases the process's lock, and
  another JVM making its folder then took the first for an ended server's and deleted it. A child process could take
  the first folder's lock once the second was made. Fixed (2026-11-25): `SessionLock` keeps the lock files that its
  process holds or shares, and `held` answers for them without opening them, as its documentation required of its
  callers. It notes a lock file before it takes the lock, so that no look by another of the process's threads comes in
  between, and a process's threads take a kind's guard one at a time, by a monitor: a second thread's attempt at the
  guard failed, as the first's lock was the process's, and closing its channel released that lock, after which it made
  its directory unguarded.
- [x] Found 2026-11-25 in a full run of the plugin's unit tests, not when the class runs alone (8 of 8 passed):
  `XtcCompletionTest`'s "a typed prefix that the server completes with nothing lists the file's words" got ScoreCard,
  Scoreboard and size from the server, which completed the line without the typed "Tal". The test typed as soon as
  the file was open, and LSP4IJ 0.30.1 loses an edit typed while it connects a file (UP83), which the product's
  `OpenedTextRepair` mends but the tests' stub server does not start. Fixed (2026-11-25): both typed prefix cases wait
  until LSP4IJ has connected the file before they type.
- [x] Found 2026-11-25 in `run-tour-1548907980096693794`, the tour's sections through Diagnostics in the container,
  not in the tour runs kept before: Reading code's Parameter info popup (Ctrl+P) did not show within its 3-minute
  bound. The step's closers, now in order, say what came after its press: "ParameterInfo, scroll", one scroll of the
  selected editor and nothing else; the server answered Eights.x's code lenses 10 ms after the press (15:38:58.432,
  asked at .419 as the step selected the file), as in the Warnings step before its fix. IntelliJ's code vision keeps
  the lenses of an editor that a change or a refresh made pending while it did not show pending until it shows and
  they are in place again. Fixed (2026-11-25): the step presses once the file's lenses are in place, some, none
  pending, and nothing else in progress, through the helper the Warnings step's two waits use too, `lensesInPlace`,
  which fails the step when they do not come.
- [x] Found 2026-11-25 in `run-tour-9510825781413485262`, the tour's sections through Diagnostics in the container:
  The error's message (Ctrl+F1 on `popCrd` in `Eights.x`) did not show its popup within 3 minutes; its closers show
  ShowErrorDescription followed by a scroll. The edit of the step before, Errors as you type, starts a compile of the
  whole project, and the file's code lenses change with it, after the press: the race of the Warnings and Parameter
  info steps in a third step. With the error, the file has no lenses: its three go. The step now presses once the
  lenses differ from those before the edit, none pending and nothing in progress, possibly none (`lensesInPlace`'s
  `some = false`); a first version that required some failed all three of its runs, each with "now none".
- [x] Found 2026-11-25 in `run-tour-14199203050296383832`, the tour's sections through Diagnostics in the container,
  not in the 8 other runs of that day's series: Navigation's Call hierarchy showed `cardMatches` with no callers and
  failed its wait and check after 3 minutes. The server's trace shows IntelliJ's `callHierarchy/incomingCalls`
  request at 18:48:48.578 and LSP4IJ's `$/cancelRequest` for it 4 ms later; the server stopped the query as asked
  (outcome canceled, the request answered with an error), and IntelliJ asked no more, so the Hierarchy window kept
  no callers. Not the server's fault: the cancel is the client's. LSP4IJ 0.30.1 sends it (UP88), and users hit it
  too, so the fix is the product's, not the tour's. IntelliJ's hierarchy tree computes a node's children in a read
  action that it cancels for a write action and runs again only when it ends with the cancellation; LSP4IJ's call
  hierarchy catches that `ProcessCanceledException`, cancels the request and returns no children, which IntelliJ
  caches in the node. The write action was the cloud registry's update of `trace.state.event.service.url`
  (`RegistryUpdater`, in `edtWriteAction`), which the IDE log has at 19:48:48.580–.583 local time, the cancel's
  5 ms. Fixed (2026-11-26, `lagergren/call-hierarchy-flake`): `XtcCallHierarchyProvider` throws the cancellation
  that LSP4IJ caught, so IntelliJ asks again. `XtcCallHierarchyProviderTest` reproduces it: a write action while the
  stub server holds its first answer leaves no callers with LSP4IJ's structure, and the caller with the plugin's.
  The Navigation section passed 5 of 5 runs in the container (`run-tour-5641130009855803754`,
  `-7223607507479699437`, `-1077955297390743273`, `-10947987415425839701`, `-1471102060868252773`); in the last
  the same registry update came during an `incomingCalls` request, LSP4IJ canceled it 5 ms later, and IntelliJ
  asked again 3 ms after that and showed the callers.
- [x] Found 2026-11-26 in VS Code's full playbook in the container (`run-Rf8Tt8`, at `d7e27f6a03`), not in the 3 full
  runs before it nor in 3 runs of the case alone after it: X342 failed with "The request (id: 721, method:
  'xtc/tests') has been cancelled". The server answered that `xtc/tests` with RequestCancelled after 21 ms, its
  `project-DIAGNOSTICS` job `cancel-or-failure-signalled`, with no `$/cancelRequest` from the client for it; the
  extension's Test Explorer refresh takes the error as a failure. The server's fault, which a user's Test Explorer
  could hit too: 19 ms into that compile came the `didClose` of the previous case's `X297/Extract.x`, and a close, as
  any source change, retires every project query (`retireProjectQueries`) and cancels its future, which
  `testsAsync` answered with. Fixed (2026-11-26, `7de5908d63`): `testsAsync` asks again when that compile ends canceled,
  which is always the server's own doing, and answers from the compile of the sources after the change.
  `XdkPullDiagnosticsTest` reproduces it by ordering: the graph's compile holds until a document of the graph has
  closed; without the fix the listing ends canceled. On the stack, X342 passed in VS Code's full playbook in the container (`run-LgTm8x`).
- [x] Found 2026-11-26 in VS Code's full tour in the container (`run-UcTGyc`, at `d7e27f6a03`): Refactoring's
  "Extracted to a local" did not happen, and its check failed with "No local in nextPlayer". The step clicked the
  action widget's "Extract expression to local variable" row at 10:17:02.071. Not a lens race, and the server's
  fault, not the tour's: the click brought a `codeAction/resolve` for that action at 10:17:02.104, which the server
  rejected with ContentModified, and VS Code's language client takes that as a cancel and applies nothing. Between the
  list (10:16:58.658) and the click, VS Code had released `CardCollection.x`, 3 minutes after the tour opened it, as
  it releases a model no editor shows, and every `didOpen` and `didClose` expired every resolve handle; the code lens
  resolves after the click came from that close's refresh. Fixed (2026-11-26, `47d92e0ded`): resolve handles have a
  revision of their own, which an open or a close that leaves the document's text as its file has it does not
  change. `XdkResolveProtocolTest` reproduces it: an action listed before a member's close resolves after it, and
  failed with "Result expired or changed" without the fix. On the stack, the step passed in both runs of the tour's refactoring section
  (`run-JvRfPu`, `run-MSVwhR`).
- [x] Found 2026-11-26 in the plugin suite under load, in the stack's gate: ClosedFileProblemsTest's "a closed file's
  error is in Project Errors once after a build" saw the report's error, not the build's, after the build. LSP4IJ pulls
  the workspace's diagnostics twice before the build (for the open and for the test's refresh), and the test waited
  for the first report only; the second, answered after the build, replaced the build's error. Fixed (`3cafd89776`):
  the stub server can hold workspace pulls, and the test holds the later ones until its next refresh.
- [x] Found 2026-11-26 in the playbook container, where `playbook.sh vscode all` runs the server's suite first:
  XdkPullDiagnosticsTest's trace test failed with a NullPointerException, as it read the queries' end entries in the
  order the trace wrote them, which a query that ends after its caller heard the answer reorders. Fixed
  (`6e7a0884d6`): ordered by span ID. Its failure stopped that VS Code playbook run before its cases.
- [x] Found 2026-11-26: one checkout's container runs in two slots (`$XTC_CONTAINER_SLOT`) shared `/out`, and a run
  removes `/out/screen.mp4` as it starts, so a VS Code run could have deleted an IntelliJ tour's recording in the
  other slot; the progress monitor also read the other slot's log. Fixed (`3286daadb1`): each slot has its own `/out`.
- [x] Found 2026-11-26 in VS Code's tour (`run-JvRfPu`): Refactoring's "Rename a file" waited 60 s for the Explorer's
  name box, which never opened, with Hand.x selected and the Explorer list focused; passed in `run-MSVwhR` and before.
  `reveal` waits for the row to be selected, and `renameFile` acts on the tree's focused element; or something took
  focus and the inline box closed. Fixed (`054f5824de`): it waits until the tree has the keyboard on the row, and asks again until the box shows.
- [x] Found 2026-11-26 in a VS Code tour run on the tour-steps branch: the building section sat 43 minutes in
  `stopAll`'s "Every task ends", a run of the card game alive and waiting for console input. VS Code drops a
  `TaskExecution.terminate()` that comes before it has started the task ("Task to terminate not found"), reproduced
  in the extension suite, where the task still ran 20 s later; a user's Stop in that moment could be lost the same
  way. Fixed (`8ea3fe91c7`): the extension's tasks repeat the terminate as the task starts, the Gradle import's and
  Gradle debug's cancellations included. The tour's harness: `stopAll` stops again while tasks are left and waits for
  every embedded run's end (`768fe3dffe`), a silent run and its task no longer count as work (`b79a852e3b`), and a
  click or focus no longer waits silently in Playwright for an hour (`d80318082e`), which had left a later tour silent
  for 7 minutes.
- [x] Found 2026-11-26 in VS Code's tour (debugging, `run-hAoYtv`): "Set a value" chose Set Value from the variable's
  context menu, but the menu stayed open with Set Value focused and the value box never came; the click had only given the item the keyboard and left the menu open ("A data breakpoint" in `run-Snny5m` the same,
  on Break on Value Change). Fixed (`870ce8e0ef`): the tour chooses the item by Enter then. A failed step also closes such a menu (`d80318082e`).
- [x] Found 2026-11-26 by the tour's new "Breakpoints while you edit" step (`run-sal4lf`): in VS Code a breakpoint the debug
  session had verified did not follow its line when a line was typed above it, until the file was saved, though the
  user guide says it does and IntelliJ moved it at once; VS Code sends a file's breakpoints only on save, and shows a
  verified breakpoint at the line the adapter answered. Fixed (`555b407660`): on an edit of a debugged file,
  the adapter sends a DAP breakpoint `changed` event for each of its breakpoints at its edited line, from the line map
  `EmbeddedRuns` keeps; one whose line the edit changed or removed is unverified and taken out of the program. Both
  tours now show it unsaved (`run-CkWG5v`, `run-tour-1183441586494297494`).
- [x] Found 2026-11-26 in VS Code's full playbook (`run-LgTm8x`): X251's first Find References gave 0 instead of 2,
  right after the case configured its graph and opened its file; it passed in the runs before and twice alone after.
  The server's fault, which a user could hit too: the references request queued behind the graph's diagnostics, and
  the `didClose` of a document VS Code had released (X207's leftover `Extract.x`, outside the graph) ended it as
  canceled, which VS Code shows as no references; a workspace query ended on any open or close, the client never
  canceled it. Fixed (`73ef53e12d`): Find References asks again
  when documents only opened or closed while it ran, and an edit still refuses it; `XdkReferencesServerTest`
  reproduces it by ordering.
- [x] Found 2026-11-26 in VS Code's full playbook (`run-InIBIg`, on the playbook deduplication branch): X216 timed out
  in "Move applied: all sources and resources agree" (30 s), and the runner stopped there with 135 cases unrun
  (`run-Oz8vjq` the same after X335, 21 unrun). The move had lost its edits, which a user could hit too: the
  `didClose` of X166's `Library.x`, which VS Code released three minutes after the case opened it, retired the
  server's graph queries as `xtc/renameFiles` proved the move, and the server answered RequestCancelled, which the
  client had not asked for; the language client's handling of that throws, so VS Code moved the file without the edits
  and said nothing. In `run-Oz8vjq` ten such releases came as X335's move started, and the participant's own check
  ("Source documents changed while calculating the refactoring") threw the same way. Fixed (`331934d3db`): the move
  participant asks again for both, as for ContentModified. The move's check now waits while VS Code's events of the
  files come, and fails at once once they are final (`f68f717a4d`; X218's UP23 Undo fails in 0 s, not 30 s). One
  case's failure, a timeout, a case past its bound or a failed or stuck cleanup too, now fails that case alone: the
  waits it is in fail, it ends through its own cleanup, the editor is restored, and the run goes on (`3f8449236f`).
  Full runs since: `run-GVNl3L` 357 of 360 passed and `run-XalCz5` 355, none unrun.
- [ ] Found 2026-11-26 with X216: the server retires every graph query on any `didClose`, also of a document it does
  not have open, and answers a query that its own retirement cancelled with RequestCancelled, as if the client had
  cancelled it. The playbook's cleanup sends its own `didClose` for each document it detaches (`Workspace.discard`),
  and VS Code's release three minutes later sends a second one. The client now asks again for a move; other requests
  that a release cancels or overtakes still end empty or failed (Find References asks again since `73ef53e12d`): X143
  failed so in `run-GVNl3L` (2026-11-27), its `workspace/symbol` rejected ("Document changed during analysis") as
  X117's `App.x` was released, and in `run-XalCz5` X172 (its own `xtc/renameFiles`), X352 (references and definitions,
  "No result.") and X357 (Organize Imports), each as VS Code released an earlier case's document. The server could
  ignore a `didClose` of a document that is not open, answer ContentModified for work it retired, and ask its
  workspace queries again as it does Find References.
- [ ] Found 2026-11-27 in `run-XalCz5`: X5 failed in "Parser recovery diagnostic" (30 s): the pull of `Navigation.x`'s
  version 2 ended "failed: Canceled" and nothing pulled it again; it passed in the full runs before.
- [ ] Found 2026-11-27 in `run-GVNl3L`: X194 failed with "locator.focus: Timeout 5000ms exceeded" in
  `WorkbenchUi.focusEditor` (from `focusTestEditor`, in `discovered`), waiting for the active editor's input; it
  passed in the four full runs before. A Playwright call with a fixed 5 s bound, not a progress-based wait; cause not
  looked into.
- [ ] Found 2026-11-27 with X216: VS Code's playbook waits other than the type moves' file check still wait a fixed
  30 s (`PLAYBOOK_WAIT_MS`), and each case has a fixed bound (90 s, or its own), which a slow machine can outlast
  while it progresses; only `settles` (the type moves) and the diagnostics waits' finality follow the hang rules.
  `cleanUp`'s restore resets the source graph and restarts the server, but not other settings that a case whose own
  cleanup failed may have left changed; a case body still in a call an hour past its bound is left to itself.
- [ ] Found 2026-11-27 recording the VS Code tour (`run-cjtCLE`, debugging): "Set a value" failed, "The value box
  takes the keyboard … nothing under way for 60 s". `TourUi.menu` clicked Set Value, which chose it, and 34 ms later,
  as the menu was still closing and its item still had the keyboard, pressed Enter for a click that only gave the item
  the keyboard (`870ce8e0ef`); the Enter went to the value box that had just opened, which took the unchanged value
  and closed. `menu` needs a sign that the click did not choose, not a menu still showing.
- [x] Found 2026-11-26 in the review of the IntelliJ tour's wait fixes, checked for the VS Code tour: its `Activity`
  already leaves out the server's background traffic (the 2-s workspace pull, `$/cancelRequest`, `$/setTrace`,
  `$/progress` and the work under them); a replay of its trace rules over the full `run-UcTGyc` finds no request or
  compile that recurs by itself. But it counted every edit of a document, the language server's log among them, which
  grows with every request the server logs, the 2-s pulls too, while VS Code keeps it. Fixed (`3b617880d2`): edits of
  output channels and log files no longer count.
- [ ] Found 2026-11-27 checking the VS Code tour's waits: a running debug session, and a shell or process task, count
  as under way however silent they are, so a wait whose condition never comes while a debugged program waits for
  input, or a Gradle task hangs, runs to the hang's bound (an embedded run counts only while it prints, since
  `b79a852e3b`). The server's requests and compiles count for every wait, also one that only the UI can satisfy.
- [ ] Found 2026-11-26 in a one-case IntelliJ run (X1, with another container running): the test Driver's first call
  into the IDE timed out 9 s after start (DriverIllegalStateException, TimeoutException), before the case ran; X1–X3
  passed in the next run. It came twice more the same evening, in the shared queue's validation runs (one in each
  slot), so it is no rare event: a startup wait that machine load can outlast, to find and fix.
- [x] Found 2026-11-26 recording the IntelliJ tour: the settings section's negative check "Choosing the adapter in use
  restarted the server" (`expect(!within(HANG) { serverState() != same })`) sat about 10 minutes without a word: `within`
  ends early only when nothing moved for its idle window, the server's periodic trace events kept resetting that, and
  `within` never reports while it waits. The run passed once it returned. Fixed: `within`, the start-up waits and the wait after a
  probe timeout report every minute (`c96fa574fd`, `279df1b59f`, `b20330b23a`), and the
  negative checks read the server's settled state once the choice has run (`db629c91bf`): the section
  takes 50 s instead of 10 minutes or the hour bound.
- [x] Found 2026-11-26 recording the VS Code tour: the new "Breakpoints while you edit" step once found Quick Open not
  opening `Eights.x` (60 s, nothing under way; it failed fast, as designed); it passed in its other runs. The check
  before it leaves the keyboard in the Debug Console's input, and Quick Open typed the name as soon as its box showed,
  before the box had the keyboard, then took any row with the name in it (SaveEights.x's has Eights.x) and pressed
  Enter on whichever row the box focused. Go to Line typed the same way. Fixed (`4c6f5d3427`): Quick Open and Go to
  Line type only once the box has the keyboard and holds nothing else, close and reopen a box that holds other text or
  lost the keyboard, choose the file's row by its exact name, and the open repeats while no tab of the file has become
  active. Recorded runs `run-154cud`, `run-cjtCLE` and `run-RgQjoX`: 43 Quick Opens and 19 Go to Lines, each at the
  first try; the step passed in all three.
- [x] Found 2026-11-26 in the review of VS Code's rename refusal: the server answered `xtc/rename` and
  `textDocument/rename` with no edit for a refused proof, and also when the project changed during the proof
  (`XdkProjectQueries`, its `isCurrent()` checks), its base compile failed, discovery had a problem, or the module had
  not compiled; both editors showed the refusal for all of them. Done 2026-11-27 (`4da6bc57c2`,
  errs): a changed project answers ContentModified (`RenameOvertaken`; `renameProposal` checks
  `isCurrent()` once before any answer, the plain rename's proof when a closed member changed on disk), and so does a
  cancellation of the proof the client did not ask for, as a watcher's event retiring it, which answered
  RequestCancelled (silent in VS Code, "Rename failed: The request ... has been cancelled" in IntelliJ); a failed
  compile, a discovery problem or an uncompiled module answers RequestFailed naming the cause, with `unproven` as its
  data; a refused proof is still no edit. Neither editor retries: VS Code says "The project changed while the language
  server proved this rename. Rename again.", IntelliJ "The project changed during the rename; invoke Rename again.";
  both show the server's reason for the rest. `renameOrNone` takes ContentModified and `unproven` as not yet; X58 and
  X63 expect the unproven answer in both editors. XdkConditionalRenameTest's collision case had passed on an
  unanalyzed module's no edit; it analyzes it now, and the proof refuses it.
- [x] Found 2026-11-26 in the same review: `XdkAdapter.renameProposalAsync`'s path outside a project query mapped
  `renameAsync` with `thenApply`, so a client's cancel did not reach the proof. Done 2026-11-27 (`4da6bc57c2`):
  `mapCancellable`; XdkRenameTest's `a cancel of a rename proposal outside a project query reaches its proof` counts 3
  compiles, where `thenApply` let the proof go on to 4.
- [ ] Found 2026-11-27 with the above: file moves (`xtc/renameFiles`, `workspace/willRenameFiles`) still answer no edit
  for a changed project or a failed base compile; the plain rename's proof checks a closed member's change only before
  an edit, so its refusal of a changed member is still no edit. VS Code's textDocument/rename (sent only to a server
  without `xtcRenameProposal`) says "No result." for ContentModified, and LSP4IJ 0.30.1's own rename "The element can't
  be renamed."; the plugin turns LSP4IJ's rename off for Ecstasy files.
- [x] Found 2026-11-27 in the review of the rename answers: cancelling IntelliJ's "Renaming ..." progress showed
  "Rename failed: null", from the CancellationException LSP4IJ's wait gives the rename's future, in the rename's hint
  and the prepare step's. Fixed (`3408f07efc`): `renameFailure` shows nothing for a failure with a cancellation among
  its causes; RenameFailureTest has it bare and inside a CompletionException.
- [ ] Found 2026-11-26 in the review of the IntelliJ tour's wait fixes: a positive wait (`within`, `until`) counts the
  server's trace events and requests as work under way, so one whose condition never comes can still run to the
  hang's bound while the server keeps working, now reporting every minute but not failing sooner. The tour's
  start-up waits (`UiWaits.awaitUi`) have no idle check at all. Fix: count only progress that could make the
  condition hold (the IDE's own tasks, a request the step made), not the server's background traffic.
- [ ] Found 2026-11-26: a checkout's copied-back VS Code reports include every run the slot's work volume keeps,
  other checkouts' too, and since the shared queue from both slots; a script that took the newest copied run as its
  own took another agent's tour run (`run-y0hT9c` in wt-video). Fix: copy back only the runs this container made, as
  `copy_runs` does for IntelliJ.
- [x] The playbooks' diagnostics waits took whatever the editor showed, which may be the report
  from before an edit, so a "no errors" wait could pass on the text before it (2026-10-29,
  `b2f452c2f2`). Both editors pull diagnostics, each pull for one version of a document, and the
  waits now take only the report for the document as it is now: VS Code's language client
  records each report's version, IntelliJ's UP07 bridge each report's snapshot. A document not
  open in an editor has no version to tie a report to; its waits are as before. Every case
  passed with them: VS Code's 337 but X218 (UP23), IntelliJ's 329 in the container.
- [x] X116 in VS Code (Undo of a module rename) waited for its diagnostics until it timed out
  (2026-10-29, `c2817f91da`). The open `Library.x` kept an analysis the compiler had retired, which
  nothing replaced, so every query on it ended as RequestCancelled, and VS Code, which pulls again
  at once after a cancel, pulled without end; the stricter waits showed it, with the pull history
  their timeout gives. The server now analyzes such a document again as a query starts. What left
  the analysis without a replacement is not known: the loop had rotated out the trace logs of the
  rename and its Undo.
- [x] The container's first full IntelliJ run (2026-10-29, `1b22011ddd`) failed four cases on the
  container's setup: X35 as root, for whom a read-only file is writable; X260, whose canceled
  Gradle daemon most likely stayed a zombie, which Java reports alive, with no init process to reap
  it (`podman run --init`); X336, whose `xec` found the image's JDK 21; and X303, whose mavenLocal
  went with the container. With those fixed, each case IntelliJ implements has passed there; X218
  timed out once under the full run's load and passed in the next run.
- [x] X259 failed with LSP4IJ 0.21.0 at its last step (2026-10-29, `41c6acc221`, `2be4a17414`,
  `6949564dba`). A restart that disposed the library's document synchronizer while LSP4IJ still
  held its change until the PSI commit let the old synchronizer send that change, numbered after
  its own versions, through the new connection; UP09's bridge forwarded it as the reopened
  document's v2 and then dropped the real v2. The bridge now drops a change numbered above what
  the document's current synchronizer has reached (UP09), the case waits for LSP4IJ to reopen the
  consumer, and it flushes the library's edit before the restart, since 0.21.0 still logs an IDE
  error for the late send. In a Linux container it failed 2 of 3 without the check and passed 4
  of 4 with all three; on macOS X74, X259 and X324 pass with them (`run-3574220030596104685`).
- [x] X74 and X150 each stopped a full IntelliJ run (2026-10-29, `ed4d091919`). X74: IntelliJ drops an
  editor hint while the IDE is not the active application, and the harness reopened the popup only
  when a poll saw the IDE unfocused; it now counts the IDE's deactivations, and reopens after any.
  That the IDE was deactivated then is inferred. X150 needs no fix: keystrokes typed elsewhere
  ("his lo") reached the test IDE, as in X283.
- [x] X257 and X324 failed only in the full nightly run of 2026-10-28 (2026-10-29, `e077e44cb4`,
  `e18c34a93b`). X257's check read the LSP console once, about 130 ms after the request, while
  LSP4IJ moves its trace there 500 ms after a message; it now waits. X324: LSP4IJ's `DAPStackFrame`
  finds a frame's file with `findFileByPath`, which never refreshes, and keeps the null, so a library
  copy in a folder made after the VFS listed its parent has no source (UP65). The debug process
  handler now refreshes, before a `stackTrace` response goes on, the nearest folder above each
  unknown frame file; with the parent listed first, X324 failed 4 of 6 without it and passed 5 of 5.
  What listed the temporary folder in the full run is not known.
- [x] X312 failed once, in the full run on the 0.30.0 branch (`run-11663425380226624296`,
  2026-10-28): the program stopped at the exception its filter names, but no exception hint showed
  in 30 s. LSP4IJ shows the hint once, and IntelliJ drops an editor hint while the IDE is not the
  active application; the failure's screenshot, and X307's of 2026-10-26, have Safari in front.
  Decided 2026-10-29: the IntelliJ playbook runs in a Linux container on Xvfb, where nothing else
  is in front (`lang/intellij-plugin/container/playbook.sh`, in `testing.md`); X307 passes there 3
  of 3 on 0.21.0. To do: X312 runs in IntelliJ only with LSP4IJ's fixes, so check it there with
  the 0.30.0 branch. Tried on 2026-11-03 on errs with `-Plsp4ijChannel=nightly` and X312 made
  full for the runs: the plugin does not compile against the nightly, whose formatting API takes a
  list of ranges where `XtcFormattingService` (UP12) passes one, which the 0.30.0 bump adapts. So the
  check waits for the bump. Done (2026-11-23): X312 passes in IntelliJ on LSP4IJ 0.30.1 in the container
  (`run-1061357732916096379`), with X308, X311, X314, X317, X318, X326 and X328–X331, which back the tour's "With LSP4IJ
  0.30.1" card.
- [x] The server never deleted its `xtc-library-sources-*` temporary directories
  (`XdkLibrarySources`); 2,068 had piled up by 2026-10-28. A JVM shutdown hook now deletes the
  directory when the server exits (`4cf5450acd`; `LibraryCopiesCleanupTest` in compilerStdioTest).
  A killed server still leaves its directory, as the playbook's does when IDE Starter closes the IDE.
- [x] X283 failed once in a full run (`run-17527198196716738382`) when keystrokes typed in another
  window reached its editor: the test IDE takes the focus to click a Run lens. It passes alone
  (`run-891840089927042241`). Don't type elsewhere during an IntelliJ run.
- [x] The server re-announced every library document a client fetched after each dependency change
  (`ReadOnlyDocuments.refresh()`), though a URI's text can't change, and LSP4IJ fetched them all
  again; in X146 that filled the LSP console. Decided 2026-10-28: no refresh is needed, since a
  library URI carries its revisions; removed (`622d0a506f`).

- [x] Popup inspection race: a popup that disappears between its presence check and its row read,
  keeping the document-change guard and never replaying an accepted edit. The rows are read and a
  row is chosen in one IDE step each, so a closed popup reads as absent and the wait reopens it
  through the guard (`803e8a7af`; `focusRecovery` `run-10337819264986324821`, 31 cases
  `run-8746776659092611335`). Mirrored into #693 as `b7d5d1ad2`.
- [x] Reproduce the full-run IntelliJ X105 intention-popup timeout with its preceding UI state.
  Closed as not reproduced (2026-11-03): in the playbook container X105 passed 3 of 3 alone
  (`run-10494949670890475220`, `run-10319767511418774313`, `run-5892947025783048634`) and in a run of
  X1-X105, 105 cases, all passing (`run-8208552682292234518`), on top of the 21 runs below. It
  hasn't recurred in the 277- and 284-case runs, but it was never diagnosed. Tried on 2026-10-21 with
  the 79 cases that ran before it in `run-8615971651239916856`: X105 passes on the current code, with
  the UP07 equal-report bridge off (`run-1679585523095307991`), and with that bridge off and fixture
  files opened before the graph switch, as before `4065e8e6c` (`run-4408482987482729041`). Not
  reproduced; the trigger is still unknown. The 2026-10-02 run's logs were pruned on 2026-10-06; the
  likeliest cause, an intention request reopened at a moved caret, was fixed on 2026-10-05
  (`84855993e6`, `549a5edd02`), and X105 has passed the 21 runs it took part in since. Reopen it
  with the run's logs if it recurs.
- [x] The opt-in IntelliJ `projectLifecycle` check failed most runs, at "closed project's pending
  reply retires" or at "Close must overlap a pending native request"; it also failed on
  `ef1491a6c`, before the R6–R7 work (`run-15132038242161374573`). Fixed in `3813e27a9`: requests
  still waiting when a connection ends are canceled (LSP4J left them waiting forever, also the
  plugin's own `xtc/*` requests), and the check sends its edits before its requests. It passed five
  runs in a row afterwards, among them `run-13148533765814685610` and `run-9366023975714933137`.

- [x] Two playbook runs at once corrupted the language server's fat jar (2026-10-24): each rebuilt it
  while the other copied it, and the IntelliJ sandbox's `xtc-lsp-server.jar` and the plugin zip kept
  the broken copy ("bad zipfile offset"; the server fails with `NoClassDefFoundError`) as up to date,
  since the jar's fingerprint matched the good one. Fixed in `05b538e55`: the fat jar, every copy of
  it (VS Code's, the IntelliJ sandbox's, `runIde`'s) and the plugin zip's are checked, and one that
  fails is deleted and fails the build, which the next build makes again. Two builds at once can
  still fail, with that message; they do not wait for each other.
- [x] `:lang:intellij-plugin:testCompilerPlaybook` seemed not to run the cases asked for
  (2026-10-24 to 26), a selection running only once it was sent twice. It always ran them. The
  script that read each run's results took the run directory changed last, and
  `pruneCompilerPlaybookReports`, which deletes older runs' payloads after each run, changes those
  directories, so the script read an older run. The run's `results.json` dates it; the pruner ranks
  runs by their `.completed` files, which it leaves alone. `8777c067c` recorded the fault as the
  build's, and `70cdd636f`'s message says its run before selected an earlier list; both were this
  misreading (found 2026-10-26).
- [x] Record both editor tours again from current errs, in the container, for new final videos.
  The ones sent on 2026-11-10, `tour-intellij.mp4` and `tour-vscode.mp4` with their decks
  (`~/shared/xtc-*-tour-draft/final/`), were recorded on 2026-11-06 and 11-07. No tour step changed
  since, but errs' features since then are not in them, such as a library frame's line, and two of
  the IntelliJ video's recordings ran on `lagergren/tour-final` commits that errs does not have
  under those subjects (`91392b946d`, `45470dc5e7`), one of them a scrolling fix.
  Recorded again on 2026-11-11 from errs `f4092dbb61`, in the container, every run used passing all
  its steps and checks: `~/shared/xtc-intellij-tour-2026-11-11/final/` (88 steps, 10:42) and
  `~/shared/xtc-vscode-tour-2026-11-11/final/` (98 steps, 19:35), each with its video, deck, clips,
  frames, evidence and `failures.md`, and the runs in `runs/` beside it; the old finals are kept.
  errs had both `tour-final` commits' changes already, from `02653af02e`. The recording found four
  faults, fixed on errs and #693: the composer crashed on every recorded run and wrote its deck over
  the last image (`701f413a69`); IntelliJ's Debugging clicked "Debug of Current File" while the run
  widget swapped its buttons (`cc8fbcdaf8`); VS Code's tour wrote `launch.json` without `.vscode`
  when Debugging and Build and run were not in the run (`a168397f1d`); and the server's document
  pull left out the module's tests, so VS Code's Testing view, which takes an open document's report
  from its own pulls, never listed a test added to an open file (`3e23e9c139`, with a test in
  `XdkPullDiagnosticsTest`). Only the VS Code run of Settings, Tests and The end has the server fix,
  where "A new test" is. Partial runs skip the protocol section's two checks that every request
  reached the servers.
- [x] Remove `lang/intellij-plugin/tour/__pycache__/compose.cpython-315.pyc`, committed by
  accident, and keep `__pycache__` out of the repository: `6a4684a033` (2026-11-11), which also
  ignores `*.pyc`.

### Beyond the current scope

Only if a real need comes up; each is a documented refusal today.

- [x] Rename: fixtures for every unsupported composition route (Implicit, Union, SansCode, Field,
  Native) before enabling source-owned routes; binary and native contracts stay read-only.
  Done 2026-10-21: adopted interface properties (SansCode) now rename; union-typed property reads
  stay refused (they need per-receiver alternatives); Implicit, Field and Native stay refused.
- [ ] Rename: VS Code edited-file resource history with refactoring auto-save off, instead of the
  current pre-edit refusal. Investigated 2026-10-21, no clean implementation: `isRefactoring` saves
  only when the setting is on, and VS Code moves a dirty file by recreating its model, which drops
  the old model's text Undo (UP62, the existing [microsoft/vscode#17977](https://github.com/microsoft/vscode/issues/17977), commented 2026-10-28). Writing the moved
  file as create plus delete would itself save it. The refusal stays. Corrected 2026-10-28: one
  workspace edit's move and text edits do share one undo group (`BulkEditService`).
  - [x] The refusal only works for Rename. In an Explorer move (`willRenameFiles`) the thrown
    refusal is only logged, so the move goes ahead without the Ecstasy edits; and Explorer moves
    never auto-save, so the setting does not protect them. Done 2026-10-29: a move no longer refuses.
    The user had chosen to save the moved file's own edit before the move, for an Undo in two steps,
    on the premise that Undo would lose that edit. Checked natively first, it does not: VS Code applies
    a move's participant edits with the move as one undo group and restores the moved file's text,
    saved or not, also with the file open and auto-save off. So the move takes every edit and one
    Undo restores them; X335 covers it, and failed with the refusal. Rename keeps its refusal.
  - [x] X103 applies an edit-and-move with a plain `applyEdit` and, after Undo, never checks the
    moved member's text at its old path, which is the documented failure. Done 2026-10-29: VS Code's
    X103 applies the edit as native Rename does, as a refactoring, and checks the member at its old
    path after Undo; it passes (`run-LArbjP`).
- [x] Rename: host discovery of external repositories beyond the explicit `sourceModules`. Done
  2026-10-21: `xtc/discoverConsumers` and **Add consumers from folders** in both editors; discovered
  modules join the graph only through `sourceModules`.
- [x] L63 wider extraction (2026-10-21): bitwise `&` (only `&name` stays refused), locals declared
  with `val`/`var` that never change, and statement runs that declare their own locals or write
  fields.
- [x] L63 mutable captures (2026-10-21): a local changed before the selection is passed by its
  current value (X184 is now positive), and statements that write one local return it to the caller
  (X231 is now positive). This also fixed a hole: a local named with `&` counted as unchanging.
- [x] L63 broader inline: inlining every read of a constant, or inlining and removing a single-use
  member. `XdkRename.Plan` relocates each original range to one place, so N copies need their own
  proof, like member inline's single copy. Done 2026-10-29: **Inline and remove** a private method or
  constant replaces every use by a copy and removes the member, with the single copy's proof made for
  N copies and a removal; X332 and X333 pass in both editors.
- [x] L63 global safe delete: deleting public or protected members needs an explicit closed-world or
  consumer-manifest decision first; the proof itself is not the obstacle. Done 2026-10-29, as the
  user chose: the configured modules count as all consumers, as Rename takes them, and the title
  names them; a module's own methods (entry points), members in a dispatch chain with another, and
  annotated members stay refused. X237 and X334 cover it in both editors.
- [x] Remove the leftover `System.err.println("DECL owner ...")` in `XdkMissingDeclarations.kt`
  (from `b970b7f56`, also on #693); it wrote to stderr whenever a method was not a direct class
  member (`33558e8ad`; mirrored into #693 as `b5015fc13`, where `:lang:lsp-server:test` passes 2,374
  tests with the 2 existing skips).

## 3. Embedded run and debugging

### R5 gaps

- [x] IntelliJ gutter Run icon for `.x` files: document line highlighters on the Run lens lines,
  from a new `xtc/runLenses` request, with no PSI needed; clicking one runs the module as the lens
  does (`53c9d3eede`, IntelliJ X280 `run-8190292223384484870`).
- [x] A shared case with a real Gradle build: X303 builds and runs a project made by `xtc init` or
  the IntelliJ wizard's creator in Gradle mode (`7a28e49bd6`, VS Code `run-2VWzIc`, IntelliJ
  `run-6946269300745727450`). IntelliJ's Build Project in Gradle mode is not driven.
- [x] Renaming a root whose editor is still open: discovery drops the open buffer whose file is gone
  when a file on disk has its text (`5ab3a005fc`, X301 in both editors).
- [x] An IDE move of a discovered root into another directory: allowed when the module uses no
  resources and the destination is inside the discovery folders (`a152acfca3`, X302 in VS Code).
- [x] The name and dependency fallbacks of a renamed root whose header doesn't parse: discovery
  records a file added with the exact text of a removed one, and keeps its last parse
  (`54bfdb7d22`).

### R6: DAP lifecycle on real run sessions

Closed 2026-10-22 for VS Code (`6312fd242`, `ccc0695d5`, `44d121779`; shared X285 in VS Code `run-ZuRYb6`),
and for IntelliJ with R7's fourth step (`69c2ff2f8`).

- [x] Placeholder half: no synthetic breakpoints or threads; launch and attach fail with the reason;
  only the configuration handshake is advertised (`XtcDebugServerTest`, `eba208bb3`).
- [x] Connect DAP `launch` to the run session service: sessions live in the language server, over
  its connection (`xtc/debugStart`, `xtc/debugMessage`, `xtc/debugEnd`), and launch through the
  service `xtc/run` uses (`ccc0695d5`).
- [x] No-debug execution through the same service: a `noDebug` launch takes the same path, and an
  embedded run passes `assert:debug` instead of hanging in the console debugger (`6312fd242`).
- [x] Program output and completion events: `output`, then `exited` and `terminated`; a failed build
  reports its diagnostics and fails the launch.
- [x] `disconnect` and `terminate`: both stop the program, also while it still builds.
- [x] Advertise only the requests that work: `configurationDone` and `terminate`.
- [x] The session owns the program it launched: `terminate`, `disconnect`, ending the session and
  the server's shutdown stop it, and its exit ends the session.

### R7: runtime debugger bridge

Closed 2026-10-26 in both editors, with source revisions (`030f4255e0`). In IntelliJ, the cases
that are partial or not implemented wait on LSP4IJ 0.21.0's limits, UP30–UP42.

The first end-to-end case works in VS Code (`41cb8b60c`, `e73878550`, `bb016ce17`; shared X304 in VS Code
`run-iwSxfQ`): a breakpoint stops the program, the call stack shows, and Continue runs it on. The
three commits are separate [integration bursts](errs-integration-plan.md#r6r7-integration-bursts-2026-10-22).

- [x] A runtime debugger per run, for hosts without a terminal: `SessionDebugger`, set through
  `EmbeddingSupport.runMethod()` before the program starts, with the runner's own code left alone
  (`41cb8b60c`).
- [x] Initial suspension: a launch's `stopOnEntry` stops at the first line of the program's own
  modules (`5a7bf8def`, `48f00ef8c`; shared X309), and IntelliJ's configuration asks for it with its
  checkbox "Stop at the program's first line" (`df6c1a873`).
- [x] Real, verified breakpoints, installed before the program's first line (`41cb8b60c`, `e73878550`).
- [x] Exception stops: every thrown exception, or those no frame will catch, at the frame that throws,
  with filters in both editors, Uncaught Exceptions on by default (`bf0b25af4`, `9d2c2f738`; shared
  X307).
- [x] Fibers and threads: a stop holds every fiber that reaches a line; `threads` lists those that
  wait.
- [x] Stacks: immutable snapshots, with file and line per frame.
- [x] Scopes and variables: a frame's locals, an object's fields and an array's elements, read on
  the waiting fiber's thread (`51cccbfbf`, `05e3ff076`; shared X305).
- [x] Stepping over, into and out of calls (`769d6f34d`, `a78d736f5`; shared X306).
- [x] Pause, at the next line a fiber reaches (`5a7bf8def`, `48f00ef8c`; shared X308 in VS Code).
  IntelliJ's Pause Program waits on LSP4IJ (UP30).
- [x] Watches and hover on variables and paths such as `point.x` or `ints[1]`, answered without
  running code (the console debugger's `WO` and `WR`; `2e4a81205`, `c966674fc`; shared X310).
  IntelliJ's debugger hover is checked by hand only (X310's manual row).
- [x] Bounded evaluation's tier 1: watches, hovers, the console, conditions and logpoint parts on
  expressions with Ecstasy's literals and operators, computed by the runtime's own native operations
  without running code; an operation that would run code is refused, naming the method
  (`fda3dc3e8`, `0540055e9`; shared X313, its logpoint VS Code only, UP32).
- [x] Bounded evaluation's tier 2: calls and properties on the stopped fiber, with the decided
  deadlines, cancellation, side effects announced by `invalidated`, and the opt-in to run the whole
  program (the console's `E`, `EM` and `WE`; doc/errs/plan-bounded-evaluation.md). In five
  steps, R7r to R7v:
  - [x] R7r, the runtime runs code on the stopped fiber (`d77fb9b51`): `SessionDebugger.run()`, the
    fiber waiting again in the same stop, exceptions and compile errors as answers, no stops inside,
    the fibers it calls running, one run at a time.
  - [x] R7s, the runtime's deadline and cancel, and resume and terminate during a run (`72c7bdbbc`):
    `run()` takes the limit, its answer fails at once with the reason, and the code unwinds from every
    line it reaches, also through a `catch`; the stop's clock no longer stays frozen when the program
    is stopped for good during a run.
  - [x] R7t, the server and worker (`68491020b`): a console line that tier 1 refuses runs as code
    for at most two seconds, DAP `cancel` ends it by its request's `seq`, also before it starts, and
    `invalidated` follows once code has run; worker protocol 10.
  - [x] R7u, the editors and shared X314 (`03a422f6c`): VS Code's Debug Console and IntelliJ's Evaluate
    Expression run code; X314 checks a call's effect, the time limit and the exit in both, and cancel
    and `invalidated` in VS Code, as LSP4IJ has neither (UP40, UP41).
  - [x] R7v, the `evaluationRunsProgram` opt-in (`70cdd636f`): a launch option, a checkbox in IntelliJ's
    configuration, that lets the program's other fibers run, without stopping, while console code does,
    and wait again at their next line; `invalidated` is then for all areas. Shared X315.
- [x] Source maps tied to source revisions (`f53883083`): a debugged run keeps the text each file was
  built from, and once a file is edited, saved or not, a breakpoint is placed on the line it was
  built with, matched line by line with whitespace at a line's ends ignored, and a frame shows on its
  line now. A breakpoint on a line changed since is unverified with the reason. Shared X321, full in
  IntelliJ.
- [x] Terminating a stopped program ends it where it is (`41cb8b60c`).
- [x] Tests: source revisions, with the source maps above (`f53883083`: `CompiledSourcesTest`, and an
  `EmbeddedExecutionTest` case that edits a debugged file and fails without the mapping).
- [x] Tests: stale handles, reads and evaluations asked for as the program resumes, and multiple
  service contexts (`550a4c01b`, four `SessionDebuggerTest` cases). An evaluation that the resume
  abandoned failed without a reason, a `NoSuchElementException`; it now says the program resumed first.
- [x] A resume/stop race: `SessionDebuggerTest.whileStoppedAFiberOfAnotherServiceWaitsAtItsNextLine`
  hung once (2026-10-23) in `Control.close()`, in the runner's `killTask`. The cause was the runtime's:
  a service with a fiber that ended while a call it made was still out failed `terminate()`'s
  assertion, with assertions on, and so never completed its shutdown, and `Container.kill()` waited
  forever (`ab9fbddeb`, with `EmbeddingSupportTest`'s deterministic case). Tests that close with a time
  limit no longer do it in try-with-resources, whose second close waited without one.
- [x] A closed run left its service fibers running: a run ended when `run()` returned, and the runner
  then dropped its container, so a spinner, or a server that `run()` started, ran on in the worker,
  where Stop could not reach it. Seen once (2026-10-23): `pauseStopsARunningProgramAtTheNextLineItReaches`
  paused in another test's spinner. Now, as with `xtc run` (decided 2026-10-23), a run lasts until its
  services have nothing left to do, and its container is then killed (`1473c7e89`;
  `EmbeddingSupportTest`'s `nothingOfARunStillRunsOnceItIsClosed` and
  `aRunEndsWhenItsServicesHaveNothingLeftToDo`).
- [x] IntelliJ debug sessions (R7 step 4): LSP4IJ's DAP client, chosen 2026-10-22, through a process
  handler that carries the session over the language server connection (`69c2ff2f8`; X304 in
  IntelliJ `run-6846671860304618586`). LSP4IJ's DAP API is experimental, which Plugin Verifier reports
  without failing the build.
- [ ] Closing a run hung once in CI after a stop that held a spinning service (2026-11-23, #693 at
  `89870a4272`, run 45245467896): `SessionDebuggerTest`'s
  `anotherServicesWaitingFiberHasItsVariablesAndStandsStillAndAStepEndsInTheSteppedFiber` failed at
  `Await.run(control::close)`, after a step over and `resume()`, with the spinner meant to spin on. The
  whole javatools test task took 3 minutes, so the watchdog did not find a livelock, which would have
  taken 10 minutes: it found a deadlock, or a minute in which nothing used CPU, so the spinner no longer
  spun while the close waited. The stop's wait (`waitWhile`) and `release()` both run under the
  debugger's monitor and check `halt` again, so it is not a lost wakeup there. Not reproduced: alone 10
  times, with every core busy 20 times, and the javatools suite with the language server's in parallel,
  as CI runs them, 3 times, all passed. The CI run kept no javatools results, as its diagnostics step
  copies none (nor on master), so the watchdog's verdict and thread dumps were lost. Since 2026-11-23
  CI keeps them (`a2e343cdc7`, on master as #800): the watchdog's failure message, in the test's JUnit
  XML, holds every thread's dump, so the next failure shows where the close waits. Not seen again by
  2026-11-25: #693's runs since have passed or were cancelled, and run 45409340006 (2026-11-24) failed
  on another test, the runner's frame in an exception's stack, fixed (recorded below). To do:
  read the dumps the next time it fails. Its other failure (2026-10-28, `errs-integration-plan.md`) was
  a different line, a stop before `count` existed.

### R9–R13: debugger features beyond R7

Planned in [the execution plan](plan-embedded-execution.md#additive-delivery-and-acceptance),
after the R8 gate unless one is chosen to gate the release. The console debugger's commands they
come from are in parentheses.

Editor batch (2026-10-27), for X280–X330 after `819e2ac754`: VS Code passes all of them
(`run-OFR39r`, X326 in `run-NGgnBV` once `modules` took no arguments), and IntelliJ passes its 44
implemented cases among them with released LSP4IJ 0.21.0 (`run-15774751429931474506`), 13 at their
recorded partial coverage.

  Looked at again 2026-11-27 (`d962a75405`): not reproduced, nor its cause found. The test repeated 150 times with 8
  CPU burners, and SessionDebuggerTest's 57 tests 6 times with 10 burners and the XVM pool pinned to 2 and 4 threads
  (CI's 4 vCPUs give it 4), all passed; a probe after each test found the native container's time unfrozen, the clock's
  timer alive and the XVM workers idle. Ruled out by reading: a lock-order inversion between the debugger's monitor and
  the runtime's, a stop made after `terminate()`, the runner's `endWhenIdle` alarm held by frozen time, console reads
  holding XVM threads (they run on IO threads), call-laters run inline on the timer thread (they are async). A passing
  CI run's javatools tests take 9.5 minutes; the failing one ended at the failure, 3 minutes in, since CI tests fail
  fast (`org.xtclang.java.test.failFast=true`), so the close watchdog's fibers print, after a minute, could never be
  kept. It prints at half the watchdog's idle window now, 30 s; a probe with a deliberately stuck close showed the
  fibers at 30 s and the watchdog's failure with every thread's dump at 60 s. To do: read both the next time it fails.
- [x] R9: conditional breakpoints (`BC`), hit counts and logpoints (`5b2686a2e`, `edeab8f5b`; shared
  X311). Conditions are `Boolean` expressions since R7's tier 1 (X313). IntelliJ checks the condition;
  its logpoints: UP32; its hit counts: UP55.
- [x] R9: the call stack and `exceptionInfo`'s stack trace without the embedding runner's frames
  (`TaskRegistry`, `runner.x`), seen in IntelliJ's hint with #1761; the call stack had them too, in both
  editors, under the program's (`ad8268260`, `48dabb5a5`; X304 and X307 check the whole stack).
- [x] R9 gap, a flake (found 2026-11-24 in K5a's validation, fixed the same day, `bb85345009`): rarely, the call stack still ended
  with the runner's `Container.invoke` at line 0, below the program's root frame.
  `EmbeddedExecutionTest`'s "a breakpoint stops a debugged program…" failed so once in three full
  runs, as `[(Stops.twice, 11), (Stops.run, 5), (Container.invoke, 0)]`. Cause: when the program
  stops, `Frame.getStackFrameArray` crosses to the fiber that called the program, the runner's, and
  finds the calling frame only if that fiber is `Waiting`. If it is `Running` at that instant, the
  calling frame is not found, and the entry has only the calling method, `Container.invoke`, with no
  frame. `SessionDebugger.isHost(StackFrame)` then judges the entry by its method's module, which is
  `ecstasy.xtclang.org`, not the runner's, so the host's frame shows. A temporary probe, 200 debug
  launches of `Stops` on one worker with the machine's ten cores kept busy, reproduced it 2 of 200
  times, and its diagnostic showed the runner's fiber `Running` each time. It predates K5a: the same
  probe on `origin/lagergren/errs` (`fac195cb82`, without K5a) also gave 2 of 200, with no
  evaluation or time limit involved. Without the load it gave 0 of 60, and 0 of 12 right after a
  console run that its time limit ended. Fix to make: judge a fiber boundary by the calling fiber's
  container, not the method's module. Hold the chain of calling fibers (`traceCaller`) before
  `getStackFrameArray`, since the calls hold them only weakly, and end the program's stack at the
  first fiber of the host's container; `frames`, `stackTraceOf` and `isCaught` all need it.
  It failed #693's CI too (run 45409340006 at `bdde618797`), as the runner's frame in
  `SessionDebuggerTest`'s exception stack trace. Fixed so: `SessionDebugger.programStack` holds the
  calling fibers, and `beforeHost` ends the stack at the first entry of a fiber in the host's
  container, whatever the entry knows of its frame; `frames`, `stackTraceOf` and `isCaught` read
  it, and `isHost(StackFrame)`, the module test, is gone. `Frame.getStackFrameArray` is unchanged.
  `ProgramStackTest` checks the decision on a stack whose caller entry is the frameless
  `Container.invoke`, as no hook holds the runner's fiber `Running` at the stop. The probe with all
  cores busy, 400 stops each: 2 ended with the runner's frame before the fix, on `5fb46b1697`, and 0
  after.
- [x] R9: function breakpoints, which stop when a method is entered (2026-10-27; shared X328). One
  names a method, alone or after its class's or module's, as the call stack names it, such as
  `twice` or `Point.move`, and stops at the first line of each of the program's methods of that
  name, not the libraries', with the reason `function breakpoint` and a condition and hit count as
  a line's has. It is installed at the first such method's line, or says that no method has the
  name, also when set before the program starts (worker protocol 20). VS Code adds them in its
  Breakpoints view; IntelliJ has none (UP46), so X328 is not implemented there.
- [x] R9: exception breakpoints by type (`BE+ <exception>`), and `exceptionInfo` for the editors'
  exception views (`5b2686a2e`, `edeab8f5b`; shared X312 in VS Code). IntelliJ: UP34 (filter
  conditions) and UP33 (`exceptionInfo`).
- [x] R9: data breakpoints, which stop when a property or field changes (2026-10-27; shared X330).
  `dataBreakpointInfo` names a field of a mutable object in the Variables view; a local, an
  element, a computed property or an immutable object's field cannot be watched. Once the field's
  value has changed, by what its variable shows, the program stops with the reason `data
  breakpoint` at the next line that a fiber of the object's service reaches, and says from what to
  what, such as `Counter.count changed from 0 to 2`. The check runs at each line of that service,
  without a hook in the runtime's writes, and only writes are offered (worker protocol 22). VS Code
  adds them with Break on Value Change; IntelliJ has none (UP48), so X330 is not implemented there.
- [x] R10: value formats (`V`) and identity codes (`DI`) (`cb551d4c2`, R10b): integers in hexadecimal when
  a request's DAP `format` asks, or the session's own `xtc/valueFormat`, which VS Code's Variables and
  Watch views' Show Values as Hexadecimal sets, since VS Code never sends a format (UP38); a mutable
  object's value ends with its identity code (worker protocol 13). Shared X317; IntelliJ's hexadecimal
  toggle is UP36 (#1769). An unsigned 64-bit value of 2^63 or more no longer shows as negative.
- [x] R10: a value's `toString()` on demand (`DS`), through R7's evaluation, and computed properties
  as lazy variables (`1d88e086d`, R10a): an object's fields are followed by its public computed
  properties and its `toString()`, `(...)`, whose reads run the getter or `toString()` on the stopped
  fiber for at most a second (worker protocol 12, `presentationHint.lazy`). Shared X316; IntelliJ
  clicks LSP4IJ's Show value link (UP35, #1768), or with 0.21.0 expands the variable to its value.
- [x] R10: paging of large arrays, in place of the first 100 elements (`00dee9671`, R10c): an array's
  variable has `indexedVariables`, and a `variables` request's `filter`, `start` and `count` reach the
  program, which reads only that part (worker protocol 14); VS Code shows ranges of 100. Shared X318;
  IntelliJ with LSP4IJ 0.21.0 shows the first 100 and how many more, and pages through IntelliJ's own
  `...` node once LSP4IJ releases #1770 (UP42). Other
  collections, such as a `Map`, show their fields: their entries would take running their code.
- [x] R10: changing a variable's value while stopped (`setVariable`) (`3a060bd39`, R10d): a local, a
  field or an array element takes the value of an expression that a watch evaluates, a literal of its
  type, so no code runs; a value of another type, `this`, a field of an immutable object, a computed
  property and what a `Ref` holds are refused with the reason (worker protocol 15). Shared X319, full
  in IntelliJ through LSP4IJ's own Set Value.
- [x] R10: completions in the Debug Console (`8178cd03d`, R10e): the name typed at the cursor
  completes to the frame's variables, `this` and its members, its private ones too, or, after a
  dot, to the public fields, properties and methods of the value before it, evaluated as a watch is,
  so no code runs (worker protocol 16). Items have no `start` (UP43). Shared X320, partial in
  IntelliJ: with 0.21.0, editing Evaluate Expression's expression fails IntelliJ's Document/PSI
  check (UP37), so it checks the names offered; with #1763 it also checks the text a chosen one
  makes.
- [x] R11: Run to Cursor in both editors (`SL`) (2026-10-27; shared X323). Both editors add a
  temporary breakpoint and continue, which the server already handled: `EmbeddedExecutionTest` sends
  that sequence, on a line in a loop, and the program stops there once and then runs to its exit.
  VS Code passes X323, whose program counts to 3,000,000 before the cursor's line. IntelliJ's was
  partial: LSP4IJ removes the temporary breakpoint once the continue request is answered, so the
  program ran past the cursor (UP44); full since 2026-11-06, with the plugin's workaround.
- [x] R11: choosing which call on a line to step into (`stepInTargets`) (2026-10-27; shared X327).
  `SessionDebugger.stepInTargets` lists the calls that the line where a fiber waits makes, from its
  ops, in the order of its code, each named by what it calls (`twice(Int)`, `new Point(Int, Int)`);
  `stepInto` steps into one: the step ends at the first line that code reaches while its frame makes
  that call, or at the next line if the line does not make it, and falls back to a plain step into
  if the frame returns first. `OpInvocable.getInvokedMethod` and `OpCallable.getCalledMethod` name a
  call's target; a call of a function that a variable holds is not offered. The worker answers
  `stepInTargets` and `stepInto` (protocol 19), and the adapter `stepInTargets` and `stepIn` with a
  `targetId`. VS Code offers it as Step Into Target, IntelliJ as Smart Step Into, which LSP4IJ 0.21.0
  sends as `stepInTargets`.
- [x] R11: restarting a frame (`T`) (2026-10-27; shared X329). The fiber leaves the frame and those
  above it, without running more of their code or their `finally` blocks, and the frame's caller
  makes the call again, with the values its arguments hold now, as the console's `T` does through
  the runtime's `R_RESET`; the program stops at the call's first line with the reason `restart`.
  A frame says whether it can (`canRestart`): not when its caller is native, the host's or a run's,
  nor when it or a frame above it is native or has a continuation, nor at an exception's stop
  (worker protocol 21). VS Code offers it in the Call Stack view. IntelliJ offers Reset Frame through
  our LSP4IJ #1778 (UP47, merged 2026-10-27, unreleased): X329 passes there with a plugin built from
  it, and stays not implemented in the catalog until a release has it.
- [x] R11: continuing, stepping or pausing one fiber while the others stay stopped (2026-11-02, worker
  protocol 24). With DAP's `singleThread` on `continue`, `next`, `stepIn` and `stepOut`,
  one fiber continues, steps or is paused alone while the program's other fibers stay stopped, with
  the fibers of other services it calls; when it stops again, at a breakpoint, an exception stop,
  its step's end or a pause, that is the program's stop again, with the same waiting fibers, and if
  it ends first, the program stops at one of them with the reason `pause`. `SessionDebugger`
  holds the others in the same stop, as while a fiber runs code in it; the clocks stand still. The
  worker and the agent share it through `RunDebuggers` and `RunDebugClient`, so embedded runs and
  Gradle-launched or attached programs take it alike. Neither editor sends `singleThread` yet (UP49),
  so it is checked at the protocol level: `SessionDebuggerTest`, `XtcDebugServerTest`, and over a
  real worker (`EmbeddedExecutionTest`) and a real agent (`XtcDebugAgentTest`); no catalog case.
- [x] R12: every service and fiber in Threads, with their containers (`VF`) (2026-10-27; shared
  X325). At a stop, `threads` lists the fibers that wait at a line first, as before, then the other
  fibers of the program's services, in its own container and those nested in it, in the order the
  services were made: each named by its service, after its container's module if that is not the
  stopped fiber's, with what it does (waiting, paused, not started, running, as in a native call, or
  ending). A fiber that waits otherwise than at a line, is paused or ends has its stack, without
  variables; one that runs has none, since its frames change as they would be read. The runtime
  gains `ServiceContext.getContainer()`, `getName()` and `getId()`, `Fiber.getContext()` and
  `Container.getParent()` for it. Found: listing every container of the runtime took in the other
  programs it runs, as the tests' shared interpreter does; only the stopped program's count now.
  Not done: thread events. A fiber starts for every call to a service, so an event for each would
  cost every call of a debugged program, and both editors ask for the threads at each stop. Known
  limit: the caller of a synchronous call to an idle service shows as running, without a stack, for
  as long as the call takes: the runtime marks a fiber waiting only once it blocks.
- [x] R12: frames whose source file is not on the machine, such as library code (2026-10-27; shared
  X324). A frame of a bundled XDK library's code is in the library's bundled source, at its line:
  the read-only copy that navigation opens too, which is the source the library's code was compiled
  from. The library records the source directory it was built in, which a user's machine does not
  have. Each frame carries its module and its source path in the module (worker protocol 17). Found
  and fixed: a frame of any other module's code had the program's source directory with the class's
  source path, a file that does not exist, since the debugger took the source directory from the
  program's module rather than the class's own. Not through the `source` request: a file, the one
  navigation opens, also gives the editors their language features there. VS Code and IntelliJ
  pass X324.
- [x] R12: the loaded modules (`modules`, `loadedSources`) (2026-10-27; shared X326). The debugger
  lists the program's modules as the runtime linked them: its own, which `addModule` made known,
  and the libraries it uses, each with its version, its recorded source directory and its classes'
  source paths, read once per module (worker protocol 18). `modules` answers them a page at a time,
  `isUserCode` for the program's own; `loadedSources` answers their files, a bundled library's in
  its bundled source, as its frames are, which `XdkLibrarySources` now writes without parsing its
  declarations, since a library has hundreds. VS Code shows them in Loaded Scripts; it has no
  Modules view (UP39). IntelliJ has neither (UP45), so X326 is not implemented there.
- [x] R13: Debug in Gradle mode (2026-11-02, `ec33120824` to `596e6bfb66`, validated with
  `a23adf884b` to `ce1bc35784`; shared X337). A Gradle-mode Debug runs the module's Gradle task with
  `xtcDebug`, and the program connects to the session. The validation fixed compileXtc's build
  cache, which gave a project in another directory a module compiled in the first, and IntelliJ's
  server stop when the last Ecstasy file closes during a run or session. Limits: VS Code shows the
  program's output in the task's terminal; LSP4IJ 0.21.0 still stops the server for a run started
  within 5 s of closing the last file (UP66, fixed by #1835, merged, unreleased).
- [x] R13: Debug in command-line mode, which runs `xtc run` (2026-11-03; shared X338). IntelliJ's
  Debug of a configuration that runs with **xtc command line** has `xtc run --deduce [--method <m>]
  --debug connect:…,suspend,token=… <module> [args]` run in the project directory (with `agent=<jar>`
  until the agent moved into javatools, below), the
  command line its Run has too, which now passes the method and `--deduce`, and the program connects
  back, as a Gradle-mode Debug's does: `xtc/debugTask` carries the launch's `buildMode`, and
  `DebugTask` names the task in the session's messages. VS Code, which has no command-line mode,
  refuses such a task, saying why. X338 passes in both editors in the playbook container (IntelliJ
  `run-3269813027182721183`, with X337; X336 too in `run-14756361706886847883`). Limits: the `xtc` on
  the `PATH` runs it, whose XDK must have this javatools; `--deduce` finds a module in `src/main/x`
  only when the project directory has no `.x` files of its own (javatools' `ModuleInfo`); fixed
  since (2026-11-03): deduction also looks for the named module's file in the project's
  conventional source directories, past a directory with other sources.
- [x] R13: running and debugging tests from the editors (2026-11-03). The language server finds each
  module's xunit tests (`xtc/tests`, `XdkTests`), and runs them, or debugs them over a connect-back,
  with `xtc test` in a process of its own (`TestProcess`; `xtc/run` and a DAP launch with `tests`);
  xunit reports each test as an event of its own `json` report (`-I xvm.xunit.report=json`), which
  the language server reads once and sends apart from the output (`xtc/testEvent`, the DAP event
  `xtcTest`), and IntelliJ's test console reads through `XtcTestEventsConverter`; a test's location
  is `xunit://<selector>`, its selector in xunit's model. VS Code's Test Explorer and IntelliJ's
  gutter icons and test console run and debug them, embedded, with Gradle's `testXtc`, which now
  takes `--test-class`, `--test-method`, `--test-package` and `--test-report`, and in IntelliJ with
  `xtc test`. Commits
  `649d5c53f2` (xunit's `-c`/`-t` selected an implicitly imported class of the same name, and a
  selection of nothing ran every test), `adc70bfce7`, `6e5dd8df3f`, `f3c77b501a`, `1223203701`,
  `281a59908d`, `12eb77c39d`, `5ac6ddab35`, `ceabc7d110`, `9398dc0eb2`, and (2026-11-04, so that no
  layer speaks a vendor's format) `80a7abfc02` (a test's selector in xunit's model, an unknown report
  refused) and `712d23d14d` (xunit's JSON events replace TeamCity's service messages); shared X339
  and X340, which pass in both editors in the container (IntelliJ partial: a failure's stack is the
  console's text). VS Code has no command-line mode.
- [x] R13: attaching to a program run outside the editor (2026-10-29, `0637f0d281`; shared X336).
  `xec`, `xtc run` and `xtc test` take `--debug listen|connect:<host>:<port>[,suspend][,token=<t>]
  [,agent=<jar>]`, through javatools' new `DebuggerProvider`; the language server's jar was the
  agent, `XtcDebugAgent`, on loopback only, until the agent moved into javatools (below). The worker and the agent share `RunDebuggers`, the
  worker client and `AttachedProgram` share `RunDebugClient` (protocol 23). VS Code's attach
  configuration, snippet and Copy Option to Debug a Program Outside the Editor; IntelliJ's Ecstasy
  Attach configuration (UP64). Both take stop at the first line, for a program under `suspend`.
  Detaching drops the debugger's breakpoints, and the program runs on. Limits: the program's XDK
  must have this javatools; its console stays its own; one debugger at a time.
  Design for all three (2026-10-27) in
  [the execution plan's R13](plan-embedded-execution.md#additive-delivery-and-acceptance):
  a debug agent in the program's JVM, which `Runner` and `TestRunner` install through a new
  `DebuggerProvider` SPI for `--debug`, speaks the worker protocol's debug commands over a socket
  to the language server; Gradle-mode and command-line Debug connect back, `xec` listens for an
  attach. Running and debugging tests came after, in the entry above (2026-11-03).
- [x] R13: the debugger agent in the XDK (2026-11-03). The agent and the program's side of the
  debug protocol moved from the language server's Kotlin (`XtcDebugAgent`, `RunDebuggers`,
  `RunWorkerDebug`) to javatools' `org.xvm.runtime.debug`, next to `SessionDebugger`, so that
  `--debug` needs no `agent=`: `DebugAgent` is the provider, registered in javatools' service file,
  and `DebugCommands` answers the worker's and the agent's debug commands alike. The protocol is
  defined once, as Java records in `DebugProtocol` that both ends serialize with Gson; the language
  server builds the request records and maps their answers to the debug adapter's data classes
  (`DebugRecords.kt`), so no command, event or field name is written in both languages. Protocol 25;
  the server passes no `agent=`, and `Runner` prefers a provider in an `agent=<jar>` over the class
  path's. Validated in the playbook container: VS Code X285, X304–X331 and X336–X338, and IntelliJ's
  24 implemented debugger cases, pass or keep their recorded partial coverage; X325 (VS Code) and
  X337 (IntelliJ) failed once each on timing, a fiber of the next call caught before it starts and
  the task's last output after the program's exit, and passed twice each when run again. The
  editors' Copy Option (VS Code's command, IntelliJ's Attach Port comment) and X336 now give
  `--debug listen:127.0.0.1:5005,suspend` without `agent=`, and `xtc/debugTask` has no `agent`.
  Fixed after the rebase's CI (2026-11-04, `fc302daaff`, #693 `c4f916bf44`): with javatools' own
  provider registered, `DebugOptionTest`'s program without one ran instead of being refused, and
  `Runner` chose an `agent=<jar>`'s provider by its class's loader, which a jar of only a service
  file never matched; it now takes the providers that the jar's service file names, and without
  `agent=` javatools' own. With it, `a145de1074` (#693 `0e95701f79`): the embedded execution
  tests' worker lifts the lazy-value limit, which CI's runner went past.

Not planned: reverse execution, disassembly, instruction breakpoints, memory access and
`runInTerminal`; the console debugger's own terminal views, command history, repeated steps and
breakpoint list, which the editors replace.

### R8: runtime release gate

- [x] Repeated compile/run/stop/debug soak, with memory and process checks, on macOS and Linux
  (2026-10-27).
  *Soak (2026-10-26, `0d6e890bad`):* `EmbeddedExecutionTest`'s soak repeats rounds of four runs
  on one worker: an edited module built and run to its exit, a run stopped while it runs, a debug
  session continued from a breakpoint after a watch and a canceled console run, and one
  terminated while console code runs. One worker serves them all and keeps no run, console,
  debugger, console code, session or compiled sources; the runtime's pool keeps its size, and
  closing ends the worker process. The suite runs five rounds, `-Plsp.soakRounds` more; 50 passed
  three times on macOS. It found that the worker counted a JDK thread started from a pool thread,
  the common pool's delay scheduler, as runtime pool growth, which retired a healthy worker; the
  worker now reports the pool's own size. Memory is checked by counts only. The worker no longer
  calls `System.gc()` (`2668896b26`): it reports the heap the JVM left after its own latest
  collections, and the server keeps its 512 MB limit on that but dropped the 128 MB growth rule,
  since two such figures say nothing about what runs retain. The tests that waited for the
  collector walk the object graph instead (`77e3c4b7c5`).
  *Editors (2026-10-26, `718ddd656e`):* shared X322 repeats runs, stopped runs and debug sessions of
  one module from each editor; each ends, and afterwards no run or debug session is open. It
  passes in VS Code and IntelliJ.
  *Linux (2026-10-27, `6b8f5bbe03`):* arm64 Linux with JDK 25, in a Podman container on the Mac, as
  a user other than root, with one test fork. `javatools`' 686 tests pass (42 skipped). With
  `--init` on a quiet host, all 2401 `lsp-server` tests pass but the 2 that are always skipped,
  with the soak at 50 rounds (56 s), in 34 minutes. The runs found:
  - As root, the tests of files without write permission failed, since root writes them anyway;
    they skip where such a file stays writable (`e4a8e6847e`).
  - When Gradle was the container's PID 1, which does not reap orphans, the worker of a killed
    server exited but stayed a zombie, so "a worker exits when the language server process dies"
    timed out waiting for it to end. With an init process that reaps (`podman run --init`) it
    passes. The test now kills a worker that outlives its server and shows what the
    worker was doing (`6b8f5bbe03`), and a worker whose input ends stops its runs on threads of
    their own and exits within 5 s even if a program does not stop (`c474566892`).
  - With the Mac's load average at 37–75 on 10 cores, `EmbeddedExecutionTest` took 667 s, against
    50 s on a quiet host, and time limits failed: `XdkOnTypeFormattingTest`'s 20 ms (32 ms), and the runtime's 1 s limit on
    a lazy `toString()` (`SessionDebugger.ON_DEMAND`) in the console test. That test then left its
    program stopped on the class's shared worker, so three later tests failed with it. Alone, and
    with its class on a quiet host, the console test passes.
- Not planned (decided 2026-10-27): the soak on Windows. CI runs the `lang` checks on
  `ubuntu-latest` only: a dispatch can add `windows-latest`, which builds without them
  (`commit.yml`). There is no Windows machine to run them on.
- [x] A JVM that makes more than one runtime kept up to two that it was done with, about 115 MB each
  (`474af84f08`, 2026-10-26). Three things held one:
  - A pending alarm on the JVM-wide Java timer. The `Timer` service belongs to the native
    container, so `xNanosTimer.TimerHandle.Alarm` kept the runtime until the alarm was due.
  - 27 values that 13 templates cached in statics on first use, such as
    `xRTSignature.PARAM_ARRAY`, from the first runtime, which every later runtime used too.
  - `FullyBoundHandle.NO_OP`, made from the first runtime when its class was loaded.

  `EmbeddingSupport.shutdownInterpreter()` (for tests) shuts a connector down: its timers' alarms
  are canceled and its executors end. The cached values are fields of each runtime's templates,
  and `NO_OP` is made by each runtime; the embedding and debugger tests shut their interpreter down.
  `RuntimeRetentionTest` checks both without waiting for the collector. The `javatools` test JVM now
  holds one runtime at a time: its peak live heap is down from 275 to 226 MB, and from 242 to 193 MB
  at the end. Kept: the about 130 statics a template sets when it is made, such as its `INSTANCE`,
  still hold the newest runtime until another is made; the servers make one runtime per process.
- [x] Parity between IntelliJ and VS Code (2026-10-27, at `718ddd656e`). VS Code passes all 34 run
  and debug cases, X280–X285, X287–X289, X291–X294, X301 and X303–X322. IntelliJ runs 32 of them,
  all but X308 and X312, which it does not implement: 21 pass and 11 are partial, each with its
  recorded limit. X287, X288 and X303 are partial because of the test driver; X311, X313–X318 and
  X320 because of LSP4IJ 0.21.0 (UP32–UP42). X308 and X312 wait on LSP4IJ fixes (UP30, UP33,
  UP34) that are merged on its main branch but not released.
- [x] Logs that correlate build ID, queue job, artifact revision, worker PID and generation, and run
  ID (`a87b63d9d`, 2026-10-26). A build's ID is its compiler queue job's, which the trace gives as
  `id`; the build line gives each module with the start of its artifact's revision. The run's
  start names its build and artifact, and the worker's lines name the run, the worker's PID and
  its generation, which counts the workers the server started; `lsp-server/README.md` says how to
  follow a run. `EmbeddedExecutionTest` checks one run's lines against each other and the trace.
  Kept: the editors' own logs do not give these IDs.
- [x] Focused Gradle test tasks (`e91b5e58cb`, `30d9685ff3`, 2026-10-26): `graphQueryTest`,
  `emissionTest`, `dependencyHostTest`, `sourceGraphHostTest`, `embeddingConsumerTest` and
  `:javatools:errorListenerTest` each run what a doc's command used to list with `--tests`.
- [x] Playbook and capability docs brought up to date (`3ac199f192`, 2026-10-26): R6 and R7 are
  complete in both editors, the debug server's capabilities are listed, the catalog is given as
  324 cases, X1–X322, and X308's and X312's IntelliJ limits name the LSP4IJ fixes.
- [x] Each runtime PR validated on its own (2026-10-27). #716, #717, #718, #724, #731 and #693, each
  rebased onto master `ddaa3d0a4`, pass CI on their own heads. #730's fix is now part of #724,
  and #730 is closed.
- Known limit kept: JIT runs are unsupported.

### Debug adapter: failures that reach the editor as "Internal error."

- [x] Found 2026-11-24 while making the evaluation limits settings (K5a): `XtcDebugServer`'s `stackTrace`,
  `scopes` and `exceptionInfo` pass a failure on as it is, so the editor shows only "Internal error."
  instead of why. `evaluate` maps its failures to their reason, and since K5a so does `variables`, whose
  lazy value that timed out or threw did the same. Map these three's failures the same way, with a test
  each that a failing request answers with its reason. Small, under half a day.
  Done 2026-11-24: the four requests answer through one helper, `answered`, which keeps a cancellation and
  an error response as they are; `XtcDebugServerTest` checks the three with a run whose state cannot be read.

## 4. Upstream fixes

### LSP4IJ: released in 0.30.1

LSP4IJ released 0.30.0 and then 0.30.1, the same code, on 2026-11-19, with every fix this section
waited for. XVM pins 0.30.1 (`gradle/libs.versions.toml`, `lang-intellij-lsp4ij`) since `b449d8a746`,
and the 0.30.0 branch's commits are on errs, rebased with the subjects the items below name. The
full IntelliJ playbook on 0.30.1 passed every case: 342 passed and 9 partial, the 8 partial on 0.21.0
too and X350 ([#741](https://github.com/xtclang/xvm/issues/741)). Rechecked on 2026-11-23: each item
says which errs commit took the release, or why it stays open. Kept since the 0.30.0 branch: UP03's
guarded entry points, UP12, UP13 and UP21's `ClosedRefactoringDocuments`; UP14, kept with them, is
removed (`f012284915`), and UP04's rename guard turned out to be XVM's own rename (`467730297d`). Their removal gates ran on 0.30.1 on 2026-11-23, and each item
says what they showed: UP03 waits for a release with the fix of UP84 (#2135), UP12 for
IntelliJ's UP24, and UP07's waits for a baseline. UP13 is LSP4IJ's extension point. What waits for 0.30.2 is in #741's to-do list. The gates are in
[the register](errs-upstream-issues.md#register).

- [x] Watch for the next LSP4IJ release and bump the pin.
  **On 0.30.1:** released on 2026-11-19, and the pin is 0.30.1 since `b449d8a746`.
- [x] Checked against LSP4IJ's nightly 0.21.1-20261027-160212 (`main` `0d3c64de`, 2026-10-27), with
  `-Plsp4ijChannel=nightly` (`4ca6f5993d`): IntelliJ's X308 (UP30), X312 (UP33, UP34), X326 (UP45),
  X328 (UP46) and X329 (UP47) pass, made full for the runs; X308, X312 and X326 are new
  (`09ffee515b`). The UP25 and UP26 replacement gates pass. What the nightly changes, and the playbook
  now takes: library sources open as the server's read-only documents without files (#1749;
  `8d845d167e`, `3b7c450765`); the console loses X146's versions to their traffic, now read from
  LSP4IJ's synchronizer (`ddcc6fda9d`); a restart reopens documents with new versions (`b499c339fb`).
  A full run's IDE was killed by macOS in IDE Starter's thread dump of every minute; the playbook
  takes none on macOS (`ff06c86d0b`). A full run stopped in turn at X74, X150 and X285 (the IDE
  crash); X74 and X150 pass alone. Before the bump: pass a full run.
- [x] Full IntelliJ run on the nightly 0.21.1-20261028-080211 (built from `7469aab9`, with UP48 and
  UP50), 2026-10-28, at `c2a9b296d5` with the playbook fixes before it (`run-793652569316991787`):
  317 passed, 13 partial, 1 not implemented, 3 failed. X308, X312, X326 and X328-X331 pass, made
  full for the run, and so do X259, X74 and X150. The UP25 gate passes. Failed: X323, as its UP44
  gate does once #1773 is in; X257 (the UP26 gate) and X324, which fail only in the full run (see
  [test harness stability](#test-harness-stability)). No IDE exception.
- [x] The bump is prepared on the local branch `lagergren/lsp4ij-0.30.0`: 35 commits, made on errs
  `4e3b1b65e7` (tip `a67965ede2`, 2026-10-28) and rebased on 2026-10-29 onto that day's errs history,
  at its record of the #742 rebase (tip `9cb776697c`). Rebased again on 2026-11-11 onto errs
  `f4092dbb61` (master `856fa585e4`), as `lagergren/lsp4ij-0.30.0-rebased-20261111` (`e98f38a07c`), 34
  commits; the original branch stays at `9cb776697c` until the user takes the new one, with
  `lagergren/lsp4ij-0.30.0-pre-rebase-20261111` as its backup. UP44's commit (`ce8867e8da`) is
  dropped, as errs has its effect; `4b4cd424dc` keeps its X254 half, errs having X257's. UP10 keeps
  `XtcParameterInfoHandler` for UP73 and removes `updateUI`; UP07 keeps `DiagnosticResultMessages` as
  the playbook's report record and removes the `previousResultId` part; UP09 removes
  `DocumentStartupMessages` with `50ee490ff8`'s guard against a retired synchronizer's late change,
  which LSP4IJ #1728 and #1718 make unneeded (gate: X259's last step on the release); the API list
  keeps `setServerWrapper` for UP70. Built against a plugin from LSP4IJ `main` `0daf60cf`
  (`-Plsp4ijPlugin`): every commit compiles on the first base; at the tip, `spotlessCheck` and the
  compiles pass, the plugin's unit tests 213 of 219, `lsp-server` 58 and `XdkStdioTest` 66. The six
  that failed tested bridges whose fixes are on `main`, and failed the same way on errs with only the
  version bump. As the user decided, the branch, which runs only with 0.30.0 and its nightlies, then
  removed five bridges, one commit each, `b410dbf472` to `a362bbcf46`: UP76's tag (its teardown stays,
  and the test checks a disposed wrapper stays stopped), UP37's `PhysicalExpressionFragments` (on
  0.30.0 it would replace LSP4IJ's physical fragment with one that has no file), UP70's re-reports
  and `setServerWrapper`, UP71's `DebugConsoleInput`, and UP73's `HeldEdits`,
  `EditsBeforeCompletion` and `XtcParameterInfoHandler`. The sixth, `ClosedFileProblemsTest`'s "a file
  the report no longer has", was the test's stub: it left a document out of its workspace report where
  the server sends an empty one, which LSP4IJ `main`, pulling the report itself, keeps the errors of;
  fixed on errs (`99e07387d1`), #693 and the branch, where the plugin's 216 unit tests now all pass.
  UP69 stays. `DebugConsoleLines` is dead on 0.30.0 but untagged, and stays for now. Removable after the
  release besides the branch's own: UP37, UP44's plugin bridge, UP64, UP65, UP69 to UP71, UP73 to
  UP78 and X259's UP09 flush. It pins 0.30.0 and removes,
  each in its own commit, the bridges 0.30.0 makes unnecessary; the items below name them by their
  2026-10-28 hashes, whose subjects the rebased commits keep. It is checked against a plugin
  built from LSP4IJ `main` `44675cb3` with #1790 and #1791 before their CodeRabbit fixes. Full
  IntelliJ run at `56c699eb5a` (`run-11663425380226624296`): 307 passed, 4 partial (X286-X288,
  X303), 1 not implemented (X302), 3 failed, and X312's timeout stopped it before X313-X331. X5 and
  X131 failed on two playbook races, fixed in `92eefb10c9` and `3e6a466b7e`; with them X5, X131 and
  X312-X331 pass (`run-10037495088984883693`; X314 partial for UP41). X312 is under [test harness
  stability](#test-harness-stability). The plugin's 113 unit tests pass. Native cases made full:
  X308, X311-X313, X315-X318, X320, X323, X326 and X328-X331 (UP30-UP37, UP40, UP42, UP44-UP48,
  UP50, UP55); X307 checks the exception hint's text; X131 opens a link with Ctrl+Click (UP52,
  `e317d03c11`, which also retires the replacement gates); X132 formats two ranges, X131 opens a
  symbol with Go to Symbol and X42 shows inlay tooltips (UP54, UP59, UP60, `a901032102`). The server
  now omits a signature's `parameters` without their metadata, which X20 needs with LSP4IJ's handler
  (`df11487151`); VS Code's X20 expects that, and X15-X20 pass in VS Code (`a67965ede2`). Kept on
  purpose: UP03, UP04's rename guard, UP12, UP13 and both halves of UP14. `verifyPlugin` resolves
  LSP4IJ from the Marketplace, so it can check the branch only after the release; against 0.21.0 it
  fails on #1786's `LSPFormattingParams` constructor. The internal API list is reviewed against the
  candidate's source (`c81995303f`). To do after the release: build against it, pass `verifyPlugin`
  and a full native run, and merge the branch.
  **On 0.30.1:** done. The branch's commits are on errs, rebased with their subjects (`5e3bd36546`
  builds against 0.30.0), and `b449d8a746` pins 0.30.1, which `verifyPlugin` resolves from the
  Marketplace.
- [x] UP01 (#1696): remove the `ConnectionLifetime` guard in `XtcLspServerSupportProvider`; gate:
  `ConnectionLifetimeTest`, the packaged process regressions, START/STARTUP and
  START/START_PROJECTS. On the 0.30.0 branch: `9ca54e5fe4`; `ConnectionProcessTest` drives the
  provider.
  **On 0.30.1:** `32fdff4453`.
- [x] UP02 (#1746): enable `willSaveWaitUntil` and server save formatting; gate: real pre-save
  requests apply version-checked edits before persistence, and X139 extended. On the 0.30.0 branch:
  `97cdb4e25d`, its settings texts `d5d64a8caf`.
  **On 0.30.1:** `5f7df7db4d`, its settings texts `603fbd394b`.
- [x] UP04 (#1698, #1718): remove the rename snapshot guard and client edit validation; gate: stale,
  closed and reopened document tests and X144. On the 0.30.0 branch: `2be956ad8c` removes the
  client's edit validation (`applyEdit`); the rename guard stays (`XtcRenameEdit`,
  `PreflightedRenames`).
  **On 0.30.1:** done. `daeaf6563e` removes the client's edit validation. With only the rename guard
  off, X118's explicit module rename fails twice: LSP4IJ's rename sends `textDocument/rename`, which
  can't carry the source-module change that only `xtc/rename` carries. So `XtcRenameHandler` is
  XVM's own rename and stays; its checks guard the edits it applies itself. Its tags are comments
  now (`467730297d`), and the server refuses that plain rename with its reason (`856d5ef01b`).
- [x] UP05 (#1742): remove `CompilerRootWatches`; gate: changes under external roots observed while
  focused, watchers released on disposal, X124. On the 0.30.0 branch: `26e2f7440e`.
  **On 0.30.1:** `a6077c9942`.
- [x] UP07 (#1730, #1735): remove `DiagnosticResultMessages` and the quick-fix data revision; gate:
  X181–X185, X202, X213 and diagnostic/action Undo/Redo. On the 0.30.0 branch: `9afed877da`.
  **On 0.30.1:** `a41465a51e` removes the `previousResultId` part, and `DiagnosticResultMessages`
  stays as the playbook's report record, and since 2026-11-25 as the UP86 bridge's (`markConnected` waits for the
  connected document's own report through it): it cannot go before LSP4IJ 0.30.2 retires UP86. Still open: the playbook's three UP07 waits
  (`CompilerPlaybook`, `RefactoringActions`, `Tour`); on 2026-11-23 its removal gate ran on 0.30.1
  with the bridge off, on an experiment branch: X181–X185 and X201–X215 pass, but only after 53
  reopened intention lists, so a baseline with the waits on decides. Decided 2026-11-23: the baseline,
  errs with the bridge and the waits, reopened none over the same cases (`run-1061357732916096379`), so both stay on
  0.30.1. The experiment turned off the bridge and the fixture isolation together, so which of them the
  reopens need is still open; that decides whether an LSP4IJ issue is left to file.
  **2026-11-27:** no LSP4IJ defect left: the reopens came only without the fixture isolation (7, against 0 with it,
  with or without the bridge), and the client canceled no code action; they were retries for answers that took up to
  9.7 s while LSP4IJ pulled 25 open broken fixtures' diagnostics. Nothing to file; the playbook's three UP07 comments are
  corrected on the IntelliJ harness branch, and `choosePopup`'s fixed 2 s menu wait becomes progress-based there.
- [x] UP08 (#1710): remove `ConfigurationJson`'s null handling; gate: X266, X268, CFG1–CFG3.
  On the 0.30.0 branch: `d8ad697b7f`.
  **On 0.30.1:** `fca2ed0b85`.
- [x] UP09 (#1724, #1725, #1728, #1736): remove `DocumentStartupMessages`; gate: startup typing and
  close/reopen regressions. On the 0.30.0 branch: `2be956ad8c`.
  **On 0.30.1:** `daeaf6563e`; X259's flush went in `e324a3b8ff`.
- [x] UP10 (#1712, #1734): remove `XtcParameterInfoHandler`; gate: X20 and the retrigger and
  constructor signature cases. On the 0.30.0 branch: `10b92e3803`, with the server's `df11487151`
  for X20.
  **On 0.30.1:** `0b17368f85`; `XtcParameterInfoHandler` itself went with UP73 (`2e389c9510`).
- [x] UP11 (#1733): remove the `CodeActionMessages` routing; gate: X105, X122, stale refusal and
  native Undo/Redo. On the 0.30.0 branch: `58782992f6`.
  **On 0.30.1:** `ea87035263`.
- [ ] UP12 (#1707): narrow the `XtcFormattingService` override; gate: absent-editor, snapshot,
  cancellation and server-lifetime guards, X138 and X139. The UP24 command ownership stays.
  **On 0.30.1:** #1707 fixes the absent editor; on 2026-11-23 its removal gate ran on 0.30.1 with
  the bridge off, on an experiment branch: with the whole override off, X31, X127, X132, X138 and
  X139 pass, X139's closed unsaved file included. The override stays for UP24's write command
  (IntelliJ, filed), which needs a formatting task of our own, so this stays open until IntelliJ
  fixes UP24.
- [x] UP14 (#1716): remove the semantic-result retirement in `XtcLanguageClient`; gate: X146.
  **On 0.30.1:** removed in `f012284915`: X146 and X259 pass twice without the retirement, and X137.
- [x] UP14's other half ([#1771](https://github.com/redhat-developer/lsp4ij/pull/1771), merged 2026-10-27,
  unreleased, `7fa1794f`): hover, definition, declaration, type
  definition, implementation, references, completion, rename and the hierarchy prepares reuse a
  completed answer for the same offset while the file's own stamp is unchanged, so another file's
  edit leaves them stale until the file is edited or another offset is asked for. The pull request
  also outdates them when the project's PSI modification count changes; its test fails without the
  fix for hover and definition. Gate after a release: `XtcLanguageClient`'s retirement shrinks to
  what #1771 leaves, answers that change after an analysis without any file changing, and the
  features still on the file stamp; X146 passes.
  **On 0.30.1:** #1771 is in 0.30.1; the retirement is removed in `f012284915`, which X146 and X259
  pass twice.
- [x] UP18 (#1711): advertise `AsIs` again; gate: X150 keeps exact source, placeholders and Undo.
  On the 0.30.0 branch: `06acfabc96`.
  **On 0.30.1:** `18478c0c46`.
- [x] UP19 (#1708): remove `DirectoryDocumentMoves`; gate: X118, X161, X162, X163 and the
  connection lifecycle tests. On the 0.30.0 branch: `4d7a8a361a`.
  **On 0.30.1:** `592538e145`.
- [x] UP21 (#1726, #1731): remove `ClosedRefactoringDocuments`; gate: X169 (exact disk text and
  current diagnostics). On the 0.30.0 branch: `1172c649d8`; `XtcRenameEdit` still refuses an unsaved
  closed target.
  **On 0.30.1:** `bb2cb51ba4` lets LSP4IJ save the documents a rename changes.
  `ClosedRefactoringDocuments` stays for a rename that also moves its directory (`e1fa5fd017`,
  X116), so this stays open.
  **2026-11-27:** done (`269a7e883f`): with the save off, X109-X116 passed 10 of 10 and X116, X161-X163 and X169 3 of
  3 on 0.30.1; the save is dropped, its guard against a closed target with unsaved changes kept (`UnsavedClosedTargets`).
- [x] UP22 (#1699): no workaround to remove; gate: native reconnect and typing acceptance.
  **On 0.30.1:** released in 0.30.1, with nothing to remove.
- [x] UP25 (#1749): advertise `workspace/textDocumentContent` to IntelliJ; gate: X254 without the
  read-only file fallback. On the 0.30.0 branch: `e1ca98fdb0`; X254's checks `56c699eb5a`.
  **On 0.30.1:** `d5b0f22282`. X350 checks the library view through the server's documents
  (`f207e1560a`) until a release has #2028.
- [x] UP26 (#1715): gate: X257 with explicit invocation and selection forwarded.
  On the 0.30.0 branch: `12ac9ec06b`; X257 waits for the Invoked request (`357045d859`).
  **On 0.30.1:** `502dec7035`.
- [x] UP27 (#1700): remove `EditorRefresh`; gate: the many-file X146 burst and the long
  X137/X146/X259 run. On the 0.30.0 branch: `bba29c4b4d`.
  **On 0.30.1:** `e96ffc056d`.
- [ ] UP03 (#1720, #1739): narrow the guarded Rename/Move entry points; gate: X103 and X130.
  **On 0.30.1:** 0.30.1 has #1720, #1739 and #1755: it sends `willRenameFiles` before a rename's or
  move's write action (`WillRenameFilesPreparation`) and renames a resource whose parent changes, so
  the UP03 tags describe 0.21.0. Kept since the 0.30.0 branch; on 2026-11-23 its removal gate ran on
  0.30.1 with the bridge off, on an experiment branch: X103 passes, and X128 fails on UP84, a new
  LSP4IJ bug that drops the `willRenameFiles` edit to a file that IntelliJ has loaded but the server
  hasn't opened (filed as #2135, our fix #2137). So this stays open until a release fixes UP84.
- [x] UP03: LSP4IJ #1740, the UI wait during the `will*` requests of an IDE rename or move, was fixed by our
  [#1755](https://github.com/redhat-developer/lsp4ij/pull/1755), merged 2026-10-26 (`d85f2c9b`), released in 0.30.1; the
  guarded entry points stay until a release with it passes X103 and X130 without them.
- [x] UP30 ([#1757](https://github.com/redhat-developer/lsp4ij/pull/1757), merged 2026-10-26): `DAPDebugProcess` did not implement `startPausing()`,
  so IntelliJ's Pause Program did nothing for any DAP session; X308 passes natively with it. Gate:
  X308 in IntelliJ with coverage full, and its IntelliJ case restored.
  **On 0.30.1:** `a0185cd0cd`.
- [x] UP33 ([#1761](https://github.com/redhat-developer/lsp4ij/pull/1761), merged 2026-10-26): the exception hint from `exceptionInfo`. Gate: X307's
  hint shows the exception and its stack without the runner's frames.
  **On 0.30.1:** `a0185cd0cd` and `7bbf0ecfa4`.
- [x] UP34 ([#1760](https://github.com/redhat-developer/lsp4ij/pull/1760), merged 2026-10-26): conditions on exception filters, sent as
  `filterOptions`. Gate: X312 in IntelliJ.
  **On 0.30.1:** `a0185cd0cd` and `46d8bdd0ff`.
- [x] UP37 ([#1763](https://github.com/redhat-developer/lsp4ij/pull/1763), merged 2026-10-26): `DAPExpressionCodeFragment` honors the `isPhysical`
  that IntelliJ asks for, so IntelliJ 2026.2's inline logpoint editor no longer fails its inspection
  pass's assertion, and an edit of Evaluate Expression's expression, a typed character or a chosen
  completion, no longer fails IntelliJ's Document/PSI check (found by R10e's X320). Checked natively
  with R10e: with a plugin built from `main`, X320's chosen completions make `box.count`, with no IDE
  error (`run-8742366318524741203`). Gate: a breakpoint with Suspend unchecked and a log expression,
  with no IDE error; X320 in IntelliJ through the inserted text, its 0.21.0 path and UP37 tags
  removed, with the plugin's bridge, `PhysicalExpressionFragments` (2026-11-05), which also ends the
  daemon's restart every 300 ms while a session is paused.
  **On 0.30.1:** `790fe3a08c`; `PhysicalExpressionFragments` went in `b202818c3c`.
- [x] UP40 ([#1766](https://github.com/redhat-developer/lsp4ij/pull/1766), merged 2026-10-25): LSP4IJ announces `supportsInvalidatedEvent` and
  handles `invalidated`, refreshing the threads, the stopped stacks and the views. Gate: X314 and X315
  full in IntelliJ, their `invalidated` checks restored.
  **On 0.30.1:** `8f454cbfae`.
- [x] UP31 ([#1758](https://github.com/redhat-developer/lsp4ij/pull/1758), merged 2026-10-26): an exception hint on an editor closed
  since the stop no longer fails with `DisposalException`. Gate: X307 without its wait for the hint, and
  no IDE errors.
  **On 0.30.1:** `7bbf0ecfa4`.
- [x] UP32 ([#1759](https://github.com/redhat-developer/lsp4ij/pull/1759), merged 2026-10-26): breakpoints that don't suspend as DAP logpoints,
  their "Evaluate and log" expression logged. Gate: X311's and X313's logpoints in IntelliJ, their UP32
  tags removed.
  **On 0.30.1:** `7bbf0ecfa4`.
- [x] UP36 ([#1769](https://github.com/redhat-developer/lsp4ij/pull/1769), merged 2026-10-26): a "Show Values as Hexadecimal" toggle,
  checked natively with R10b (`run-5016431580533175464`). Gate: X317's hexadecimal check always on,
  its UP36 tag removed.
  **On 0.30.1:** `d01cd346a0`.
- [x] UP35 ([#1768](https://github.com/redhat-developer/lsp4ij/pull/1768), merged 2026-10-26, `42f1f16e`): lazy variables
  behind a "Show value" link, checked natively with R10a. Gate: X316 in IntelliJ through the link
  only, its 0.21.0 path and UP35 tag removed.
  **On 0.30.1:** `d01cd346a0`.
- [x] UP42 ([#1770](https://github.com/redhat-developer/lsp4ij/pull/1770), merged 2026-10-26, `642d0e9d`): a variable with more
  than 100 indexed children read a page at a time behind IntelliJ's own `...` node, checked natively
  with R10c (`run-9236570344305345016`). Gate: X318 through the `...` node only, its 0.21.0 path and
  UP42 tag removed.
  **On 0.30.1:** `d01cd346a0`.
- UP13 stays: the `createSettings` override is LSP4IJ's supported extension point.

### LSP4IJ: fixed for 0.30.2, not released (UP80–UP83)

The plugin ships against LSP4IJ 0.30.1, and 0.30.2 has no release date, so these four fixes are not yet in
users' hands. Decided 2026-11-23: until 0.30.2 is out, the plugin works around each one well enough in the
product itself, not only in the playbook, or the gap is recorded here; each workaround is tagged
`// TODO LSP4IJ: UPxx` and goes once a release has the fix ([the register](errs-upstream-issues.md)).

- [ ] UP83 ([#2126](https://github.com/redhat-developer/lsp4ij/issues/2126), our
  [#2127](https://github.com/redhat-developer/lsp4ij/pull/2127), merged): an edit made while LSP4IJ connects
  a file never reaches the server. Bridged in the plugin on 2026-11-23: `OpenedTextRepair` watches the
  didOpen notifications that go to the server, and when one carried older text than the document now has,
  sends the current text through the document's synchronizer, with LSP4IJ's next version. That works with
  full text synchronization, the default. With incremental synchronization, which a user chooses in the
  Ecstasy settings, the synchronizer can only send ranges, which cannot repair text the server does not
  have: the plugin then logs a warning naming UP83, and the server's text stays wrong until the file is
  opened again. Accepted as good enough until 0.30.2, rather than switching such users to full
  synchronization. The tour's kill step no longer waits for the restarted server before its edit, so the
  edit falls in the window. The plugin's unit tests' stub server does not start the bridge, so
  `XtcCompletionTest`'s typed prefix cases wait until LSP4IJ has connected the file before they type. To do
  after 0.30.2: remove `OpenedTextRepair` and its registration in `XtcLanguageServerFactory`, and those waits.
- [ ] UP81 ([#2097](https://github.com/redhat-developer/lsp4ij/issues/2097), fixed upstream): when the server
  stops with 100 or more closed documents, LSP4IJ 0.30.1 submits a read action per file at once to clear their
  Project view marks, and IntelliJ logs "Too many non-blocking read actions submitted at once" as an IDE error.
  The marks are cleared all the same: users see an error report, nothing stops working. Decided 2026-11-23: no
  product bridge. The plugin could keep LSP4IJ from that clear (`canReportProblem` of its client features), but
  would then have to clear the marks itself, without LSP4IJ's private check of a file's current errors, so a clear
  that ran after the restarted server reported a file's errors would wipe them: worse than the logged error. The
  playbook records this one error under `knownIdeFailures` in results.json instead of failing the run
  (`PlaybookIde`, `// TODO LSP4IJ: UP81`); any other IDE error still fails it. The filter matches the error of the
  full run of 2026-11-22 (`run-2421176764147966521`), which failed on it in X145. To do after 0.30.2: remove the
  filter.
- [ ] UP82 ([#2129](https://github.com/redhat-developer/lsp4ij/issues/2129), our fix merged): Quick
  Documentation shows nothing when the hover answer takes more than 500 ms, as right after a start or
  during a large compile. Bridged in the plugin on 2026-11-23: `LateHoverDocumentation`, a documentation
  target provider registered after LSP4IJ's, takes the hover request that LSP4IJ's provider made
  (`getValidLSPFuture`), or asks again, as LSP4IJ's next Quick Documentation would, once LSP4IJ counts it as
  outdated, which it does once any file of the project has changed since. When that request has its answer, LSP4IJ's targets show
  it and the plugin's provider gives none; when it hasn't, LSP4IJ gave up, and the plugin's target shows
  the answer once it comes. IntelliJ 2026.2.3 shows the targets of every provider (it collects them all), so
  the documentation shows once. `LateHoverDocumentationTest` holds the stub server's answer until LSP4IJ
  has stopped waiting: without the provider it gets no target, with it the late answer; an answer at once
  shows once; and documentation that IntelliJ cancels while it waits leaves LSP4IJ's request alone. A first
  version awaited the request itself, with kotlinx's `await`, which cancels what it waits for when its
  coroutine is canceled: the tour's first hover after a restart then ended in an error. It awaits a stage of
  its own now, and the third test fails with the old version. A second version still asked LSP4IJ for the
  hover (`getHover`), which starts a new request, and cancels LSP4IJ's, whenever a file of the project has
  changed since it asked, as right after a restart: the tour's first hover after a restart again ended in
  an error, the cancel arriving 6 ms after the provider's call. A third version only took LSP4IJ's request,
  and so showed nothing when LSP4IJ already counted it as outdated. The provider now takes LSP4IJ's request
  while LSP4IJ counts it, and otherwise asks again, which cancels only an outdated request, as LSP4IJ does
  itself; its target waits for the answer outside the read action, and asks again while LSP4IJ keeps counting
  the answered request as outdated. The tour's first-hover check skips hovers that the client canceled
  (RequestCancelled, -32800) and still fails on one the server rejected. The tour's second Ctrl+Q is gone. The provider uses five LSP4IJ internal APIs, reviewed
  in `verifier-lsp4ij-apis.txt`. To do after 0.30.2: remove the provider, its registration and those
  entries.
- [ ] UP80 ([#2093](https://github.com/redhat-developer/lsp4ij/issues/2093), fixed upstream): a Go to
  Declaration, Implementation, Type Definition or References request that ends unanswered, canceled or
  failed shows "No … found". Decided 2026-11-23: no product bridge, a known gap until 0.30.2. The message
  comes from the `finally` of LSP4IJ's `AbstractLSPGoToAction` task, which the plugin cannot reach: a bridge
  would replace LSP4IJ's Go to Declaration, Implementation, Type Definition and References actions for Ecstasy
  files with copies of its own, a large and risky change for a misleading message that shows only when such
  a request is canceled or fails. The playbook keeps waiting until LSP4IJ's Go to task has ended
  (`NativeSemanticViews`, `// TODO LSP4IJ: UP80`). To do after 0.30.2: remove that wait.

### LSP4IJ: pull requests open

- [x] Our open pull requests (checked 2026-11-04), filed 2026-11-04: [#1857](https://github.com/redhat-developer/lsp4ij/pull/1857)
  (UP67, below); [#1854](https://github.com/redhat-developer/lsp4ij/pull/1854), which has Ctrl+hover
  underline only the reference, not the whole file, in a plugin's file type without a language (LSP4IJ
  issue [#1612](https://github.com/redhat-developer/lsp4ij/issues/1612)), not a UP entry, since no
  XVM code or case refers to it; and [#1858](https://github.com/redhat-developer/lsp4ij/pull/1858),
  0.30.0's LSP and DAP changes in LSP4IJ's docs. The other 96 are merged
  ([the register](errs-upstream-issues.md#upstream-pull-requests-2026-10-18)). To do: get them merged.
  **On 0.30.1:** all three merged on 2026-11-04, and are in 0.30.1.
- [x] X304 on LSP4IJ `main` is not an LSP4IJ regression. Its console seemed to lose what the program
  prints after Resume, but that was our probe's race: it read the console before the exit code, so a
  program that ended in between was reported without its last output. R7n's probe fix (`c966674fc`)
  reads the exit code first; with it, X304 and X307 pass twice in a row with a plugin built from `main`
  (`ef2b18c3`; `run-11462216125559745352`, `run-9056817084446612838`). Nothing to file.

### LSP4IJ: a kept-alive server is still stopped (UP66): our #1835, merged

- [x] UP66 (2026-11-02): LSP4IJ asks `keepAlive()` only when it schedules the stop after the last
  document closes, so a lease or `keepServerAlive()` that starts during those 5 s doesn't keep the
  server, and a run or debug session started then loses it. `XtcClientFeatures.keepServerAlive()`
  keeps the server while a run or session runs (`c36c8f6e68`), which covers closing files during one.
  Filed as [#1834](https://github.com/redhat-developer/lsp4ij/issues/1834); fixed by our
  [#1835](https://github.com/redhat-developer/lsp4ij/pull/1835), merged on 2026-11-02
  (`33a35e96`), which also fixes [#1836](https://github.com/redhat-developer/lsp4ij/issues/1836), a
  `WorkspaceDiagnosticsTest` flake. Both issues are closed. Nothing of ours to remove; a release
  with #1835 keeps the server for a run started within 5 s of closing the last file.

### LSP4IJ: a debug adapter's output gets a line end (UP67): filed as #1856, our #1857 open

- [ ] UP67 (2026-11-04): LSP4IJ's `DAPDebugProcess.print()`, which `DAPClient.output()` uses, adds a
  line end to output that doesn't end with one (since its first DAP commit, `3499339e`, and on `main`
  `d1a281d6`). IntelliJ's Debug console therefore breaks a prompt, such as X285's `Name? `, which the
  worker sends alone while the program waits for input; a line printed in pieces; and a line longer
  than the worker's 16,384-character batch. VS Code prints output as sent. IntelliJ's X285 checks
  only that Debug is offered and the exit code, so nothing caught it. Found reading LSP4IJ's code and
  our output path, not seen in an IDE. Filed as
  [#1856](https://github.com/redhat-developer/lsp4ij/issues/1856). The fix is on our fork's
  `fix/dap-output-as-sent` (`8edb8a7e`, on `main` `d1a281d6`): `DAPClient.output()` prints through
  a new `printOutput()` as sent, and LSP4IJ's own messages keep a line of their own, starting one
  after a partial line; `DAPDebugProcessConsoleTextTest`, 6 tests, and the 229 DAP tests pass. Our
  pull request [#1857](https://github.com/redhat-developer/lsp4ij/pull/1857), opened 2026-11-04 at
  the user's go-ahead. To do: get it merged, and after a release with it, check X285's prompt in
  IntelliJ's Debug console. Nothing of ours to remove.
  **On 0.30.1:** #1857 is in 0.30.1, with nothing of ours to remove. Still to do: check X285's
  prompt in IntelliJ's Debug console.

### LSP4IJ: a line typed in the Debug console goes nowhere (UP71): filed as #1862, fixed by our #1866

- [x] UP71 (2026-11-05): LSP4IJ's `DAPConsoleView` attaches to the adapter's process for output
  only (`ConsoleViewRunningState(..., true, false)`, on 0.21.0 and `main` `1988ab6c`), so a line
  typed in a debug session's Console goes nowhere: a program that reads its input cannot be debugged
  in IntelliJ, and the console evaluates nothing. The IntelliJ tour found it with the card game, which
  waits for Enter at every turn. Worked around by `DebugConsoleInput`, which gives the console a
  running state with input; `LanguageServerDebugProcessHandler` then sends each typed line as an
  `evaluate` with the context `repl`, as VS Code does (`DebugConsoleInputTest`). Filed as
  [#1862](https://github.com/redhat-developer/lsp4ij/issues/1862) on 2026-11-05, and fixed by our
  [#1866](https://github.com/redhat-developer/lsp4ij/pull/1866), merged the same day (`171f1a7c`);
  unreleased, checked 2026-11-11 (see `errs-upstream-issues.md`'s UP71 row). To do: after a release
  with #1866, remove `DebugConsoleInput`.
  **On 0.30.1:** `e5b7f0dcf2` removes `DebugConsoleInput`.

### LSP4IJ: a closed file's errors never reached IntelliJ (UP69, UP70): bridged

- [x] UP69 (2026-11-05): LSP4IJ 0.21.0 never asks for `workspace/diagnostic` and pulls the diagnostics
  of open documents only, so with our server, which IntelliJ pulls, a closed file's errors never
  reached IntelliJ: the IntelliJ tour found `SaveEights.x` unmarked after `cardMatches`' signature
  change broke its caller. `ClosedDocumentDiagnostics` asks for the workspace report after each
  refresh and publishes the closed documents' reports through LSP4IJ's push path, which marks a file
  with errors red (X341). LSP4IJ `main` (`d1a281d6`) has its own workspace pull, unreleased. To do:
  remove the bridge after a release with it.
  **On 0.30.1:** `3ba76ac505`.
- [x] UP70 (2026-11-05): LSP4IJ's problem mark goes stale as a document opens or closes, also on
  `main`. As a closed document opens, LSP4IJ drops its diagnostics without telling
  `WolfTheProblemSolver`, so a file that an edit elsewhere fixed, opened before the next publication,
  stayed red; the bridge reports the file again. Filed as
  [#1861](https://github.com/redhat-developer/lsp4ij/issues/1861). As a file closes, LSP4IJ clears
  its mark in a background task that nothing orders with the report of the errors then published
  for its closed document, so the clear could come last and leave the file unmarked; the client
  reports the file again after each clear when LSP4IJ holds errors for it. Both halves are fixed
  upstream: the opening file (#1861) by our [#1864](https://github.com/redhat-developer/lsp4ij/pull/1864)
  and [#1867](https://github.com/redhat-developer/lsp4ij/pull/1867), the closing file by our
  [#1868](https://github.com/redhat-developer/lsp4ij/pull/1868), all merged 2026-11-05 and
  unreleased, checked 2026-11-11 (see `errs-upstream-issues.md`'s UP70 row). To do: after a release
  with them, remove both parts of the bridge.
  **On 0.30.1:** `27802b4150`.
- [x] Run the IntelliJ playbook on 2026.3: `-PintellijPlaybookIde=2026.3` drives it while the plugin still
  builds against 2026.2.3. IDE Starter and its driver follow the IDE they drive, so the few classes whose
  Starter API differs have a copy per line (`src/starter262`, `src/starter263`), and the probes are a
  source set of their own; 2026.3 also wants the write-intent lock for closing editors and reloading
  documents, which the probes now take. Full runs on 2026.3 (`run-16968013414583787248`) and 2026.2.3
  (`run-2702324546959338057`): 327 passed, 17 partial, none failed, each. X122's earlier hang on
  2026.3 did not come back, alone or in the full run.
- [x] IntelliJ's Problems view still lists a closed file's errors only after **Build**: its Project
  Errors tab is fed by `ProblemsCollector`, which the embedded build reports to
  (`EmbeddedBuildProblems`), not by IntelliJ's problem files, which LSP4IJ reports to; LSP4IJ `main`
  (`0daf60cf`, 2026-11-11) reports nothing to `ProblemsCollector`. Designed 2026-11-11, not built:
  one provider takes Project Errors for Ecstasy files from both the build's errors and the workspace
  report, per file the newest wins, so a workspace report for a file, an empty one included,
  replaces the build's errors for it; its problems are values, so the two never show one error
  twice (`ProjectErrorsCollector` keeps a set per file). The build's problems also become
  `HighlightingDuplicateProblem`s (`@ApiStatus.Experimental`), which IntelliJ drops for a file with
  highlighting problems, as it does a Gradle or Maven build's; the highlighting side,
  `HighlightingBaseProblem`, is internal, so the workspace report's dedup has to be the provider's.
  To check first: that the server's workspace report has an entry for a closed file without
  errors. Gate: X341 extended, a closed file's error in Project Errors without a Build, once after
  a Build.
  Built 2026-11-11 on `lagergren/project-errors` (`83982dea8c`, `8155cfbc67`), not yet on errs:
  `ProjectErrors` replaces `EmbeddedBuildProblems` as the one provider; the server stopping clears
  the report's errors; the UP69 bridge passes each document's full report, open or closed, as a
  file and its diagnostics, the shape of LSP4IJ `main`'s `LSPDiagnosticFeature.diagnosticsUpdated`
  (#1704), which feeds it on 0.30.0 when the bridge goes; messages are read through LSP4IJ's
  `getMessage`, since the plugin compiles against LSP4J 0.24 and gets 1.0.0's diagnostics. The
  check first holds: `DiagnosticReports.workspace()` gives every document of an analyzed module a
  full report, an empty one when clean or fixed, and a removed one an empty report once. Tested:
  37 unit tests with `StubLanguageServer` (a closed file's error without a Build, once after one,
  a report that clears it removes the build's copy too; one bridge test), `verifyPlugin`
  compatible, and X341 extended in the container on 2026.2.3 (`run-59451308402330961`), its root's
  module configured for the Build, since a Build compiles configured modules only, and the member
  closed again after it, since a failed Build opens the first error. Found then: IntelliJ 2026.3
  (263.7307.200) copies every error of the Build window into Project Errors itself
  (`BuildViewProblemsService`, which 2026.2 connects to Gradle's and Maven's views only), so there
  an error shows twice after a Build, IntelliJ's copy and ours, and ours cannot retire IntelliJ's,
  whose problems are equal only to themselves; errs' `EmbeddedBuildProblems` likely does the same
  on 2026.3 today (inferred). Decided 2026-11-11, option 1: the embedded build sends its errors to
  the Build window as plain messages that navigate to their file and position, not as file
  messages, which IntelliJ does not copy, so `ProjectErrors` stays the one source on every
  version; its cost, that the Build window lists the errors without grouping them under their
  files. Not taken, kept here to reevaluate: (2) on 2026.3, leave the build's errors to IntelliJ's
  copy and drop ours, where a closed file would show IntelliJ's copy and the live one, and
  IntelliJ's copy outlives a fix until the next Build; (3) keep the change as built, right on
  2026.2 and twice after a Build on 2026.3. How to compare them: X341 in the container on both
  2026.2.3 and 2026.3, counting a closed member's error in Project Errors without a Build, after a
  Build, and after its fix, and the playbook's cases that navigate from a Build window error.
  Landed 2026-11-11 on errs (`3fb8d14ea6` to `c61c06e917`) and #693, with option 1 (`b7251e99cd`:
  the Build window's messages read `File.x:line:col: …` and navigate there, not grouped by file).
  Found on the way: IntelliJ 2026.3 moved the Problems view into the bundled plugin
  `com.intellij.problemsView`, so without an optional dependency on it (`1c1ba9f9c6`) the plugin
  could not load `ProblemsCollector` there at all, errs' `EmbeddedBuildProblems` included, while
  `verifyPlugin` said Compatible. The double listing is shown on 2026.3 (X341,
  `run-5281959389485290655` errs, twice; `run-8704265814916614805` the provider before option 1,
  twice; `run-356384057713571395` with it, once), from 2026.3's `BuildViewProblemsListenerRegistrar`
  (`AbstractViewManager.java:61`, `intellij.platform.problemView.backend.xml:39`), which 2026.2.3 and
  2026.2.4 lack; on 2026.2.3 X341 and X281 pass (`run-7835392141255911648`), and the tour's Build and
  run (`run-tour-4914951615836343626`). The plugin's whole unit suite passes, 260 of 260, on both
  branches, and `verifyPlugin`. A first run on errs failed seven tests from a stale
  `build/instrumented/instrumentTestCode` of 2026-11-10: `instrumentCode` is off since #766, but the
  IntelliJ Platform Gradle Plugin still puts that directory first on the test classpath, so old test
  classes ran; the test task now leaves it out (`c61c06e917`). Earlier plugin unit-test runs in a
  checkout that had it, such as the stub fix's on errs that day, may have run old classes; the full
  suite since covers the same classes.

### LSP4IJ: a killed server comes back as several (UP72): done, fixed in 0.30.1

- [x] UP72 (2026-11-05): after a server's process dies, LSP4IJ 0.21.0 keeps `serverError` until a
  restart attempt initializes, so each `start()` meanwhile stops the attempt in progress and starts
  another (up to 20, then it disables the server), and a stopped attempt still initializes and
  installs itself over the current one. The IntelliJ tour's run 6 (`run-tour-6451871955669100195`)
  killed the server: 8 processes started in 0.33 s, two served the project at once, the document's
  edits going to one and LSP4IJ's requests to the other, and one was never stopped. Every start was
  LSP4IJ's own `start()`, none the plugin's restarts. Fixed on `main`, unreleased:
  [#1673](https://github.com/redhat-developer/lsp4ij/issues/1673) and our `6bc4bc0` with
  angelozerr, merged 2026-11-03. Left on `main` (`1988ab6c`): the stages after `initServer` don't
  check that the attempt is still the current one, filed as
  [#1863](https://github.com/redhat-developer/lsp4ij/issues/1863), whose fix is under way upstream;
  our local draft of a patch is in `errs-upstream-issues.md`'s UP72 row, for reference. No plugin
  workaround (2026-11-05). To do: after a release with the fixes, the tour's "the server's process
  dies" check should see one restart.
  **On 0.30.1:** #1865 and #1867 are in 0.30.1, and #1863 was closed on 2026-11-05. Done
  (2026-11-23): the tour's "the server's process dies" checks that one server started after the kill,
  by the traces the servers write, and that the IDE's server processes come to that one (`run-tour-1441735048023870850`).

### LSP4IJ: a stack frame's file is looked up without a refresh (UP65): our #1799, merged

- [x] UP65 (2026-10-29): LSP4IJ's `DAPStackFrame` finds a frame's file with
  `LocalFileSystem.findFileByPath`, which never refreshes, and keeps the null, so a file in a
  folder made after the VFS listed its parent has no source. The debug process handler refreshes
  the nearest known folder before a `stackTrace` response goes on (X324). Filed as our
  [#1799](https://github.com/redhat-developer/lsp4ij/pull/1799) (merged 2026-10-29, unreleased,
  `0ff8b7aa`, for 0.30.0): a lookup that refreshes off the EDT and outside a read action. To do: drop
  the handler's refresh once a release has it.
  **On 0.30.1:** `9b00d41506`.

### LSP4IJ: an attach cannot use the adapter's own process (UP64): our #1798, merged

- [x] UP64 (2026-10-29): LSP4IJ's `DebugMode.ATTACH` replaces the adapter's process with a
  `NopProcessHandler` and reaches the adapter by its socket, so an adapter whose transport is its
  process handler, as ours is, can only be launched. The Ecstasy Attach configuration keeps the
  descriptor's mode at `LAUNCH` and makes only its client in `ATTACH` mode. Filed as our
  [#1798](https://github.com/redhat-developer/lsp4ij/pull/1798) (merged 2026-10-29, unreleased,
  `8c4a5ba3`, for 0.30.0), which adds `DebugAdapterDescriptor.startServer(DebugMode)`. To do: use it
  once a release has it.
  **On 0.30.1:** `d78e7514b4`.

### LSP4IJ: LSP4J's debug modules at 0.21.0 (UP63): our #1793, merged

- [x] UP63 (2026-10-28): LSP4IJ bundles LSP4J's LSP modules at 1.0.0 and its debug modules at 0.21.0,
  so no fix to LSP4J's DAP endpoint since 0.21.0 reaches IntelliJ, our LSP4J #1004, #1008 and #1009
  among them, and LSP4IJ's DAP model stays at DAP 1.60. Our
  [#1793](https://github.com/redhat-developer/lsp4ij/pull/1793) (merged 2026-10-28, unreleased, `2afdaf45`)
  bundles them at LSP4J's one version. UP41's LSP4J part needs it to reach IntelliJ.
  **On 0.30.1:** released in 0.30.1, with nothing to remove.

### IntelliJ Platform and LSP4IJ: evaluations cannot be canceled (UP41): IJPL-260358, LSP4J part our #1009

- [ ] UP41 (2026-10-24, cause found 2026-10-26): an IntelliJ evaluation that runs code cannot be ended
  before its time limit; X314 checks cancel in VS Code only. LSP4IJ never sends DAP `cancel`, but the
  gap is IntelliJ's first: `XDebuggerEvaluator.XEvaluationCallback` has no obsolescence
  (`XValueCallback` has no `isObsolete()`, and `XEvaluationCallbackBase` adds none), and the Evaluate
  dialog neither cancels an evaluation when it closes or evaluates again nor offers to stop one, so an
  evaluator never learns that its result is no longer wanted. LSP4IJ could only cancel at a resume,
  step or stop, where our server ends the run itself. Filed for IntelliJ as
  [IJPL-260358](https://youtrack.jetbrains.com/issue/IJPL-260358) (2026-11-02, Tools. Debugger): it
  proposes that `XEvaluationCallback` extend `Obsolescent`, and that the dialog's callback, whose
  `XCompositeNode` the dialog already marks obsolete before it evaluates again, return its node's
  `isObsolete()`. To do: get it fixed; then the LSP4J part, our [LSP4J #1009](https://github.com/eclipse-lsp4j/lsp4j/pull/1009)
  (2026-10-28): `DebugRemoteEndpoint.sendCancelNotification()` sends DAP `cancel` with the request's
  `seq` as `requestId` (LSP4J sent LSP's `$/cancelRequest` notification, which a DAP endpoint writes
  as an `event`), and an incoming `cancel` cancels the request it names; it waits on the maintainer's
  view of older LSP4J peers and of an adapter's own requests. Then the LSP4IJ part: an evaluator that
  cancels its request, to an adapter with `supportsCancelRequest`, once IntelliJ abandons it. LSP4IJ
  cancels no DAP request today.

### LSP4IJ: Run to Cursor removes its breakpoint too early (UP44): our #1773, merged

- [x] UP44 (2026-10-27): `DAPDebugProcess.runToPosition()` registers a temporary breakpoint and
  resumes, and `resume(context, position)` removes it once the `continue` request is answered, not
  when the program stops there (0.21.0 and `main`, `42f1f16e`). A program that takes longer to reach
  the cursor than that round trip runs past it: with the cursor two statements after the stop,
  IntelliJ stopped there; after a count to 3,000,000 it ran to its exit. VS Code 1.140 removes its
  temporary breakpoint when the session stops. Fixed by our
  [#1773](https://github.com/redhat-developer/lsp4ij/pull/1773) (merged 2026-10-27, unreleased): the
  temporary breakpoint stays until the program next stops, and the session's end forgets it; with a
  plugin built from it, and again from `main` at `e9f8bb42` (`run-11586456472789120644`), X323 stops
  at the cursor in IntelliJ. The nightly 0.21.1-20261028-080211 has it: there X323 failed with its
  gate's message, "UP44 is fixed" (`run-793652569316991787`). Since 2026-11-06 the plugin works
  around it with 0.21.0: its debug process gives the client a `continue`'s answer only once the
  program next stops or ends (`LanguageServerDebugProcessHandler`), so the temporary breakpoint stays
  until then, and X323 is full in IntelliJ. The IntelliJ tour's Run to cursor, after ten deals in a
  just-started worker, had run past dealAll (`run-tour-18322434560654050888`). To do: after a release
  with #1773, drop the workaround.
  **On 0.30.1:** `03fe6dfe21`.

### LSP4IJ: no loaded sources or modules (UP45): our #1774, merged

- [x] UP45 (2026-10-27): LSP4IJ never sends `loadedSources` or `modules`, and has no view for them
  (0.21.0 and `main`, `42f1f16e`). XVM's adapter answers both since R12, which VS Code shows in
  Loaded Scripts (X326). Implemented by our
  [#1774](https://github.com/redhat-developer/lsp4ij/pull/1774) (merged 2026-10-27, unreleased): Loaded Sources and
  Modules tabs, added once the adapter reports any; with a plugin built from it, a temporary X326
  found both, with the program's sources and modules. To do: after a release with it, implement
  IntelliJ's X326.
  **On 0.30.1:** `a0185cd0cd`.

### LSP4IJ: no function breakpoints (UP46): our #1780, merged

- [x] UP46 (2026-10-27): LSP4IJ has no function breakpoints: it never sends `setFunctionBreakpoints`
  (0.21.0 and `main`, `42f1f16e`); its breakpoints are lines, disassembly instructions and
  exception filters. XVM's adapter installs them since R9 (X328), which VS Code's Breakpoints view
  adds. Implemented by our [#1780](https://github.com/redhat-developer/lsp4ij/pull/1780) (merged
  2026-10-27 as `0d3c64de`, unreleased): a DAP Function Breakpoint type, added from the Breakpoints dialog's "+", sent
  with `setFunctionBreakpoints` and matched by `hitBreakpointIds`. Checked natively: with a plugin
  built from it, IntelliJ's X328 stops in `Entered.twice` and runs to its exit
  (`run-11586456472789120644`), and again at `bef74868`, rebased onto `main` with #1752
  (`run-551361467612578401`). To do: after a release with it, make IntelliJ's X328 full.
  **On 0.30.1:** `a0185cd0cd`.

### LSP4IJ: no restart frame (UP47): our #1778, merged

- [x] UP47 (2026-10-27): LSP4IJ never sends `restartFrame`, and ignores `supportsRestartFrame` and a
  frame's `canRestart` (0.21.0 and `main`, `42f1f16e`), so IntelliJ's Reset Frame is not offered in
  a DAP session. XVM's adapter restarts frames since R11 (X329), which VS Code's Call Stack view
  offers. Fixed by our [#1778](https://github.com/redhat-developer/lsp4ij/pull/1778) (merged
  2026-10-27, unreleased, `62c4a53e`): a DAP drop-frame handler that offers Reset Frame on a frame that can restart,
  sends `restartFrame`, and resumes the session until the `restart` stop. Checked natively
  (2026-10-27): with a plugin built from it, IntelliJ's X329 resets outer() through the Reset Frame
  action, which is hidden on run(), and passes (`run-17331679936087029005`). To do: after a
  release with it, make IntelliJ's X329 full.
  **On 0.30.1:** `a0185cd0cd`.

### VS Code and LSP4IJ: no single-thread execution (UP49): open upstream

- [ ] UP49 (2026-10-27): neither editor sends `singleThread` with `continue` or a step, so they
  always resume every fiber; VS Code's request
  [#166450](https://github.com/microsoft/vscode/issues/166450) is open, and LSP4IJ has none. R11's
  last item is built anyway (2026-11-02): the server takes `singleThread`. To do: check it from an
  editor once one sends it, with a catalog case.
  **On 0.30.1:** LSP4IJ 0.30.1 has #1869, which steps only the current thread with `singleThread`.
  Still to do: an IntelliJ catalog case.

### LSP4IJ: no inline values for Ecstasy (UP50): our #1781, merged

- [x] UP50 (2026-10-27): IntelliJ shows no inline values for Ecstasy. LSP4IJ never sends
  `textDocument/inlineValue` (0.21.0 and `main`, `f22304a4`); its scan of the editor's TextMate tokens
  places none of the frame's variables; and it leaves the frame's Locals group collapsed, while
  IntelliJ computes inline data only for the variables it shows. LSP4IJ issue
  [#1432](https://github.com/redhat-developer/lsp4ij/issues/1432) reports the same; its maintainer
  answered that inline values are an LSP feature. Fixed by our
  [#1781](https://github.com/redhat-developer/lsp4ij/pull/1781) (merged 2026-10-28, unreleased, `7469aab9`): it asks
  the language server, falls back to the token scan, and expands the locals' group; with a plugin
  built from it, IntelliJ draws the values and X331 passes (`run-4604772112093360200`). To do: after a
  release with it, make IntelliJ's X331 full.
  **On 0.30.1:** `a0185cd0cd`.

### LSP4IJ: no data breakpoints (UP48): our #1782, merged

- [x] UP48 (2026-10-27): LSP4IJ never sends `dataBreakpointInfo` or `setDataBreakpoints` (0.21.0 and
  `main`, `42f1f16e`), so IntelliJ has no breakpoint on a variable's value in a DAP session. XVM's
  adapter installs them since R9 (X330), which VS Code's Break on Value Change adds. Fixed by our
  [#1782](https://github.com/redhat-developer/lsp4ij/pull/1782) (merged 2026-10-28, unreleased,
  `c7955a13`): Break on Value Change in the Variables view asks `dataBreakpointInfo` and adds
  a DAP data breakpoint, which it sends with `setDataBreakpoints`; one that cannot persist goes with
  its session. In IntelliJ 2026.2 the action finds the DAP value behind the split debugger's
  frontend proxy through `XDebuggerTreeBackendOnlyActionBase`. Its `DAPBreakOnValueChangeSessionTest`
  runs a debug session against a DAP server in the test's process. With a plugin built from it,
  IntelliJ's X330 passes (`run-15563293796254899407`, `b6191ffbce`). To do: after a release with
  it, make IntelliJ's X330 full.
  **On 0.30.1:** `a0185cd0cd`.

### A DAP completion's `start` (UP43): not filed

- UP43 (2026-10-26): DAP measures a completion item's `start` in the client's column base, but the
  adapters send it 0-based and LSP4IJ and VS Code read it so, although both declare
  `columnsStartAt1`: vscode-js-debug and CodeLLDB as an index into the text, debugpy as a column in
  the request's line, java-debug always 0; lldb-dap sends only `length`, and Delve has no
  completions (their sources, read 2026-10-26). Converting by the column base in LSP4IJ would move
  their insertions one character to the left, so nothing is filed. XVM's adapter sends `length`
  only, which both editors replace before the cursor (`0e0d7e2c4`).

### LSP4IJ: no monikers (UP51): not filed

- UP51 (2026-10-28): LSP4IJ never sends `textDocument/moniker` (0.21.0 and `main`, `7469aab9`), and
  IntelliJ has no view or navigation that uses monikers; no LSP4IJ issue or pull request mentions
  them. VS Code has no moniker view either. XVM's server answered them from L74/L75 until
  2026-11-22, when the provider was removed with X252 and X253, as neither editor uses them; the
  cases had sent the request themselves, a protocol check rather than native coverage. Nothing is
  filed until IntelliJ has a use for them.

### LSP4IJ: document links without a target (UP52): our #1785, merged

- [x] UP52 (2026-10-28): LSP4IJ declares document links but never resolves one, and its Ctrl+Click
  ignores a link without a target (0.21.0 and `main`, `7469aab9`). XVM's server defers every link's
  target to `documentLink/resolve` for any client that declares document links, so an HTTP(S) link
  in a comment very likely opens nothing in IntelliJ. X131 resolves links itself, so it can't show
  this. Fixed by our [#1785](https://github.com/redhat-developer/lsp4ij/pull/1785) (merged 2026-10-28, unreleased, `02faa149`):
  a clicked link without a target is resolved when the server has `resolveProvider`. Checked natively
  in IntelliJ's X131 (2026-10-28, `a521e2ebbf`): 0.21.0 opens nothing
  (`run-2626450875688172472`), and a plugin built from #1785 resolves the link and opens it
  (`run-8879176847449743226`, gate UP52 passed). To do: after a release with it,
  make X131's UP52 step pass without the gate.
  **On 0.30.1:** `620eeb5374`.

### LSP4IJ: no linked editing (UP53): done, our #1812, in 0.30.1

- [x] UP53 (2026-10-28): LSP4IJ never sends `textDocument/linkedEditingRange`, so IntelliJ users get no
  rename-on-type for locals, which XVM's server answers. IntelliJ's X133 sends the request itself,
  a protocol check rather than native coverage. Fixed by our
  [#1812](https://github.com/redhat-developer/lsp4ij/pull/1812) (filed 2026-11-02, merged 2026-11-03, `625621bb`):
  when the caret stops, the ranges linked at it are asked for, and typing in one edits the others in
  the same command. At Angelo's request it is off by default, as in VS Code: a user turns it on for
  every server in `Settings | Editor | General | Smart Keys` (`50dfa73c`), and our plugin can't turn
  it on for Ecstasy files. Checked natively in the playbook container while recording LSP4IJ's user
  guide demo, which found that our server linked nothing at a caret just after a name
  (`394bb23eb8` fixes it). To do: after a release with #1812, make IntelliJ's X133 turn it on, type
  in a local's declaration and see its uses follow.
  **On 0.30.1:** #1812 is in 0.30.1, and done (2026-11-23): IntelliJ's X133 turns linked editing on,
  waits until LSP4IJ has linked the ranges at the end of a local's declaration, types a letter, and sees
  the local's use follow and the other method's local of the same name stay (`run-1061357732916096379`). The probe reads
  the ranges from LSP4IJ's synchronizer, which keeps them to itself (`LinkedEditingUi`).

### LSP4IJ: only the first of several ranges is formatted (UP54): our #1786, merged

- [x] UP54 (2026-10-28): when IntelliJ formats several ranges of a file at once, LSP4IJ formats the
  first and drops the rest, and never sends LSP 3.18's `rangesFormatting`, which XVM's server
  supports. Our `XtcFormattingService` takes the first range too. Fixed in LSP4IJ by our
  [#1786](https://github.com/redhat-developer/lsp4ij/pull/1786) (merged 2026-10-28, unreleased, `0616567a`): one
  `rangesFormatting` to a server with `rangesSupport`, otherwise one `rangeFormatting` per range.
  To do: make `XtcFormattingService` send every range too; after a release with #1786, a native case that
  formats two separate ranges. 0.30.0's `LSPFormattingParams` takes the list of ranges, so
  `XtcFormattingService.kt:76` must change when XVM moves to it.
  **On 0.30.1:** `5e3bd36546` sends every range, and `de91701c94` formats two ranges natively.

### LSP4IJ: no hit counts on line breakpoints (UP55): our #1787, merged

- [x] UP55 (2026-10-28): LSP4IJ's line breakpoints send a condition and a log message, never a hit
  condition; function and data breakpoints do (#1780, #1782). XVM's adapter supports hit conditions
  since R9. The R9 item above put IntelliJ's hit counts under UP32 until 2026-11-04, but our #1759
  added logpoints only. Fixed by our [#1787](https://github.com/redhat-developer/lsp4ij/pull/1787) (merged 2026-10-28,
  unreleased, `0ecbcdeb`): a DAP line breakpoint has a `Hit condition`, sent as `hitCondition`. To do:
  after a release with it, a native IntelliJ hit-count case.
  **On 0.30.1:** `7bbf0ecfa4`.

### LSP4IJ: the `exited` event (UP56): done, our #1791, in 0.30.1

- [x] UP56 (2026-10-28): LSP4IJ ignores DAP's `exited` event, so the program's exit code never reaches
  IntelliJ through it. XVM's `LanguageServerDebugProcessHandler` reads `exited` itself and ends its
  process with that exit code. Our [#1791](https://github.com/redhat-developer/lsp4ij/pull/1791) (merged
  2026-10-28, unreleased, `116a18d1`) puts the exit code on the session's process handler
  (`DAPDebugProcess.PROGRAM_EXIT_CODE`) and shows it when the adapter's process ends; CodeRabbit's
  finding, a child session's exit code replacing its parent's, was fixed in it first. To do: after a
  release with it, have our handler take the exit code from that key instead of reading `exited`.
  **On 0.30.1:** #1791 is in 0.30.1, and done (2026-11-23): our handler no longer reads `exited`, and
  ends its process with the exit code LSP4IJ keeps under `PROGRAM_EXIT_CODE`. The playbook's debug cases
  X285 and X304–X307, whose programs end with exit codes 4, 42, 7, 6 and 1, check it.

### LSP4IJ: inline-value and folding-range refreshes (UP57, UP58): our #1790, merged

- UP57, UP58 (2026-10-28): LSP4IJ declares neither inline-value nor (LSP 3.18) folding-range
  refresh. XVM's server sends each only to a client that declares it, so nothing is lost in
  IntelliJ. Our [#1790](https://github.com/redhat-developer/lsp4ij/pull/1790) (merged 2026-10-28,
  unreleased, `436a4db5`) declares both and handles them; CodeRabbit's finding, a race on a stack
  frame's variables context, was fixed in it first.

### LSP4IJ: no workspace symbol resolve (UP59): our #1788, merged

- [x] UP59 (2026-10-28): LSP4IJ resolves no workspace symbol, so XVM's server sends it each
  symbol's full location. Our [#1788](https://github.com/redhat-developer/lsp4ij/pull/1788) (merged
  2026-10-28, unreleased, `44675cb3`) declares `resolveSupport` for `location.range` and resolves a URI-only symbol when it
  is opened. To do: after a release with it, a native Go to Symbol case that opens a URI-only symbol
  at its range.
  **On 0.30.1:** `de91701c94`.

### LSP4IJ: inlay hint tooltips never showed (UP60): our #1789, merged

- [x] UP60 (2026-10-28): LSP4IJ showed only the plain-string tooltips of a hint's label parts, so
  XVM's Markdown tooltips, which its server puts on the hint, never showed in IntelliJ. This was
  first recorded as having no effect on XVM, which was wrong. Fixed by our
  [#1789](https://github.com/redhat-developer/lsp4ij/pull/1789) (merged 2026-10-28, unreleased,
  `f4e57c05`): the hint's tooltip, or else its parts', with Markdown rendered. LSP4IJ still resolves
  no tooltip lazily, which IntelliJ's declarative inlays don't allow, so XVM's server keeps sending
  tooltips with the hints. To do: after a release with it, a native case that shows an inlay hint's
  tooltip.
  **On 0.30.1:** `de91701c94`.

### LSP4IJ: its support lists disagreed with its code (UP61): our #1784, merged

- [x] UP61 (2026-10-28): LSP4IJ's `LSPSupport.md` and `DAPSupport.md` marked some supported
  features as unsupported, listed pull diagnostics under two names and
  `textDocument/publishDiagnostics` twice, and left out others. Fixed by our
  [#1784](https://github.com/redhat-developer/lsp4ij/pull/1784) (merged 2026-10-28, `e301a971`).
  The lists are read on `main`, so no release is needed.

### LSP4J

- [x] UP15: LSP4J #997 merged on 2026-10-29 (`37565290`, by @pisv), closing #996. Before the merge,
  @cdietrich found that its rethrow of a failure without a cause let `EitherTypeAdapter`'s
  cause-less `JsonParseException` escape, so the request got no response; #997 now rethrows only a
  failure whose causes hold an `IOException` or an `Error` (`fee81751`, with a test that fails
  without it). Unreleased: LSP4J 1.1.0 has no date.
- [x] LSP4J #1010 (filed 2026-10-29): a numeric message id outside the `int` range fails
  `nextInt()`, and the request is logged and dropped without a response. Our
  [#1011](https://github.com/eclipse-lsp4j/lsp4j/pull/1011) (on `37565290`) reads such an id
  exactly: an `Integer` in the `int` range, a `Long` in the `long` range, and otherwise a
  `BigDecimal`, which the response gives back (`b7f7823b`, 2026-11-02, after @cdietrich's note that
  larger ids were still dropped). Merged by @pisv on 2026-11-03 (`36a72950`), closing #1010;
  unreleased.
- [x] LSP4J #1008, #1004's follow-up, merged by @pisv on 2026-11-03 (`1431dd6c`); unreleased. Open
  since (checked 2026-11-04): #1009 (UP41's LSP4J part, above), and #1016 and #1017, DAP 1.71.0 and
  its 64-bit and unsigned 32-bit fields as `long`.
- [ ] UP15: after an LSP4J release with #997, adopt it and change `XdkStdioTest`'s assertion to
  `ResponseErrorCode.InvalidParams`, keeping reader recovery.
- [x] UP06: after an LSP4IJ release with #1723 (0.30.0), remove the `DiagnosticReportJson` adapter;
  gate: full and unchanged reports round-trip in the bundled client. No LSP4J release is needed:
  #1723 registers its own adapter in `configureCompatibilityAdapters`, which the client's Gson applies
  (checked 2026-10-28 against LSP4IJ's `main`; done on the local branch `lagergren/lsp4ij-0.30.0`,
  `fe12afc23e`).
  **On 0.30.1:** `5e19689f6b`.

### vscode-languageclient

- [x] UP28: #1896 (fix for #1307) merged by the maintainer on 2026-10-21 (`49328700`); #1307 is
  still open.
- [x] UP28: `vscode-languageclient` 11.0.0 (2026-10-22) has #1867 and #1896; the extension takes it
  in `40013e5306` (2026-10-29), without the double observation of `start()` or the `stop()`
  override. X272 passes on 11.0.0 without them and fails on 10.1.2 without them.

### IntelliJ Platform and VS Code: our fixes, filed 2026-10-28

Each was a local commit with an upstream-style test, verified natively on 2026-10-20, and was filed
on 2026-10-28 from the forks `lagergren/intellij-community` and `lagergren/vscode`. The drafts of
the tickets, PR descriptions and the #219122 comment are in `~/src/upstream-drafts/`, outside the
repository, one file each. UP17's commit message quotes 14.6 s with 80,000 highlighters in an
editor, which its probe never measured (the probe item below).

- [x] UP17 (IntelliJ Platform): our [#3728](https://github.com/JetBrains/intellij-community/pull/3728) (2026-10-28) for the existing
  [IJPL-259475](https://youtrack.jetbrains.com/issue/IJPL-259475), a console freeze whose stacks end in the same `maxEnd` correction.
  JetBrains pushed it to intellij-community `master` on 2026-11-03 as `5079f25b88`, "with more
  conservative guard for full tree rebuild" (cdracm), and closed the pull request on 2026-11-04
  (checked 2026-11-04). Unreleased; the gate is below.
- [x] UP17: fix the probe's readiness check. Its decorated replacement started with about 36,000
  of 80,000 highlighters installed, so it never measured the decorated case. Fixed in `2cf2996adf`
  (2026-11-11): the replacement waits until LSP4IJ's semantic tokens for the committed document are
  done, the daemon has finished the file, and the `LSP_*` highlighters number at least the tokens,
  and fails if it started with fewer. In the container, `run-2693557204668213345` and
  `run-17493816147983052741` started with 60,003 of 60,003 and took 258 and 292 ms; the gate's
  problem that this shows is under L82 above.
- [x] UP17: find why the decorated replacement stopped freezing, and give the probe a workload
  that shows UP17 again (2026-11-11, `2f09c71ccd`). Read in 2026.2.3's sources: the highlighters
  are in the markup model's own `RangeHighlighterTree`, but that is a `RangeMarkerTree` like the
  document's, updated the same way; `setText` under `WriteCommandAction` takes no bulk path, and
  `DocumentImpl.replaceString` trims the replacement to a deletion of the last three quarters.
  Without the fix, the tree removes the nodes an edit invalidates one at a time and after each
  recomputes `maxEnd` for every valid node above it, over all the invalidated nodes still below
  that node; invalidated nodes above are skipped. The cost is the square of the most invalidated
  nodes below one valid node, which depends on where the edit starts in the tree, not on its size.
  The daemon installs highlighters in ascending order, which puts the red-black tree's root at
  2^k − 1, at 0.2 to 0.4 of the nodes. Keeping a quarter of October's 80,005 highlighters (4 tokens
  a method) left the root at 16,383 valid with about 59,500 invalidated below it (14.6 s); of
  today's 60,003 (3 tokens a method) the 15,003 kept fall short of the root, the edit invalidates
  the root and all joined to it, and only 1,024 hang below a kept highlighter (276 ms in
  `run-7878288516979224096`). Plain markers do the same: with 2026.2.3's classes outside the IDE,
  20,000/40,000/60,000/80,000 markers took 386/1,278/116/5,048 ms with 14,881/29,761/512/59,521
  below a kept one. The probe now records that number as `invalidBelowValid` and replaces the
  second half, which starts past the root at any size: 2,087 ms with 27,236 for 20,000 methods
  (same run), 8,161 and 7,862 ms with 54,468 for 40,000 (`run-10918673005354525358`,
  `run-6810812479400253317`). JetBrains' fix as committed, ported onto 2026.2.3's two classes, took
  22 to 101 ms for all of these outside the IDE; its guard, affected nodes times the tree's depth
  above its node count, holds for them.
- [ ] UP24 (IntelliJ Platform): filed as [IJPL-259881](https://youtrack.jetbrains.com/issue/IJPL-259881), a duplicate of the older
  [IJPL-29917](https://youtrack.jetbrains.com/issue/IJPL-29917) and [IJPL-223024](https://youtrack.jetbrains.com/issue/IJPL-223024); our [#3727](https://github.com/JetBrains/intellij-community/pull/3727) (open, 2026-10-28; JetBrains asked
  vobal-jb to review it on 2026-11-02). To do: get it
  merged.
- [ ] UP20 (IntelliJ test Driver): filed as [IJPL-259883](https://youtrack.jetbrains.com/issue/IJPL-259883), restricted by JetBrains on 2026-10-29
  at about 17:37 UTC (it now answers "not found", to our token too); our [#3729](https://github.com/JetBrains/intellij-community/pull/3729) (open, 2026-10-28;
  JetBrains asked anastakats to review it on 2026-11-02).
  To do: get it merged.
- [ ] UP29 (IntelliJ test Driver): filed as [IJPL-259884](https://youtrack.jetbrains.com/issue/IJPL-259884), restricted by JetBrains on 2026-10-29
  at about 17:37 UTC (it now answers "not found", to our token too); our [#3730](https://github.com/JetBrains/intellij-community/pull/3730) (open, 2026-10-28;
  JetBrains asked anastakats to review it on 2026-11-02).
  To do: get it merged.
  **On 0.30.1:** JetBrains pushed #3730 to `master` on 2026-11-03; no release has it yet (#741).
- [ ] UP16 (VS Code): our [microsoft/vscode#344760](https://github.com/microsoft/vscode/pull/344760) (open, 2026-10-28) for the existing
  microsoft/vscode#219122. To do: get it merged.
- [ ] After each is fixed and released: remove the matching test or editor workaround and pass its
  gate (UP16: X130 and the standalone reproduction; UP17: the large-file probe's decorated
  replacement; UP20: Rename and focus recovery cases; UP24: formatting history without the write
  command; UP29: restart X137/X146/X259 without `UiWaits`).
- UP23 (VS Code overlapping-move Undo) has no fix; X218 stays a failed assertion and an accepted
  exception unless the host gains an API for it. Filed as [microsoft/vscode#344765](https://github.com/microsoft/vscode/issues/344765), fixed by our
  [microsoft/vscode#344772](https://github.com/microsoft/vscode/pull/344772) (2026-10-28). To do: get it merged; after a release, X218 passes with child-first moves.

## 5. Pull requests and the release

- [x] A master PR for the xunit selector fix alone, errs `649d5c53f2`: #755, "Make xtc test -c
  and -t select the test module's own classes", opened 2026-11-04 at the user's go-ahead and
  validated on master f7d87b4d88. Its patch is the same as errs `649d5c53f2` and #693's
  `6b36fe5aa5`. Merged 2026-11-04 (`a8d50aaffa`); the rebase onto master `2f18c1335c` dropped both.
- [x] #756, merged 2026-11-04 (`3a6f7f6004`): xunit's JSON report on master, the xunit parts of errs `80a7abfc02` and
  `712d23d14d` as one commit, as master never had the TeamCity reporter; validated on master
  a8d50aaffa. Opened 2026-11-04 at the user's go-ahead.
- [x] #757, merged 2026-11-04 (`2f18c1335c`): testXtc's `--test-package`, `--test-class`, `--test-method` and `--test-report`
  on master, errs `ceabc7d110` without the debugger's plugin changes, which master lacks, and with
  `json`; validated on master a8d50aaffa. Opened with #756. Gradle-mode test runs in the editors
  need both in a released plugin and XDK.
- [ ] #693 (the complete reference PR): reviews. It's for building and testing the whole product,
  and likely won't merge as it stands.
- [x] #693 takes errs' XDK source work of 2026-11-10 as two commits after its commit 277: `f01e6e24c8`
  and `7482c37cae` as 278 (`c9be92b5f8`), and `45e1bb70ec` as 279 (`2afb23929a`). Pushed on
  2026-11-10 at the user's go-ahead (see "errs' XDK source work of 2026-11-10" in
  [the integration plan](errs-integration-plan.md)).
- [x] #693's commit 281, `2e5854af45` (errs `663ea7cad4`), which fixes the CI failure of commit 279's
  directory locks (run 42854314561), made and validated with the full lsp-server and stdio suites.
  Pushed at the user's go-ahead; CI passes on it (run 42862014359).
- [x] #764, opened 2026-11-10 at the user's go-ahead: errs `8f650fe08c` alone on master,
  `XtcVersionSanityTest`'s projects under `@TempDir` (branch `lagergren/xtc-version-test-tempdir`,
  `f8242a0a38` on master `973268d2a0`). Approved, CI green; squash-merged the same day at the
  user's go-ahead as `85de7ec12c`. errs and #693 drop their copies on their next rebase onto master.
- [x] #693 takes errs `2e921902ce`, the tours' video and deck named `tour-intellij` and `tour-vscode`,
  as its commit 280, `8d22b98fdc`: a clean cherry-pick whose three files equal errs'. On #693
  `spotlessCheck` passes and the IntelliJ plugin's integration tests compile; its VS Code sources
  equal errs', where `tsc` and eslint pass. Pushed on 2026-11-10 at the user's go-ahead.
- [ ] Keep #693 in sync with new `lagergren/errs` commits, the debugger's included since 2026-10-28,
  when the user decided that #693 takes everything that completes the LSP and DAP implementation,
  distilled per feature, without tracking documents or plans. Last synchronized 2026-11-11, later
  that day: #693's tip `958d21cc15`, commit 286, after 284 `f6be30bcc4` and 285 `96e5a84288`, which
  are errs `42f212eba0` (an `ecstasy-library:` source compiled with its library, which VS Code
  never had), `642f6c83c8` (X350) and `2cf2996adf` (the UP17 probe's readiness), each the same
  change as on errs; on #693, `LibraryContentProtocolTest` (6) and `XdkLibraryDocumentsTest` (6)
  pass with every case, and the IntelliJ integration tests, `tsc` and `spotlessCheck` too. Pushed
  with errs at the user's go-ahead. #693 cannot run CI meanwhile: it conflicts with master's #763 in
  `TypeConstant.java` until it is rebased. Before that, the same day: #693's
  tip `94a02309e0`, commit 283, is errs `6a4684a033`, the tour's committed `.pyc` removed and
  `__pycache__/` and `*.pyc` ignored, the same patch; pushed with errs (`a0780d6b85..7745ae1fff`) at
  the user's go-ahead, and the links into errs' tracking files in #693's and xvm #741's descriptions
  now name `doc/errs/`. errs' other commits of the day stay off #693: the two `final`s in javatools
  (`d2ecd0a254`), which no LSP feature needs, and the records, the moves into `doc/` and
  `doc/errs/`, and the three analyses (`cd7ff2a052`, `92302fba3b`, `9dc1a6ebb8`, `7745ae1fff`),
  none of which #693 carries. Before that, 2026-11-10: #693's
  tip `5f07a72eba`, commit 282, has errs' work through `3b9bb74e87`, all but `8f650fe08c`, which
  master has as #764's `85de7ec12c`; 281 is `2e5854af45` (errs `663ea7cad4`), and 282 carries errs
  `4ef6532ab0` and `3b9bb74e87` together. Before that, #693's tip `8d22b98fdc`, commit 280, had
  errs' work through `2e921902ce`; its commits 277–280 are in the integration plan's records of
  2026-11-10. Before that, 2026-11-09: #693's
  tip `ffe34357bb`, commit 276, rebased with errs onto master `8097b66a89` that day, has errs' work
  through the VS Code extension's npm upgrade, errs `623d20c12b`. Its commits 115–276 came from errs
  from 2026-11-04 to 2026-11-09, and both branches were rebased onto master `46a5536e1f` on
  2026-11-05; [the synchronization map](errs-693-sync-2026-11-04-to-09.tsv) gives the errs commits
  each one carries (see "#693 synchronized with errs" and the two rebases around it in
  [the integration plan](errs-integration-plan.md)). Before that, 2026-11-04: #693's
  tip `c4f916bf44`, rebased with errs onto master `2f18c1335c`, had errs' features through R13 and
  its two CI fixes of that day, `a145de1074` and `fc302daaff`, as `0e95701f79` and `c4f916bf44`.
  On 2026-10-28 #693 was also regrouped
  into 70 commits on master `6c025c0426`, without its plans, every commit compiling, with
  [the regroup map](errs-693-regroup-2026-10-28.tsv) from each new commit to the old ones (see "#693
  regrouped and rebased onto master" in
  [the integration plan](errs-integration-plan.md#693-regrouped-and-rebased-onto-master-2026-10-28));
  its last commit, the rename proof fix, is errs `a8264753db`. On 2026-10-29 #693 was rebased onto
  master `636b29d80b` (#742), dropping its copy of #742, as 70 commits (`51c0b48aed`), and took
  `vscode-languageclient` 11.0.0 without UP28's workarounds as its commit 71 (`4a5f624b67`, errs
  `40013e5306`). Synchronized before that, on 2026-10-28: the
  debugger track through `a521e2ebbf`, as 22 feature commits and two of #693's own,
  `9b3b802eb2..6f5f5fde1b` before the regroup (see "The debugger track in #693" in
  [the integration plan](errs-integration-plan.md#the-debugger-track-in-693-2026-10-28)). Before
  that, the same day, the two library document fixes `622d0a506f` and `4cf5450acd` as `9e90dd1e13` and
  `b80a36e050`, the second without errs' `file()` and `copy()`, which came with the debugger's library
  frames, and with an import #693 lacked; XdkLibrarySourcesTest, LibraryContentProtocolTest and
  LibraryCopiesCleanupTest pass there. Before them, `f1d2060f29..3137736743`, 15 commits, each a clean cherry-pick: the discovery race fix
  `f71c9cc0a5` as `3137736743`; the focused test tasks `bdab767b8c` and `021948fcdb`; the LSP4IJ
  channel option and the nightly's playbook fixes `4ca6f5993d`, `4918127129`, `8d845d167e`,
  `ddcc6fda9d`, `0952a3f0c9`, `3b7c450765`, `ff06c86d0b` and `b499c339fb`; and the full runs' fixes
  `a82a762d19`, `95b4cfa2f1`, `5e6a0f2391` and `c2a9b296d5`. The lsp-server tests and the IntelliJ
  and VS Code playbooks compile; XdkResourcesTest (3) and the new errorListenerTest (68) pass. A
  survey of every errs commit since 2026-10-16 that #693 lacks found nothing else to take: the
  others are debugger work and its records, already in #693 in another form (the close races as
  `75af7a168c`, the build-logic fields), for code #693 doesn't have (the `f_errs` alignment, X31's
  `inlineValueProvider`), or the 2026-10-16 file-event fixes and their revert, which cancel out.
  Before that, 2026-10-27: the semantic token history's synchronization `efdcff889c` as `f1d2060f29`, its
  SemanticTokenReportsTest passing; before that, after both were rebased onto master `bcd8e30d58`: R8's fixes that are not debugger
  work are the pool count, the graph-walk tests and the worker's heap without `System.gc()` as
  `9eae4bfa50`, `1feca90e99` and `34b3ef3878`, then the read-only test skips `c730053240` as
  `c5adf2518e`, a worker's exit within 5 s `c68bbcba08` as `fa879b8b7f`, with `RunWorker` by hand,
  its test's thread dump `49d4f0dd5a` as `a2d059ddf9`, `FileRepository`'s temporary file
  `02b9109102` as `18b27fe115`, a fresh worker after a failed test `06dacc9c91` as `ce5dfab340`,
  and the test warnings `3321ab391b` as `63f03a4bac`. Before that, 2026-10-26, after both were
  rebased onto master `5130e4211`, see the
  [integration plan](errs-integration-plan.md): the torn-jar check `143594eec` as `12170a7c1`, the
  AGENTS.md inline-comment rule `b15c44ab7` as `00f7289c0`, and the plan's part of the R5 record
  `362aaafc4` as `f863ffabd`; #720's reviewed weak map came from master. Before that, 2026-10-23,
  in the old history: `eddd77ee9` as `c7fedbe53`, `ab9fbddeb` as `e618f7a22`, without its `SessionDebuggerTest`
  change, `1473c7e89` as `a6c3f519d`, `89e519d99` as `78e0a9deb`, and #717's three fixes as `076907bda`
  (its test only, since #693 stopped at an unterminated string already), `3ce7fc147` and `18a8d910e`,
  and #718's `getRuntime()` review changes `dc12e9390` and `db624ebbc` as `d7701ed06` and `5a68503df`;
  the other commits since are debugger work and its records).
- [x] errs and #693 rebased onto master `8b5257f10b` (2026-11-25), past #792, #798, #797, #796, #799,
  #800, #790, #793 and #803; before that onto `535e80fca6` on 2026-11-22 (`8ff1fe7d21`). Backups:
  `backup/lagergren-errs-2026-11-25` (`c8c6d6f2f4`) and `backup/693-2026-11-25` (`49b5f8aca6`).
  [errs' map](errs-rebase-2026-11-25.tsv) gives its 1746 commits on `60d36426eb`, `c8c6d6f2f4` with the
  commit that aligns errs' code to #693's: 1739 replayed, 6 resolved and `a2e343cdc7` dropped, master's #800;
  [#693's map](errs-693-rebase-2026-11-25.tsv) its 468, 466 replayed, `29029a823c` resolved and
  `09e61bd83e` dropped, #800 as well. Every conflict was in `TypeConstant.java`, where #803 removed
  `getJitCCType()`, `getJitICType()` and the `CD_Class` and `CD_Ctx` imports: edits to the removed
  methods keep master's side, and the `ErrorListener` static imports keep their place without the two
  removed ones. #803's three new newable checks said `ErrorListener.BLACKHOLE`, which errs no longer
  has; they say `silent(PROBE)` from the commit that removes `BLACKHOLE` on (errs `e2e53f3fff`, #693
  `1ddea3e0be`), so that every rebased commit compiles, and errs `4fbb4a7369`, #693 `ef370304b5` drop the
  import that #803 left unused in `TypeConstant`. Outside master's files
  both trees are the ones they were rebased from, and errs' code is #693's.
- [ ] Reconcile the stacked branches with what actually merged: #685 and #683 were closed unmerged,
  and #685's contracts now live in #693.
- [ ] Submit, when authorized: compilation-owned syntax and semantic facts
  (`errs/compiler-semantic-facts-20261009`).
- [ ] Then incomplete-source, cursor and declaration analysis
  (`errs/compiler-partial-analysis-20261009`).
- [ ] Then the compiler-backed LSP and editor integrations.
- [ ] Build and test each extracted PR on its own; the integrated branch passing doesn't prove a
  slice.
- [x] Full playbook runs, 2026-10-28:
  - VS Code 1.140.0 at `5e6a0f2391` (`run-Pc161s`): 332 passed; X218 fails as UP23 records, now as
    an assertion that names UP23, not a timeout that stopped the run (`a82a762d19`). Its
    dependencies passed: `:lsp-server:test` (2,414, 2 skipped) and `compilerStdioTest` (81).
  - IntelliJ 2026.2.3 with LSP4IJ 0.21.0 at `c2a9b296d5` (`run-17527198196716738382`): 310 passed,
    15 partial, 8 not implemented; X283 failed once, to stray keystrokes, and passes alone. No IDE
    exception. The run before it (`run-7875402505096583343`) failed X303, now fixed, and hit UP14's
    cache race in LSP4IJ 0.21.0 (#1716, unreleased): IntelliJ's breadcrumbs got no document symbol
    request and reported an `IllegalArgumentException` from `awaitWithCheckCanceled`.
  - Fixed on the way: X303's root kept `.gradle` and `gradle` from X129 and the compiler-import
    cases, in both editors (`95b4cfa2f1`, `5e6a0f2391`, `c2a9b296d5`); the server reported a module
    container that moved away during discovery as an invalid `xtc.compiler` setting, seen in X130
    (`f71c9cc0a5`).
  - The nightly run is under [LSP4IJ](#lsp4ij-released-in-0301).
- [ ] Release 0.5.0, the agreed boundary for the breaking listener and embedding API, with migration
  and recompilation notes.
- [ ] Settle the reconciliation inventory in the roadmap's
  [remaining work outside the prepared branches](pr-roadmap.md#remaining-work-outside-the-five-prepared-branches).
- [x] Declined for master (2026-10-26): native `toString()` for `String`,
  `Char` and the integer types, or a native `String` `+` for them. A `String`'s `+`
  (`xString.invokeAdd`) calls `Utils.callToString()`, which runs Ecstasy code for these
  (`String.toString()`, `Stringable.toString()`), so every string join of every program pays an
  Ecstasy call; found by tier 1's refusal of `s + i` (`fda3dc3e8`). It would also let tier 1 join strings.
  In master pull request [#722](https://github.com/xtclang/xvm/pull/722) (2026-10-24,
  `lagergren/native-to-string`, `12d691d3e`): `Int8` to `UInt64`, `Nibble`, `Char` and `String`, and `markNativeMethod()`, which found
  no `toString()` to mark in a const class, since `Const` and its `Stringable` come with no
  contribution. 200,000 iterations of five joins: about 16.3 s on master, 0.14 to 0.3 s with it. Not
  on this branch. Closed on 2026-10-26 after review: the interpreter keeps natural Ecstasy code wherever
  it can, and speed is the JIT's job. The closing comment notes `markNativeMethod()`'s gap through
  `Const`, which nothing reaches today.
- [x] Candidate for master on its own, apart from the debugger and the `toString()` one above:
  `ab9fbddeb`'s `ServiceContext` change and its `EmbeddingSupportTest` case, which master has unchanged
  (its `terminate()` and `shutdown()` assert the same, and the test uses only `EmbeddingSupport`).
  `Container.kill()` hangs, with assertions on, once a service has a fiber that ended with a call still
  out. It is on #693 since 2026-10-23 (`e618f7a22`), and in master pull request
  [#716](https://github.com/xtclang/xvm/pull/716) (2026-10-23, `lagergren/fix-embedded-run-end`), with
  `1473c7e89` below, written for master: without the `SessionDebuggerTest` change and the server
  README's line, which master's README has no place for. Merged as #716 on 2026-10-28 (`22ec660f1f`).
- [x] Candidates for master on their own, found by a scan of this branch's fixes outside `lang/` against
  master (2026-10-23), each reproduced on master; all decided by 2026-10-29. State on 2026-10-29: #716, #717, #718, #720, #723,
  #731 and #735 are merged; #719, #721 and #722 are closed, and #730 is closed with its fix in #724,
  merged on 2026-10-29 (`5ba865f16e`); `814153a42` stays on this branch.
  - `1473c7e89`: an embedded run ended when `run()` returned, and the services it left at work ran on
    with nothing able to stop them; now it lasts until they have nothing left to do, as `xtc run` does,
    and is then killed. Master has the runner and `EmbeddingSupport` (#703); the `xtc/runExited`
    README line stays here. On #693 since 2026-10-23 (`a6c3f519d`); in master pull request #716 with
    `ab9fbddeb`.
  - One hunk of `7b9b5856a`, a `break` in `Lexer.eatStringChars()`: `xcc` hangs forever on a file that
    ends inside a string, template or `|` literal, since the repeated error is dropped as a duplicate
    and never reaches the error limit (master's `Lexer.java:1397`). The errs test uses errs-only APIs
    and needs writing again. In master pull request [#717](https://github.com/xtclang/xvm/pull/717)
    (2026-10-23), with a new `LexerTest` case.
  - `814153a42`: two diagnostics with the same code and place whose parameters hash alike are taken for
    duplicates, and the second is dropped (master's `ErrorListener.java:318`); one line, and a test
    written again without errs' Site API. Dropped from #717 on 2026-10-26, as decided on #677: the
    compiler logs many errors it never reports, and a UID with the parameters as text would render
    them for each, while a collision needs the same code, place and parameter hash. Stays on this
    branch only.
  - `6312fd242`: an embedded run that reaches `assert:debug` reads the host's standard input;
    only third-party hosts reach it on master. In master pull request
    [#718](https://github.com/xtclang/xvm/pull/718) (2026-10-23, `lagergren/fix-embedded-assert-debug`),
    its `javatools` part with the `EmbeddingSupportTest` case. Its alternative,
    [#719](https://github.com/xtclang/xvm/pull/719) (2026-10-23, from ggleyzer), turns the console
    debugger off with a runtime flag that embedding connectors clear. Both fix the hang: a program run
    through `EmbeddingSupport` with a standard input that stays open waits at `Enter command:` on
    master and ends with either (2026-10-26). Neither PR's test catches it, since the Gradle test JVM's
    standard input is at its end; master's console debugger then returns without clearing the
    runtime's "debugger is active" flag, which stays true. The feedback on #719 says so, and that it has
    no debugger a host supplies. On 2026-10-26 Gene accepted #718's approach if it also turns the
    debugger off globally, as #719 does: #718 now has the system property
    `org.xvm.runtime.debugger=off` (`6de116655`, with `RuntimeDebuggerTest`), drops the comment he
    asked about, keeps the console debugger once chosen, as Cam asked, and keeps `getRuntime()`, with
    the reason (a point an instrumentation agent or test can intercept) in its thread. Rebased onto
    master and re-requested; #719 was closed on 2026-10-26, and #718 merged on 2026-10-27.
  - Found by R7r (`d77fb9b51`, 2026-10-23), in `EvalCompiler`, which master's console debugger uses
    for `E`: a method's parameter is unresolvable until something has shown it, an `@Inject` variable
    is captured with its `Ref`'s type, so no method of it compiles, and a lambda that does not compile
    stays in its class. The `@Inject` one reproduces on master's console: `E random.int(1)` fails
    with "Could not find a matching method or function "int" for type "@Inject Ref<Random>""; `E` of
    a call without a value, such as `console.print("hi")`, fails there either way, since the console
    compiles `E` as a lambda that returns a value. The parameter one does not show there, since the
    console shows a frame's variables before `E` runs, and nothing showed what the lambdas left
    behind break. Waiting for #718, see the item below: pull request
    [#721](https://github.com/xtclang/xvm/pull/721) was closed on 2026-10-24, since its test needs
    #718's runtime debugger, which master does not have yet.
  - `7e475dc98`, found while validating R7s (2026-10-23): a weak map's key and entry sets leave
    collected keys out of their iterators but not out of `toArray()`, which returns null for them, so
    `Container.terminateServices()` fails with "Cannot invoke ServiceContext.requestShutdown() because
    services[i] is null" once one of its services is collected; an embedded run's close then fails, or
    its end is never reported. Master's `javatools_utils` files are the same, and the commit's new
    `WeakHasherMapTest` case fails on master `7a79c6ef0` as it did here. In master pull request
    [#720](https://github.com/xtclang/xvm/pull/720) (2026-10-24, `lagergren/fix-weak-map-arrays`),
    and on #693 since 2026-10-24 (`a5e25c725`). After review (2026-10-26, `8c457fe25`, here
    `7fe6450b8`): `toArray()` fills one array, copied only when collected keys were left out, and the
    test clears the map's weak reference itself instead of waiting for `System.gc()`. #693 has it
    from master since #720 merged (2026-10-26).
  - Found by the native `toString()` test (2026-10-23): `new Nibble(Bit[])` makes 0 for every value,
    and so does `Nibble`'s table, and with it `Nibble.of()`, `IntLiteral.toNibble()` and
    `Char.isNibble()`; `xConstrainedInteger.constructFromBits()` packs the bits into a byte's high
    bits, and `Nibble`'s mask drops them. Its `bits` failed and `Nibble(String)` overflowed for the
    same reason, sizes counted in whole bytes. All three in master pull request
    [#723](https://github.com/xtclang/xvm/pull/723) (2026-10-24, `lagergren/fix-nibble-from-bits`),
    tested with asserts in `manualTests`' `numbers.x`, which CI runs. Merged on 2026-10-26 as
    `80bf83f4d`, with the review's wording of its comments. `Nibble(Byte[])` still failed, and so did
    a `Nibble`'s `toByteArray()`: a bit array makes bytes only from whole bytes, and the runtime took
    `f_cNumBits / 8` bytes. In master pull request [#731](https://github.com/xtclang/xvm/pull/731)
    (2026-10-26, `lagergren/fix-nibble-bytes`): one byte whose low four bits hold the value, both
    ways, and a byte with a high bit set is out of bounds; asserts in `numbers.x`' `testNibble()`.
  - Found through #723's callers (2026-10-24): `lib_json`'s `Lexer` dropped every `\u` escape, so it
    could not read back the control characters its `Printer` writes. In master pull request
    [#724](https://github.com/xtclang/xvm/pull/724) (`lagergren/fix-json-unicode-escape`), with
    surrogate pairs read as one `Char`, and independent of #723. After review (2026-10-26,
    `8a37676ac`): `eatHexits()` is a local function of `eatString()`, with shorter comments.
  - Found with #724: `Char.appendEscaped()` writes the first three hexits of a `\u` escape as 0.
    Shifts and `&` share a precedence level, so `codepoint & 0xF000 >>> 24` is
    `(codepoint & 0xF000) >>> 24`. On master `0x2028.toChar().quoted()` is `'\u0008'`. In master
    pull request [#730](https://github.com/xtclang/xvm/pull/730) (2026-10-26,
    `lagergren/fix-char-escape-hexits`), with a `testEscapes()` in `literals.x`. Its expected texts
    are each two literals: the compiler reads a backslash, a `u` and four hexits as a character
    anywhere in the source, also after an escaped backslash, as `doc/x.md` specifies. Issue
    [#729](https://github.com/xtclang/xvm/issues/729) asks whether that should change.
  The rest is on master already (#672, #703 and others), withdrawn, or part of the stacked pull requests.
- [x] The `EvalCompiler` fixes above, filed again on 2026-10-27 as
  [#735](https://github.com/xtclang/xvm/pull/735), once #718 let a host set its own debugger; the
  closed [#721](https://github.com/xtclang/xvm/pull/721) points to it.
  `lagergren/fix-debugger-eval-captures`, rebased onto master, has only its own commit
  (`312846cf9d`). Its `EmbeddingSupportTest` case sets a debugger of its own on the run's connector
  with `InterpreterConnector.setDebugger()`, and compiles `n + 1`, `random.int(1)` and `nope` with
  `EvalCompiler` where the run stopped at `assert:debug`; with master's `EvalCompiler` it fails all
  three ways, checked again on master `bcd8e30d58`. `:javatools:test` passes 460 (42 skipped). The
  console cannot be driven from `manualTests` instead: the run task only inherits Gradle's standard
  input, which is empty in CI.
- [x] A compiler crash on both master and this branch: a file that ends in `$"abc{x`, `"abc\` or `'\`
  failed with `NoSuchElementException` from `Source.next`, through `Lexer.eatTemplateExpression`,
  `eatStringChars` and `eatCharLiteral`. Fixed here (`92d87bb08`), in master pull request #717, and on #693.
- Reviewers when submitting: ggleyzer, cpurdy, thegridman.

## 6. VS Code Marketplace

All steps and their details are in [#710](https://github.com/xtclang/xvm/issues/710), assigned to
Cam; the publishing job is [#711](https://github.com/xtclang/xvm/pull/711).

- [ ] Marcus: add Cam as Owner on the `xtclang` and `xtclang-org` publishers (#710 step 0).
- [ ] Cam: delete `xtclang-org` and its TXT record (step 1).
- [ ] Cam: check that both are Owners of `xtclang` (step 2).
- [ ] Cam: settle the display name (step 3).
- [ ] Cam: redo the DNS proof for `xtclang` (`_visual-studio-marketplace-xtclang`), and give Marcus
  DNS access (step 4).
- [ ] Cam: find the trusted publishing setting (step 5), or set up the Azure identity (step 5b).
- [ ] Marcus: move #711 to a vsce with microsoft/vscode-vsce#1337 (4.0.1-1 or later) for OIDC, or to
  `azure/login` with `--azure-credential`, whichever step 5 leads to.
- [ ] Marcus: install the Marketplace pre-release VSIX in a fresh profile on macOS, Windows and
  Linux, and review the Marketplace metadata in `package.json`. CI builds it, as the
  `vscode-marketplace-<sha>` artifact, only while `VSCODE_MARKETPLACE_PUBLISH` is true; locally,
  `:lang:vscode-extension:packageMarketplaceExtension` builds it.
- [x] Merge #711 (2026-10-26, `5130e4211`): `commit.yml` packages and uploads the Marketplace
  pre-release only when the repository variable `VSCODE_MARKETPLACE_PUBLISH` is true, so with it
  unset CI runs as on master before. Its last CI run passed with those steps skipped.
- [ ] Agree how the first upload happens (step 6), set `VSCODE_MARKETPLACE_PUBLISH`, and run the
  first-publish checks in #710.
- [ ] About six months after the first publish: check that `xtclang` shows as verified, and apply
  again if the request was turned down.

## 7. One compile path for editors and builds

Today the two compile differently. The XDK's Gradle build compiles each of its 21 libraries with the
command-line compiler (`org.xvm.tool.Compiler`), by default in a forked JVM per library
(`ExecutionMode.ATTACHED`, `plugin/.../XtcPluginConstants.java:73`); the language server compiles
in-process through `EmbeddingSupport`. Measured on 2026-11-12 on an M-series Mac, the libraries'
`compileXtc` tasks forced to rerun, and a harness that compiles them through `EmbeddingSupport`:

| Way | Fresh daemon or JVM | Warm |
|-----|---------------------|------|
| Gradle, forked per library (today) | 35 s | 32 s |
| Gradle, in-process (`ExecutionMode.DIRECT`) | 38 s | 23 s |
| Gradle, either with `--parallel` | | 40 s forked, 23 s in-process |
| One JVM, the libraries in import order, each against the ones before it in memory | 15.5 s | 12.1 s |

The last row compiles 20 libraries (`lib_runner`, with two roots, was left out) without Gradle's own
work, and starts from the build's `ecstasy.xtc`, as the language server's bundle does. Its modules
have the same structure as Gradle's for all 20: every component's format and identity, with the
compiler's numbered `_` names made equal. Bytes cannot be compared: a `_` variable's name comes from a
JVM-wide counter, so even serial compiles differ ([parallel-compiler.md](../parallel-compiler.md),
`MethodDeclarationStatement.COUNTER`).

The ways, and what each needs:

- **Build the XDK with the language server's embedded build.** Not a fit: it compiles with the
  server's bundled javatools, not the repository's, which core developers change together with the
  libraries; it compiles `.x` modules only, not javatools, the natives or the distribution; and what
  it builds would run on the bundled runtime.
- **In-process compiles in Gradle by default** (`ExecutionMode.DIRECT`, which exists): about 28%
  faster from the second build in a daemon, slower in a fresh one. It runs the command-line compiler
  in the daemon, in a class loader per javatools classpath (`DirectRuntimeBuildService`). Before it
  can be the default: javatools' JVM-wide static state is shared by every compile in that class
  loader, across tasks and parallel workers ([parallel-runtime.md](../parallel-runtime.md) has a
  deterministic failure from it), so compiles must be serialized (a build service with
  `maxParallelUsages = 1`) or the statics removed; the daemon needs the heap of an `ecstasy`
  compile, about 0.5–1 GB live; and a compiler crash or `System.exit` takes the daemon down, where a
  fork loses only itself. A quick win for the XDK's own build, on those terms.
- **Port the plugin's in-process compile to `EmbeddingSupport`**, the API the language server uses,
  so that editors and builds compile one way and report the same diagnostics. Needs:
  `EmbeddingSupport` as instances, one per core repository, instead of a JVM-wide singleton
  configured once (`javatools/.../EmbeddingSupport.java:120-121`, `:304`, `:327`), which a daemon
  serving builds of different module paths cannot share; and a "compile this source set" call on it
  for what the command-line compiler does today: order the modules by their imports, compile
  mutually dependent modules together (`mack` and `ecstasy` refer to each other, and one module at a
  time needs a binary `ecstasy` to start from), the resource folder, version stamping
  (`--set-version`), qualified output names, `strict` and warnings, and errors per module. The
  language server's embedded build would use the same call. Runs and tests keep forking: they run
  user code, which isolation protects.
- **One pass over the XDK's libraries**, on top of that call: a single task that compiles them in
  import order, each against the ones before it in memory, as in the table's last row, about twice as
  fast as the warm in-process build. It gives up Gradle's per-library up-to-date checks, so it skips
  a library whose sources and dependencies are unchanged itself, reusing its previous `.xtc`; it runs
  on one thread, which costs little as most libraries wait for `ecstasy`; it holds the compiled
  modules in memory; and it must still say which library failed. Checked by structure, as above,
  until the output is deterministic (parallel-compiler.md, Stage 1).

Recommended order: `EmbeddingSupport` as instances, then the source-set call, then the plugin on it
with one pass for the XDK; in-process compiles for the XDK's own build in the meantime, if serialized.

- [ ] `EmbeddingSupport` as instances, one per core repository.
- [ ] A source-set compile on `EmbeddingSupport`, mutually dependent modules included; the language
  server's embedded build on it.
- [ ] The Gradle plugin's in-process compile on it, and one pass over the XDK's libraries.
- [ ] Meanwhile, if wanted: `ExecutionMode.DIRECT` for the XDK's own build, its compiles serialized.

## 8. The user guide's known limitations

The limits that [the user guide](../../lang/doc/user-guide/settings-and-troubleshooting.md#known-limitations) lists, each looked at on
2026-11-24 for what blocks it, what would solve it, how much work that is and whether it is worth it.
Estimates are working days for one person, with tests and both editors' playbook cases where the
change shows in them. In the order worth doing:

| ID | Limitation | Estimate | Worth it |
|----|------------|----------|----------|
| K2a | IntelliJ's Run 'All Ecstasy Tests' has no Rerun Failed Tests | 1 day | Done 2026-11-24, embedded and with `xtc test`; Gradle mode in K2c |
| K2c | ... and in Gradle mode none yet | 0.5–1 day | Done 2026-11-24, where the Gradle model is imported |
| K5a | Evaluations stop after 2 s, lazy values after 1 s, by default | done | Done: a launch and attach attribute each, and options of both IntelliJ configurations |
| K6b | Rename and safe delete miss consumers the server does not know of | Done | Yes, as a note: done 2026-11-24; automatic discovery is not |
| K3 | Embedded mode ignores the project's Gradle settings | 2–3 days | Done 2026-11-25: `xtcRun`'s method, arguments and JVM options, `xtcTest`'s JVM options; never the XDK version |
| K1 | VS Code has no `xtc` command-line build mode | 1.5–2 days | When a VS Code user asks |
| K7 | The formatter does not reflow comments or split strings | 1.5–2 days for comments | Comments done 2026-11-26, with `wrapComments` on; strings never |
| K2b | IntelliJ's Run 'All Ecstasy Tests' has no Debug | 2–3 days, after a check of xunit | Later: one module's tests already debug |
| K5b | IntelliJ cannot cancel an evaluation | upstream | Ours is done; waits on IJPL-260358, LSP4J #1009 and LSP4IJ (UP41) |
| K4 | A debugged program keeps the code it was built from | weeks, in the runtime | No, not here: a runtime feature |
| K8 | The Tree-sitter adapter knows only syntax | — | No: by design |

- [x] K2a: Rerun Failed Tests after Run 'All Ecstasy Tests'. `XtcTestConsoleProperties.createRerunFailedTestsAction`
  (`TestRuns.kt`) offered none for an all-modules run, as a failed test's selector does not name its
  module and `XtcTestLocator` resolved locations in the configuration's one module. Done 2026-11-24
  (`lagergren/k2a-rerun-all-tests`): `AllTestModulesState` names each module in its output ahead of the
  module's run (`XtcTestEventsConverter.moduleLine`), so each test's location is
  `xunit://<module>/<selector>` (`XtcTestLocation`), which `XtcTestLocator` finds in that module; Rerun
  Failed Tests groups the failed tests by module and runs each module with only its failed tests, in
  turn, into one console, as `AllTestModulesState` with tests. A run of one module's tests keeps
  `xunit://<selector>`. None under Debug, as before. Gradle mode's came with K2c, which also takes each
  test's module from its event's ID instead of the module line. X348 checks it in IntelliJ.
- [x] K2c: Rerun Failed Tests after Run 'All Ecstasy Tests' in Gradle mode, which K2a leaves without one.
  Gradle mode runs every project's `testXtc` in one Gradle build, so IntelliJ cannot write the line that
  names each module before its events, as K2a's embedded and `xtc test` runs do. K2a's report took this
  to need xunit's events to name their module, but they already do: each event's `id` is the test's
  `UniqueId.path`, `<module>:<package>.<class>.<method>` (`lib_xunit/src/main/x/xunit/UniqueId.x`,
  written by `JsonExecutionListener.x`), and `XtcTestEventsConverter` reads only the selector. So: take
  each test's module from its event's `id` in every mode, which makes the module line redundant; in
  Gradle mode, group the failed tests by module, find each module's Gradle project in the imported model
  (`xtc/gradleProjects`, as runs' `:xdk:lib-json:testXtc` do), and rerun them in one Gradle build,
  `:a:testXtc --test-class=… :b:testXtc --test-method=…`; without an imported model there is no project to
  send them to, and no rerun, as the import notice says. Check that the `id`'s module is the declared
  name the model uses. A converter test on Gradle-mode output, a test of the rerun's command, and a
  playbook case with X288's recording Gradle wrapper. 0.5–1 day; worth it, as the XVM repository, whose
  own modules replace the bundled libraries, can run its tests only in Gradle mode.
  Done 2026-11-24 (`lagergren/k2c-gradle-rerun`): in a run of every test module, in every mode,
  `XtcTestEventsConverter` takes each test's module from its event's ID (`TestEvent.module`), and K2a's
  module line is gone; a run of one module's tests keeps `xunit://<selector>`. In Gradle mode Rerun
  Failed Tests runs one build of `XtcRunConfiguration.gradleRerunTasks`: the failed tests grouped by module,
  each module's project from `xtc/gradleProjects`, `:a:testXtc --module=a --test-method=… :b:testXtc …`,
  and where one project has several failed modules its `testXtc` without `--module`, which takes one. No
  rerun without an imported model, or where the model names no project of a failed test's module. The
  ID's module is the declared name that the model gives each project by, a qualified one included: a real
  run of `qualified.example.org` in `EmbeddedTestRunsTest`. X348 checks Gradle mode with a recording
  wrapper: without the model the bare `testXtc` and no rerun, with it each project's `testXtc` and a
  rerun of only the failed test's module's project.
- [ ] K2b: Debug for Run 'All Ecstasy Tests'. LSP4IJ's DAP launch debugs one configuration, one program,
  and an all-modules run is one program per module, in turn. Either the debug server runs the modules
  one after another in one session, keeping its breakpoints (2–3 days), or xunit runs every test module
  in one program, which needs a look at whether xunit and the runtime can load several test modules into
  one container first. Debugging one module's tests works today, from the gutter or its configuration.
- [x] K5a: evaluation limits as settings (2026-11-24, `a7b0dc26fd`). `XtcDebugServer.CONSOLE_LIMIT` (2 s, decided 2026-10-23)
  bounds code run from the Debug Console and Evaluate Expression, and `SessionDebugger.ON_DEMAND` (1 s) a lazy
  value; both stay the defaults. A launch or an attach now sets either, in milliseconds: `evaluationTimeLimit`,
  100 to 60000, which `XtcDebugServer.Limits.console` carries, and `lazyValueTimeLimit`, 100 to 30000, which goes
  to the program's debugger with its setup (`DebugProtocol.Setup.onDemand`, protocol version 26), so embedded,
  Gradle, command-line and attached runs all take it; one out of range or not a number refuses the launch.
  Nothing cancels a lazy value's read, hence its lower maximum. A lazy value's read that fails, at its limit or
  by what its code throws, now answers with the reason; it answered lsp4j's "Internal error." before, as
  `variables` passed the failure on unmapped. VS Code declares both for launch and attach in
  `package.json`; IntelliJ's Ecstasy Application and Ecstasy Attach configurations have **Evaluation time limit
  (ms)** and **Lazy value time limit (ms)**, 100 to 30000, sent only when changed, whose text says that
  Evaluate Expression waits out the limit, as IntelliJ cannot cancel (K5b). VS Code has no user setting for
  them, as the extension keeps no debug defaults among its settings: a launch without a `launch.json` has the
  defaults. Proven with code that never ends: `EmbeddedExecutionTest` (a launch's limits end a console line's
  `spin()` and a lazy getter's loop, under the livelock guard's session and worker limits) and
  `XtcDebugAgentTest` (an attached program's debugger takes the setup's lazy limit).
- [ ] K5b: IntelliJ cannot cancel an evaluation: UP41 in section 4. Our debug server answers DAP `cancel`
  (`supportsCancelRequest`, X314 in VS Code); IntelliJ's evaluator has no obsolescence
  ([IJPL-260358](https://youtrack.jetbrains.com/issue/IJPL-260358)), LSP4J cannot send DAP `cancel`
  ([LSP4J #1009](https://github.com/eclipse-lsp4j/lsp4j/pull/1009), ours, open), and LSP4IJ sends none.
  Nothing to do here until those land.
- [x] K6b: consumers outside the workspace. A rename's proof covers the modules the server knows of; others
  come in only through Add consumers from folders (`XdkConsumerDiscovery`). Scanning the disk outside the
  workspace on its own is unbounded and not worth it, and a Gradle composite build's included builds are
  already known once its model is imported. Done on `775ad1dfdc`: a rename or safe
  delete of a declaration that other modules can name (the compiler's access: public, or protected in a
  class another module can extend, up to the module) says, as a `window/showMessage` that both editors show
  as a notification, that modules outside the workspace that use it are not changed, and how to add them in
  that editor (its initialization option `xtcAddConsumers`). A rename's message comes as `xtc/rename`
  answers; a safe delete's, from its code action's command `xtc.outsideUses`, which the client runs after
  the edit (that action carries its edit, as LSP4IJ 0.30.1 resolves no action with a command: UP85). Quiet
  for private and local declarations, for a module that a configured module outside the workspace folders
  consumes, and for a Gradle model's graph, which holds every module of its composite build. A change
  annotation was not used: VS Code shows one only in its refactor preview, which a `needsConfirmation`
  forces open on every rename, and LSP4IJ 0.30.1 shows none. Moves and plain renames came after, below.
- [x] K6b's gaps, found 2026-11-24 with it.
  - [x] Moves and plain renames, done on `lagergren/k6b-gaps`: a file move or rename through `xtc/renameFiles`,
    which both editors send, or `workspace/willRenameFiles` names what it changes that other modules name by
    its place, with the same rules for staying quiet: a moved file's type, which gets another qualified name, a
    module root moved under another name, and what renaming a file in its folder renames. The message says
    "… are not changed by this move", or "by this rename" when every file stays in its folder; it comes as the
    server answers, before the client applies the edit: as a toast in VS Code, whose Explorer runs the move's
    participant, and as a balloon in IntelliJ, whose Move dialog asks for the proposal. A root moved under its own
    name says nothing, as other modules name it by its name. `textDocument/rename`, which neither editor sends,
    says so as `xtc/rename` does.
  - [x] The tours' rename frames, done on `lagergren/k6b-gaps`: the tour's workspace graph is discovered, so the
    message shows in the frame of each rename and move, as it should: IntelliJ's Renamed everywhere and Rename a
    class, VS Code's Renamed everywhere, Rename a class, Rename a file and Move a file. VS Code's tour clears its
    toasts as it restores the project, and its guide crops leave out the corner they show in. IntelliJ's balloons
    stayed: the checks' renames and move left three, and "1 more notification", over the lower right of Extract and
    inline, the frame of intellij-extract.png. The IntelliJ tour's dismiss, which its restore runs, now closes the
    language server's messages too, and the frame is clean. guide.py on the section runs makes the six refactoring
    images with no message in any; they differ from the guide's only as a run of one section leaves the tabs and
    scroll, so the guide keeps its images from the full runs.
- [x] K3: Gradle's settings in embedded runs. Embedded builds and runs use the server's compiler and
  runtime, and run in an execution worker JVM (`RunWorkerProcess`); `xtcRun`'s module arguments, method
  and `jvmArgs` were never read. Done 2026-11-25 (`lagergren/k3-gradle-run-settings`):
  - The plugin's model export (`XtcLspSourceSetTask`, `.gradle/xtc/lsp-model.json` and the init script's
    `lsp-workspace.json`) carries each project's run settings in every one of its source sets' entries:
    `run`, the `jvmArgs` of its `xtcRun`, by module name the `methodName` and `moduleArgs` that it runs
    each module it names with, and `problems`; and `test`, the `jvmArgs` of its `xtcTest` and `problems`.
    The export reads the extensions, which `runXtc` and `testXtc` take their defaults from, and neither
    realizes nor runs the tasks, so that no task's configuration can fail an import. It never fails
    because of these settings (`XtcLspRunSettings`): a value it cannot read, a provider without a value or
    one that throws, is left out, a module with it, and `problems` names it; a blank module name is no
    module, and a blank method the default.
  - The server reads them with the model (`XdkBuildModel.Runs`; `Inputs.runs`, an `XdkRunSettings` by
    module name, for the modules that the source set owns; a qualified module is also found by its first
    part, as the runner finds it), leniently: a malformed section, module or field is left out with a
    warning in the log and never makes the model refused, and the export's problems are logged. It keeps
    them with the Gradle projects each time it applies a model, dropping both without one
    (`XtcLanguageServer.gradleModel`): a refresh of the model, as the editors already do it, reaches the
    next run.
  - Embedded runs and debug sessions, from both editors' run configurations, IntelliJ's gutter and
    the Run lenses, take `xtcRun`'s method and arguments where the run gives none: an empty method or
    arguments field takes Gradle's, field by field, and any value the editor sends wins. Their worker JVM
    has `xtcRun`'s JVM options; a run of tests, `xtcTest`'s, on its `xtc test` process (`TestProcess`),
    where the method and arguments do not apply. The run's output says, ahead of the program's, what it
    took but for the defaults, the method `run` and no arguments (`From Gradle project :app's xtcRun:
    method greet, arguments [...], JVM options [...].`), what it left out (`Left out of Gradle project
    :app's xtcRun JVM options: ... (why).`), which options hold a relative path, and what the export could
    not read.
  - `JvmOptions` leaves out what the worker or test process cannot run with: a class path, a main
    class, jar, module or source file of their own, a Java debugger's agent (JDWP, by its library's name
    for `-agentpath`, which would hold the worker before it is ready), options that print and exit
    (`-version`, `-fullversion`, `-Xshare:dump`, ...), argument files and option files (`-XX:VMOptionsFile=`,
    `-XX:Flags=`), whose options cannot be checked, and empty items; an option's value as the next item
    goes with it. A Gradle build that debugs the launcher with `-agentlib:jdwp=...,suspend=y`, as
    manualTests' comment shows, thus still runs embedded.
  - Workers are kept by their JVM options (`RunWorkerProcess`): a run gets a worker with its options, and
    one started for it retires an idle worker with other options, so one idle worker is kept, as before;
    runs with the same options share one, as before. A worker that stops before it is ready, with options,
    names its options, and what its JVM printed, as for an option it refuses, is in the run's output. What
    a worker's JVM writes to standard output and error, as `-Xlog`, `-verbose` or `--show-version` have it
    do, goes to the output of the run it hosts, as `runXtc` shows it, and cannot break the worker's
    events, which come over a loopback socket of their own since the first commit of the series (below).
    Each worker keeps the command line it was started with, which the tests read. Log lines add
    `options=` and `gradle=` only where there are any.
  - The editors' embedded notice says which of the build's settings apply, `xtcRun`'s and `xtcTest`'s,
    and that its XDK version does not; IntelliJ's run configuration editor and VS Code's launch
    attributes say that an empty method or arguments takes `xtcRun`'s. No other UI: the run's output
    names what it took. X351 checks a run in both editors; X288's notice changed with the text.

  Limits, as found: settings made only on a task, as `tasks.runXtc { jvmArgs(...) }`, are not seen, as the
  export reads the extensions. A relative path in a JVM option resolves against the language server's
  working directory, which the worker shares, not the project's, where Gradle's forked runs resolve it
  (`ForkedStrategy`): `-javaagent:libs/a.jar` then fails to start the worker, which its run reports, and
  `-Djava.library.path=native` or a log or dump file point elsewhere; the run's output names such
  options, and the guide says to give the paths in full. No program that the runner runs sees a system
  property, as its container forwards only the console, a UTC clock, a timer, randomness and named strings, and gives
  each run its own directories; the tests read the worker's command line, as the server builds it,
  instead. xunit's container forwards `properties`, which the test run's test reads. A module takes only
  its own project's settings: another project's `xtcRun` that names it gives it none. With an explicit
  module list the settings apply as the Gradle projects do, where the server still evaluates the model.
  Ordering at a run's end is best-effort: the worker's JVM writes its own lines to pipes that the server
  reads apart from the events, so a JVM line written just before the program's exit can reach the run's
  output just after the exit, and a line still in the pipe once the run has ended goes to the log, or to
  the worker's next run if that has begun; what the JVM writes as it starts, as `-showversion`'s lines,
  shows only in the run that started the worker, not in later runs that reuse it.
  Not exported nor applied: `xtcRun`'s `modulePath`, `jit`, `verbose`, `showVersion`, output paths,
  execution mode and debug options, the compile settings and the build's XDK version, which can never
  apply. The guide's screenshots of embedded runs show the earlier notice until the tours' next image run.
  - [x] Found with K3's review, and fixed in the series' first commit, before K3's own: the worker sent its
    events on its standard output, which its JVM writes to as well, HotSpot's tty output (such as
    `-XX:+PrintCompilation`'s) and unified logging (whose default `all=warning:stdout` puts JVM warnings
    there with no option), a line in several writes at any time. A JVM line within an event broke it and
    lost it: a lost exit left its run running until Stop ended the worker after 10 s, a lost ready failed
    the run at the 60 s startup limit, a lost debug reply left its request waiting. The events now come
    over a loopback socket of each worker's own, with a token (`RunWorkerProcess.connect`,
    `RunWorker.connect`); commands stay on its standard input, and its JVM's standard output and error
    go to the run's output, between the program's lines. Its review then found four faults, which the
    same commit fixes: a worker with `-Djava.net.preferIPv6Addresses=true` connected to `::1` while the server
    listened at 127.0.0.1, so both now use the literal address; one local connection that sent nothing
    held the wait for the token until the startup limit, so each now has 5 seconds of its own while
    others are taken; an idle worker's crash output went only to the log at debug, so its standard error
    outside a run is now a warning; and whether the JVM printed in a run carried over to the worker's
    later runs.
  - [x] Found with K3: `jvmArgs("...")` in `xtcRun` or `xtcTest` replaced the plugin's default JVM
    options (`-ea` and the build's others) instead of adding to them, as Gradle's `addAll` does on a
    property with only a convention; `runXtc` ran without them, though manualTests' build script says the
    call adds to the defaults. Fixed in master PR [#804](https://github.com/xtclang/xvm/pull/804)
    (2026-11-25, mirrored here): the defaults are the extension's value, and a task's JVM options take the
    extension's as theirs, so `jvmArgs(...)` adds; the export, and so embedded runs, then carry the
    defaults and the build's options alike.
  - [x] Found with K3: the editors' Gradle mode runs `runXtc --module=<module>`, which the plugin ran
    without the method and arguments that `xtcRun` gives that module (`XtcRunTask.createCliOverrideModule`),
    so an embedded run took them where the editor's Gradle run did not. Fixed in master PR
    [#804](https://github.com/xtclang/xvm/pull/804) (2026-11-25, mirrored here): `--module` runs the
    configured module of that name with its method and arguments, which `--method` and `--args` override.
- [ ] K1: an `xtc` command-line mode in VS Code. IntelliJ's command-line mode runs `xtc run --deduce`,
  `xtc test` and `--debug` (`XtcRunConfiguration.xtcCommandLine`); VS Code's `xtc.build.mode` has
  embedded, Gradle and automatic, and its debug launch says it does not run `commandLine`
  (`debug-adapter.ts`). The same shell tasks as its Gradle mode, with `xtc` on the PATH or a setting
  naming it: 1.5–2 days. Worth it only for checking a program against an installed XDK of another
  version than the server's, which no one has asked for.
- [x] K7: comment reflow. The compiler adapter's formatter wraps code at `maxLineWidth` and moves a comment
  only as a whole, and never changes a literal (`XdkLexical.kt`). Reflowing doc and block comments to the
  margin, keeping paragraphs, `@param` lines, lists and indented code: 1.5–2 days with fixtures. Splitting
  string literals changes tokens, and in a template `$"…"` what it means: not worth it. Done 2026-11-26
  (`lagergren/reflow-and-rename-note`, `XdkCommentReflow`), for comments, with the new formatting setting
  `wrapComments`, off by default as IntelliJ's **Comments: Wrap at right margin** is, which the plugin
  shows and sends; VS Code's `xtc.formatting.wrapComments`. A comment of the XDK's shape, its opening and
  closing alone on their lines and a star starting each line between, has each paragraph that runs past
  the margin filled again, word by word, never breaking a word; a tag or a list item starts a paragraph,
  whose lines after the first align with its second, or with a tag's description or an item's text; blank
  lines, indented and fenced code, `<pre>`, tables, headings, quotes and HTML stay as they are, and so
  does a paragraph that fits. Range formatting refills only the paragraphs it holds whole; on-type
  formatting never. `XdkCommentReflowTest`, 11 cases. Over the XDK's 466 library sources, at 120 columns
  one paragraph is refilled, at 80 columns 393 files change, and formatting again changes nothing. Not
  refilled: a comment of another shape (one line, text on its opening or closing line, a line without a
  star, as commented-out code) and runs of `//` comments.
- [ ] K4: no hot code replace. The runtime loads a module's compiled form once per container and has no
  redefinition of a loaded type; the debugger steps against that code (`SessionDebugger`). Replacing
  method bodies in a running container, as the JVM's HotSwap does, is runtime work of weeks across the
  interpreter's frames, constant pools and the JIT's generated classes, and DAP has no standard request
  for it. Not for this project; restart frame and a rerun are the way.
- [x] K8: the Tree-sitter adapter is syntax only by design: the compiler adapter, the default, is the
  semantic one, and Tree-sitter is the fallback without it. Making it semantic would be writing a second
  compiler. Closed; the guide states it as a fact.
