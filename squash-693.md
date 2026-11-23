# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 397 commits of `errs/full-testable-lsp-20261009` over `master` (tip `3dda3d7d17`, 2026-11-23), numbered 1 to 397 from the oldest, with their short hashes.

It proposes 15 pull requests, each of which changes either only files outside lang/ (xvm only: X1-X5) or only files in lang/ (lang only: L1-L10), merged in one order. To get there, the commits are first sorted into 13 topics (P1-P13), the commits of a topic that implement or fix the same thing, however far apart, are grouped to be joined, and every commit is then divided at lang/: its other files go to its topic's xvm PR, its lang/ files to its topic's lang PR.

## The pull requests, in merge order

| Order | PR | Changes only | Title | Prerequisites | Built from topics | Commits with a part in it | Added lines: main / tests |
|---|---|---|---|---|---|---|---|
| 1 | **X1** | files outside lang/ | Compiler diagnostics: the error-listener contract and repository read failures | — (master) | P1 | 10 | 2.9k / 2.8k |
| 2 | **X2** | files outside lang/ | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | X1 | P2, P11 | 9 | 5.2k / 931 |
| 3 | **X3** | files outside lang/ | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | X2 | P3, P4 | 8 | 5.8k / 1.7k |
| 4 | **X4** | files outside lang/ | Runtime support for running, testing and debugging from the editors: the debugger and its agent, xtc, xunit and the Gradle plugin | X3 | P9, P10 | 50 | 9.3k / 5.0k |
| 5 | **X5** | files outside lang/ | Build, CI and test tooling outside lang: CI test tiers, build checks, test hang detection, the LSP4IJ and IntelliJ versions, PR previews | X4 | P4, P7, P12, P13 | 23 | 1.6k / 806 |
| 6 | **L1** | lang/ | Language server: the server, its protocol, document synchronization, Tree-sitter and the mock adapter | X3, X5 | P4 | 33 | 9.9k / 12.9k |
| 7 | **L2** | lang/ | Language server: the compiler adapter's core, compiling, discovery, diagnostics and workspace pulls | L1 | P1, P2, P3, P4 | 37 | 9.1k / 17.6k |
| 8 | **L3** | lang/ | Language server: the semantic model and read-only editor features | L2 | P5 | 13 | 5.7k / 5.8k |
| 9 | **L4** | lang/ | Language server: code actions and refactorings | L3 | P6 | 22 | 8.1k / 11.3k |
| 10 | **L5** | lang/ | Language server: the XDK's library sources and the XDK workspace | L3 | P11 | 15 | 1.7k / 1.2k |
| 11 | **L6** | lang/ | IntelliJ plugin, on LSP4IJ 0.30.1 | L4, L5, X5 | P7 | 65 | 9.6k / 5.9k |
| 12 | **L7** | lang/ | VS Code extension | L4, L5 | P8 | 11 | 2.7k / 341 |
| 13 | **L8** | lang/ | Embedded build, run and tests in the language server and both editors | X4, L6, L7 | P9 | 37 | 7.7k / 6.6k |
| 14 | **L9** | lang/ | Debugging: the DAP server, debug sessions, launch and attach, and both editors' debug UI | X4, L8 | P10 | 44 | 8.3k / 10.5k |
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 118 | 3.3k / 56.5k |

Prerequisites are the PRs that must be merged first; the order above satisfies all of them, so merging in it always works. Where a PR lists two, either can be merged first of the two, but both before it. Some can be reviewed side by side: L4 and L5 both need only L3, and L6 and L7 both need L4 and L5.

Lang-only PRs still depend on xvm PRs: the language server compiles against javatools from the same tree through the composite build, not against a published XDK, so L1 needs X3 (the compiler API) and X5 (the CI tiers and build checks), L6 needs X5 (the LSP4IJ version in the root version catalog), and L8 and L9 need X4 (the runtime, debugger and Gradle plugin changes they drive). The xvm PRs carry their own javatools tests, so each is tested on its own before any lang PR.

L2, L9 and L10 are large: review L2 by package, L9 as the DAP server and the editors, and L10 as L10a (playbooks, scenarios, container) and L10b (tours and video). L10 is test code, which reviewers can skim; it goes last since it tests everything. The lang docs of P13 go with L10 here, or better, into the PR each part documents.

## Cutting the PRs

- Divide every commit at lang/: `git checkout <commit> -- <paths>` takes one side. A commit whose files are all on one side goes there whole. The appendix gives, for every commit, its xvm PR and its lang PR.
- Within a PR, join the groups of its topics (the tables under each topic) with an interactive rebase.
- Build each PR on the one before it in the order, and check it compiles and passes its tests there: an xvm PR with the javatools suite, a lang PR with the lsp-server suite and, from L6 on, a playbook run in the container.

## The topics at a glance

| Topic | Title | Builds on | Only lang/? | Commits now | After squashing (about) | Added lines: main / tests |
|---|---|---|---|---|---|---|
| P1 | Compiler diagnostics: the error-listener contract and repository read failures | master | no | 10 | 3 | 2.9k / 2.9k |
| P2 | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | P1 | no | 8 | 4 | 5.8k / 3.1k |
| P3 | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | P2 | no | 6 | 2 | 5.7k / 2.0k |
| P4 | Language server core: protocol, document sync, compile pipeline, discovery, diagnostics | P3 | yes, once 5 commits' other files move | 37 | 13 | 18.6k / 27.9k |
| P5 | Language server: semantic model and read-only editor features | P4 | yes | 12 | 6 | 5.7k / 5.8k |
| P6 | Language server: code actions and refactorings | P5 | yes | 21 | 7 | 8.1k / 11.3k |
| P7 | IntelliJ plugin, built against LSP4IJ 0.30.1 | P6 | nearly | 64 | 23 | 9.7k / 6.0k |
| P8 | VS Code extension | P6 | yes | 10 | 6 | 2.7k / 341 |
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 40 | 19 | 8.5k / 7.4k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 55 | 34 | 16.8k / 14.6k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 14 | 7 | 1.7k / 1.2k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 102 | 19 | 2.9k / 56.9k |
| P13 | Documentation, build and CI | the PRs it documents | no | 18 | 3 | 1.7k / 268 |
| | **Total** | | | **397** | **146** | |

