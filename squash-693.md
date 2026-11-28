# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 680 commits of `errs/full-testable-lsp-20261009` over `master` (tip `4e56e17585`, master `e6116f2eb0`, 2026-11-28), numbered 1 to 680 from the oldest, with their short hashes. Commits 1-397 were planned on 2026-11-23; the rebase onto master of 2026-11-25 kept their numbers and gave them new hashes. Commits 398-680 came after, and were placed by the same rules. Three are open on master as their own PRs, and each takes its commits out of #693 when it merges: 458, with 499, 521, 644 and 652 its review rounds, is #802; 196, with the test fixtures of 194 (`Await` and `Watchdog`; 194's edits of #693's own tests stay), is #811; and 170 and 198 are #812, on #811's branch, as their tests wait through its fixtures. #813's four commits, 407, 465, 470 and 613 then, merged to master as ede5667376 on 2026-11-27; #693 was rebased onto it without them, which renumbered the commits after 407. #804's four commits, 467, 471, 473 and 474 then, merged to master as 2e26663249 on 2026-11-25; #693 was rebased onto it without them, which renumbered the commits after 467. #808's three, 481-483 then, merged as fb7ba82052 on 2026-11-26, and #693 was rebased onto it without them, which renumbered the commits after 480. #810, the plugin installable in every JetBrains IDE, merged as 4350e40af0 the same day; #693 was rebased onto it with no commit dropped, as its own fuller change (Gradle optional, not removed) is commit 530.

It proposes 15 pull requests, each of which changes either only files outside lang/ (xvm only: X1-X5) or only files in lang/ (lang only: L1-L10), merged in one order. To get there, the commits are first sorted into 13 topics (P1-P13), the commits of a topic that implement or fix the same thing, however far apart, are grouped to be joined, and every commit is then divided at lang/: its other files go to its topic's xvm PR, its lang/ files to its topic's lang PR.

## The pull requests, in merge order

| Order | PR | Changes only | Title | Prerequisites | Built from topics | Commits with a part in it | Added lines: main / tests |
|---|---|---|---|---|---|---|---|
| 1 | **X1** | files outside lang/ | Compiler diagnostics: the error-listener contract and repository read failures | — (master) | P1 | 12 | 2.9k / 2.8k |
| 2 | **X2** | files outside lang/ | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | X1 | P2, P11 | 15 | 5.7k / 1.1k |
| 3 | **X3** | files outside lang/ | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | X2 | P3, P4 | 8 | 5.8k / 1.7k |
| 4 | **X4** | files outside lang/ | Runtime support for running, testing and debugging from the editors: the debugger and its agent, xtc, xunit and the Gradle plugin | X3 | P9, P10 | 54 | 9.6k / 5.3k |
| 5 | **X5** | files outside lang/ | Build, CI and test tooling outside lang: CI test tiers, build checks, test hang detection, the LSP4IJ and IntelliJ versions, PR previews | X4 | P4, P7, P12, P13 | 29 | 1.7k / 1.0k |
| 6 | **L1** | lang/ | Language server: the server, its protocol, document synchronization, Tree-sitter and the mock adapter | X3, X5 | P4 | 58 | 12.2k / 15.7k |
| 7 | **L2** | lang/ | Language server: the compiler adapter's core, compiling, discovery, diagnostics and workspace pulls | L1 | P1, P2, P3, P4 | 49 | 9.7k / 17.6k |
| 8 | **L3** | lang/ | Language server: the semantic model and read-only editor features | L2 | P5 | 46 | 7.5k / 8.2k |
| 9 | **L4** | lang/ | Language server: code actions and refactorings | L3 | P6 | 49 | 10.1k / 14.6k |
| 10 | **L5** | lang/ | Language server: the XDK's library sources and the XDK workspace | L3 | P11 | 23 | 3.1k / 3.3k |
| 11 | **L6** | lang/ | IntelliJ plugin, on LSP4IJ 0.30.1 | L4, L5, X5 | P7 | 80 | 10.5k / 6.6k |
| 12 | **L7** | lang/ | VS Code extension | L4, L5 | P8 | 13 | 2.7k / 379 |
| 13 | **L8** | lang/ | Embedded build, run and tests in the language server and both editors | X4, L6, L7 | P9 | 58 | 10.5k / 9.7k |
| 14 | **L9** | lang/ | Debugging: the DAP server, debug sessions, launch and attach, and both editors' debug UI | X4, L8 | P10 | 49 | 8.7k / 11.1k |
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 252 | 11.0k / 68.9k |

Already open on master, apart from these, each leaving #693 when it merges: #802 (unused constructor arguments, commits 458, 499, 521, 644 and 652); #811 (a hasher map's spare key per thread, 196, with 194's test fixtures); and #812 (Future callbacks and service shutdowns reported and completed, 170 and 198, on #811). #804 (runXtc keeps xtcRun's settings), #813 (the Gradle model's build-tree paths and run settings, and its reports written whole, merged as `ede5667376`), #808 (the plugin tests' one network download dropped) and #810 (the plugin installable in every JetBrains IDE) are merged; #693 is based on master `e6116f2eb0`, Dependabot's #818 and #819 after #813, since its rebase of 2026-11-28, which dropped no commit.

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
| P1 | Compiler diagnostics: the error-listener contract and repository read failures | master | no | 17 | 4 | 3.1k / 3.3k |
| P2 | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | P1 | no | 8 | 4 | 5.8k / 3.1k |
| P3 | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | P2 | no | 6 | 2 | 5.7k / 2.0k |
| P4 | Language server core: protocol, document sync, compile pipeline, discovery, diagnostics | P3 | yes, once 5 commits' other files move | 62 | 16 | 21.6k / 30.7k |
| P5 | Language server: semantic model and read-only editor features | P4 | yes | 45 | 8 | 7.6k / 8.2k |
| P6 | Language server: code actions and refactorings | P5 | yes | 48 | 10 | 10.1k / 14.6k |
| P7 | IntelliJ plugin, built against LSP4IJ 0.30.1 | P6 | nearly | 79 | 28 | 10.6k / 6.7k |
| P8 | VS Code extension | P6 | yes | 12 | 7 | 2.7k / 379 |
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 62 | 22 | 11.6k / 10.7k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 62 | 36 | 17.3k / 15.3k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 24 | 9 | 3.5k / 3.5k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 205 | 23 | 5.6k / 69.3k |
| P13 | Documentation, build and CI | the PRs it documents | no | 50 | 6 | 6.9k / 376 |
| | **Total** | | | **680** | **175** | |

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

Commit 8, `f28b38ee2d` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `18c3228152` | 366 `f8a769187a` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `dafb36558a` | 345 `27d06697ce` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `70ca4b321d` | 374 `0fd5b84af6` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `88465c33d3` | 369 `8231d15ed5` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `1d2a1042fc` | 367 `9a0185bf27` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `a5d102e128` | 363 `3f23961c0f` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `40e2b9c224` | 373 `78271d108e` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `e4ff6b9628` | 362 `fd437a3de1` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `eff77b33d4` | 360 `23aa1c9820` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `1ebac9849c` | 112 `4e7b6a8d0d` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `38aee8c12f` | 105 `d6b650995e` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `f28b38ee2d` | 36 `3f118fe020` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `f28b38ee2d` | 387 `ddf72703b5` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `718047b06f` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `82094d8238` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `35d52ccd11` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `3ff67efef1` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `11ce7e7b05` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `04e139d2a6` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `9ed6223ade` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `779f667500` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `7f909a6c8e` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `d38a9855c2` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `f504a0c129` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `ab60b71ef0` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `2d361a5f2c` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `2e50de1167` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `81c6149665` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `83a67a231f` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `1a1ece9c9a` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `9200b87c9c` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `9a784acfe3` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `88651bde25` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `044d1da637` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `f4e5ba88bf` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `102ae10142` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `5cd4bd00e3` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `c937d5be47` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `cafebc4604` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `b66d208ee5` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `34ce1b68a5` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `9124f850dd` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `43cd2fe1e0` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `12597601f2` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `23ccab0bc4` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `e1a093c21a` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `1ae8a7540e` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `37c9eeaed8` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `aea72f5e3c` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `433c5110cc` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `25b317db09` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `d4f805d572` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `2a8b870303` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `0250b0a36d` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `cda974049e` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `98a2930d1d` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `9f228873ba` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `38aee8c12f` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `f79982f235` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `b3c40b255e` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `53a774ec12` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `f0faa4be4f` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `d6b650995e` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `1ebac9849c` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `cecf45b77c` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `93e89acd06` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `4e7b6a8d0d` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `f4d9a2dfec` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `0ea8397c16` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `08c6665129` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `e94edcfd59` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `2e36458e6f` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `23e5ae114d` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `3cc5c3b7a0` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `501afbfd56` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `c01d1d30d7` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `40e2b9c224` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `8421ff1b6e` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `70ca4b321d` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `4e701a5beb` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `13e8345381` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `e07b4d6543` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `00d7144d16` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `7c893c20f3` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `b5af38360c` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `e44c430a0a` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `1efbebf461` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `f112e0ae8c` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `1ae3230d48` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.
- 400 `85eb64bc6a` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82), in P7 (+272; also touches: playbook)
- 409 `3e03a4f116` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774), in P9 (+114; also touches: playbook)
- 414 `aa87039c36` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries, in P9 (+55; also touches: playbook)
- 420 `10f42095cb` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive, in P7 (+141; also touches: playbook, server, vscode)
- 421 `cc73af6f95` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded, in P9 (+220; also touches: playbook)
- 422 `cd0b6055b6` Show the embedded compiler as not available where the project's own modules replace bundled libraries, in P9 (+133; also touches: playbook)
- 423 `2d4bed701c` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours, in P13 (+754; also touches: intellij, vscode)
- 424 `a2b46bd38b` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy, in P7 (+277; also touches: playbook, server)
- 425 `13d87e25dc` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run, in P13 (+193; also touches: intellij, playbook)
- 426 `458afa9e50` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide, in P7 (+73; also touches: playbook)
- 427 `182e532d34` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own, in P9 (+447; also touches: playbook)
- 428 `4de42c5ca8` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature, in P13 (+251; also touches: playbook)
- 429 `6200c1fdb0` Move the user guide into lang/doc/user-guide, with its screenshots in images, in P13 (+996; also touches: intellij, playbook, vscode)
- 430 `c121913565` Let a debug launch or attach set how long evaluations and lazy values may run, in P10 (+716; also touches: playbook)
- 431 `0b0ce276a9` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode, in P12 (+5; also touches: build, intellij)
- 432 `11f671cedc` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc, in P9 (+485; also touches: playbook)
- 434 `5c6c79f60d` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting, in P13 (+125; also touches: playbook)
- 435 `7f99ad861a` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed, in P6 (+589; also touches: intellij, playbook, vscode)
- 440 `2986023105` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration, in P5 (+110; also touches: javatools)
- 442 `5b4df56b47` Bind the names of a method's multiple return values to the values they declare, in P5 (+65; also touches: javatools)
- 443 `3a260c2d73` Bind this in a lazy property's calc() and super in a method reference to what they name, in P5 (+147; also touches: javatools)
- 444 `36a70b0b8d` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed, in P6 (+275; also touches: playbook)
- 453 `6883fb4803` Keep the initializer facts of a property declared in a loop's body, such as a static local, in P5 (+47; also touches: javatools)
- 458 `828ac6bf62` Report constructor arguments and default values that no constructor uses, in P1 (+129; also touches: xdk)
- 460 `f9c9436b31` Digest the API of each declaration of a module apart (DeclarationDigests), in P11 (+622; also touches: javatools)
- 462 `73afd92725` Keep diagnostics working when a restart opens another server's library copy again, in P11 (+294; also touches: playbook)
- 464 `b14af2e869` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors, in P9 (+1,453; also touches: playbook)
- 467 `3d94fa09c3` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own, in P11 (+1; also touches: javatools)
- 469 `010c1e3b2d` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages, in P13 (+1,264; also touches: intellij)
- 470 `a2c08a81a4` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered, in P12 (+141; also touches: intellij, server)
- 490 `4e932cd6b7` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run, in P12 (+367; also touches: build, intellij, vscode)
- 499 `c698badfc1` Allow contribution arguments and shorthand defaults that match the explicit constructor, in P1 (+400; also touches: xdk)
- 509 `cff2383439` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed, in P13 (+17; also touches: intellij, server)
- 510 `18a95abb47` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops, in P13 (+16; also touches: server)
- 511 `96bbf89ebe` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate, in P13 (+2; also touches: server)
- 512 `8721ab8e95` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair, in P13 (+60; also touches: intellij)
- 513 `50d00b8192` Share the language server's location requests and edit conversion, and say the semantic tokens setting once, in P13 (+32; also touches: server)
- 514 `1231d6668d` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end, in P13 (+40; also touches: server)
- 515 `88e1ba935c` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias, in P13 (+68; also touches: server)
- 517 `9be28e81f1` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode, in P13 (+7; also touches: vscode)
- 518 `edfc757e3a` Share the language server's cancellation-owning future and its formatter's parenthesis search, in P13 (+23; also touches: server)
- 522 `df5672d9a5` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume, in P12 (+57; also touches: intellij)
- 523 `5bf3d9d63a` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share, in P12 (+14; also touches: intellij)
- 526 `1410c164d3` Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too, in P12 (+23; also touches: intellij)
- 530 `1e703ae793` Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed, in P7 (+10; also touches: playbook)
- 532 `2ee42f7191` Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times, in P12 (+15; also touches: intellij)
- 533 `53900f21cb` Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent, in P4 (+285; also touches: dap, intellij)
- 534 `843d6b4ac8` Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire, in P4 (+1,129; also touches: dap, intellij, playbook)
- 535 `edd91a43af` Share the xtc.* keys and a build's summary text through lang/protocol, in P4 (+188; also touches: intellij, playbook)
- 536 `6a264f7b27` Share the compiler's library settings through lang/protocol: their shape and how a path names a file, in P4 (+224; also touches: intellij)
- 543 `c8594e4d29` Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps, in P12 (+52; also touches: dap)
- 545 `d11635dc79` Share the plugin unit tests' disk files, connection, waits and opens through LightEditor, in P12 (+125; also touches: intellij)
- 546 `89c25e6495` Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup, in P12 (+201; also touches: server)
- 547 `9b3a04509b` Share the lsp-server adapter tests' source writer and text edit application, in P12 (+190; also touches: server)
- 548 `4032292cff` Share the embedding's incomplete analysis in the header tests, and merge their identical cases, in P12 (+137; also touches: server)
- 549 `e53223bfbe` Shorten the code action tests with the shared edits and per-class helpers, in P12 (+81; also touches: server)
- 550 `69f25a354c` Share an adapter with a module compile hook among the tests that count or hold compiles, in P12 (+84; also touches: server)
- 551 `75f3a1bfab` Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures, in P12 (+121; also touches: server)
- 552 `e0552d4d5e` Merge FormattingConfigRoundTripTest's mock clients and server setup, in P12 (+48; also touches: server)
- 553 `6e84d619db` Apply the missing member, extraction and initializer tests' edits with the shared apply, in P12 (+7; also touches: server)
- 554 `9e2ffa46f0` Check XdkMemberUseTrackerTest's renames of foo through one helper, in P12 (+36; also touches: server)
- 555 `9c611b8f02` Share the marker comment position of the navigation, lookup and occurrence tests, in P12 (+77; also touches: server)
- 556 `196b62522d` Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file, in P12 (+91; also touches: server)
- 557 `f6a665aef8` Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know, in P4 (+31; also touches: intellij)
- 565 `29589abc34` Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7), in P5 (+677; also touches: intellij, vscode)
- 566 `7acd2e83f2` Say, when a rename leaves string literals and comments that spell the old name, how many and where, in P6 (+232; also touches: playbook)
- 567 `af9f4b6ef8` Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording, in P12 (+10; also touches: intellij)
- 568 `84548950cb` Terminate the extension's tasks also when the terminate comes before VS Code has started them, in P9 (+91; also touches: playbook)
- 572 `96a8bd8100` Build the playbook container's XDK first when the slot's build is of other sources than the checkout's, in P12 (+85; also touches: build, intellij)
- 573 `9ba8d81862` Let playbook container runs take the first free slot, in the order they asked, instead of their checkout's slot, in P12 (+123; also touches: intellij)
- 588 `42a9dc4740` Find the XDK's declarations in Go to Symbol in Workspace, after the workspace's own, so that X356 passes in both editors, in P11 (+263; also touches: playbook)
- 590 `9609d90de3` Press Ctrl+Alt+X, B and Ctrl+Alt+X, R as keys in both editor tours, and check what each does, in P12 (+79; also touches: build)
- 600 `0f613894a4` Remove the Gradle caches and wrappers of other Gradle versions from a container slot before its run, in P12 (+16; also touches: intellij)
- 602 `472f8be71c` Make lang's three copies of Awaiting.kt one test fixture, org.xvm.testing's, in a new project, lang/test-support, in P12 (+252; also touches: build, dap, intellij, server)
- 603 `b8fa851cca` Say in VS Code why a rename is refused, as IntelliJ's plugin does, instead of VS Code's "No result.", in P6 (+101; also touches: playbook, vscode)
- 609 `90cf6a69bb` Copy back only the VS Code runs that a container made, not every run its slot's work volume keeps, in P12 (+27; also touches: intellij)
- 610 `ee093bcbef` Answer a rename that can't be proven, or that a change overtook, for what it is, and let a cancel reach a plain rename's proof, in P6 (+584; also touches: intellij, playbook, vscode)
- 612 `0a9cdf91c8` Show nothing in IntelliJ for a rename that the user cancels, not "Rename failed: null", in P6 (+33; also touches: intellij)
- 613 `713113f100` Ask the server again for a move's edits when a document's release cancelled or overtook its proof, not move without them, in P8 (+74; also touches: playbook)
- 618 `7ba13f7924` Run the server's suites before a full VS Code playbook run only when asked, and print the icon task's files only on request, in P12 (+19; also touches: vscode)
- 619 `5ed10ac44d` Drop the IntelliJ plugin's own save of the closed documents a rename changes, which LSP4IJ 0.30.1 does (UP21), in P7 (+37; also touches: playbook)
- 620 `75c281f2af` Give the playbook container a session bus and a stand-in system bus, so VS Code's Chromium stops logging D-Bus errors, in P12 (+14; also touches: intellij)
- 624 `8d42d3c033` Run the IntelliJ playbook's harness tests in a category of their own, first in every playbook and tour run, in P12 (+30; also touches: intellij)
- 630 `4836402abe` Let a rename or a move in VS Code outlast the release of a document whose text its file has, as the server's proof does, in P6 (+48; also touches: playbook, vscode)
- 632 `3b9d861978` Give the editor the keyboard in VS Code's playbook again until it takes, and wait for the workbench's widgets while it works, not for fixed seconds, in P12 (+318; also touches: vscode)
- 634 `32447330e1` Report a VS Code playbook case that fails with a known host bug's exact symptom as a known host failure, apart, and leave the run green, in P12 (+155; also touches: build)
- 635 `2a1f1ccb0a` Run VS Code's playbook and tour harness tests in a group of their own, first in every playbook and tour run, not in the extension suite, in P12 (+167; also touches: vscode)
- 638 `edc0218d98` Read .editorconfig files in IntelliJ only while IntelliJ applies them itself, as its EditorConfig switch says, in P5 (+142; also touches: intellij)
- 640 `ff4d943f30` Apply a file's end_of_line from .editorconfig in VS Code's Format Document, which no formatting edit can do, in P5 (+93; also touches: build, playbook, vscode)
- 641 `168c36cd01` Keep the lines between IntelliJ's formatter markers as they are, and read the wiki's continuation_indent_size too, in P5 (+421; also touches: intellij, vscode)
- 642 `24a9e14a3e` Pad the aligned Markdown tables under lang/ so their columns line up again, in P13 (+101; also touches: vscode)
- 646 `6da271abc3` Refuse embedded builds and runs of a project on another XDK, and tell the editors which folders build with Gradle and why, in P9 (+663; also touches: build)
- 647 `aecbf2e393` Build a folder on another XDK with Gradle in both editors, and say which XDK it builds with, in P9 (+315; also touches: playbook)
- 651 `b21e47e990` Answer a file move that can't be proven, or that a change overtook, for what it is, as a rename answers, and show its reason in both editors, in P6 (+394; also touches: build, intellij, playbook, vscode)
- 652 `93607a6335` Move the negative constructor argument tests to the XDK's compiler-errors suite, in P1 (+105; also touches: xdk)
- 655 `bacf6901cf` Run a module's Gradle tasks in its own folder's Gradle build in a VS Code window of several folders, in P9 (+119; also touches: playbook)
- 656 `270ae0da2f` Add X361–X366 to the VS Code playbook, in P12 (+391; also touches: vscode)
- 658 `9b2b5639ce` Run an included build's Gradle tasks in VS Code from the build that includes it, as its imported model says, in P9 (+274; also touches: playbook)
- 659 `ffe3ea2a75` Ask which folder's build runs Run Module's module when no folder holds it, in a VS Code window of several folders, in P9 (+68; also touches: playbook)
- 660 `37b1a2a181` Give VS Code's notice that a folder builds with Gradle once a session for each folder while it is in the window, in P9 (+80; also touches: playbook)
- 662 `c46313997b` Give an Ecstasy file .editorconfig's end_of_line at IntelliJ's Reformat Code, and check X363's unchanged formats through it, in P5 (+165; also touches: intellij, playbook)
- 670 `e4ddf64d7a` Keep a rename's proof current through a document opened with its file's text or deleted before the request, in P6 (+217; also touches: intellij)
- 671 `589c0157de` Refuse a rename whose edit reaches a document with unsaved changes that the server never read, saved since or not, in P6 (+72; also touches: intellij)
- 675 `6d7896c5be` Write the narration of the IntelliJ and VS Code tours, a paragraph per section and step, in P12 (+1,137; also touches: intellij)
- 676 `6dd395f6f8` Narrate editor tours with local voices and synchronized subtitles, in P12 (+628; also touches: intellij)
- 677 `927278d5ce` Count xtc/endOfLine among the server's xtc messages in the plugin's protocol endpoints test, in P5 (+1; also touches: intellij)
- 678 `614e2dc248` Prove each edit of a rename or a move against the text the server computed it from, by its hash, in P6 (+683; also touches: build, intellij, playbook, vscode)


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