The topics group the commits; the PRs above divide them at lang/. Sizes count added lines, with commit 8 divided by package (below). They leave out only the two files that later commits delete whole (XdkColorPrototype, XdkMonikers); the rest of what the pairs in finding 2 take back is still counted, so squashed PRs come out somewhat smaller. Tests are most of every PR's size: reviewers read the main code and skim the tests.

## How each topic divides at lang/

From every file each topic's commits change (commit 8 divided by package): P5, P6 and P8 have nothing outside lang/; the others divide as the route of their files says. The "Only lang/?" column shows how close a topic already was to one side.

| Topic | Only lang/? | Files outside lang/ | What they are, and where they go |
|---|---|---|---|
| P1 | no | 166: javatools/src (152), manualTests/src (7), javatools/README.md (4), javatools/LSP-COMPILER.md (1) | javatools |
| P2 | no | 114: javatools/src (102), javatools/README.md (4), build-logic/common-plugins (4), javatools_utils/src (2) | javatools |
| P3 | no | 129: javatools/src (125), javatools/LSP-COMPILER.md (1), javatools/README.md (1), manualTests/build.gradle.kts (1) | javatools |
| P4 | yes, once 5 commits' other files move | 36: javatools/src (26), build-logic/common-plugins (4), .github/workflows (3), AGENTS.md (1) | javatools' part of 35 (style fixes of compiler classes) to P2 or P3; 42's build-logic, javatools build and AGENTS.md changes, and the .github/workflows/commit.yml changes of 13, 138 and 143, to P13 |
| P5 | yes | 0 | — |
| P6 | yes | 0 | — |
| P7 | nearly | 5: build-logic/common-plugins (3), gradle/libs.versions.toml (2) | only the LSP4IJ version in the root gradle/libs.versions.toml (325, 364) and the plugin's API checks in build-logic (14) are outside lang/; they can go to P13, which P7 then follows |
| P8 | yes | 0 | — |
| P9 | no | 54: javatools/src (39), plugin/src (5), lib_xunit_engine/src (4), doc/xtc-cli.md (2) | javatools' runtime and xtc tool, the Gradle plugin, xunit |
| P10 | no | 106: javatools/src (92), plugin/src (10), plugin/README.md (2), doc/xtc-cli.md (1) | javatools' debugger and agent, the Gradle plugin |
| P11 | yes, once 1 commit's other files move | 2: javatools/src (2) | 151's two compiler files (doc comments kept on declarations) to P2, beside 115, which keeps them too |
| P12 | no | 26: javatools/src (15), javatools_utils/src (3), build-logic/common-plugins (2), javatools/build.gradle.kts (2) | the hang detection of 148 and 194 changes javatools' and javatools_utils' tests too; the container and channel wiring is in root build files (43, 100, 146, 279, 309) |
| P13 | no | 25: build-logic/common-plugins (6), gradle.properties (2), xdk/build.gradle.kts (2), build-logic/settings-plugins (2) | build-logic, root build files, CI |

## How the commits were grouped

- Every commit's subject, files and size were read from git, and its message searched for the playbook cases and upstream items it names; each commit was placed by what it changes (the compiler, the server's packages, an editor, the debugger, the tests).
- Commits were grouped when they implement one feature in steps, or fix, extend or partly undo an earlier one. No #693 commit names another by hash (their messages cite errs' hashes), so the links come from their subjects, their files and the lines they change.
- Where a later commit removes what an earlier one added, the lines were measured: the share of the earlier commit's added lines that the later one deletes in the same file. Those pairs are the best squashes, since joined they add almost nothing.

## Findings

### 1. Commit 8 has to be split, not squashed

Commit 8, `256fe39288` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `bb6bf70bf8` | 366 `7d18ee82a1` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `0874a872ca` | 345 `546b4ca5fa` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `6e77209ffc` | 374 `930c6dd069` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `f153c373b1` | 369 `7a7b2c369f` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `a615a98252` | 367 `1dc59f2665` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `c1f61280c8` | 363 `b536b0fbc7` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `84a97f2e29` | 373 `3c60646591` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `734eb3ee9c` | 362 `33e30be135` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `60c46a5e85` | 360 `c26cfecade` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `d3d3769258` | 112 `84d5116beb` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `cd39d0a0bd` | 105 `ceaffc8c32` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `256fe39288` | 36 `e21527131e` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `256fe39288` | 387 `70cfe3589b` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `ff6912f8ed` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `97d447aa5d` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `6ea329f445` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `f75726bc8c` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `e7b637eb0f` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `57d5985d1d` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `bbfba51ac9` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `162eb1a04e` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `6f6d854012` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `f05a7e8889` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `f2414e294c` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `0f99dfb570` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `16b4d1aeeb` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `6ff02a679c` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `fe14c826de` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `c037a38fcd` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `f2ee1ef867` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `d0c5091a51` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `1ce1ecf2ff` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `094fe30eba` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `a9f1afa3b7` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `2ca966bb8b` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `27835ef2a1` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `3bae33f9a2` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `88fb8ad536` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `1215e5c3dd` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `e48a4797f0` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `0543e52229` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `8a478d8338` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `7f993f1dff` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `32867d6570` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `7190e6fc80` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `af275489a5` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `b4c8acfb6e` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `ba91f32e99` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `324b9427d4` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `ca6c88726e` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `75ba33ed3c` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `a2943aa2b8` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `9635078fdb` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `d2405bae67` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `17b21761cc` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `a8a0010f34` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `61360d40c7` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `cd39d0a0bd` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `96bacb4085` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `cc73d85007` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `a4fc35f6ce` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `b533429dab` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `ceaffc8c32` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `d3d3769258` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `84ce211b78` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `681a2573b7` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `84d5116beb` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `f4b1cdb60a` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `03e1fcb8c7` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `11cfb7899f` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `2873a7cdaf` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `3bd621b42b` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `0e7f1e2174` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `6b2d9506e2` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `e4eb7e0865` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `1e01a96137` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `84a97f2e29` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `6ce15630f2` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `6e77209ffc` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `5e76ecd58b` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `62a86abdc9` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `b87acea62f` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `b8f02027ea` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `0b1a5938c7` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `1f22ff11a5` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `1c8b2234d4` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `2b3886f8b1` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `6e12e080e7` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `c9c5293a20` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.