**Builds on** master. **Commits:** 17 now, about 4 after squashing. **Added lines:** 3,198 main, 3,352 tests.

| Join into | Commits | Why |
|---|---|---|
| Keep, cache and report module repository read failures | 1 `91e55b3246` Preserve module repository read failures without blocking usable candidates<br>9 `3ff67efef1` Report repository read attempts without inferring dependency failures<br>11 `04e139d2a6` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `03a30ed4bc` Separate diagnostic reporting from listener abort policy<br>3 `09a9a461de` Require explicit compiler listeners and reasons for silence<br>4 `254cff197c` Scope parser diagnostics and restore validation listeners on every exit<br>19 `2fa057ef2c` Route repository and runtime reports through error listeners<br>41 `95f6fcbadc` Stop the lexer at a source's end and keep diagnostics that only look like duplicates<br>465 `213b4df252` Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType()<br>466 `59061e49ec` Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `23a46109b8` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `2160d5d5a3` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |
| Unused constructor arguments and defaults (open PR #802) | 458 `828ac6bf62` Report constructor arguments and default values that no constructor uses<br>499 `c698badfc1` Allow contribution arguments and shorthand defaults that match the explicit constructor<br>521 `d63abfd525` Test that a class without constructors compiles and uses its contribution's arguments<br>644 `8aa286f978` Say plainly what is unused and what to do in COMPILER-188 and COMPILER-210<br>652 `93607a6335` Move the negative constructor argument tests to the XDK's compiler-errors suite | already open on master as #802, with 499, 521, 644 and 652 its review rounds; they drop out of #693 when #802 merges |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `0d21f86156` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `718047b06f` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `e92f1bd24a` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `82094d8238` Expose compilation-owned syntax and resolved semantic facts<br>10 `11ce7e7b05` Clarify tooling contracts and isolate partial AST helpers<br>12 `8f3a41c09c` Verify compiler output without binary AST dependencies<br>115 `3941808e3e` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `9ed6223ade` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `35d52ccd11` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `567f9c59c1` Complete a member access typed at the end of a line<br>144 `c9da9a782a` Complete a member with the cursor right after its dot<br>161 `e94edcfd59` Complete names and show a call's signature where they are typed at a line's end<br>385 `29714dd46a` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `d38a9855c2` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 62 now, about 16 after squashing. **Added lines:** 21,554 main, 30,768 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `7de05be6a9` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `0905df53f3` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `bca7a5c45e` Let two run tests check what they claim instead of how long they wait<br>301 `5f1684c097` Make the retention test's cancellations and the library content test's refresh check independent of timing<br>559 `b4aa2d623d` Read XdkPullDiagnosticsTest's traced answers in the order their queries came, not the order their ends were written | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `f28b38ee2d` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `3f118fe020` Remove the document-color prototype<br>266 `d2cfe04526` Remove the unused xtc/runnableTargets request<br>387 `ddf72703b5` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `2d361a5f2c` Keep documents current and the server responsive, and name its own diagnostics<br>28 `69c67244c2` Judge file events against a running analysis and apply them in order<br>92 `18f7d2b6bb` Analyze again an open document whose analysis was retired without a replacement<br>184 `002235d873` Answer requests that a restarted client sends while it opens its files again<br>537 `53947722d4` Answer xtc/tests when a document's close retires the compile it waits for (X342)<br>538 `288c79400f` Keep listed code actions resolvable across a close that leaves a document's text as on disk<br>587 `3b4d6d9286` Answer Find References again when documents only opened or closed while it ran, not with the cancellation that showed X251 none<br>628 `e43a28e92f` End no workspace request when the editor releases a document outside the graph, or closes one the server does not have open<br>629 `94042face0` Pull a workspace document's diagnostics without waiting for a library source's compile, which Go to Definition starts<br>631 `d98d8a3f5e` End no workspace request when VS Code releases a library's source that Go to Definition opened<br>649 `67e1eb487f` Keep workspace requests and analyses through the close or open of a graph member with its file's text<br>650 `25f7c009d8` Answer a request whose work the server retired for a change with ContentModified, and a document's diagnostic pull with a cancellation<br>664 `d796688609` Let the queries on a module's compile go on when a member's open with its file's text replaces that compile<br>665 `86215b657d` Keep a document's own requests through its release or its open with its file's text, and say in ContentModified what overtook a request<br>666 `5adea5a94b` Let a request share a pending graph query that asks what it asks of the same inputs, instead of taking its place<br>667 `240b7c7131` Test that a shared graph query ends only with its last request, and end a closed file's query that no analysis answers<br>674 `002ec2cf1e` Test that a watcher's creation and deletion of a closed member come and go in the workspace's pull, in and out of the graph<br>679 `44b82b2c8e` Recheck the sources of open modules outside the graph at each workspace pull, for a change that no watcher reported | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `a773f54012` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `425f58cd27` List a closed test module's tests from the workspace pull's compile<br>237 `aee11086ab` Clear a deleted file's diagnostics in the next workspace pull<br>248 `d625883267` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `0de4d19de7` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `18252e3985` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `74ea340e42` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `9cccf5dd4d` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `46430dd4e7` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `68420437c7` Split the LSP tests into a fast default tier and complete runs<br>143 `cadc25167e` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `88651bde25` Declare fields first and use plain names in the new classes<br>95 `8900ea8af2` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `df0d19e8d4` Compile a closure as a fold, without mutable accumulators<br>137 `6b079e4692` Name the properties that hold behavior or mutable state in camel case<br>44 `38de7e0bdd` Synchronize the semantic token history that concurrent queries share<br>163 `c54618fd30` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `5cd4bd00e3` Keep the build's jars whole and its test runs focused<br>97 `b060dce4c2` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |
| Time and heap logs of the server's work | 418 `ae73ae4efa` Log each project query's and each compile's time and heap<br>419 `259b3c7f74` Log what the server did in each minute in which it worked, and over its session | the server's own logs of what each compile, query and minute of work cost |
| Trace less: one end per request, answers only when changed, one start and end per discovery | 491 `502e30adf7` Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase<br>492 `d0bdbeb88f` Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count<br>493 `ebe81e4f6d` Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG | the INFO trace keeps a start and one end with the phases' times; DEBUG keeps the phases and writes a pull's answer only when it differs |
| One xtc/ protocol, shared by the plugin and the servers: lang/protocol | 533 `53900f21cb` Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent<br>534 `843d6b4ac8` Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire<br>535 `edd91a43af` Share the xtc.* keys and a build's summary text through lang/protocol<br>536 `6a264f7b27` Share the compiler's library settings through lang/protocol: their shape and how a path names a file<br>557 `f6a665aef8` Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know | the plugin and the servers each kept their own copy of the xtc/ extension's interfaces and wire types, TestEvent, the xtc.* keys, a build's summary text and the library settings, and the copies had drifted; lang/protocol is the one copy (compileOnly on LSP4J and Gson), which the plugin ships as its own jar; 560 lets the plugin's Compiler page open when the library settings hold a key it doesn't know |

Kept as they are:

- 24 `c68680c75b` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `2e36458e6f` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `a66af7e8a4` Keep the execution trace's events out of stderr and server.log
- 173 `c40ab89730` Let one source that discovery cannot read cost only itself
- 180 `925aa8b50d` Keep a project's module, source and library order the same in every JVM
- 261 `8fbc2fb23f` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 45 now, about 8 after squashing. **Added lines:** 7,623 main, 8,120 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `4782290f4a` Complete with the language model's keywords and built-in types<br>162 `1481088eac` Complete a type of another module where a statement starts, with its import<br>185 `2e351cd76d` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `16f91fa471` Name a type by what it is when hovering its name<br>156 `87d2d5485c` Give a constructor's finally block no references lens of its own<br>157 `eb0640952d` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `9200b87c9c` Keep indentation while typing, and lay out switches as the XDK does<br>135 `5905046d59` Share the formatter's empty-block skeleton and whitespace edits<br>186 `aa1fec04e4` Answer on-type formatting without waiting for the change to compile<br>565 `29589abc34` Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7)<br>637 `9c8b89de0b` Format each file with the settings its .editorconfig files give, over the editor's, and describe the formatting settings in the user guide<br>638 `edc0218d98` Read .editorconfig files in IntelliJ only while IntelliJ applies them itself, as its EditorConfig switch says<br>639 `3d0bd10a89` Trim trailing whitespace in the Tree-sitter and base formatters only as the options ask, for a whole document too<br>640 `ff4d943f30` Apply a file's end_of_line from .editorconfig in VS Code's Format Document, which no formatting edit can do<br>641 `168c36cd01` Keep the lines between IntelliJ's formatter markers as they are, and read the wiki's continuation_indent_size too<br>643 `b646daf0a4` Ask for the trim in the server test that formats a document whose only fault is its trailing whitespace<br>662 `c46313997b` Give an Ecstasy file .editorconfig's end_of_line at IntelliJ's Reformat Code, and check X363's unchanged formats through it<br>677 `927278d5ce` Count xtc/endOfLine among the server's xtc messages in the plugin's protocol endpoints test | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |
| Bind more names in the semantic model | 440 `2986023105` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration<br>441 `c197a0ea3d` Make the _ placeholder no occurrence in the semantic model<br>442 `5b4df56b47` Bind the names of a method's multiple return values to the values they declare<br>443 `3a260c2d73` Bind this in a lazy property's calc() and super in a method reference to what they name<br>452 `3796aab8be` Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares<br>453 `6883fb4803` Keep the initializer facts of a property declared in a loop's body, such as a static local<br>454 `0bfb47be16` Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration<br>455 `297213386f` Declare the names of a function type's values in the semantic model, as of its return values in a typedef<br>456 `c310576e28` Bind a reserved name such as this:class in a property's accessor, as in a method of the class<br>457 `df3295a040` Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells | each adds a kind of binding; the compiler facts that 441, 443, 444 and 454 add go to X2 |
| The semantic model's censuses, and the bindings they found missing | 478 `82c84206d3` Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence<br>480 `5418fbbe11` Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares<br>483 `3de1a5ba9d` Count the dispatch chains that hold an unproven identity in the semantic model's census<br>484 `604d989fe6` Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder<br>486 `dd3badf857` Count the bindings that no proof can place in the semantic model's census, and fail on one<br>500 `d4fc58f456` Count in the semantic model's census what hover, definition, references and highlights miss at a name<br>501 `0b501487d9` Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations<br>502 `00ae19db9f` Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to<br>504 `65a333f6c2` Count in the editor's census the references whose declaration is in no source of the workspace<br>505 `8e943821d4` Compile the census's libraries as one workspace folder, as an editor that opens the repository does<br>560 `f3d9ca1f2d` Bind a named argument of another module's method, as inPlace in freeze(inPlace=True), to its parameter in that module's source<br>561 `3b8079bf36` Bind a formal type that a lambda names, as To in n -> n.toIntN().as(To), to the method's or the class's formal type<br>562 `1b49bd41c3` Bind a parameter of a constructor with a finally block that only lambdas in its body name to its declaration<br>563 `31cae66cbe` Place a label written with unicode escapes where it is written, in its declaration, a break and a continue | the census of the 21 libraries as a heavy test, counting dispatch chains and unplaceable bindings, and the formal children and implicit formals it found unbound; the editor's census of what hover, definition, references and highlights miss, over the repository as one workspace folder, and the parameters of a constructor with a finally block and labels it found unbound |

Kept as they are:

- 102 `5464999ec5` Link a local's ranges when the caret is just after its name
- 235 `404937e3a8` Fold comments and runs of imports with the compiler adapter
- 347 `093781a1c7` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 48 now, about 10 after squashing. **Added lines:** 10,276 main, 14,671 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `70ca4b321d` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `82d52da842` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `0fd5b84af6` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `3cc5c3b7a0` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `81bd6065b1` Prove a code action by compiling only the modules its edit reaches<br>193 `91a1d89c84` Start a revision's code action compile from the previous one's, and log its cost<br>205 `345292f65c` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `e264ef8b92` Keep the compiles of modules that compiled when only private declarations move<br>436 `26b08815c1` Start a rename's proof from the revision's compile, and compile after the edit only what it reaches<br>449 `4b072515fa` After a rename's edit, compile only the modules depending on the edited ones that it can reach | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `3c79038f1c` Safely delete an unused public or protected member<br>214 `604d925565` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `fd9b3b37fc` Extract statements that use an injected local, passing its value<br>177 `7b3ec35cab` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `044d1da637` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `880332c140` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `779f667500` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `6b681309f7` Prove a rename past a register the semantic model cannot place<br>394 `812380ca32` Refuse a plain rename that also changes the configured source modules, and say why<br>411 `4afd6bc82c` Prove a rename in a workspace module that replaces a bundled library as the workspace's sources<br>415 `e99bc7188c` Find a text's line breaks once per comparison in a rename's proof, not once per position<br>416 `5d0f16ed0c` Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes<br>417 `f5dde5a62e` Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy<br>459 `27afe6d18c` Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element<br>603 `b8fa851cca` Say in VS Code why a rename is refused, as IntelliJ's plugin does, instead of VS Code's "No result."<br>604 `cf8effbb6e` Answer xtc/rename's refusals with their reason, as textDocument/rename does, instead of as an internal error<br>610 `ee093bcbef` Answer a rename that can't be proven, or that a change overtook, for what it is, and let a cancel reach a plain rename's proof<br>612 `0a9cdf91c8` Show nothing in IntelliJ for a rename that the user cancels, not "Rename failed: null"<br>630 `4836402abe` Let a rename or a move in VS Code outlast the release of a document whose text its file has, as the server's proof does<br>651 `b21e47e990` Answer a file move that can't be proven, or that a change overtook, for what it is, as a rename answers, and show its reason in both editors<br>670 `e4ddf64d7a` Keep a rename's proof current through a document opened with its file's text or deleted before the request<br>671 `589c0157de` Refuse a rename whose edit reaches a document with unsaved changes that the server never read, saved since or not<br>678 `614e2dc248` Prove each edit of a rename or a move against the text the server computed it from, by its hash | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `36d8eda105` Offer code actions in a module that has a const with a toString<br>176 `a8ef9d39b0` Offer Create class for the diagnostic of a statement that constructs it<br>211 `670e0917fa` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |
| Say what a change to a public declaration reaches outside the workspace | 435 `7f99ad861a` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed<br>444 `36a70b0b8d` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed<br>566 `7acd2e83f2` Say, when a rename leaves string literals and comments that spell the old name, how many and where | renames, safe deletes and file moves say when a public declaration changes for modules outside it |
| A client's cancellation reaches the adapter's work | 437 `5c625704c4` Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work<br>450 `83a48a298e` Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done | cancelling a rename or xtc/build stops the adapter's work, also once a result is converted |
| Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | 479 `df667bd3d7` Prove an anonymous class's this and construction by the span of the type it constructs<br>481 `885bc9a886` Prove a union receiver's call of a method that the runtime implements, as a const's appendTo<br>482 `75d928e1ef` Prove a call on a union receiver whose legs share one route, as toString on a Point?<br>485 `555d03998a` Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method<br>487 `574992a270` Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name<br>503 `0720b66c56` Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property | an anonymous class's this and constructors by the span and the constructor they pass to, calls whose union legs share a route or a runtime body, methods merged for an annotation into a union, the Referent the compiler adds to a property by its property, and tests that the refusals that must stay are the proof's |

Kept as they are:

- 73 `9f228873ba` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 79 now, about 28 after squashing. **Added lines:** 10,551 main, 6,767 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `63666f9821` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83)<br>495 `005fd072f1` Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `aae2aa7875` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `67b8604415` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `35269c195e` Review the internal LSP4IJ APIs for 0.30.0<br>392 `0c3d0eedcb` Name LSP4IJ 0.30.1 in the plugin's README and two test comments<br>398 `77d6331787` Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83)<br>400 `85eb64bc6a` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `b029b52fdb` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `5756c29792` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `86077ea006` Let snippet completions insert as they are (UP18)<br>339 `0e5dbda079` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `d1e53d21e7` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `30efb7544f` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `38a5b21b87` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `ec1100211d` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `c94fee8abc` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `16b80afc7c` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `8bb6e34283` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `33936d9e4c` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `b9df9837d9` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `6bc4e8f8ae` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `b02e2578d4` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `dafb36558a` Drop the change a restart's retired document synchronizer still sends<br>345 `27d06697ce` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `a5d102e128` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `3f23961c0f` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `711f193294` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `88465c33d3` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `8231d15ed5` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `40e2b9c224` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `8fcac97fe7` Put the embedded build's errors in the Problems window's Project Errors<br>292 `e98f88dcaf` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `5eef6c2d12` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `e2666e1d97` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `78271d108e` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `3842c97dda` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `dda9c34b3b` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `f2149076b2` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `78431e81df` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `8eabd7e869` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `20db2122b6` Give Ecstasy files a PSI of the Ecstasy language<br>153 `c44f9812fd` Close a block that Enter opens after a { in an Ecstasy file<br>155 `6889173c7e` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `9dffd6ffee` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `210e2a7e12` Give Ecstasy its Code Style settings page<br>140 `113a319c5d` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `ffb5d202c3` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `504388fc85` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `b61c5da8b6` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `1ae3230d48` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `5791f0773c` Keep each light test's stub server and wrappers to that test<br>209 `8f06384315` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `6a143fab69` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `37ec61ad6a` Keep the stale instrumented classes of an older build off the plugin's unit tests<br>558 `bcbb23434d` Hold ClosedFileProblemsTest's later workspace pulls until its next refresh, so that none lands after its build | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `d2a2cb5f23` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `b2c4efce9f` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04)<br>619 `5ed10ac44d` Drop the IntelliJ plugin's own save of the closed documents a rename changes, which LSP4IJ 0.30.1 does (UP21) | Two comments that explain why kept workarounds stay (UP21, UP04). |
| The language server JVM's memory setting | 420 `10f42095cb` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive | a setting in both editors with its server side; divide by path |
| Every IntelliJ settings page under one Ecstasy node | 424 `a2b46bd38b` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy<br>426 `458afa9e50` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide | one Ecstasy node in IntelliJ's settings, its pages fitted to the dialog |
| Marks that LSP4IJ made before it connected a file's document (UP86) | 472 `cd331a9552` Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86)<br>488 `3d743ef456` Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap | refreshes an editor's marks once the connected document holds its own report; lsp4ij#2231 and our fix #2232 would retire it |
| Callers that a write action interrupted in the Call Hierarchy (UP88) | 506 `c0250b98dc` Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88)<br>520 `8eba9c3221` Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy<br>530 `1e703ae793` Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed | LSP4IJ 0.30.1's call hierarchy returns no children when a write action cancels its wait for the server; the provider rethrows the cancellation so IntelliJ asks again; redhat-developer/lsp4ij#2278 would retire it |
| Installable in every JetBrains IDE: no Java dependency, Gradle optional | 527 `e4e4fb400f` Make the IntelliJ plugin installable in every JetBrains IDE: no Java dependency, and Gradle only where the IDE has it<br>531 `0af1aaf6e9` Name the plugin's required-dependency test as master's #810 does, with the test runner among them | the plugin required the Java and Gradle plugins, which kept the Marketplace to IntelliJ IDEA, Android Studio and MPS; master's #810 dropped them where nothing used them, and here Gradle's three uses are optional (xtc-gradle.xml); 534 gives the dependency test master's name |