## Splitting commit 8

| Goes to | Main lines | Test lines | What |
|---|---|---|---|
| P4 | 15,525 | 24,972 | server/, the adapter core (XdkAdapter, sources, discovery, build model, dispatch), Tree-sitter, mock, util, index, the DSL and Tree-sitter builds, the server README |
| P5 | 5,222 | 4,711 | the semantic model and the read-only features' adapter files and tests |
| P6 | 6,045 | 8,251 | the refactoring and code action adapter files and tests |
| P7 | 6,443 | 2,216 | intellij-plugin src/main and src/test (not dap/ or run/) |
| P8 | 2,282 | 0 | vscode-extension src (not its tests) |
| P9 | 13 | 0 | the run/ packages |
| P10 | 21 | 0 | the debug and dap packages |
| P11 | 377 | 100 | XdkLibrarySources and XdkAttachedSources |
| P12 | 504 | 31,920 | the IntelliJ playbook and probes, the VS Code tests, the scenarios, the manual test plan, scripts |
| P13 | 319 | 121 | build logic and root files |
| dropped | 311 | 0 | XdkColorPrototype and XdkMonikers: leave them out (36 and 387 delete them, and about 1,800 more lines of their tests and wiring) |

The division is by file; inside XdkAdapter.kt, the largest file (2,448 lines), all of it goes to P4, which the later PRs build on. Cut so, each PR compiles on the one before it, but that has to be checked commit by commit.

## Topic P1: Compiler diagnostics: the error-listener contract and repository read failures

**Scope.** javatools: every report goes to an explicit ErrorListener, with a reason for silence; parser and validation listeners scoped and restored; module repository read failures kept, cached and reported.

**Builds on** master. **Commits:** 10 now, about 3 after squashing. **Added lines:** 2,927 main, 2,949 tests.

| Join into | Commits | Why |
|---|---|---|
| Keep, cache and report module repository read failures | 1 `d4ec7e0057` Preserve module repository read failures without blocking usable candidates<br>9 `f75726bc8c` Report repository read attempts without inferring dependency failures<br>11 `57d5985d1d` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `7393a74612` Separate diagnostic reporting from listener abort policy<br>3 `29029a823c` Require explicit compiler listeners and reasons for silence<br>4 `80abb85c86` Scope parser diagnostics and restore validation listeners on every exit<br>19 `3067e59b71` Route repository and runtime reports through error listeners<br>41 `2140538277` Stop the lexer at a source's end and keep diagnostics that only look like duplicates | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `9f72e44270` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `dec506c512` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `fadafbedee` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `ff6912f8ed` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `1ef9f8733d` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `97d447aa5d` Expose compilation-owned syntax and resolved semantic facts<br>10 `e7b637eb0f` Clarify tooling contracts and isolate partial AST helpers<br>12 `c4c05d82bd` Verify compiler output without binary AST dependencies<br>115 `25eb9e757f` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `bbfba51ac9` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `6ea329f445` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `e9e27195f1` Complete a member access typed at the end of a line<br>144 `0812577ca8` Complete a member with the cursor right after its dot<br>161 `2873a7cdaf` Complete names and show a call's signature where they are typed at a line's end<br>385 `be61812b3c` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `f05a7e8889` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 37 now, about 13 after squashing. **Added lines:** 18,609 main, 27,881 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `11ebb326b9` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `9069200386` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `35c4418edd` Let two run tests check what they claim instead of how long they wait<br>301 `2ac725ba5b` Make the retention test's cancellations and the library content test's refresh check independent of timing | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `256fe39288` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `e21527131e` Remove the document-color prototype<br>266 `760c0f6448` Remove the unused xtc/runnableTargets request<br>387 `70cfe3589b` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `16b4d1aeeb` Keep documents current and the server responsive, and name its own diagnostics<br>28 `353cac1739` Judge file events against a running analysis and apply them in order<br>92 `745e6fb834` Analyze again an open document whose analysis was retired without a replacement<br>184 `394131ffa9` Answer requests that a restarted client sends while it opens its files again | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `931068aba4` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `f8b44de8d2` List a closed test module's tests from the workspace pull's compile<br>237 `122cbf6bb9` Clear a deleted file's diagnostics in the next workspace pull<br>248 `e02a4358d8` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `7b2dfc1c44` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `f5d5c0297c` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `0a2e02533d` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `0b629a7aed` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `25e4c4c2e7` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `df3288c6e5` Split the LSP tests into a fast default tier and complete runs<br>143 `d9c8b6eba5` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `094fe30eba` Declare fields first and use plain names in the new classes<br>95 `9119d3804a` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `caf428a9d6` Compile a closure as a fold, without mutable accumulators<br>137 `e030c86751` Name the properties that hold behavior or mutable state in camel case<br>44 `54491b353f` Synchronize the semantic token history that concurrent queries share<br>163 `718b292eb7` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `3bae33f9a2` Keep the build's jars whole and its test runs focused<br>97 `1ee0813a8b` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |

Kept as they are:

- 24 `0120b86ffb` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `3bd621b42b` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `03e21e7805` Keep the execution trace's events out of stderr and server.log
- 173 `477fed24ca` Let one source that discovery cannot read cost only itself
- 180 `56c7d2b6ea` Keep a project's module, source and library order the same in every JVM
- 261 `647a711c3f` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 12 now, about 6 after squashing. **Added lines:** 5,677 main, 5,807 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `5ba9d58b8b` Complete with the language model's keywords and built-in types<br>162 `451a636a3d` Complete a type of another module where a statement starts, with its import<br>185 `bb718c7051` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `f1a0470f7f` Name a type by what it is when hovering its name<br>156 `66d691461a` Give a constructor's finally block no references lens of its own<br>157 `8ba5d254aa` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `d0c5091a51` Keep indentation while typing, and lay out switches as the XDK does<br>135 `968d6fbd4e` Share the formatter's empty-block skeleton and whitespace edits<br>186 `3a341cc5fb` Answer on-type formatting without waiting for the change to compile | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |

Kept as they are:

- 102 `39d6ec7938` Link a local's ranges when the caret is just after its name
- 235 `eaeb0f7b6b` Fold comments and runs of imports with the compiler adapter
- 347 `3924f1c673` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 21 now, about 7 after squashing. **Added lines:** 8,138 main, 11,298 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `6e77209ffc` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `3944e513a3` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `930c6dd069` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `6b2d9506e2` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `b2b7f9849e` Prove a code action by compiling only the modules its edit reaches<br>193 `3a7bc6f890` Start a revision's code action compile from the previous one's, and log its cost<br>205 `5660172534` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `b8cb84021b` Keep the compiles of modules that compiled when only private declarations move | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `a052d28933` Safely delete an unused public or protected member<br>214 `09e7020a1c` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `96d0c84668` Extract statements that use an injected local, passing its value<br>177 `b72fe7b7ee` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `a9f1afa3b7` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `bdaf58732e` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `162eb1a04e` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `8615f8c873` Prove a rename past a register the semantic model cannot place<br>394 `470ec4e3d4` Refuse a plain rename that also changes the configured source modules, and say why | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `b364eb6b03` Offer code actions in a module that has a const with a toString<br>176 `a30fe975a9` Offer Create class for the diagnostic of a statement that constructs it<br>211 `ae958b2b77` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |

Kept as they are:

- 73 `61360d40c7` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 64 now, about 23 after squashing. **Added lines:** 9,683 main, 6,031 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `3dda3d7d17` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `a8108fce10` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `f3d3473303` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `c7731b619e` Review the internal LSP4IJ APIs for 0.30.0<br>392 `b8049a57ca` Name LSP4IJ 0.30.1 in the plugin's README and two test comments | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `8a5c002049` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `4f8ce716d9` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `fabafc0c19` Let snippet completions insert as they are (UP18)<br>339 `c59c55bed7` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `ea19ad3d98` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `7782e17c51` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `116213bd06` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `c47240c531` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `e6c46239ec` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `af87762fcf` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `5c8d80d6f8` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `b399c27144` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `a83cf44017` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `3bb70bd09f` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `d9c8e2f981` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `0874a872ca` Drop the change a restart's retired document synchronizer still sends<br>345 `546b4ca5fa` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `c1f61280c8` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `b536b0fbc7` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `fbe1afe542` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `f153c373b1` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `7a7b2c369f` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `84a97f2e29` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `5ad8a1ee03` Put the embedded build's errors in the Problems window's Project Errors<br>292 `638ce76d26` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `e7280a98ac` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `d399acc2d5` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `3c60646591` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `63f0a2f7f8` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `d3d9efd0e5` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `a435b9fe37` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `9825257dda` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `f6f03e65f9` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `c2879ce062` Give Ecstasy files a PSI of the Ecstasy language<br>153 `5aee38c247` Close a block that Enter opens after a { in an Ecstasy file<br>155 `65d29390db` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `11d20d23a3` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `58b2b34c6e` Give Ecstasy its Code Style settings page<br>140 `b4ccbe3b60` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `3ee13c9e90` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `00700cc1c0` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `772eaa8b88` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `c9c5293a20` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `4d2b421ea6` Keep each light test's stub server and wrappers to that test<br>209 `7860336c70` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `0934f7a1fa` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `eb8c73759f` Keep the stale instrumented classes of an older build off the plugin's unit tests | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `fc265b60ef` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `485db593aa` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) | Two comments that explain why kept workarounds stay (UP21, UP04). |

Kept as they are:

- 14 `23f90f8128` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `6f6d854012` Restart editor-owned language servers from a project-scoped CLI
- 32 `5c43a60581` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `544d029721` Cancel IntelliJ requests whose connection ends
- 99 `170e6fb655` Keep the language server running while a run or debug session uses it
- 130 `20d9286666` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `4f6dcfe161` Build an embedded run's process handler in one place
- 154 `11cfb7899f` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `10f998125f` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `81daacd909` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 10 now, about 6 after squashing. **Added lines:** 2,678 main, 341 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `d4425f34c8` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `27e62f12a6` Say when a move leaves the uses of its sources as they were<br>250 `d1d334130b` Ask the server again for a move's edits when a change overtook its answer | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `d1b7a4b5b6` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `b06bb67490` Compile the VS Code extension's TypeScript into an empty out/<br>272 `b21dc88587` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |

Kept as they are:

- 119 `b7d0aae602` List every VS Code command in the extension README, by its own title
- 234 `6f5a5fec6a` Trace the language server's messages when xtc.trace.server says so
- 304 `f694f85ff9` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `6e0155abfd` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 40 now, about 19 after squashing. **Added lines:** 8,475 main, 7,441 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `b3b6397e75` Report a Future callback that throws or that a terminated service drops<br>198 `2a68626523` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `d072c85019` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `f2414e294c` Build and run with the embedded compiler in the server and both editors<br>27 `fe14c826de` Make embedded the default build mode, chosen per editor<br>179 `e389e21c60` Build a Gradle-imported project embedded in embedded mode | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `6ff02a679c` Let embedded runs read console input in both editors<br>34 `1ce1ecf2ff` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `e870e85b22` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `168af5a922` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `c037a38fcd` Run programs side by side in supervised execution workers<br>33 `04fc4f6f4e` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `27835ef2a1` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `ba91f32e99` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `1746d4b6eb` Lift the lazy-value time limit for the embedded execution tests' worker | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `890126579b` Let deduction find a named module in src/main/x beside other sources<br>178 `1e01a96137` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `d3d3769258` Run and debug a module's xunit tests from the editors, embedded<br>112 `84d5116beb` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `84ce211b78` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `8f3d3b48b9` Rerun only the failed Ecstasy tests from the test console<br>202 `ed475c4a72` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `c8688ad073` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `306b775d91` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `53da75c39b` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `0934854bbe` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `681a2573b7` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `7daa8dd2fd` Pass a run task's args in Gradle and command-line mode<br>236 `d4accb5f8d` Run the editor's module from its Run button and context menu | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `1b1d6e05ab` Don't reuse a module compiled in another copy of the project<br>204 `131ec4488a` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `813764b83c` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `3b30b2db54` Describe running and debugging tests from both editors in the READMEs<br>121 `65530e7c38` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |

Kept as they are:

- 30 `f2ee1ef867` Find entry methods through the module's type and pass them with their arguments
- 38 `2ca966bb8b` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `b8950f86e8` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `07e384877c` Report a stopped run's end without the failure its stop causes
- 199 `e9dc031e8e` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `6842bc8626` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 55 now, about 34 after squashing. **Added lines:** 16,844 main, 14,619 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `cd39d0a0bd` Attach the debugger to a program run with --debug<br>105 `ceaffc8c32` Move the debugger agent into javatools, with the protocol as records<br>114 `f5ca1ce74f` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `a5c23f3b80` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `c67e8d9376` Let the debugger find a module's sources where the language server says<br>383 `8f6b1a9a59` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `44cf8cb2e0` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `1215e5c3dd` Debug programs from VS Code and IntelliJ through the language server<br>72 `144670ce25` Send a step's response before the stop where the step ends | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `0543e52229` Stop at exceptions, pause a running program, and stop on entry<br>75 `9f11d2007a` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `7f993f1dff` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `8375550653` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `af275489a5` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `375bfb71f8` Let a host give lazy variables' code longer than ON_DEMAND | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `9635078fdb` Restart a stopped frame, which its caller then calls again<br>215 `2ff64040fe` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `72fe46205d` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `3186fc2d37` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `af6c8d07d6` Lift SessionDebuggerTest's time limit to an hour<br>166 `0e7f1e2174` Make SessionDebuggerTest independent of how its threads are scheduled | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `734eb3ee9c` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `33e30be135` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `60c46a5e85` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `c26cfecade` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `a615a98252` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `1dc59f2665` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `bb6bf70bf8` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `7d18ee82a1` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `cc73d85007` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `b533429dab` Debug a command-line run: xtc run runs the program, which connects<br>149 `b3ab8c0383` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `886b4b98f2` Let VS Code set breakpoints in Ecstasy files<br>232 `7f4ef8fe2f` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `f91dfab3cc` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `f0234cb8b9` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |

Kept as they are:

- 47 `e48a4797f0` Show a stopped frame's variables, and step over, into and out of calls
- 49 `8a478d8338` Evaluate watches and hovers on paths in a stopped frame
- 51 `32867d6570` Evaluate expressions with operators without running the program's code
- 52 `9e310d2ab4` Leave the runner's frames out of the debugger's stacks and traces
- 53 `7190e6fc80` Run code typed in the Debug Console on the stopped fiber
- 54 `89d91c32a1` Stop at the program's first line from IntelliJ's configuration
- 56 `b4c8acfb6e` Place breakpoints on the lines a debugged program was built from
- 59 `ca6c88726e` Show library frames in their bundled source, every fiber, and modules
- 60 `75ba33ed3c` Step into a chosen call of the stopped line
- 61 `a2943aa2b8` Stop where a method of a name is entered
- 63 `d2405bae67` Stop once a watched field of an object has changed
- 65 `a8a0010f34` Answer inline values with the stopped method's variables
- 101 `a4fc35f6ce` Continue, step or pause one fiber while the others stay stopped
- 103 `7392cc56b8` Test that a part of a value's variables from past its end is empty
- 106 `34888e5113` Keep a debugged task's last output, and X325's queued fiber
- 141 `20e00aee5e` Debug a program that has an interface
- 169 `475161b764` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `e4eb7e0865` Let a debugger condition name an enum value by its enumeration
- 195 `bb3e798d35` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 14 now, about 7 after squashing. **Added lines:** 1,678 main, 1,213 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `52e9e94d6d` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `1f22ff11a5` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `1c8b2234d4` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `0c687fc809` Compile a library source's ecstasy-library content with its library, as its copy is | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `23851623e5` Report no diagnostics for a library source's read-only copy<br>249 `e8f552c76a` Compile a library source's copy on its own even once a compile has named it | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `85684e8dea` Delete the language server's temporary directories once no server needs them<br>277 `4596142553` Keep a server directory's lock beside it, where reading the directory cannot release it | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `39719018ba` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `fbc11c9593` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `bb6ae0da6a` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `67c3d385a2` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |

Kept as they are:

- 45 `930d1cdb22` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `38bfaa62ba` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 102 now, about 19 after squashing. **Added lines:** 2,889 main, 56,907 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `03e1fcb8c7` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `5e76ecd58b` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `62a86abdc9` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `f5da229d0b` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `603b5d9123` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `afed4dd398` Report the written video's real length from the tour composer<br>276 `f56545210a` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `74fe23944e` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `6410ad081e` Record a playbook run's screen as video in the container<br>228 `7b0eff298e` Delete a playbook run's IDE folder once its logs are copied, in the container | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `2cd03528b2` Press the keymap's own Undo key in the IntelliJ tour<br>253 `53ba35fa38` Undo in the IntelliJ tour from the editor as the change left it<br>255 `691d0ee437` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `b70e3fa5a6` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `e441e45ab6` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `074ae2c834` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `94bcff80b4` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `a1a28082cd` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `5d04b02ea9` Edit in the tour's kill step only once the restarted server has the file open (UP83) | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `0b1a5938c7` Record a tour of the VS Code extension that checks what it shows<br>251 `aec9f611c9` Make the VS Code tour pass as one whole run, unrecorded<br>257 `f7a8b299a6` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `03a6d40fd2` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `4cbb52e882` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `c30f815dc7` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `05e7eba7ff` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `f6cdaed3e3` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `f4b1cdb60a` Let tests wait for a hang only, not for a fast machine<br>194 `6ce15630f2` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `d042bd9165` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `cb7ac2c27e` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `1e9d3ae16e` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `96bacb4085` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `b87acea62f` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `50ac848f9a` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `d165ce3347` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `5f8bb69caf` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `6e12e080e7` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `5287ad53c6` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `7ec2b06c93` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `575a8dad72` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `9fb9b92ad5` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `8220300ad0` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `64d3e9dc22` Run the editor playbooks in the container from Gradle, with any engine<br>146 `89bdf8edbb` Queue playbook container runs on one machine lock, each with its own export<br>147 `e124eb0130` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `2b3886f8b1` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `dcbd0b0153` Offer server save formatting in IntelliJ (UP02)<br>330 `cb2afd2303` Open bundled library sources as the server's virtual documents (UP25)<br>331 `79821fc329` Check inline completion's invocation and selection natively (UP26)<br>332 `d85c456e3e` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `c4a186c843` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `77c924149d` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `4ffe88936e` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `ae1b5a32ef` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `6756b2a801` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `928e6e543c` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `ad7d1633aa` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `c1f1fdae09` Read a signature without parameters in the playbook<br>351 `0d4a8448d9` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `78d43de1ea` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `24ecfffc28` Check X254's library view through the server<br>354 `f23ebc0d09` Read X5's library target from the server until LSP4IJ connects it<br>355 `fcddef2f39` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `c9c4c452ab` Expect X20's signature without parameters in VS Code<br>365 `17d7288d6e` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `7f52767408` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `7efe534263` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `cb1e6c6959` Ask X254's definition again when the restarted server retires its analysis<br>376 `10476099bd` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `b4e3ff1131` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `876c613d1c` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `fe397dfba8` Send X259's library edit to the old server before the restart<br>87 `bb7fd090f7` Wait for X257's Invoked request to reach the LSP console<br>88 `cde9ae64d2` Reopen a popup under inspection after the IDE was deactivated<br>175 `90e5b24873` Give X145 a reference search that waits until it is canceled<br>282 `9e720f4bba` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `ae01b8bf92` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `3e22c83170` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `2c0f8a301d` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `8bfae7c3eb` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `0f99dfb570` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `b8f02027ea` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `d0918229ab` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `b76dffd367` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `ba4c4ec124` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `324b9427d4` Check Run to Cursor in both editors<br>64 `17b21761cc` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `517c2a43bf` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `5191f05192` Check a document link's Ctrl+Click in IntelliJ's X131 | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `e630225664` Give the playbook catalog's size as its 342 cases everywhere<br>122 `385b96f67b` Point the playbook instructions at the playbook container<br>127 `d5cde6069e` List the test and debug-task messages and a test row in the playbook's 18a | Fold into the catalog's documentation. |

Kept as they are:

- 43 `88fb8ad536` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `cb3cf245d1` Apply X103's rename as a refactoring and check the member after Undo
- 187 `648415f642` Check that Debug of Current File launches an Ecstasy configuration
- 189 `2b6489592c` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `19594c96a8` Run the VS Code playbook after the extension suite, not with it
- 200 `677a13443c` Count the code actions at the tour's unused import and unused local
- 247 `4b1a82d2ed` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `ec387444af` Print both compiler playbooks' progress per case in the build's output
- 279 `48f3f5c56c` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 18 now, about 3 after squashing. **Added lines:** 1,743 main, 268 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `45f3676225` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `72870eaf11` Drop the references to documents and plans this branch does not carry<br>69 `e792222ab9` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `8767daa0b4` Fix lang/README's task paths, versions and feature summary<br>123 `6b38aac19b` Use American spelling in the shippable docs<br>124 `40220b18a0` Name testing in the embedded build and run index entry<br>125 `b491342e0d` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `7bdeef20e4` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `f317c0dcfe` Say that every adapter needs Java 25 in editor-setup.md<br>283 `235767533a` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `489a4207d5` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `f2362a1251` Point the docs' line citations at the lines today's changes moved | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `d0e6b770f3` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `77ce233d8d` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `721a52f369` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `227bab050c` Leave the lang build out of the root build by default again<br>379 `594914f790` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `cb693d3fc3` Say in AGENTS.md that gradle.properties turns the lang build flags on | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |

## Doing it

1. Start each PR's branch from the one before it in the merge order (X1 from master), and take its commits in their #693 order, each divided at lang/ and, for commit 8 and the mixed commits, by path.
2. Join each group with an interactive rebase (`fixup`/`squash`), keeping the group's earliest commit as the message to edit; a "fold into" group goes into the commit that introduced what it fixes.
3. After every joined commit: compile, and run the affected tests; after every PR: the javatools suite for an xvm PR, the lsp-server suite for a lang PR, and from L6 on one playbook run in the container. Nothing in this plan was compiled.
4. Compare the end: after L10, the tree must equal #693's tip (the document colors and monikers left out on purpose are deleted by #693's own later commits, so the trees still match).

## Limits of this plan

- It reads commits, not a build: a group that looks like one change can still need its middle steps to compile, and a "fold into" can conflict. Each squash has to be checked by compiling.
- The sizes count added lines; renames and moved code count as added.
- A few commits could sit in either of two PRs (a run fix that the debugger needs, a test case of a feature); the table puts each in one, and moving it is cheap.
- 384 (errs' regression tests) holds tests for P1 and P2; divide it by test class.

## Appendix: every commit

| # | Commit | Topic | xvm PR | lang PR | Group | Subject |
|---|---|---|---|---|---|---|
| 1 | `d4ec7e0057` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `7393a74612` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `29029a823c` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `80abb85c86` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `ff6912f8ed` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `97d447aa5d` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `6ea329f445` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `256fe39288` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `f75726bc8c` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `e7b637eb0f` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `57d5985d1d` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `c4c05d82bd` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `25e4c4c2e7` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `23f90f8128` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `bbfba51ac9` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `162eb1a04e` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `6f6d854012` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `f05a7e8889` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `3067e59b71` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `f2414e294c` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `45f3676225` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `0f99dfb570` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `16b4d1aeeb` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `0120b86ffb` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `6ff02a679c` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `e870e85b22` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `fe14c826de` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `353cac1739` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `c037a38fcd` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `f2ee1ef867` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `d0c5091a51` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `5c43a60581` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `04fc4f6f4e` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `1ce1ecf2ff` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `094fe30eba` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `e21527131e` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `a9f1afa3b7` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `2ca966bb8b` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `27835ef2a1` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `544d029721` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `2140538277` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `3bae33f9a2` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `88fb8ad536` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `54491b353f` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `930d1cdb22` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `1215e5c3dd` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `e48a4797f0` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `0543e52229` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `8a478d8338` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `7f993f1dff` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `32867d6570` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `9e310d2ab4` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `7190e6fc80` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `89d91c32a1` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `af275489a5` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `b4c8acfb6e` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `ba91f32e99` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `324b9427d4` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `ca6c88726e` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `75ba33ed3c` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `a2943aa2b8` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `9635078fdb` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `d2405bae67` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `17b21761cc` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `a8a0010f34` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `517c2a43bf` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `5191f05192` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `72870eaf11` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `e792222ab9` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `8615f8c873` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `d1b7a4b5b6` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `144670ce25` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `61360d40c7` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `cb3cf245d1` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `9f11d2007a` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `a052d28933` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `d4425f34c8` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `375bfb71f8` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `cd39d0a0bd` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `72fe46205d` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `11ebb326b9` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `9069200386` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `35c4418edd` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `0874a872ca` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `876c613d1c` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `fe397dfba8` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `bb7fd090f7` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `cde9ae64d2` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `bb6bf70bf8` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `9fb9b92ad5` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `3186fc2d37` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `745e6fb834` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `96bacb4085` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `8220300ad0` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `9119d3804a` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `cc73d85007` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `1ee0813a8b` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `1b1d6e05ab` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `170e6fb655` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `64d3e9dc22` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `a4fc35f6ce` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `39d6ec7938` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `7392cc56b8` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `b533429dab` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `ceaffc8c32` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `34888e5113` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `890126579b` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `d3d3769258` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `caf428a9d6` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `84ce211b78` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `681a2573b7` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `84d5116beb` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `1746d4b6eb` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `f5ca1ce74f` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `25eb9e757f` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `f91dfab3cc` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `3b30b2db54` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `e630225664` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `b7d0aae602` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `8767daa0b4` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `65530e7c38` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `385b96f67b` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `6b38aac19b` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `40220b18a0` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `b491342e0d` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `7bdeef20e4` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `d5cde6069e` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `f317c0dcfe` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `f0234cb8b9` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `20d9286666` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `e9e27195f1` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `f1a0470f7f` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `b8950f86e8` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `5ba9d58b8b` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `968d6fbd4e` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `4f6dcfe161` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `e030c86751` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `df3288c6e5` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `58b2b34c6e` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `b4ccbe3b60` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `20e00aee5e` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `af6c8d07d6` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `d9c8b6eba5` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `0812577ca8` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `b364eb6b03` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `89bdf8edbb` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `e124eb0130` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `f4b1cdb60a` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `b3ab8c0383` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `03e1fcb8c7` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `38bfaa62ba` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `c2879ce062` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `5aee38c247` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `11cfb7899f` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `65d29390db` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `66d691461a` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `8ba5d254aa` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `8f3d3b48b9` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `734eb3ee9c` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `1ef9f8733d` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `2873a7cdaf` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `451a636a3d` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `718b292eb7` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `3bd621b42b` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `63f0a2f7f8` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `0e7f1e2174` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `bdaf58732e` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `96d0c84668` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `475161b764` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `b3b6397e75` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `6b2d9506e2` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `03e21e7805` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `477fed24ca` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `e4eb7e0865` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `90e5b24873` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `a30fe975a9` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `b72fe7b7ee` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `1e01a96137` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `e389e21c60` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `56c7d2b6ea` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `c1f61280c8` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `11d20d23a3` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `8375550653` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `394131ffa9` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `bb718c7051` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `3a341cc5fb` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `648415f642` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `07e384877c` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `2b6489592c` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `19594c96a8` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `84a97f2e29` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `b2b7f9849e` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `3a7bc6f890` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `6ce15630f2` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `bb3e798d35` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `fadafbedee` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `60c46a5e85` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `2a68626523` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `e9dc031e8e` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `677a13443c` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `931068aba4` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `ed475c4a72` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `6e77209ffc` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `131ec4488a` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `5660172534` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `c8688ad073` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `4d2b421ea6` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `f5d5c0297c` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `7860336c70` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `f8b44de8d2` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `ae958b2b77` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `d3d9efd0e5` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `a435b9fe37` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `09e7020a1c` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `2ff64040fe` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `6842bc8626` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `b06bb67490` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `23851623e5` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `306b775d91` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `3944e513a3` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `5ad8a1ee03` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `fbe1afe542` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `9825257dda` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `53da75c39b` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `a615a98252` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `0a2e02533d` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `6410ad081e` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `7b0eff298e` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `5e76ecd58b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `62a86abdc9` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `886b4b98f2` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `7f4ef8fe2f` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `7daa8dd2fd` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `6f5a5fec6a` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `eaeb0f7b6b` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `d4accb5f8d` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `122cbf6bb9` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `27e62f12a6` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `d072c85019` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `b87acea62f` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `b8f02027ea` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `f5da229d0b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `0b1a5938c7` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `813764b83c` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `f153c373b1` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `b8cb84021b` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `4b1a82d2ed` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `e02a4358d8` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `e8f552c76a` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `d1d334130b` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `aec9f611c9` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `2cd03528b2` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `53ba35fa38` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `f6f03e65f9` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `691d0ee437` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `b70e3fa5a6` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `f7a8b299a6` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `03a6d40fd2` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `4cbb52e882` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `e441e45ab6` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `647a711c3f` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `603b5d9123` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `ec387444af` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `afed4dd398` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `d042bd9165` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `760c0f6448` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `52e9e94d6d` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `0934854bbe` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `168af5a922` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `074ae2c834` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `c30f815dc7` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `b21dc88587` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `721a52f369` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `1f22ff11a5` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `85684e8dea` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `f56545210a` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `4596142553` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `1c8b2234d4` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `48f3f5c56c` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `0c687fc809` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `d0918229ab` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `9e720f4bba` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `235767533a` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `cb693d3fc3` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `0934f7a1fa` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `74fe23944e` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `94bcff80b4` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `05e7eba7ff` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `7b2dfc1c44` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `489a4207d5` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `ae01b8bf92` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `638ce76d26` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `b76dffd367` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `e7280a98ac` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `d399acc2d5` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `ba4c4ec124` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `eb8c73759f` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `0b629a7aed` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `10f998125f` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `50ac848f9a` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `2ac725ba5b` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `3ee13c9e90` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `3e22c83170` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `f694f85ff9` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `f2362a1251` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `d165ce3347` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `00700cc1c0` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `5f8bb69caf` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `2b3886f8b1` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `cb7ac2c27e` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `39719018ba` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `bb6ae0da6a` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `fbc11c9593` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `67c3d385a2` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `d0e6b770f3` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `77ce233d8d` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `227bab050c` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `c67e8d9376` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `6e12e080e7` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `2c0f8a301d` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `1e9d3ae16e` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `5287ad53c6` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `7ec2b06c93` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `575a8dad72` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `a8108fce10` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `8a5c002049` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `4f8ce716d9` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `fabafc0c19` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `dcbd0b0153` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `cb2afd2303` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `79821fc329` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `d85c456e3e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `c4a186c843` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `77c924149d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `4ffe88936e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `ae1b5a32ef` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `6756b2a801` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `928e6e543c` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `c59c55bed7` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `ea19ad3d98` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `7782e17c51` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `116213bd06` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `c47240c531` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `e6c46239ec` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `546b4ca5fa` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `af87762fcf` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `3924f1c673` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `5c8d80d6f8` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `ad7d1633aa` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `c1f1fdae09` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `0d4a8448d9` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `78d43de1ea` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `24ecfffc28` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `f23ebc0d09` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `fcddef2f39` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `c7731b619e` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `81daacd909` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `c9c4c452ab` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `b399c27144` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `c26cfecade` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `a83cf44017` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `33e30be135` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `b536b0fbc7` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `f3d3473303` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `17d7288d6e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `7d18ee82a1` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `1dc59f2665` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `a5c23f3b80` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `7a7b2c369f` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `7f52767408` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `7efe534263` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `3bb70bd09f` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `3c60646591` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `930c6dd069` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `cb1e6c6959` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `10476099bd` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `fc265b60ef` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `b4e3ff1131` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `594914f790` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `772eaa8b88` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `6e0155abfd` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `8bfae7c3eb` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `8f6b1a9a59` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `9f72e44270` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `be61812b3c` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `dec506c512` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `70cfe3589b` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `c9c5293a20` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `f6cdaed3e3` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `a1a28082cd` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `5d04b02ea9` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `b8049a57ca` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `d9c8e2f981` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `470ec4e3d4` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `485db593aa` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `44cf8cb2e0` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `3dda3d7d17` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