Kept as they are:

- 14 `643d520022` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `7f909a6c8e` Restart editor-owned language servers from a project-scoped CLI
- 32 `8bf519ce5e` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `b38c852d7f` Cancel IntelliJ requests whose connection ends
- 99 `66e3b0e410` Keep the language server running while a run or debug session uses it
- 130 `f318e2ee58` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `dc1ba2d8a0` Build an embedded run's process handler in one place
- 154 `08c6665129` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `be77d380a8` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `36b20c4a22` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 12 now, about 7 after squashing. **Added lines:** 2,731 main, 379 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `e506c12f70` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `007a1bbb1c` Say when a move leaves the uses of its sources as they were<br>250 `851de70399` Ask the server again for a move's edits when a change overtook its answer<br>613 `713113f100` Ask the server again for a move's edits when a document's release cancelled or overtook its proof, not move without them | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `14aac0b3ed` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `de176464d2` Compile the VS Code extension's TypeScript into an empty out/<br>272 `b7e5bbecbb` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |
| npm dependencies of master's #805 | 497 `8ee38cf2b1` Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm | the rebase onto master kept the branch's package.json and lock; this brings in #805's glob 14 and minimatch 11 overrides and regenerates the lock |

Kept as they are:

- 119 `7f22ad63bf` List every VS Code command in the extension README, by its own title
- 234 `18379119cb` Trace the language server's messages when xtc.trace.server says so
- 304 `5b7a17303b` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `1b00547086` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 62 now, about 22 after squashing. **Added lines:** 11,595 main, 10,677 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `d22e414e08` Report a Future callback that throws or that a terminated service drops<br>198 `07b4b68b4d` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `847acd618c` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `f504a0c129` Build and run with the embedded compiler in the server and both editors<br>27 `81c6149665` Make embedded the default build mode, chosen per editor<br>179 `21a87daeb6` Build a Gradle-imported project embedded in embedded mode<br>568 `84548950cb` Terminate the extension's tasks also when the terminate comes before VS Code has started them<br>601 `e909e3e0fb` Tag the extension's repeated task terminate with UP91, VS Code's dropped early terminate (microsoft/vscode#350588) | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `2e50de1167` Let embedded runs read console input in both editors<br>34 `9a784acfe3` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `6b4cb69b56` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `6e5868b155` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `83a67a231f` Run programs side by side in supervised execution workers<br>33 `f30aaa7a73` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `102ae10142` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `37c9eeaed8` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `26d9afbfea` Lift the lazy-value time limit for the embedded execution tests' worker<br>463 `8b88148ad0` Take the execution worker's events off its standard output, onto a loopback socket of their own | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `883f4400c3` Let deduction find a named module in src/main/x beside other sources<br>178 `c01d1d30d7` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `1ebac9849c` Run and debug a module's xunit tests from the editors, embedded<br>112 `4e7b6a8d0d` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `cecf45b77c` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `8eff7be073` Rerun only the failed Ecstasy tests from the test console<br>202 `ae89e5753f` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `30a5a20121` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `83f5798689` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `19cc830d9f` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `2e7d86b4c9` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders<br>427 `182e532d34` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own<br>432 `11f671cedc` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `93e89acd06` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `b82acac78b` Pass a run task's args in Gradle and command-line mode<br>236 `ecc1a7aeb8` Run the editor's module from its Run button and context menu<br>655 `bacf6901cf` Run a module's Gradle tasks in its own folder's Gradle build in a VS Code window of several folders<br>658 `9b2b5639ce` Run an included build's Gradle tasks in VS Code from the build that includes it, as its imported model says<br>659 `ffe3ea2a75` Ask which folder's build runs Run Module's module when no folder holds it, in a VS Code window of several folders | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `1fda2323b8` Don't reuse a module compiled in another copy of the project<br>204 `591df3b596` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `633d438f38` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `a64d519573` Describe running and debugging tests from both editors in the READMEs<br>121 `340ef61d6b` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |
| Library replacement in Gradle mode, per workspace folder, with the editors' setting | 406 `5d3d32e573` Build, run and debug with Gradle while the workspace's own modules replace bundled libraries<br>414 `aa87039c36` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries<br>421 `cc73af6f95` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded<br>422 `cd0b6055b6` Show the embedded compiler as not available where the project's own modules replace bundled libraries<br>645 `56b778112a` Name the XDK that each project builds with in the language server's Gradle model<br>646 `6da271abc3` Refuse embedded builds and runs of a project on another XDK, and tell the editors which folders build with Gradle and why<br>647 `aecbf2e393` Build a folder on another XDK with Gradle in both editors, and say which XDK it builds with<br>648 `a95eb6a510` Describe the Gradle mode of a project on another XDK in the user guide and the configuration reference<br>660 `37b1a2a181` Give VS Code's notice that a folder builds with Gradle once a session for each folder while it is in the window | the workspace's own modules replacing bundled libraries: in Gradle mode, kept to the folder that replaces them, the editors' setting, and the embedded compiler shown as not available; the editor parts need L6 and L7, so the group goes to L8 |
| Gradle tasks in each module's own project (#774) | 407 `fcaed9e469` Tell the client each module's Gradle project from the imported build model (#774)<br>408 `5cbbf5d6c3` Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774)<br>409 `3e03a4f116` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774)<br>410 `7b7bbd7a76` Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) | the server names each module's Gradle project, and both editors run its tasks there, reaching a composite's included builds |
| Embedded runs with their Gradle project's xtcRun and xtcTest settings | 464 `b14af2e869` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors | embedded runs take the Gradle project's method, arguments and JVM options, from 465's export |

Kept as they are:

- 30 `1a1ece9c9a` Find entry methods through the module's type and pass them with their arguments
- 38 `f4e5ba88bf` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `4cc6588f8e` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `218a58ef48` Report a stopped run's end without the failure its stop causes
- 199 `42dd572454` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `cb4b4e2326` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 62 now, about 36 after squashing. **Added lines:** 17,343 main, 15,307 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `38aee8c12f` Attach the debugger to a program run with --debug<br>105 `d6b650995e` Move the debugger agent into javatools, with the protocol as records<br>114 `e8b8fb948b` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `86824ba55a` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `2f5dcf3dca` Let the debugger find a module's sources where the language server says<br>383 `420ba8d4a1` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `40216ae3c1` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it<br>405 `8c0818b1c8` Find a debugged module's sources in the workspace when no source graph names it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `cafebc4604` Debug programs from VS Code and IntelliJ through the language server<br>72 `0731a7ce77` Send a step's response before the stop where the step ends<br>594 `4c3224e15a` Tell a debug session where a debugged file's breakpoints are as an edit moves them, so VS Code moves its markers without a save | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `34ce1b68a5` Stop at exceptions, pause a running program, and stop on entry<br>75 `f99f050a5d` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `43cd2fe1e0` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `099e8e55cc` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `e1a093c21a` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `49f20cf749` Let a host give lazy variables' code longer than ON_DEMAND<br>430 `c121913565` Let a debug launch or attach set how long evaluations and lazy values may run | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `2a8b870303` Restart a stopped frame, which its caller then calls again<br>215 `478329a1c8` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `b928db5ad5` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `99874733e5` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `06cfd34283` Lift SessionDebuggerTest's time limit to an hour<br>166 `23e5ae114d` Make SessionDebuggerTest independent of how its threads are scheduled<br>611 `b537f0d9c0` Print a stalled close's fibers before the watchdog fails it, which in CI they never were | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `e4ff6b9628` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `fd437a3de1` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `eff77b33d4` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `23aa1c9820` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `1d2a1042fc` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `9a0185bf27` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `18c3228152` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `f8a769187a` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `b3c40b255e` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `f0faa4be4f` Debug a command-line run: xtc run runs the program, which connects<br>149 `0cf6f71dca` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `87d4859705` Let VS Code set breakpoints in Ecstasy files<br>232 `7a5af962c7` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `e495da5007` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `db4dbab3dc` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |
| End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | 401 `648b4f3b21` End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) | LSP4IJ 0.30.1 keeps the exit code from the exited event; one commit |
| Debugged stacks end at the host's fiber; failed requests say why | 438 `818faf9ce2` End a debugged program's stack at the host's fiber, also when it has moved on from its call<br>439 `17d9d465b0` Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." | the runtime ends a debugged program's stack at the host's fiber (X4), the DAP server answers a failed request with its reason (L9) |

Kept as they are:

- 47 `b66d208ee5` Show a stopped frame's variables, and step over, into and out of calls
- 49 `9124f850dd` Evaluate watches and hovers on paths in a stopped frame
- 51 `12597601f2` Evaluate expressions with operators without running the program's code
- 52 `b8586becf1` Leave the runner's frames out of the debugger's stacks and traces
- 53 `23ccab0bc4` Run code typed in the Debug Console on the stopped fiber
- 54 `6cfd18e67b` Stop at the program's first line from IntelliJ's configuration
- 56 `1ae8a7540e` Place breakpoints on the lines a debugged program was built from
- 59 `433c5110cc` Show library frames in their bundled source, every fiber, and modules
- 60 `25b317db09` Step into a chosen call of the stopped line
- 61 `d4f805d572` Stop where a method of a name is entered
- 63 `0250b0a36d` Stop once a watched field of an object has changed
- 65 `98a2930d1d` Answer inline values with the stopped method's variables
- 101 `53a774ec12` Continue, step or pause one fiber while the others stay stopped
- 103 `27635e0fe5` Test that a part of a value's variables from past its end is empty
- 106 `fee66b73b6` Keep a debugged task's last output, and X325's queued fiber
- 141 `36cca2935b` Debug a program that has an interface
- 169 `b431510cc1` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `501afbfd56` Let a debugger condition name an enum value by its enumeration
- 195 `b4f2df7576` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 24 now, about 9 after squashing. **Added lines:** 3,538 main, 3,504 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `a54b8475f8` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `b5af38360c` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `e44c430a0a` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `a561a1fffb` Compile a library source's ecstasy-library content with its library, as its copy is<br>588 `42a9dc4740` Find the XDK's declarations in Go to Symbol in Workspace, after the workspace's own, so that X356 passes in both editors | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `dd9e390a61` Report no diagnostics for a library source's read-only copy<br>249 `03891dd943` Compile a library source's copy on its own even once a compile has named it<br>462 `73afd92725` Keep diagnostics working when a restart opens another server's library copy again | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `47bcd157b6` Delete the language server's temporary directories once no server needs them<br>277 `d7f5ed5cfc` Keep a server directory's lock beside it, where reading the directory cannot release it<br>494 `88662e70a0` Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `f72402bb58` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `8bc01969ac` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `a300562281` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `b0d7642d0d` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |
| Replaced libraries: their import candidates and their errors | 412 `240a00e547` Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's<br>413 `a21f882293` Say on a replaced library with errors that the server's own compiler may be older than the repository's | two server follow-ups of library replacement |
| Compile only what an edit changes: declaration digests and the member-use tracker | 447 `ae4071d02f` Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies<br>448 `e97d69c28f` Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do<br>460 `f9c9436b31` Digest the API of each declaration of a module apart (DeclarationDigests)<br>461 `be38d9a4b3` Compile again, after an edit, only the modules that use what it changed (member-use tracker)<br>467 `3d94fa09c3` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own | DeclarationDigests in javatools (X2), the artifact API fingerprint and the member-use tracker in the server (L5); 469 fixes 461's Javadoc, fold it in |

Kept as they are:

- 45 `1d91824f9a` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `384867677d` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 205 now, about 23 after squashing. **Added lines:** 5,631 main, 69,404 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `0ea8397c16` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `4e701a5beb` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `13e8345381` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `47a3c48d7c` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `7725c5ab8e` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `1436cfc39b` Report the written video's real length from the tour composer<br>276 `e00deaddff` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `1ad7dc6371` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `726ca8e0a5` Record a playbook run's screen as video in the container<br>228 `9527693e19` Delete a playbook run's IDE folder once its logs are copied, in the container<br>532 `2ee42f7191` Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `4875d22d03` Press the keymap's own Undo key in the IntelliJ tour<br>253 `1b73e28784` Undo in the IntelliJ tour from the editor as the change left it<br>255 `c93e00bd05` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `13e708db61` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `d90ffb68a1` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `72a339eabf` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `95f50ed82d` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `c088cb659a` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `e9162aa771` Edit in the tour's kill step only once the restarted server has the file open (UP83)<br>403 `89b55e09da` Check that one server comes back when the tour kills the server's process (UP72)<br>404 `eb697487d4` Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will<br>446 `d520d9d214` Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames<br>473 `004fda61ba` Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place<br>489 `46c482b040` Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does<br>496 `7439506f48` Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does<br>591 `a0782e6dab` Check Rebuild Project, Ctrl+Shift+F10 and a Gradle-mode Build Project in the IntelliJ tour<br>596 `eedc8aa947` Bring the IntelliJ tour's coverage map up to LSP4IJ 0.30.1, and list the plugin's build mode, restart and Run All Tests actions<br>605 `e26a7ad566` Report a tour wait in IntelliJ about once a minute, as the VS Code tour's waits do<br>606 `8844c8a76c` Read the IntelliJ tour's negative checks once what could cause them has run, instead of waiting out the hang's bound<br>607 `04aff3dbfe` Report the IntelliJ tour's start-up waits about once a minute, as its other waits now do<br>608 `99f00e69ef` Report a tour wait once a minute, not in a burst after a blocked call, and leave no wait after a probe timeout silent<br>621 `b12a97f449` Count as a tour wait's progress only what could make its condition hold, and give the start-up waits the idle check<br>623 `8b7d64eecf` Show the IntelliJ tour's conditional breakpoint balloon once Deck.x's lenses are in place, past the build mode's balloon<br>626 `6b2cf3d89e` Say in the IntelliJ tour's Ctrl+Alt+X, L check's failure what the IDE got of the chord's keys | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `7c893c20f3` Record a tour of the VS Code extension that checks what it shows<br>251 `eb8247743f` Make the VS Code tour pass as one whole run, unrecorded<br>257 `d450132b12` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `a15bc74a3c` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `b52d9961fd` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `a23abed886` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `162b6740ea` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `a760964f72` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop<br>569 `d2a3549733` Stop tasks again while any are left in the VS Code tour's stopAll, and wait for the embedded runs' ends<br>570 `62131c55a0` Count no silent embedded run as work in the VS Code tour's waits, so a stop that does not take fails within the hang window<br>571 `f7bdcc2261` Wait for the VS Code tour's clicks and focus as its waits do, and close the context menu that a failed step leaves<br>589 `0dec05bf8e` Open the VS Code tour's Explorer name box in Rename a file once the tree has the keyboard on Hand.x, and again until it shows<br>590 `9609d90de3` Press Ctrl+Alt+X, B and Ctrl+Alt+X, R as keys in both editor tours, and check what each does<br>592 `42ec725f89` Check in the VS Code tour that the language server comes back when its process dies<br>593 `409afed78e` Show refactoring's refusals in both editor tours: a rename that would change what the code means, and one of the XDK's name<br>595 `61104cb4d4` Show breakpoints while you edit in both editor tours: one that follows its line as a line is typed above it<br>597 `66ee590afa` Show Go to Definition from a named argument into the XDK's source, read-only, in both editor tours<br>598 `d9b1dc84dc` Choose a context menu's item by Enter in the VS Code tour when its click only gave it the keyboard<br>616 `e3fe3a4110` Open a file by Quick Open in the VS Code tour again until it takes, typing only once the box has the keyboard<br>617 `0cfb4ba3e5` Count no edit of a log as work under way in the VS Code tour's waits<br>633 `3586c7ab23` Press Enter on a VS Code tour menu's item only once the window has run what the click started, not into what it opened | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `f4d9a2dfec` Let tests wait for a hang only, not for a fast machine<br>194 `8421ff1b6e` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `991bac2bfb` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `fe4ca426b0` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `e4efad3174` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `f79982f235` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `e07b4d6543` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `d98533a369` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `1d687e0698` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `0f402adf82` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `f112e0ae8c` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `dbd105f5da` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `cdd3623248` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `d1079830a0` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `07c68aeddc` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `ae3b15a9cc` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `86e83edd94` Run the editor playbooks in the container from Gradle, with any engine<br>146 `c0137dc066` Queue playbook container runs on one machine lock, each with its own export<br>147 `a0bdc68bd4` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `1efbebf461` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3<br>431 `0b0ce276a9` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode<br>522 `df5672d9a5` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume<br>523 `5bf3d9d63a` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share<br>525 `305ca9959a` Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed<br>526 `1410c164d3` Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too<br>567 `af9f4b6ef8` Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording<br>572 `96a8bd8100` Build the playbook container's XDK first when the slot's build is of other sources than the checkout's<br>573 `9ba8d81862` Let playbook container runs take the first free slot, in the order they asked, instead of their checkout's slot<br>600 `0f613894a4` Remove the Gradle caches and wrappers of other Gradle versions from a container slot before its run<br>609 `90cf6a69bb` Copy back only the VS Code runs that a container made, not every run its slot's work volume keeps<br>618 `7ba13f7924` Run the server's suites before a full VS Code playbook run only when asked, and print the icon task's files only on request<br>620 `75c281f2af` Give the playbook container a session bus and a stand-in system bus, so VS Code's Chromium stops logging D-Bus errors<br>624 `8d42d3c033` Run the IntelliJ playbook's harness tests in a category of their own, first in every playbook and tour run<br>635 `2a1f1ccb0a` Run VS Code's playbook and tour harness tests in a group of their own, first in every playbook and tour run, not in the extension suite | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `d346599f13` Offer server save formatting in IntelliJ (UP02)<br>330 `6a59b9bd1e` Open bundled library sources as the server's virtual documents (UP25)<br>331 `8240eb3f72` Check inline completion's invocation and selection natively (UP26)<br>332 `ed0bedf419` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `09e0a550b4` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `a65ad9255b` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `b3159432c8` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `7ee5c10fa3` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `6e0e15e406` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `da4c037596` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `36ac07e5ff` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `f290a98577` Read a signature without parameters in the playbook<br>351 `e3ce3188ee` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `36051747f2` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `74f770c748` Check X254's library view through the server<br>354 `485691ecdb` Read X5's library target from the server until LSP4IJ connects it<br>355 `5f89ee3723` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `e7fd660e85` Expect X20's signature without parameters in VS Code<br>365 `c60cf8e03d` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `10107305d8` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `1070c80832` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `e21359067d` Ask X254's definition again when the restarted server retires its analysis<br>376 `58669f3dd8` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `28c949d721` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view<br>399 `0935971d3e` Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81)<br>402 `86dbb2abc4` Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53)<br>627 `e3f966e224` Say what the IntelliJ playbook's UP07 waits are, and reopen the intention menu once nothing could show it | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `b2366ca8a8` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `85515dbe28` Send X259's library edit to the old server before the restart<br>87 `6753ce014b` Wait for X257's Invoked request to reach the LSP console<br>88 `43fdd651df` Reopen a popup under inspection after the IDE was deactivated<br>175 `b2b555dec9` Give X145 a reference search that waits until it is canceled<br>282 `169c31accf` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `17530e4447` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `96a807db66` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `d7151949de` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `af2e9ea8ef` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open<br>445 `cf5a1e2d5e` Give the VS Code client its own handler of the server's messages back after the playbook's CFG2<br>539 `30527e45f9` Open a companion destination in IntelliJ's playbook after the quick fix's edit has changed it (X214)<br>614 `a6f17a8861` Wait for a moved type's files in VS Code's playbook while VS Code's events of them come, not for a fixed 30 s<br>615 `7696ae2531` Let one case's failure in VS Code's playbook fail that case alone, and restore the editor, not end the run<br>622 `644f5c7ac2` Wait for the IntelliJ playbook's Structure tree as the tour's waits do, not with the driver's fixed ten and five seconds<br>625 `8c9aa41339` Find the IntelliJ playbook's Structure window by its own title, not by names that the Project window can have<br>632 `3b9d861978` Give the editor the keyboard in VS Code's playbook again until it takes, and wait for the workbench's widgets while it works, not for fixed seconds<br>634 `32447330e1` Report a VS Code playbook case that fails with a known host bug's exact symptom as a known host failure, apart, and leave the run green<br>636 `652953bdd7` Press X130's Redo only once VS Code's Undo has ended with its didRenameFiles, not as soon as the folders are back on disk<br>653 `2f42317e7a` Wait in VS Code's playbook while what could end a wait or a case is under way, not for fixed bounds<br>668 `d0291e6026` Wait in a native Go to check until the EDT has run LSP4IJ's answer, before the case closes its file<br>669 `29f152934d` Fail a playbook wait for a refactoring's edit at once with the IDE's refusal, and by the idle rule<br>672 `588976728c` Make the VS Code harness's own waits hear only what they are fed, and count no change of the window's focus as work<br>673 `f462592cb3` Make X27's folders one at a time in VS Code, each once the server heard of it, and wait for the watcher's events of Bad.x<br>680 `4e56e17585` Drive the IDE's refusal of a file rename by notification in X128, and the rename dialog's error text in X58 | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `ab60b71ef0` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `00d7144d16` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `336bf1b363` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `5dc9184920` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `026a8c4f96` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `aea72f5e3c` Check Run to Cursor in both editors<br>64 `cda974049e` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `9bcd2b0d0d` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `490cc03b45` Check a document link's Ctrl+Click in IntelliJ's X131<br>577 `fb069cc751` Check in both editors' X269 that the memory policy and the maximum heap reach the restarted server, and that JVM options win<br>578 `be6d1ad63d` Add X352 to both editors' playbooks: a loop's label, its hover, highlights, references, definition and renames<br>579 `8a506473d1` Add X353 to both editors' playbooks: a constructor's parameter with a finally block, from its body and a named argument<br>580 `4afd9a5118` Add X354 to both editors' playbooks: Build and Run in the automatic build mode, beside a Gradle build and without one<br>581 `7d7053fd5b` Add X355 to both editors' playbooks: tests that don't show, of a module that doesn't parse and of one whose dependency has an error<br>582 `df06462456` Add X356 to both editors' playbooks: Go to Symbol in Workspace finds the XDK's declarations, read-only<br>583 `9b4f364bab` Add X357 to both editors' playbooks: Organize Imports removes an unused import and sorts the rest, in one edit<br>584 `420035c785` Add X358 to both editors' playbooks: hover and Quick Documentation of the XDK's types and methods show their doc comments<br>585 `1d15954747` Add X359 to both editors' playbooks: Go to Definition on a named argument of an XDK method opens its parameter, read-only<br>586 `2e97a20508` Add X360 to both editors' playbooks: a method's type parameter named in a lambda is the method's<br>654 `dd65bc5b4a` Describe X361–X366 in the playbook catalog and the manual test plan: formatting from .editorconfig, and a folder on another XDK<br>656 `270ae0da2f` Add X361–X366 to the VS Code playbook<br>657 `13d865e9b8` Add X361–X363, X365 and X366 to the IntelliJ playbook<br>661 `0056e85cff` Read X366's Gradle run output in VS Code's task terminal, check its notice always, and make its XDKs from this tree's installDist<br>663 `f7484667e9` Open X366's and X303's Gradle builds as IntelliJ projects of their own, whose Build Project runs Gradle | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `484ed1aaa7` Give the playbook catalog's size as its 342 cases everywhere<br>122 `4d128b9ad6` Point the playbook instructions at the playbook container<br>127 `f1ee2fdba2` List the test and debug-task messages and a test row in the playbook's 18a<br>529 `8c168a89d9` Drop the retired X273-X275 from the scope that the VS Code playbook's results.json reports | Fold into the catalog's documentation. |
| What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | 470 `a2c08a81a4` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered<br>471 `c0e38e71af` Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it | the playbook keeps each server's whole trace, with each pull's answer at DEBUG only, and the tour's waits and popup steps say what LSP4IJ holds and what could close them |
| Playbook traces: compressed, kept whole for failures and the newest pass | 490 `4e932cd6b7` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run | the pruning compresses a finished run's traces, keeps the newest 10 failed runs and the newest passing run of each mode whole, and judges pass and fail from results.json |
| Test code shared instead of copied | 540 `8f3a8db6df` Share the IntelliJ playbook executors' repeated steps instead of copying them per case<br>541 `725ab8c50c` Share the playbook probe's breakpoint states, variable paths and child nodes<br>542 `e5f562be64` Share the IntelliJ tour's repeated test-run, attach and adapter steps<br>543 `c8594e4d29` Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps<br>544 `d36fd968a5` Share the playbook's semantic-token reads, workspace models and xunit case heads<br>545 `d11635dc79` Share the plugin unit tests' disk files, connection, waits and opens through LightEditor<br>546 `89c25e6495` Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup<br>547 `9b3a04509b` Share the lsp-server adapter tests' source writer and text edit application<br>548 `4032292cff` Share the embedding's incomplete analysis in the header tests, and merge their identical cases<br>549 `e53223bfbe` Shorten the code action tests with the shared edits and per-class helpers<br>550 `69f25a354c` Share an adapter with a module compile hook among the tests that count or hold compiles<br>551 `75f3a1bfab` Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures<br>552 `e0552d4d5e` Merge FormattingConfigRoundTripTest's mock clients and server setup<br>553 `6e84d619db` Apply the missing member, extraction and initializer tests' edits with the shared apply<br>554 `9e2ffa46f0` Check XdkMemberUseTrackerTest's renames of foo through one helper<br>555 `9c611b8f02` Share the marker comment position of the navigation, lookup and occurrence tests<br>556 `196b62522d` Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file<br>574 `9bea8fa548` Share the VS Code playbook's fixture steps across its files: one module, a Gradle model, the server's status, the outside-uses message<br>575 `bd8194c92c` Run the VS Code playbook's debug cases through one Debugger, its runs through Executions, and its tests through one TestWatch<br>576 `a9613c61d7` Share the VS Code playbook's completion and argument steps: names without snippets, a replaced range, a signature, an accepted name<br>602 `472f8be71c` Make lang's three copies of Awaiting.kt one test fixture, org.xvm.testing's, in a new project, lang/test-support | the duplicated test code CPD found in lsp-server, dap-server, the plugin's unit tests, the playbook, its probe and the IntelliJ tour, shared as helpers and fixtures without dropping a check |
| Narration of the editor tours | 675 `6d7896c5be` Write the narration of the IntelliJ and VS Code tours, a paragraph per section and step<br>676 `6dd395f6f8` Narrate editor tours with local voices and synchronized subtitles | The tours' narration, a paragraph per section and step, and its local voices with synchronized subtitles. |

Kept as they are:

- 43 `c937d5be47` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `cf5e25e15e` Apply X103's rename as a refactoring and check the member after Undo
- 187 `232f2d6daa` Check that Debug of Current File launches an Ecstasy configuration
- 189 `4c15e063fb` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `a784684e31` Run the VS Code playbook after the extension suite, not with it
- 200 `c7049d5a6d` Count the code actions at the tour's unused import and unused local
- 247 `9eaddd58e1` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `1d5649d5fa` Print both compiler playbooks' progress per case in the build's output
- 279 `3ce50ee9b4` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 50 now, about 6 after squashing. **Added lines:** 6,886 main, 376 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `bbb35ba17e` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `d45ea91698` Drop the references to documents and plans this branch does not carry<br>69 `e90f7855da` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `05fe01b55c` Fix lang/README's task paths, versions and feature summary<br>123 `9a16618de1` Use American spelling in the shippable docs<br>124 `df33c77588` Name testing in the embedded build and run index entry<br>125 `1cc4ba19d4` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `23ba203911` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `c2336006ed` Say that every adapter needs Java 25 in editor-setup.md<br>283 `5dec04e2f2` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `a9092cd7e0` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `a2f3d9f5d2` Point the docs' line citations at the lines today's changes moved<br>642 `24a9e14a3e` Pad the aligned Markdown tables under lang/ so their columns line up again | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `b2906caa67` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `2f53d8071e` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `ebd994f039` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `2072317b63` Leave the lang build out of the root build by default again<br>379 `7d84e19554` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `f8db484d2e` Say in AGENTS.md that gradle.properties turns the lang build flags on<br>564 `2ec03de93c` Say what MarketplaceListingTest's listing numbers are: a real listing's, and which of them the reader uses | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |
| The user guide, with screenshots from the tours | 423 `2d4bed701c` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours<br>425 `13d87e25dc` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run<br>428 `4de42c5ca8` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature<br>429 `6200c1fdb0` Move the user guide into lang/doc/user-guide, with its screenshots in images<br>433 `49c041f22d` Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's<br>434 `5c6c79f60d` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting<br>451 `1fea955637` Say in the user guide what a rename doesn't change or check<br>468 `2f7d9edda9` Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs<br>469 `010c1e3b2d` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages<br>474 `065c562d1d` Mark the user guide's keys up as keys, and the names it left plain as code<br>475 `72d61fdba4` Add an index of every page and section of the user guide to its landing page<br>476 `9d041bfdd4` Give the user guide's long sections subsections, and its index the third level<br>477 `5641f01e3f` Box the user guide's single screenshots with their captions, as its pairs are<br>528 `056a90264d` Correct the user guide where the playbooks show it wrong, and add links and implement or override<br>599 `302c24b563` Say in the user guide that only a parse error hides a test module's tests, and how IntelliJ finds the XDK's symbols | the guide, its testing chapter and its screenshots, made from tagged tour steps |
| Opt-in static analysis of lang: detekt and CPD | 498 `bc3881ebf6` Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript<br>507 `94199dd33b` Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings<br>508 `9be36d02c4` Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too<br>524 `51ba924aa0` Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates | lang's tasks for detekt over its Kotlin and CPD over its Kotlin and TypeScript, reporting only, not in check; the two tools' version pins in the root catalog go to X5 |
| Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | 509 `cff2383439` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed<br>510 `18a95abb47` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops<br>511 `96bbf89ebe` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate<br>512 `8721ab8e95` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair<br>513 `50d00b8192` Share the language server's location requests and edit conversion, and say the semantic tokens setting once<br>514 `1231d6668d` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end<br>515 `88e1ba935c` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias<br>516 `8c0a02b99b` Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over<br>517 `9be28e81f1` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode<br>518 `edfc757e3a` Share the language server's cancellation-owning future and its formatter's parenthesis search<br>519 `09745b5413` Generate TextMate's operator patterns from one list of categories, not seven copies of the same block | detekt's and the duplicate checks' (CPD, IntelliJ's) findings fixed in lang's main code; it touches files of L1-L9, so it goes last, in L10, or is divided by path into each file's PR when the PRs are cut |

## Notes for review

- X1–X3 are the breaking listener and embedding API across `org.xvm.asm`, `compiler` and `api`; release 0.5.0 is
  the agreed boundary ([remaining.md, section 5](doc/errs/remaining.md#5-pull-requests-and-the-release)). Master's
  callers take the explicit-listener contract.
- X1 is the successor of the closed #683 and #685: one PR, as #685 was, not #680–#682's pieces again.
- `errs/compiler-semantic-facts-20261009` and `errs/compiler-partial-analysis-20261009` are stale snapshots of
  X2's and X3's compiler work; build X2 and X3 from #693, not from them.
- X4 holds the javatools half of the debugger (about 7k lines in `org.xvm.runtime`), most of it in commits split
  with L9, so it is assembled by path. The plugin's debug options need the javatools agent; gate plugin changes with
  `:plugin:validatePlugins` and a real task run. The xtc tool and xunit fixes in it (107, 178, 199, 386) are
  independent of each other and could go as one-commit PRs.
- X5's CI and preview-publishing changes need a maintainer's go-ahead; 42's AGENTS.md comment rule can go alone.
- L6 is written against LSP4IJ 0.30.1, so the workarounds that 0.30.x retired never appear; every workaround left
  carries its `TODO LSP4IJ: UPxx` tag.
- L10's playbooks and tours run in the container only.

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
| 1 | `91e55b3246` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `03a30ed4bc` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `09a9a461de` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `254cff197c` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `718047b06f` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `82094d8238` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `35d52ccd11` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `f28b38ee2d` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `3ff67efef1` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `11ce7e7b05` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `04e139d2a6` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `8f3a41c09c` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `46430dd4e7` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `643d520022` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `9ed6223ade` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `779f667500` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `7f909a6c8e` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `d38a9855c2` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `2fa057ef2c` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `f504a0c129` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `bbb35ba17e` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `ab60b71ef0` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `2d361a5f2c` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `c68680c75b` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `2e50de1167` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `6b4cb69b56` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `81c6149665` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `69c67244c2` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `83a67a231f` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `1a1ece9c9a` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `9200b87c9c` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `8bf519ce5e` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `f30aaa7a73` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `9a784acfe3` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `88651bde25` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `3f118fe020` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `044d1da637` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `f4e5ba88bf` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `102ae10142` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `b38c852d7f` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `95f6fcbadc` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `5cd4bd00e3` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `c937d5be47` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `38de7e0bdd` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `1d91824f9a` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `cafebc4604` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `b66d208ee5` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `34ce1b68a5` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `9124f850dd` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `43cd2fe1e0` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `12597601f2` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `b8586becf1` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `23ccab0bc4` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `6cfd18e67b` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `e1a093c21a` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `1ae8a7540e` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `37c9eeaed8` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `aea72f5e3c` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `433c5110cc` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `25b317db09` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `d4f805d572` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `2a8b870303` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `0250b0a36d` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `cda974049e` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `98a2930d1d` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `9bcd2b0d0d` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `490cc03b45` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `d45ea91698` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `e90f7855da` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `6b681309f7` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `14aac0b3ed` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `0731a7ce77` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `9f228873ba` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `cf5e25e15e` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `f99f050a5d` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `3c79038f1c` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `e506c12f70` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `49f20cf749` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `38aee8c12f` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `b928db5ad5` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `7de05be6a9` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `0905df53f3` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `bca7a5c45e` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `dafb36558a` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `b2366ca8a8` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `85515dbe28` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `6753ce014b` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `43fdd651df` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `18c3228152` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `07c68aeddc` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `99874733e5` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `18f7d2b6bb` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `f79982f235` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `ae3b15a9cc` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `8900ea8af2` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `b3c40b255e` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `b060dce4c2` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `1fda2323b8` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `66e3b0e410` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `86e83edd94` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `53a774ec12` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `5464999ec5` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `27635e0fe5` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `f0faa4be4f` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `d6b650995e` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `fee66b73b6` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `883f4400c3` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `1ebac9849c` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `df0d19e8d4` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `cecf45b77c` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `93e89acd06` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `4e7b6a8d0d` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `26d9afbfea` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `e8b8fb948b` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `3941808e3e` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `e495da5007` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `a64d519573` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `484ed1aaa7` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `7f22ad63bf` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `05fe01b55c` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `340ef61d6b` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `4d128b9ad6` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `9a16618de1` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `df33c77588` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `1cc4ba19d4` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `23ba203911` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `f1ee2fdba2` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `c2336006ed` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `db4dbab3dc` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `f318e2ee58` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `567f9c59c1` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `16f91fa471` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `4cc6588f8e` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `4782290f4a` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `5905046d59` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `dc1ba2d8a0` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `6b079e4692` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `68420437c7` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `210e2a7e12` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `113a319c5d` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `36cca2935b` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `06cfd34283` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `cadc25167e` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `c9da9a782a` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `36d8eda105` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `c0137dc066` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `a0bdc68bd4` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `f4d9a2dfec` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `0cf6f71dca` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `0ea8397c16` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `384867677d` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `20db2122b6` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `c44f9812fd` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `08c6665129` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `6889173c7e` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `87d2d5485c` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `eb0640952d` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `8eff7be073` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `e4ff6b9628` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `e92f1bd24a` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `e94edcfd59` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `1481088eac` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `c54618fd30` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `2e36458e6f` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `3842c97dda` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `23e5ae114d` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `880332c140` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `fd9b3b37fc` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `b431510cc1` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `d22e414e08` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `3cc5c3b7a0` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `a66af7e8a4` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `c40ab89730` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `501afbfd56` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `b2b555dec9` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `a8ef9d39b0` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `7b3ec35cab` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `c01d1d30d7` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `21a87daeb6` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `925aa8b50d` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `a5d102e128` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `9dffd6ffee` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `099e8e55cc` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `002235d873` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `2e351cd76d` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `aa1fec04e4` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `232f2d6daa` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `218a58ef48` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `4c15e063fb` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `a784684e31` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `40e2b9c224` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `81bd6065b1` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `91a1d89c84` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `8421ff1b6e` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `b4f2df7576` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `0d21f86156` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `eff77b33d4` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `07b4b68b4d` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `42dd572454` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `c7049d5a6d` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `a773f54012` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `ae89e5753f` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `70ca4b321d` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `591df3b596` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `345292f65c` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `30a5a20121` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `5791f0773c` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `18252e3985` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `8f06384315` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `425f58cd27` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `670e0917fa` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `dda9c34b3b` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `f2149076b2` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `604d925565` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `478329a1c8` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `cb4b4e2326` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `de176464d2` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `dd9e390a61` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `83f5798689` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `82d52da842` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `8fcac97fe7` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `711f193294` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `78431e81df` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `19cc830d9f` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `1d2a1042fc` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `74ea340e42` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `726ca8e0a5` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `9527693e19` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `4e701a5beb` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `13e8345381` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `87d4859705` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `7a5af962c7` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `b82acac78b` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `18379119cb` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `404937e3a8` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `ecc1a7aeb8` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `aee11086ab` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `007a1bbb1c` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `847acd618c` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `e07b4d6543` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `00d7144d16` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `47a3c48d7c` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `7c893c20f3` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `633d438f38` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `88465c33d3` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `e264ef8b92` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `9eaddd58e1` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `d625883267` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `03891dd943` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `851de70399` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `eb8247743f` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `4875d22d03` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `1b73e28784` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `8eabd7e869` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `c93e00bd05` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `13e708db61` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `d450132b12` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `a15bc74a3c` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `b52d9961fd` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `d90ffb68a1` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `8fbc2fb23f` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `7725c5ab8e` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `1d5649d5fa` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `1436cfc39b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `991bac2bfb` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `d2cfe04526` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `a54b8475f8` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `2e7d86b4c9` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `6e5868b155` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `72a339eabf` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `a23abed886` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `b7e5bbecbb` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `ebd994f039` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `b5af38360c` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `47bcd157b6` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `e00deaddff` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `d7f5ed5cfc` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `e44c430a0a` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `3ce50ee9b4` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `a561a1fffb` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `336bf1b363` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `169c31accf` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `5dec04e2f2` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `f8db484d2e` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `6a143fab69` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `1ad7dc6371` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `95f50ed82d` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `162b6740ea` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `0de4d19de7` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `a9092cd7e0` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `17530e4447` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `e98f88dcaf` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `5dc9184920` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `5eef6c2d12` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `e2666e1d97` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `026a8c4f96` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `37ec61ad6a` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `9cccf5dd4d` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `be77d380a8` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `d98533a369` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `5f1684c097` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `ffb5d202c3` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `96a807db66` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `5b7a17303b` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `a2f3d9f5d2` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `1d687e0698` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `504388fc85` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `0f402adf82` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `1efbebf461` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `fe4ca426b0` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `f72402bb58` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `a300562281` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `8bc01969ac` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `b0d7642d0d` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `b2906caa67` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `2f53d8071e` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `2072317b63` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `2f5dcf3dca` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `f112e0ae8c` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `d7151949de` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `e4efad3174` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `dbd105f5da` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `cdd3623248` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `d1079830a0` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `aae2aa7875` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `b029b52fdb` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `5756c29792` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `86077ea006` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `d346599f13` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `6a59b9bd1e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `8240eb3f72` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `ed0bedf419` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `09e0a550b4` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `a65ad9255b` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `b3159432c8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `7ee5c10fa3` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `6e0e15e406` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `da4c037596` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `0e5dbda079` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `d1e53d21e7` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `30efb7544f` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `38a5b21b87` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `ec1100211d` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `c94fee8abc` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `27d06697ce` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `16b80afc7c` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `093781a1c7` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `8bb6e34283` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `36ac07e5ff` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `f290a98577` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `e3ce3188ee` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `36051747f2` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `74f770c748` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `485691ecdb` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `5f89ee3723` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `35269c195e` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `36b20c4a22` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `e7fd660e85` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `33936d9e4c` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `23aa1c9820` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `b9df9837d9` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `fd437a3de1` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `3f23961c0f` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `67b8604415` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `c60cf8e03d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `f8a769187a` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `9a0185bf27` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `86824ba55a` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `8231d15ed5` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `10107305d8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `1070c80832` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `6bc4e8f8ae` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `78271d108e` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `0fd5b84af6` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `e21359067d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `58669f3dd8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `d2a2cb5f23` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `28c949d721` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `7d84e19554` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `b61c5da8b6` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `1b00547086` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `af2e9ea8ef` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `420ba8d4a1` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `23a46109b8` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `29714dd46a` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `2160d5d5a3` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `ddf72703b5` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `1ae3230d48` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `a760964f72` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `c088cb659a` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `e9162aa771` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `0c3d0eedcb` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `b02e2578d4` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `812380ca32` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `b2c4efce9f` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `40216ae3c1` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `63666f9821` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
| 398 | `77d6331787` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83) |
| 399 | `0935971d3e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81) |
| 400 | `85eb64bc6a` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) |
| 401 | `648b4f3b21` | P10 | — | L9 | End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) |
| 402 | `86dbb2abc4` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) |
| 403 | `89b55e09da` | P12 | — | L10 | IntelliJ tour step fixes | Check that one server comes back when the tour kills the server's process (UP72) |
| 404 | `eb697487d4` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will |
| 405 | `8c0818b1c8` | P10 | — | L9 | The debugger finds sources where its host names them | Find a debugged module's sources in the workspace when no source graph names it |
| 406 | `5d3d32e573` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build, run and debug with Gradle while the workspace's own modules replace bundled libraries |
| 407 | `fcaed9e469` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Tell the client each module's Gradle project from the imported build model (#774) |
| 408 | `5cbbf5d6c3` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774) |
| 409 | `3e03a4f116` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774) |
| 410 | `7b7bbd7a76` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) |
| 411 | `4afd6bc82c` | P6 | — | L4 | Rename guards and proofs | Prove a rename in a workspace module that replaces a bundled library as the workspace's sources |
| 412 | `240a00e547` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's |
| 413 | `a21f882293` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Say on a replaced library with errors that the server's own compiler may be older than the repository's |
| 414 | `aa87039c36` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Add an editor setting for whether the workspace's own modules replace bundled XDK libraries |
| 415 | `e99bc7188c` | P6 | — | L4 | Rename guards and proofs | Find a text's line breaks once per comparison in a rename's proof, not once per position |
| 416 | `5d0f16ed0c` | P6 | — | L4 | Rename guards and proofs | Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes |
| 417 | `f5dde5a62e` | P6 | — | L4 | Rename guards and proofs | Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy |
| 418 | `ae73ae4efa` | P4 | — | L1, L2 | Time and heap logs of the server's work | Log each project query's and each compile's time and heap |
| 419 | `259b3c7f74` | P4 | — | L1 | Time and heap logs of the server's work | Log what the server did in each minute in which it worked, and over its session |
| 420 | `10f42095cb` | P7 | — | L6 | The language server JVM's memory setting | Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive |
| 421 | `cc73af6f95` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded |
| 422 | `cd0b6055b6` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Show the embedded compiler as not available where the project's own modules replace bundled libraries |
| 423 | `2d4bed701c` | P13 | — | L10 | The user guide, with screenshots from the tours | Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours |
| 424 | `a2b46bd38b` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy |
| 425 | `13d87e25dc` | P13 | — | L10 | The user guide, with screenshots from the tours | Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run |
| 426 | `458afa9e50` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide |
| 427 | `182e532d34` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own |
| 428 | `4de42c5ca8` | P13 | — | L10 | The user guide, with screenshots from the tours | Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature |
| 429 | `6200c1fdb0` | P13 | — | L10 | The user guide, with screenshots from the tours | Move the user guide into lang/doc/user-guide, with its screenshots in images |
| 430 | `c121913565` | P10 | X4 | L9 | Lazy properties and their time limit | Let a debug launch or attach set how long evaluations and lazy values may run |
| 431 | `0b0ce276a9` | P12 | X5 | L10 | The playbook container and the IDE it drives | Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode |
| 432 | `11f671cedc` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc |
| 433 | `49c041f22d` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's |
| 434 | `5c6c79f60d` | P13 | — | L10 | The user guide, with screenshots from the tours | Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting |
| 435 | `7f99ad861a` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed |
| 436 | `26b08815c1` | P6 | — | L4 | Prove code actions with incremental compiles | Start a rename's proof from the revision's compile, and compile after the edit only what it reaches |
| 437 | `5c625704c4` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work |
| 438 | `818faf9ce2` | P10 | X4 | — | Debugged stacks end at the host's fiber; failed requests say why | End a debugged program's stack at the host's fiber, also when it has moved on from its call |
| 439 | `17d9d465b0` | P10 | — | L9 | Debugged stacks end at the host's fiber; failed requests say why | Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." |
| 440 | `2986023105` | P5 | X2 | L3 | Bind more names in the semantic model | Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration |
| 441 | `c197a0ea3d` | P5 | — | L3 | Bind more names in the semantic model | Make the _ placeholder no occurrence in the semantic model |
| 442 | `5b4df56b47` | P5 | X2 | L3 | Bind more names in the semantic model | Bind the names of a method's multiple return values to the values they declare |
| 443 | `3a260c2d73` | P5 | X2 | L3 | Bind more names in the semantic model | Bind this in a lazy property's calc() and super in a method reference to what they name |
| 444 | `36a70b0b8d` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed |
| 445 | `cf5a1e2d5e` | P12 | — | L10 | Individual playbook case fixes | Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 |
| 446 | `d520d9d214` | P12 | — | L10 | IntelliJ tour step fixes | Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames |
| 447 | `ae4071d02f` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies |
| 448 | `e97d69c28f` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do |
| 449 | `4b072515fa` | P6 | — | L4 | Prove code actions with incremental compiles | After a rename's edit, compile only the modules depending on the edited ones that it can reach |
| 450 | `83a48a298e` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done |
| 451 | `1fea955637` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide what a rename doesn't change or check |
| 452 | `3796aab8be` | P5 | — | L3 | Bind more names in the semantic model | Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares |
| 453 | `6883fb4803` | P5 | X2 | L3 | Bind more names in the semantic model | Keep the initializer facts of a property declared in a loop's body, such as a static local |
| 454 | `0bfb47be16` | P5 | — | L3 | Bind more names in the semantic model | Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration |
| 455 | `297213386f` | P5 | — | L3 | Bind more names in the semantic model | Declare the names of a function type's values in the semantic model, as of its return values in a typedef |
| 456 | `c310576e28` | P5 | — | L3 | Bind more names in the semantic model | Bind a reserved name such as this:class in a property's accessor, as in a method of the class |
| 457 | `df3295a040` | P5 | — | L3 | Bind more names in the semantic model | Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells |
| 458 | `828ac6bf62` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Report constructor arguments and default values that no constructor uses |
| 459 | `27afe6d18c` | P6 | — | L4 | Rename guards and proofs | Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element |
| 460 | `f9c9436b31` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Digest the API of each declaration of a module apart (DeclarationDigests) |
| 461 | `be38d9a4b3` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Compile again, after an edit, only the modules that use what it changed (member-use tracker) |
| 462 | `73afd92725` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Keep diagnostics working when a restart opens another server's library copy again |
| 463 | `8b88148ad0` | P9 | — | L8 | Supervised execution workers | Take the execution worker's events off its standard output, onto a loopback socket of their own |
| 464 | `b14af2e869` | P9 | — | L8 | Embedded runs with their Gradle project's xtcRun and xtcTest settings | Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors |
| 465 | `213b4df252` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType() |
| 466 | `59061e49ec` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them |
| 467 | `3d94fa09c3` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own |
| 468 | `2f7d9edda9` | P13 | — | L10 | The user guide, with screenshots from the tours | Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs |
| 469 | `010c1e3b2d` | P13 | — | L10 | The user guide, with screenshots from the tours | Split the user guide into a landing page, one page per chapter, and features and shortcuts pages |
| 470 | `a2c08a81a4` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered |
| 471 | `c0e38e71af` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it |
| 472 | `cd331a9552` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) |
| 473 | `004fda61ba` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place |
| 474 | `065c562d1d` | P13 | — | L10 | The user guide, with screenshots from the tours | Mark the user guide's keys up as keys, and the names it left plain as code |
| 475 | `72d61fdba4` | P13 | — | L10 | The user guide, with screenshots from the tours | Add an index of every page and section of the user guide to its landing page |
| 476 | `9d041bfdd4` | P13 | — | L10 | The user guide, with screenshots from the tours | Give the user guide's long sections subsections, and its index the third level |
| 477 | `5641f01e3f` | P13 | — | L10 | The user guide, with screenshots from the tours | Box the user guide's single screenshots with their captions, as its pairs are |
| 478 | `82c84206d3` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence |
| 479 | `df667bd3d7` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove an anonymous class's this and construction by the span of the type it constructs |
| 480 | `5418fbbe11` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares |
| 481 | `885bc9a886` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a union receiver's call of a method that the runtime implements, as a const's appendTo |
| 482 | `75d928e1ef` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a call on a union receiver whose legs share one route, as toString on a Point? |
| 483 | `3de1a5ba9d` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the dispatch chains that hold an unproven identity in the semantic model's census |
| 484 | `604d989fe6` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder |
| 485 | `555d03998a` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method |
| 486 | `dd3badf857` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the bindings that no proof can place in the semantic model's census, and fail on one |
| 487 | `574992a270` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name |
| 488 | `3d743ef456` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap |
| 489 | `46c482b040` | P12 | — | L10 | IntelliJ tour step fixes | Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does |
| 490 | `4e932cd6b7` | P12 | X5 | L10 | Playbook traces: compressed, kept whole for failures and the newest pass | Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run |
| 491 | `502e30adf7` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase |
| 492 | `d0bdbeb88f` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count |
| 493 | `ebe81e4f6d` | P4 | — | L1, L2 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG |
| 494 | `88662e70a0` | P11 | — | L5 | Temporary directories and their locks | Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time |
| 495 | `005fd072f1` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) |
| 496 | `7439506f48` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does |
| 497 | `8ee38cf2b1` | P8 | — | L7 | npm dependencies of master's #805 | Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm |
| 498 | `bc3881ebf6` | P13 | X5 | L10 | Opt-in static analysis of lang: detekt and CPD | Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript |
| 499 | `c698badfc1` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Allow contribution arguments and shorthand defaults that match the explicit constructor |
| 500 | `d4fc58f456` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the semantic model's census what hover, definition, references and highlights miss at a name |
| 501 | `0b501487d9` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations |
| 502 | `00ae19db9f` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to |
| 503 | `0720b66c56` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property |
| 504 | `65a333f6c2` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the editor's census the references whose declaration is in no source of the workspace |
| 505 | `8e943821d4` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Compile the census's libraries as one workspace folder, as an editor that opens the repository does |
| 506 | `c0250b98dc` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88) |
| 507 | `94199dd33b` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings |
| 508 | `9be36d02c4` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too |
| 509 | `cff2383439` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Keep the cause of a refused settings change, undo or range format, which detekt found swallowed |
| 510 | `18a95abb47` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops |
| 511 | `96bbf89ebe` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate |
| 512 | `8721ab8e95` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair |
| 513 | `50d00b8192` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's location requests and edit conversion, and say the semantic tokens setting once |
| 514 | `1231d6668d` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end |
| 515 | `88e1ba935c` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias |
| 516 | `8c0a02b99b` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over |
| 517 | `9be28e81f1` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode |
| 518 | `edfc757e3a` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's cancellation-owning future and its formatter's parenthesis search |
| 519 | `09745b5413` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Generate TextMate's operator patterns from one list of categories, not seven copies of the same block |
| 520 | `8eba9c3221` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy |
| 521 | `d63abfd525` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Test that a class without constructors compiles and uses its contribution's arguments |
| 522 | `df5672d9a5` | P12 | — | L10 | The playbook container and the IDE it drives | Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume |
| 523 | `5bf3d9d63a` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share |
| 524 | `51ba924aa0` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates |
| 525 | `305ca9959a` | P12 | — | L10 | The playbook container and the IDE it drives | Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed |
| 526 | `1410c164d3` | P12 | — | L10 | The playbook container and the IDE it drives | Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too |
| 527 | `e4e4fb400f` | P7 | — | L6 | Installable in every JetBrains IDE: no Java dependency, Gradle optional | Make the IntelliJ plugin installable in every JetBrains IDE: no Java dependency, and Gradle only where the IDE has it |
| 528 | `056a90264d` | P13 | — | L10 | The user guide, with screenshots from the tours | Correct the user guide where the playbooks show it wrong, and add links and implement or override |
| 529 | `8c168a89d9` | P12 | — | L10 | Playbook documentation | Drop the retired X273-X275 from the scope that the VS Code playbook's results.json reports |
| 530 | `1e703ae793` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed |
| 531 | `0af1aaf6e9` | P7 | — | L6 | Installable in every JetBrains IDE: no Java dependency, Gradle optional | Name the plugin's required-dependency test as master's #810 does, with the test runner among them |
| 532 | `2ee42f7191` | P12 | — | L10 | The IntelliJ tour, its recording and video | Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times |
| 533 | `53900f21cb` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent |
| 534 | `843d6b4ac8` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire |
| 535 | `edd91a43af` | P4 | — | L1, L2 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Share the xtc.* keys and a build's summary text through lang/protocol |
| 536 | `6a264f7b27` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Share the compiler's library settings through lang/protocol: their shape and how a path names a file |
| 537 | `53947722d4` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer xtc/tests when a document's close retires the compile it waits for (X342) |
| 538 | `288c79400f` | P4 | — | L1 | Keep documents current and the server responsive | Keep listed code actions resolvable across a close that leaves a document's text as on disk |
| 539 | `30527e45f9` | P12 | — | L10 | Individual playbook case fixes | Open a companion destination in IntelliJ's playbook after the quick fix's edit has changed it (X214) |
| 540 | `8f3a8db6df` | P12 | — | L10 | Test code shared instead of copied | Share the IntelliJ playbook executors' repeated steps instead of copying them per case |
| 541 | `725ab8c50c` | P12 | — | L10 | Test code shared instead of copied | Share the playbook probe's breakpoint states, variable paths and child nodes |
| 542 | `e5f562be64` | P12 | — | L10 | Test code shared instead of copied | Share the IntelliJ tour's repeated test-run, attach and adapter steps |
| 543 | `c8594e4d29` | P12 | — | L10 | Test code shared instead of copied | Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps |
| 544 | `d36fd968a5` | P12 | — | L10 | Test code shared instead of copied | Share the playbook's semantic-token reads, workspace models and xunit case heads |
| 545 | `d11635dc79` | P12 | — | L10 | Test code shared instead of copied | Share the plugin unit tests' disk files, connection, waits and opens through LightEditor |
| 546 | `89c25e6495` | P12 | — | L10 | Test code shared instead of copied | Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup |
| 547 | `9b3a04509b` | P12 | — | L10 | Test code shared instead of copied | Share the lsp-server adapter tests' source writer and text edit application |
| 548 | `4032292cff` | P12 | — | L10 | Test code shared instead of copied | Share the embedding's incomplete analysis in the header tests, and merge their identical cases |
| 549 | `e53223bfbe` | P12 | — | L10 | Test code shared instead of copied | Shorten the code action tests with the shared edits and per-class helpers |
| 550 | `69f25a354c` | P12 | — | L10 | Test code shared instead of copied | Share an adapter with a module compile hook among the tests that count or hold compiles |
| 551 | `75f3a1bfab` | P12 | — | L10 | Test code shared instead of copied | Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures |
| 552 | `e0552d4d5e` | P12 | — | L10 | Test code shared instead of copied | Merge FormattingConfigRoundTripTest's mock clients and server setup |
| 553 | `6e84d619db` | P12 | — | L10 | Test code shared instead of copied | Apply the missing member, extraction and initializer tests' edits with the shared apply |
| 554 | `9e2ffa46f0` | P12 | — | L10 | Test code shared instead of copied | Check XdkMemberUseTrackerTest's renames of foo through one helper |
| 555 | `9c611b8f02` | P12 | — | L10 | Test code shared instead of copied | Share the marker comment position of the navigation, lookup and occurrence tests |
| 556 | `196b62522d` | P12 | — | L10 | Test code shared instead of copied | Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file |
| 557 | `f6a665aef8` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know |
| 558 | `bcbb23434d` | P7 | — | L6 | Light test fixes | Hold ClosedFileProblemsTest's later workspace pulls until its next refresh, so that none lands after its build |
| 559 | `b4aa2d623d` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Read XdkPullDiagnosticsTest's traced answers in the order their queries came, not the order their ends were written |
| 560 | `f3d9ca1f2d` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a named argument of another module's method, as inPlace in freeze(inPlace=True), to its parameter in that module's source |
| 561 | `3b8079bf36` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that a lambda names, as To in n -> n.toIntN().as(To), to the method's or the class's formal type |
| 562 | `1b49bd41c3` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a parameter of a constructor with a finally block that only lambdas in its body name to its declaration |
| 563 | `31cae66cbe` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Place a label written with unicode escapes where it is written, in its declaration, a break and a continue |
| 564 | `2ec03de93c` | P13 | X5 | — | Packaging tasks and build defaults | Say what MarketplaceListingTest's listing numbers are: a real listing's, and which of them the reader uses |
| 565 | `29589abc34` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7) |
| 566 | `7acd2e83f2` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename leaves string literals and comments that spell the old name, how many and where |
| 567 | `af9f4b6ef8` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording |
| 568 | `84548950cb` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Terminate the extension's tasks also when the terminate comes before VS Code has started them |
| 569 | `d2a3549733` | P12 | — | L10 | The VS Code tour | Stop tasks again while any are left in the VS Code tour's stopAll, and wait for the embedded runs' ends |
| 570 | `62131c55a0` | P12 | — | L10 | The VS Code tour | Count no silent embedded run as work in the VS Code tour's waits, so a stop that does not take fails within the hang window |
| 571 | `f7bdcc2261` | P12 | — | L10 | The VS Code tour | Wait for the VS Code tour's clicks and focus as its waits do, and close the context menu that a failed step leaves |
| 572 | `96a8bd8100` | P12 | X5 | L10 | The playbook container and the IDE it drives | Build the playbook container's XDK first when the slot's build is of other sources than the checkout's |
| 573 | `9ba8d81862` | P12 | — | L10 | The playbook container and the IDE it drives | Let playbook container runs take the first free slot, in the order they asked, instead of their checkout's slot |
| 574 | `9bea8fa548` | P12 | — | L10 | Test code shared instead of copied | Share the VS Code playbook's fixture steps across its files: one module, a Gradle model, the server's status, the outside-uses message |
| 575 | `bd8194c92c` | P12 | — | L10 | Test code shared instead of copied | Run the VS Code playbook's debug cases through one Debugger, its runs through Executions, and its tests through one TestWatch |
| 576 | `a9613c61d7` | P12 | — | L10 | Test code shared instead of copied | Share the VS Code playbook's completion and argument steps: names without snippets, a replaced range, a signature, an accepted name |
| 577 | `fb069cc751` | P12 | — | L10 | Playbook cases added after the catalog | Check in both editors' X269 that the memory policy and the maximum heap reach the restarted server, and that JVM options win |
| 578 | `be6d1ad63d` | P12 | — | L10 | Playbook cases added after the catalog | Add X352 to both editors' playbooks: a loop's label, its hover, highlights, references, definition and renames |
| 579 | `8a506473d1` | P12 | — | L10 | Playbook cases added after the catalog | Add X353 to both editors' playbooks: a constructor's parameter with a finally block, from its body and a named argument |
| 580 | `4afd9a5118` | P12 | — | L10 | Playbook cases added after the catalog | Add X354 to both editors' playbooks: Build and Run in the automatic build mode, beside a Gradle build and without one |
| 581 | `7d7053fd5b` | P12 | — | L10 | Playbook cases added after the catalog | Add X355 to both editors' playbooks: tests that don't show, of a module that doesn't parse and of one whose dependency has an error |
| 582 | `df06462456` | P12 | — | L10 | Playbook cases added after the catalog | Add X356 to both editors' playbooks: Go to Symbol in Workspace finds the XDK's declarations, read-only |
| 583 | `9b4f364bab` | P12 | — | L10 | Playbook cases added after the catalog | Add X357 to both editors' playbooks: Organize Imports removes an unused import and sorts the rest, in one edit |
| 584 | `420035c785` | P12 | — | L10 | Playbook cases added after the catalog | Add X358 to both editors' playbooks: hover and Quick Documentation of the XDK's types and methods show their doc comments |
| 585 | `1d15954747` | P12 | — | L10 | Playbook cases added after the catalog | Add X359 to both editors' playbooks: Go to Definition on a named argument of an XDK method opens its parameter, read-only |
| 586 | `2e97a20508` | P12 | — | L10 | Playbook cases added after the catalog | Add X360 to both editors' playbooks: a method's type parameter named in a lambda is the method's |
| 587 | `3b4d6d9286` | P4 | — | L1 | Keep documents current and the server responsive | Answer Find References again when documents only opened or closed while it ran, not with the cancellation that showed X251 none |
| 588 | `42a9dc4740` | P11 | — | L5 | Navigate the XDK's library sources | Find the XDK's declarations in Go to Symbol in Workspace, after the workspace's own, so that X356 passes in both editors |
| 589 | `0dec05bf8e` | P12 | — | L10 | The VS Code tour | Open the VS Code tour's Explorer name box in Rename a file once the tree has the keyboard on Hand.x, and again until it shows |
| 590 | `9609d90de3` | P12 | — | L10 | The VS Code tour | Press Ctrl+Alt+X, B and Ctrl+Alt+X, R as keys in both editor tours, and check what each does |
| 591 | `a0782e6dab` | P12 | — | L10 | IntelliJ tour step fixes | Check Rebuild Project, Ctrl+Shift+F10 and a Gradle-mode Build Project in the IntelliJ tour |
| 592 | `42ec725f89` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that the language server comes back when its process dies |
| 593 | `409afed78e` | P12 | — | L10 | The VS Code tour | Show refactoring's refusals in both editor tours: a rename that would change what the code means, and one of the XDK's name |
| 594 | `4c3224e15a` | P10 | — | L9 | Debug through the language server | Tell a debug session where a debugged file's breakpoints are as an edit moves them, so VS Code moves its markers without a save |
| 595 | `61104cb4d4` | P12 | — | L10 | The VS Code tour | Show breakpoints while you edit in both editor tours: one that follows its line as a line is typed above it |
| 596 | `eedc8aa947` | P12 | — | L10 | IntelliJ tour step fixes | Bring the IntelliJ tour's coverage map up to LSP4IJ 0.30.1, and list the plugin's build mode, restart and Run All Tests actions |
| 597 | `66ee590afa` | P12 | — | L10 | The VS Code tour | Show Go to Definition from a named argument into the XDK's source, read-only, in both editor tours |
| 598 | `d9b1dc84dc` | P12 | — | L10 | The VS Code tour | Choose a context menu's item by Enter in the VS Code tour when its click only gave it the keyboard |
| 599 | `302c24b563` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that only a parse error hides a test module's tests, and how IntelliJ finds the XDK's symbols |
| 600 | `0f613894a4` | P12 | — | L10 | The playbook container and the IDE it drives | Remove the Gradle caches and wrappers of other Gradle versions from a container slot before its run |
| 601 | `e909e3e0fb` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Tag the extension's repeated task terminate with UP91, VS Code's dropped early terminate (microsoft/vscode#350588) |
| 602 | `472f8be71c` | P12 | — | L10 | Test code shared instead of copied | Make lang's three copies of Awaiting.kt one test fixture, org.xvm.testing's, in a new project, lang/test-support |
| 603 | `b8fa851cca` | P6 | — | L4 | Rename guards and proofs | Say in VS Code why a rename is refused, as IntelliJ's plugin does, instead of VS Code's "No result." |
| 604 | `cf8effbb6e` | P6 | — | L4 | Rename guards and proofs | Answer xtc/rename's refusals with their reason, as textDocument/rename does, instead of as an internal error |
| 605 | `e26a7ad566` | P12 | — | L10 | IntelliJ tour step fixes | Report a tour wait in IntelliJ about once a minute, as the VS Code tour's waits do |
| 606 | `8844c8a76c` | P12 | — | L10 | IntelliJ tour step fixes | Read the IntelliJ tour's negative checks once what could cause them has run, instead of waiting out the hang's bound |
| 607 | `04aff3dbfe` | P12 | — | L10 | IntelliJ tour step fixes | Report the IntelliJ tour's start-up waits about once a minute, as its other waits now do |
| 608 | `99f00e69ef` | P12 | — | L10 | IntelliJ tour step fixes | Report a tour wait once a minute, not in a burst after a blocked call, and leave no wait after a probe timeout silent |
| 609 | `90cf6a69bb` | P12 | — | L10 | The playbook container and the IDE it drives | Copy back only the VS Code runs that a container made, not every run its slot's work volume keeps |
| 610 | `ee093bcbef` | P6 | — | L4 | Rename guards and proofs | Answer a rename that can't be proven, or that a change overtook, for what it is, and let a cancel reach a plain rename's proof |
| 611 | `b537f0d9c0` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Print a stalled close's fibers before the watchdog fails it, which in CI they never were |
| 612 | `0a9cdf91c8` | P6 | — | L4 | Rename guards and proofs | Show nothing in IntelliJ for a rename that the user cancels, not "Rename failed: null" |
| 613 | `713113f100` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a document's release cancelled or overtook its proof, not move without them |
| 614 | `a6f17a8861` | P12 | — | L10 | Individual playbook case fixes | Wait for a moved type's files in VS Code's playbook while VS Code's events of them come, not for a fixed 30 s |
| 615 | `7696ae2531` | P12 | — | L10 | Individual playbook case fixes | Let one case's failure in VS Code's playbook fail that case alone, and restore the editor, not end the run |
| 616 | `e3fe3a4110` | P12 | — | L10 | The VS Code tour | Open a file by Quick Open in the VS Code tour again until it takes, typing only once the box has the keyboard |
| 617 | `0cfb4ba3e5` | P12 | — | L10 | The VS Code tour | Count no edit of a log as work under way in the VS Code tour's waits |
| 618 | `7ba13f7924` | P12 | — | L10 | The playbook container and the IDE it drives | Run the server's suites before a full VS Code playbook run only when asked, and print the icon task's files only on request |
| 619 | `5ed10ac44d` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Drop the IntelliJ plugin's own save of the closed documents a rename changes, which LSP4IJ 0.30.1 does (UP21) |
| 620 | `75c281f2af` | P12 | — | L10 | The playbook container and the IDE it drives | Give the playbook container a session bus and a stand-in system bus, so VS Code's Chromium stops logging D-Bus errors |
| 621 | `b12a97f449` | P12 | — | L10 | IntelliJ tour step fixes | Count as a tour wait's progress only what could make its condition hold, and give the start-up waits the idle check |
| 622 | `644f5c7ac2` | P12 | — | L10 | Individual playbook case fixes | Wait for the IntelliJ playbook's Structure tree as the tour's waits do, not with the driver's fixed ten and five seconds |
| 623 | `8b7d64eecf` | P12 | — | L10 | IntelliJ tour step fixes | Show the IntelliJ tour's conditional breakpoint balloon once Deck.x's lenses are in place, past the build mode's balloon |
| 624 | `8d42d3c033` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook's harness tests in a category of their own, first in every playbook and tour run |
| 625 | `8c9aa41339` | P12 | — | L10 | Individual playbook case fixes | Find the IntelliJ playbook's Structure window by its own title, not by names that the Project window can have |
| 626 | `6b2cf3d89e` | P12 | — | L10 | IntelliJ tour step fixes | Say in the IntelliJ tour's Ctrl+Alt+X, L check's failure what the IDE got of the chord's keys |
| 627 | `e3f966e224` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Say what the IntelliJ playbook's UP07 waits are, and reopen the intention menu once nothing could show it |
| 628 | `e43a28e92f` | P4 | — | L1, L2 | Keep documents current and the server responsive | End no workspace request when the editor releases a document outside the graph, or closes one the server does not have open |
| 629 | `94042face0` | P4 | — | L1, L2 | Keep documents current and the server responsive | Pull a workspace document's diagnostics without waiting for a library source's compile, which Go to Definition starts |
| 630 | `4836402abe` | P6 | — | L4 | Rename guards and proofs | Let a rename or a move in VS Code outlast the release of a document whose text its file has, as the server's proof does |
| 631 | `d98d8a3f5e` | P4 | — | L1 | Keep documents current and the server responsive | End no workspace request when VS Code releases a library's source that Go to Definition opened |
| 632 | `3b9d861978` | P12 | — | L10 | Individual playbook case fixes | Give the editor the keyboard in VS Code's playbook again until it takes, and wait for the workbench's widgets while it works, not for fixed seconds |
| 633 | `3586c7ab23` | P12 | — | L10 | The VS Code tour | Press Enter on a VS Code tour menu's item only once the window has run what the click started, not into what it opened |
| 634 | `32447330e1` | P12 | X5 | L10 | Individual playbook case fixes | Report a VS Code playbook case that fails with a known host bug's exact symptom as a known host failure, apart, and leave the run green |
| 635 | `2a1f1ccb0a` | P12 | — | L10 | The playbook container and the IDE it drives | Run VS Code's playbook and tour harness tests in a group of their own, first in every playbook and tour run, not in the extension suite |
| 636 | `652953bdd7` | P12 | — | L10 | Individual playbook case fixes | Press X130's Redo only once VS Code's Undo has ended with its didRenameFiles, not as soon as the folders are back on disk |
| 637 | `9c8b89de0b` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Format each file with the settings its .editorconfig files give, over the editor's, and describe the formatting settings in the user guide |
| 638 | `edc0218d98` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Read .editorconfig files in IntelliJ only while IntelliJ applies them itself, as its EditorConfig switch says |
| 639 | `3d0bd10a89` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Trim trailing whitespace in the Tree-sitter and base formatters only as the options ask, for a whole document too |
| 640 | `ff4d943f30` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Apply a file's end_of_line from .editorconfig in VS Code's Format Document, which no formatting edit can do |
| 641 | `168c36cd01` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep the lines between IntelliJ's formatter markers as they are, and read the wiki's continuation_indent_size too |
| 642 | `24a9e14a3e` | P13 | — | L10 | The lang documentation | Pad the aligned Markdown tables under lang/ so their columns line up again |
| 643 | `b646daf0a4` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Ask for the trim in the server test that formats a document whose only fault is its trailing whitespace |
| 644 | `8aa286f978` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Say plainly what is unused and what to do in COMPILER-188 and COMPILER-210 |
| 645 | `56b778112a` | P9 | X4 | — | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Name the XDK that each project builds with in the language server's Gradle model |
| 646 | `6da271abc3` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Refuse embedded builds and runs of a project on another XDK, and tell the editors which folders build with Gradle and why |
| 647 | `aecbf2e393` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build a folder on another XDK with Gradle in both editors, and say which XDK it builds with |
| 648 | `a95eb6a510` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Describe the Gradle mode of a project on another XDK in the user guide and the configuration reference |
| 649 | `67e1eb487f` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep workspace requests and analyses through the close or open of a graph member with its file's text |
| 650 | `25f7c009d8` | P4 | — | L1 | Keep documents current and the server responsive | Answer a request whose work the server retired for a change with ContentModified, and a document's diagnostic pull with a cancellation |
| 651 | `b21e47e990` | P6 | — | L4 | Rename guards and proofs | Answer a file move that can't be proven, or that a change overtook, for what it is, as a rename answers, and show its reason in both editors |
| 652 | `93607a6335` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Move the negative constructor argument tests to the XDK's compiler-errors suite |
| 653 | `2f42317e7a` | P12 | — | L10 | Individual playbook case fixes | Wait in VS Code's playbook while what could end a wait or a case is under way, not for fixed bounds |
| 654 | `dd65bc5b4a` | P12 | — | L10 | Playbook cases added after the catalog | Describe X361–X366 in the playbook catalog and the manual test plan: formatting from .editorconfig, and a folder on another XDK |
| 655 | `bacf6901cf` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run a module's Gradle tasks in its own folder's Gradle build in a VS Code window of several folders |
| 656 | `270ae0da2f` | P12 | — | L10 | Playbook cases added after the catalog | Add X361–X366 to the VS Code playbook |
| 657 | `13d865e9b8` | P12 | — | L10 | Playbook cases added after the catalog | Add X361–X363, X365 and X366 to the IntelliJ playbook |
| 658 | `9b2b5639ce` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run an included build's Gradle tasks in VS Code from the build that includes it, as its imported model says |
| 659 | `ffe3ea2a75` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Ask which folder's build runs Run Module's module when no folder holds it, in a VS Code window of several folders |
| 660 | `37b1a2a181` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Give VS Code's notice that a folder builds with Gradle once a session for each folder while it is in the window |
| 661 | `0056e85cff` | P12 | — | L10 | Playbook cases added after the catalog | Read X366's Gradle run output in VS Code's task terminal, check its notice always, and make its XDKs from this tree's installDist |
| 662 | `c46313997b` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Give an Ecstasy file .editorconfig's end_of_line at IntelliJ's Reformat Code, and check X363's unchanged formats through it |
| 663 | `f7484667e9` | P12 | — | L10 | Playbook cases added after the catalog | Open X366's and X303's Gradle builds as IntelliJ projects of their own, whose Build Project runs Gradle |
| 664 | `d796688609` | P4 | — | L1, L2 | Keep documents current and the server responsive | Let the queries on a module's compile go on when a member's open with its file's text replaces that compile |
| 665 | `86215b657d` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep a document's own requests through its release or its open with its file's text, and say in ContentModified what overtook a request |
| 666 | `5adea5a94b` | P4 | — | L1, L2 | Keep documents current and the server responsive | Let a request share a pending graph query that asks what it asks of the same inputs, instead of taking its place |
| 667 | `240b7c7131` | P4 | — | L1, L2 | Keep documents current and the server responsive | Test that a shared graph query ends only with its last request, and end a closed file's query that no analysis answers |
| 668 | `d0291e6026` | P12 | — | L10 | Individual playbook case fixes | Wait in a native Go to check until the EDT has run LSP4IJ's answer, before the case closes its file |
| 669 | `29f152934d` | P12 | — | L10 | Individual playbook case fixes | Fail a playbook wait for a refactoring's edit at once with the IDE's refusal, and by the idle rule |
| 670 | `e4ddf64d7a` | P6 | — | L4 | Rename guards and proofs | Keep a rename's proof current through a document opened with its file's text or deleted before the request |
| 671 | `589c0157de` | P6 | — | L4 | Rename guards and proofs | Refuse a rename whose edit reaches a document with unsaved changes that the server never read, saved since or not |
| 672 | `588976728c` | P12 | — | L10 | Individual playbook case fixes | Make the VS Code harness's own waits hear only what they are fed, and count no change of the window's focus as work |
| 673 | `f462592cb3` | P12 | — | L10 | Individual playbook case fixes | Make X27's folders one at a time in VS Code, each once the server heard of it, and wait for the watcher's events of Bad.x |
| 674 | `002ec2cf1e` | P4 | — | L1 | Keep documents current and the server responsive | Test that a watcher's creation and deletion of a closed member come and go in the workspace's pull, in and out of the graph |
| 675 | `6d7896c5be` | P12 | — | L10 | Narration of the editor tours | Write the narration of the IntelliJ and VS Code tours, a paragraph per section and step |
| 676 | `6dd395f6f8` | P12 | — | L10 | Narration of the editor tours | Narrate editor tours with local voices and synchronized subtitles |
| 677 | `927278d5ce` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Count xtc/endOfLine among the server's xtc messages in the plugin's protocol endpoints test |
| 678 | `614e2dc248` | P6 | — | L4 | Rename guards and proofs | Prove each edit of a rename or a move against the text the server computed it from, by its hash |
| 679 | `44b82b2c8e` | P4 | — | L1, L2 | Keep documents current and the server responsive | Recheck the sources of open modules outside the graph at each workspace pull, for a change that no watcher reported |
| 680 | `4e56e17585` | P12 | — | L10 | Individual playbook case fixes | Drive the IDE's refusal of a file rename by notification in X128, and the rename dialog's error text in X58 |
