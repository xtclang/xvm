# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 643 commits of `errs/full-testable-lsp-20261009` over `master` (tip `5029fdc600`, master `ede5667376`, 2026-11-27), numbered 1 to 643 from the oldest, with their short hashes. Commits 1-397 were planned on 2026-11-23; the rebase onto master of 2026-11-25 kept their numbers and gave them new hashes. Commits 398-643 came after, and were placed by the same rules. Three are open on master as their own PRs, and each takes its commits out of #693 when it merges: 458, with 499 and 521 its review round, is #802; 196, with the test fixtures of 194 (`Await` and `Watchdog`; 194's edits of #693's own tests stay), is #811; and 170 and 198 are #812, on #811's branch, as their tests wait through its fixtures. #813's four commits, 407, 465, 470 and 613 then, merged to master as ede5667376 on 2026-11-27; #693 was rebased onto it without them, which renumbered the commits after 407. #804's four commits, 467, 471, 473 and 474 then, merged to master as 2e26663249 on 2026-11-25; #693 was rebased onto it without them, which renumbered the commits after 467. #808's three, 481-483 then, merged as fb7ba82052 on 2026-11-26, and #693 was rebased onto it without them, which renumbered the commits after 480. #810, the plugin installable in every JetBrains IDE, merged as 4350e40af0 the same day; #693 was rebased onto it with no commit dropped, as its own fuller change (Gradle optional, not removed) is commit 530.

It proposes 15 pull requests, each of which changes either only files outside lang/ (xvm only: X1-X5) or only files in lang/ (lang only: L1-L10), merged in one order. To get there, the commits are first sorted into 13 topics (P1-P13), the commits of a topic that implement or fix the same thing, however far apart, are grouped to be joined, and every commit is then divided at lang/: its other files go to its topic's xvm PR, its lang/ files to its topic's lang PR.

## The pull requests, in merge order

| Order | PR | Changes only | Title | Prerequisites | Built from topics | Commits with a part in it | Added lines: main / tests |
|---|---|---|---|---|---|---|---|
| 1 | **X1** | files outside lang/ | Compiler diagnostics: the error-listener contract and repository read failures | — (master) | P1 | 12 | 2.9k / 2.8k |
| 2 | **X2** | files outside lang/ | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | X1 | P2, P11 | 15 | 5.7k / 1.1k |
| 3 | **X3** | files outside lang/ | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | X2 | P3, P4 | 8 | 5.8k / 1.7k |
| 4 | **X4** | files outside lang/ | Runtime support for running, testing and debugging from the editors: the debugger and its agent, xtc, xunit and the Gradle plugin | X3 | P9, P10 | 53 | 9.3k / 5.0k |
| 5 | **X5** | files outside lang/ | Build, CI and test tooling outside lang: CI test tiers, build checks, test hang detection, the LSP4IJ and IntelliJ versions, PR previews | X4 | P4, P7, P12, P13 | 29 | 1.7k / 1.0k |
| 6 | **L1** | lang/ | Language server: the server, its protocol, document synchronization, Tree-sitter and the mock adapter | X3, X5 | P4 | 50 | 11.5k / 14.3k |
| 7 | **L2** | lang/ | Language server: the compiler adapter's core, compiling, discovery, diagnostics and workspace pulls | L1 | P1, P2, P3, P4 | 43 | 9.3k / 17.6k |
| 8 | **L3** | lang/ | Language server: the semantic model and read-only editor features | L2 | P5 | 44 | 7.4k / 8.1k |
| 9 | **L4** | lang/ | Language server: code actions and refactorings | L3 | P6 | 45 | 9.6k / 13.7k |
| 10 | **L5** | lang/ | Language server: the XDK's library sources and the XDK workspace | L3 | P11 | 23 | 3.1k / 3.3k |
| 11 | **L6** | lang/ | IntelliJ plugin, on LSP4IJ 0.30.1 | L4, L5, X5 | P7 | 80 | 10.5k / 6.6k |
| 12 | **L7** | lang/ | VS Code extension | L4, L5 | P8 | 13 | 2.7k / 379 |
| 13 | **L8** | lang/ | Embedded build, run and tests in the language server and both editors | X4, L6, L7 | P9 | 51 | 9.6k / 8.9k |
| 14 | **L9** | lang/ | Debugging: the DAP server, debug sessions, launch and attach, and both editors' debug UI | X4, L8 | P10 | 49 | 8.7k / 11.1k |
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 239 | 9.2k / 64.8k |

Already open on master, apart from these, each leaving #693 when it merges: #802 (unused constructor arguments, commits 458, 499 and 521); #811 (a hasher map's spare key per thread, 196, with 194's test fixtures); and #812 (Future callbacks and service shutdowns reported and completed, 170 and 198, on #811). #804 (runXtc keeps xtcRun's settings), #813 (the Gradle model's build-tree paths and run settings, and its reports written whole, merged as `ede5667376`, which #693 is now based on), #808 (the plugin tests' one network download dropped) and #810 (the plugin installable in every JetBrains IDE) are merged.

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
| P1 | Compiler diagnostics: the error-listener contract and repository read failures | master | no | 15 | 4 | 3.1k / 3.2k |
| P2 | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | P1 | no | 8 | 4 | 5.8k / 3.1k |
| P3 | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | P2 | no | 6 | 2 | 5.7k / 2.0k |
| P4 | Language server core: protocol, document sync, compile pipeline, discovery, diagnostics | P3 | yes, once 5 commits' other files move | 54 | 16 | 20.5k / 29.3k |
| P5 | Language server: semantic model and read-only editor features | P4 | yes | 43 | 8 | 7.5k / 8.1k |
| P6 | Language server: code actions and refactorings | P5 | yes | 44 | 10 | 9.6k / 13.7k |
| P7 | IntelliJ plugin, built against LSP4IJ 0.30.1 | P6 | nearly | 79 | 28 | 10.6k / 6.7k |
| P8 | VS Code extension | P6 | yes | 12 | 7 | 2.7k / 379 |
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 54 | 22 | 10.4k / 9.6k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 62 | 36 | 17.3k / 15.3k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 24 | 9 | 3.5k / 3.5k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 192 | 22 | 3.8k / 65.2k |
| P13 | Documentation, build and CI | the PRs it documents | no | 50 | 6 | 6.9k / 376 |
| | **Total** | | | **643** | **174** | |

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

Commit 8, `359a9f70f0` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `816a5eaf27` | 366 `f8dfab767b` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `01d71f15a3` | 345 `d58be68ec3` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `bbc9b65898` | 374 `cb33d5d448` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `aed24e48a4` | 369 `94f730140d` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `0daa0c3580` | 367 `b9c7997529` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `171cb432ca` | 363 `12dca7985d` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `5721a67f2a` | 373 `c1e78eb82d` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `c934aa08d4` | 362 `78b51ab5cb` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `860142c745` | 360 `3e67aa1251` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `1e8fe89c79` | 112 `f4dd9b73cf` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `3572746910` | 105 `7dc03e8622` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `359a9f70f0` | 36 `097234afa2` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `359a9f70f0` | 387 `a6b0d958d9` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `5ba27d04a3` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `494329601d` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `66ae1540e8` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `6e2df65490` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `63880cbd47` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `f6dd130142` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `37fff18f97` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `3a472a6918` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `3c4366d8c4` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `7945dc72ee` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `5b5f7186cc` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `87afab0662` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `199f1d584c` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `d5a2663aca` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `48301c955d` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `0fe7ab0560` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `e049178a8d` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `d25c1f903d` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `08eeb51b22` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `dbeda2c987` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `34664c07ae` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `24a79f91c8` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `430d39399c` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `4385bc396d` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `0ce37813fd` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `585018f763` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `3f4ac8da03` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `cb4213598d` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `3abf355362` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `15010c4043` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `25adb6b433` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `eba60a0540` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `fac6e6bd5a` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `2215832f5b` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `70af0b11f9` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `11665a0b91` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `300b55c094` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `bc9647039d` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `fcb3b097fb` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `e0ea192f8a` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `7bbf7ae9b7` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `b573998280` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `b343ddc0c7` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `7c9d8e7700` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `3572746910` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `7fea2e2a6c` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `26c808fcee` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `39fa555bf9` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `d083457102` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `7dc03e8622` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `1e8fe89c79` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `40ead327ad` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `c6624931a2` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `f4dd9b73cf` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `89116399f4` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `0e378a3683` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `82f4191ae9` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `d31b6933fd` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `6e090858d2` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `e5700128b9` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `1a7b7d5463` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `cfd7f06a66` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `2d6ee15e1d` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `5721a67f2a` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `b4da44b5c1` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `bbc9b65898` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `fc9c845446` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `b27ce81b15` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `f48c9f637c` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `1b5eb12281` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `db66822e4d` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `add8c00987` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `2089f2ddcc` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `4a3d9f2206` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `39ce34948f` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `fcee12236b` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.
- 400 `5baf0462b9` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82), in P7 (+272; also touches: playbook)
- 409 `e29e550024` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774), in P9 (+114; also touches: playbook)
- 414 `8bca79e541` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries, in P9 (+55; also touches: playbook)
- 420 `1b91ba48bd` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive, in P7 (+141; also touches: playbook, server, vscode)
- 421 `255918295e` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded, in P9 (+220; also touches: playbook)
- 422 `0a296dc230` Show the embedded compiler as not available where the project's own modules replace bundled libraries, in P9 (+133; also touches: playbook)
- 423 `b9407e6bbe` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours, in P13 (+754; also touches: intellij, vscode)
- 424 `f692c55ad8` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy, in P7 (+277; also touches: playbook, server)
- 425 `4a84f94273` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run, in P13 (+193; also touches: intellij, playbook)
- 426 `cb8115f2d3` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide, in P7 (+73; also touches: playbook)
- 427 `1116f8e775` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own, in P9 (+447; also touches: playbook)
- 428 `671bdc54c5` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature, in P13 (+251; also touches: playbook)
- 429 `6ac3777c8a` Move the user guide into lang/doc/user-guide, with its screenshots in images, in P13 (+996; also touches: intellij, playbook, vscode)
- 430 `9db798c44a` Let a debug launch or attach set how long evaluations and lazy values may run, in P10 (+716; also touches: playbook)
- 431 `80b0e059f8` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode, in P12 (+5; also touches: build, intellij)
- 432 `149c2e7a4c` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc, in P9 (+485; also touches: playbook)
- 434 `0f8d335976` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting, in P13 (+125; also touches: playbook)
- 435 `e7f867f53c` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed, in P6 (+589; also touches: intellij, playbook, vscode)
- 440 `30cd2e38ec` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration, in P5 (+110; also touches: javatools)
- 442 `eb687dd113` Bind the names of a method's multiple return values to the values they declare, in P5 (+65; also touches: javatools)
- 443 `4fb6a51b41` Bind this in a lazy property's calc() and super in a method reference to what they name, in P5 (+147; also touches: javatools)
- 444 `213b7d4785` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed, in P6 (+275; also touches: playbook)
- 453 `372c51b60b` Keep the initializer facts of a property declared in a loop's body, such as a static local, in P5 (+47; also touches: javatools)
- 458 `a52c397838` Report constructor arguments and default values that no constructor uses, in P1 (+129; also touches: xdk)
- 460 `7e45521d85` Digest the API of each declaration of a module apart (DeclarationDigests), in P11 (+622; also touches: javatools)
- 462 `1f2ade29d1` Keep diagnostics working when a restart opens another server's library copy again, in P11 (+294; also touches: playbook)
- 464 `8473885213` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors, in P9 (+1,453; also touches: playbook)
- 467 `bd9626cca4` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own, in P11 (+1; also touches: javatools)
- 469 `285bb8f019` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages, in P13 (+1,264; also touches: intellij)
- 470 `7c4789b66b` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered, in P12 (+141; also touches: intellij, server)
- 490 `d7ab4b5b74` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run, in P12 (+367; also touches: build, intellij, vscode)
- 499 `e1601172eb` Allow contribution arguments and shorthand defaults that match the explicit constructor, in P1 (+400; also touches: xdk)
- 509 `157e9c9c52` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed, in P13 (+17; also touches: intellij, server)
- 510 `57c5bc9fa7` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops, in P13 (+16; also touches: server)
- 511 `d7ad594473` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate, in P13 (+2; also touches: server)
- 512 `47e952c331` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair, in P13 (+60; also touches: intellij)
- 513 `eaa43fb0c2` Share the language server's location requests and edit conversion, and say the semantic tokens setting once, in P13 (+32; also touches: server)
- 514 `5c2679a233` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end, in P13 (+40; also touches: server)
- 515 `bc109a6fb1` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias, in P13 (+68; also touches: server)
- 517 `90f4c4347b` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode, in P13 (+7; also touches: vscode)
- 518 `3257254215` Share the language server's cancellation-owning future and its formatter's parenthesis search, in P13 (+23; also touches: server)
- 522 `b1d2d02586` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume, in P12 (+57; also touches: intellij)
- 523 `1e0ab80fbc` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share, in P12 (+14; also touches: intellij)
- 526 `321ee9208d` Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too, in P12 (+23; also touches: intellij)
- 530 `3a3b3336bd` Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed, in P7 (+10; also touches: playbook)
- 532 `152274e874` Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times, in P12 (+15; also touches: intellij)
- 533 `c996e30737` Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent, in P4 (+285; also touches: dap, intellij)
- 534 `eaea2681f0` Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire, in P4 (+1,129; also touches: dap, intellij, playbook)
- 535 `65bbada907` Share the xtc.* keys and a build's summary text through lang/protocol, in P4 (+188; also touches: intellij, playbook)
- 536 `632af00e4a` Share the compiler's library settings through lang/protocol: their shape and how a path names a file, in P4 (+224; also touches: intellij)
- 543 `c4cd6ff9ec` Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps, in P12 (+52; also touches: dap)
- 545 `b57ca5ab3b` Share the plugin unit tests' disk files, connection, waits and opens through LightEditor, in P12 (+125; also touches: intellij)
- 546 `9179e78505` Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup, in P12 (+201; also touches: server)
- 547 `6b7f429f17` Share the lsp-server adapter tests' source writer and text edit application, in P12 (+190; also touches: server)
- 548 `7e3e14c591` Share the embedding's incomplete analysis in the header tests, and merge their identical cases, in P12 (+137; also touches: server)
- 549 `3775838e0c` Shorten the code action tests with the shared edits and per-class helpers, in P12 (+81; also touches: server)
- 550 `da21c70dbc` Share an adapter with a module compile hook among the tests that count or hold compiles, in P12 (+84; also touches: server)
- 551 `46dc903069` Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures, in P12 (+121; also touches: server)
- 552 `91cc7ca9c4` Merge FormattingConfigRoundTripTest's mock clients and server setup, in P12 (+48; also touches: server)
- 553 `e145f9a239` Apply the missing member, extraction and initializer tests' edits with the shared apply, in P12 (+7; also touches: server)
- 554 `35cb21e69e` Check XdkMemberUseTrackerTest's renames of foo through one helper, in P12 (+36; also touches: server)
- 555 `00e218b221` Share the marker comment position of the navigation, lookup and occurrence tests, in P12 (+77; also touches: server)
- 556 `2b026fca0c` Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file, in P12 (+91; also touches: server)
- 557 `9c0f19a122` Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know, in P4 (+31; also touches: intellij)
- 565 `4f4bc23983` Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7), in P5 (+677; also touches: intellij, vscode)
- 566 `b8d6e3a12f` Say, when a rename leaves string literals and comments that spell the old name, how many and where, in P6 (+232; also touches: playbook)
- 567 `fd9b77d1fc` Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording, in P12 (+10; also touches: intellij)
- 568 `e062d20888` Terminate the extension's tasks also when the terminate comes before VS Code has started them, in P9 (+91; also touches: playbook)
- 572 `270eb086d8` Build the playbook container's XDK first when the slot's build is of other sources than the checkout's, in P12 (+85; also touches: build, intellij)
- 573 `65a50826ca` Let playbook container runs take the first free slot, in the order they asked, instead of their checkout's slot, in P12 (+123; also touches: intellij)
- 588 `85bb8aade2` Find the XDK's declarations in Go to Symbol in Workspace, after the workspace's own, so that X356 passes in both editors, in P11 (+263; also touches: playbook)
- 590 `a58225e5d1` Press Ctrl+Alt+X, B and Ctrl+Alt+X, R as keys in both editor tours, and check what each does, in P12 (+79; also touches: build)
- 600 `2da9be1be8` Remove the Gradle caches and wrappers of other Gradle versions from a container slot before its run, in P12 (+16; also touches: intellij)
- 602 `d7f2e11f6b` Make lang's three copies of Awaiting.kt one test fixture, org.xvm.testing's, in a new project, lang/test-support, in P12 (+252; also touches: build, dap, intellij, server)
- 603 `9e5f2b69fc` Say in VS Code why a rename is refused, as IntelliJ's plugin does, instead of VS Code's "No result.", in P6 (+101; also touches: playbook, vscode)
- 609 `8b86c30c02` Copy back only the VS Code runs that a container made, not every run its slot's work volume keeps, in P12 (+27; also touches: intellij)
- 610 `4a0c265304` Answer a rename that can't be proven, or that a change overtook, for what it is, and let a cancel reach a plain rename's proof, in P6 (+584; also touches: intellij, playbook, vscode)
- 612 `29fc3b1735` Show nothing in IntelliJ for a rename that the user cancels, not "Rename failed: null", in P6 (+33; also touches: intellij)
- 613 `6652dfc072` Ask the server again for a move's edits when a document's release cancelled or overtook its proof, not move without them, in P8 (+74; also touches: playbook)
- 618 `7016c1e810` Run the server's suites before a full VS Code playbook run only when asked, and print the icon task's files only on request, in P12 (+19; also touches: vscode)
- 619 `8d0b93692b` Drop the IntelliJ plugin's own save of the closed documents a rename changes, which LSP4IJ 0.30.1 does (UP21), in P7 (+37; also touches: playbook)
- 620 `06056b52e9` Give the playbook container a session bus and a stand-in system bus, so VS Code's Chromium stops logging D-Bus errors, in P12 (+14; also touches: intellij)
- 624 `82941c6019` Run the IntelliJ playbook's harness tests in a category of their own, first in every playbook and tour run, in P12 (+30; also touches: intellij)
- 630 `1f0140961a` Let a rename or a move in VS Code outlast the release of a document whose text its file has, as the server's proof does, in P6 (+48; also touches: playbook, vscode)
- 632 `6f46f8ae74` Give the editor the keyboard in VS Code's playbook again until it takes, and wait for the workbench's widgets while it works, not for fixed seconds, in P12 (+318; also touches: vscode)
- 634 `3afc061b12` Report a VS Code playbook case that fails with a known host bug's exact symptom as a known host failure, apart, and leave the run green, in P12 (+155; also touches: build)
- 635 `c1d59e449d` Run VS Code's playbook and tour harness tests in a group of their own, first in every playbook and tour run, not in the extension suite, in P12 (+167; also touches: vscode)
- 638 `9f4c85e4d8` Read .editorconfig files in IntelliJ only while IntelliJ applies them itself, as its EditorConfig switch says, in P5 (+142; also touches: intellij)
- 640 `a8989dcd12` Apply a file's end_of_line from .editorconfig in VS Code's Format Document, which no formatting edit can do, in P5 (+93; also touches: build, playbook, vscode)
- 641 `18f12985f6` Keep the lines between IntelliJ's formatter markers as they are, and read the wiki's continuation_indent_size too, in P5 (+421; also touches: intellij, vscode)
- 642 `b45c401d96` Pad the aligned Markdown tables under lang/ so their columns line up again, in P13 (+101; also touches: vscode)


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

**Builds on** master. **Commits:** 15 now, about 4 after squashing. **Added lines:** 3,196 main, 3,247 tests.

| Join into | Commits | Why |
|---|---|---|
| Keep, cache and report module repository read failures | 1 `f597130694` Preserve module repository read failures without blocking usable candidates<br>9 `6e2df65490` Report repository read attempts without inferring dependency failures<br>11 `f6dd130142` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `0393125d13` Separate diagnostic reporting from listener abort policy<br>3 `6e5d586776` Require explicit compiler listeners and reasons for silence<br>4 `c5d57e2ddd` Scope parser diagnostics and restore validation listeners on every exit<br>19 `c7262be202` Route repository and runtime reports through error listeners<br>41 `171c45ff62` Stop the lexer at a source's end and keep diagnostics that only look like duplicates<br>465 `6d62bb081e` Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType()<br>466 `9352626a2e` Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `444710c1a0` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `15b7606972` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |
| Unused constructor arguments and defaults (open PR #802) | 458 `a52c397838` Report constructor arguments and default values that no constructor uses<br>499 `e1601172eb` Allow contribution arguments and shorthand defaults that match the explicit constructor<br>521 `8687101044` Test that a class without constructors compiles and uses its contribution's arguments | already open on master as #802, with 502 and 524 its review round; they drop out of #693 when #802 merges |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `5ab49b4d23` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `5ba27d04a3` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `b151849ea0` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `494329601d` Expose compilation-owned syntax and resolved semantic facts<br>10 `63880cbd47` Clarify tooling contracts and isolate partial AST helpers<br>12 `a6d6423461` Verify compiler output without binary AST dependencies<br>115 `a7e4cc983b` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `37fff18f97` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `66ae1540e8` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `be8c73f809` Complete a member access typed at the end of a line<br>144 `13514d8870` Complete a member with the cursor right after its dot<br>161 `d31b6933fd` Complete names and show a call's signature where they are typed at a line's end<br>385 `1771a8f8d5` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `7945dc72ee` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 54 now, about 16 after squashing. **Added lines:** 20,381 main, 29,349 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `4ab9c038be` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `3add636d22` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `ae4ba88494` Let two run tests check what they claim instead of how long they wait<br>301 `e9ea54368f` Make the retention test's cancellations and the library content test's refresh check independent of timing<br>559 `b30c2d1f55` Read XdkPullDiagnosticsTest's traced answers in the order their queries came, not the order their ends were written | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `359a9f70f0` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `097234afa2` Remove the document-color prototype<br>266 `0c7a277f5d` Remove the unused xtc/runnableTargets request<br>387 `a6b0d958d9` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `199f1d584c` Keep documents current and the server responsive, and name its own diagnostics<br>28 `60f640d5cc` Judge file events against a running analysis and apply them in order<br>92 `57a3c77d1b` Analyze again an open document whose analysis was retired without a replacement<br>184 `c07fb3c6df` Answer requests that a restarted client sends while it opens its files again<br>537 `7ca88c80cb` Answer xtc/tests when a document's close retires the compile it waits for (X342)<br>538 `2c2a3e6a17` Keep listed code actions resolvable across a close that leaves a document's text as on disk<br>587 `18f35f8b15` Answer Find References again when documents only opened or closed while it ran, not with the cancellation that showed X251 none<br>628 `188f211161` End no workspace request when the editor releases a document outside the graph, or closes one the server does not have open<br>629 `d21f6a699a` Pull a workspace document's diagnostics without waiting for a library source's compile, which Go to Definition starts<br>631 `7a2db0b7d8` End no workspace request when VS Code releases a library's source that Go to Definition opened | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `a7dfe21c24` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `d4b287588d` List a closed test module's tests from the workspace pull's compile<br>237 `246ff7b1d1` Clear a deleted file's diagnostics in the next workspace pull<br>248 `ad2b8d203a` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `84dd89c231` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `885e9b43e5` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `4864c5debe` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `c7913f445c` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `4cb63b37a7` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `9b043fca58` Split the LSP tests into a fast default tier and complete runs<br>143 `f82e7942a2` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `dbeda2c987` Declare fields first and use plain names in the new classes<br>95 `2117bbe99f` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `7b1d233a5c` Compile a closure as a fold, without mutable accumulators<br>137 `88fbab689b` Name the properties that hold behavior or mutable state in camel case<br>44 `1228371c75` Synchronize the semantic token history that concurrent queries share<br>163 `f399c3d6a1` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `4385bc396d` Keep the build's jars whole and its test runs focused<br>97 `0c7ba9871c` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |
| Time and heap logs of the server's work | 418 `1a2a67aa8a` Log each project query's and each compile's time and heap<br>419 `be82af4e15` Log what the server did in each minute in which it worked, and over its session | the server's own logs of what each compile, query and minute of work cost |
| Trace less: one end per request, answers only when changed, one start and end per discovery | 491 `85f28eb8de` Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase<br>492 `5ba5691bf4` Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count<br>493 `6ecf818b9c` Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG | the INFO trace keeps a start and one end with the phases' times; DEBUG keeps the phases and writes a pull's answer only when it differs |
| One xtc/ protocol, shared by the plugin and the servers: lang/protocol | 533 `c996e30737` Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent<br>534 `eaea2681f0` Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire<br>535 `65bbada907` Share the xtc.* keys and a build's summary text through lang/protocol<br>536 `632af00e4a` Share the compiler's library settings through lang/protocol: their shape and how a path names a file<br>557 `9c0f19a122` Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know | the plugin and the servers each kept their own copy of the xtc/ extension's interfaces and wire types, TestEvent, the xtc.* keys, a build's summary text and the library settings, and the copies had drifted; lang/protocol is the one copy (compileOnly on LSP4J and Gson), which the plugin ships as its own jar; 560 lets the plugin's Compiler page open when the library settings hold a key it doesn't know |

Kept as they are:

- 24 `7baa796386` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `6e090858d2` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `dcbc19a9ab` Keep the execution trace's events out of stderr and server.log
- 173 `726fb08200` Let one source that discovery cannot read cost only itself
- 180 `e5fb2068e8` Keep a project's module, source and library order the same in every JVM
- 261 `974fa463b3` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 43 now, about 8 after squashing. **Added lines:** 7,522 main, 8,055 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `a7ceb0b726` Complete with the language model's keywords and built-in types<br>162 `c70ffc89aa` Complete a type of another module where a statement starts, with its import<br>185 `4a983f76f6` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `b723c56b71` Name a type by what it is when hovering its name<br>156 `76a0f91877` Give a constructor's finally block no references lens of its own<br>157 `d38f7f8fa9` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `d25c1f903d` Keep indentation while typing, and lay out switches as the XDK does<br>135 `aa33d43bb2` Share the formatter's empty-block skeleton and whitespace edits<br>186 `95a2b41cbb` Answer on-type formatting without waiting for the change to compile<br>565 `4f4bc23983` Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7)<br>637 `7172e96506` Format each file with the settings its .editorconfig files give, over the editor's, and describe the formatting settings in the user guide<br>638 `9f4c85e4d8` Read .editorconfig files in IntelliJ only while IntelliJ applies them itself, as its EditorConfig switch says<br>639 `36a985f68c` Trim trailing whitespace in the Tree-sitter and base formatters only as the options ask, for a whole document too<br>640 `a8989dcd12` Apply a file's end_of_line from .editorconfig in VS Code's Format Document, which no formatting edit can do<br>641 `18f12985f6` Keep the lines between IntelliJ's formatter markers as they are, and read the wiki's continuation_indent_size too<br>643 `5029fdc600` Ask for the trim in the server test that formats a document whose only fault is its trailing whitespace | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |
| Bind more names in the semantic model | 440 `30cd2e38ec` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration<br>441 `6bb9656a71` Make the _ placeholder no occurrence in the semantic model<br>442 `eb687dd113` Bind the names of a method's multiple return values to the values they declare<br>443 `4fb6a51b41` Bind this in a lazy property's calc() and super in a method reference to what they name<br>452 `2f74ea0f01` Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares<br>453 `372c51b60b` Keep the initializer facts of a property declared in a loop's body, such as a static local<br>454 `51407e782b` Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration<br>455 `44abbb283a` Declare the names of a function type's values in the semantic model, as of its return values in a typedef<br>456 `392434b1c8` Bind a reserved name such as this:class in a property's accessor, as in a method of the class<br>457 `bddecb9375` Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells | each adds a kind of binding; the compiler facts that 441, 443, 444 and 454 add go to X2 |
| The semantic model's censuses, and the bindings they found missing | 478 `54dceba041` Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence<br>480 `ac76b333a8` Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares<br>483 `15763bc07b` Count the dispatch chains that hold an unproven identity in the semantic model's census<br>484 `2878697574` Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder<br>486 `f1b5c5e07c` Count the bindings that no proof can place in the semantic model's census, and fail on one<br>500 `21289fc18e` Count in the semantic model's census what hover, definition, references and highlights miss at a name<br>501 `ba6c59ffb4` Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations<br>502 `b59891d491` Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to<br>504 `7624486f3b` Count in the editor's census the references whose declaration is in no source of the workspace<br>505 `c2112d8f4d` Compile the census's libraries as one workspace folder, as an editor that opens the repository does<br>560 `b51cce3bef` Bind a named argument of another module's method, as inPlace in freeze(inPlace=True), to its parameter in that module's source<br>561 `ed869bddf3` Bind a formal type that a lambda names, as To in n -> n.toIntN().as(To), to the method's or the class's formal type<br>562 `e62c69be14` Bind a parameter of a constructor with a finally block that only lambdas in its body name to its declaration<br>563 `71a53db34d` Place a label written with unicode escapes where it is written, in its declaration, a break and a continue | the census of the 21 libraries as a heavy test, counting dispatch chains and unplaceable bindings, and the formal children and implicit formals it found unbound; the editor's census of what hover, definition, references and highlights miss, over the repository as one workspace folder, and the parameters of a constructor with a finally block and labels it found unbound |

Kept as they are:

- 102 `0b65ed3a67` Link a local's ranges when the caret is just after its name
- 235 `6a43011635` Fold comments and runs of imports with the compiler adapter
- 347 `61d291a926` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 44 now, about 10 after squashing. **Added lines:** 9,755 main, 13,826 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `bbc9b65898` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `76d52117de` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `cb33d5d448` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `1a7b7d5463` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `bd7dc672f2` Prove a code action by compiling only the modules its edit reaches<br>193 `6bc7982817` Start a revision's code action compile from the previous one's, and log its cost<br>205 `df7737a62d` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `eb9c40056a` Keep the compiles of modules that compiled when only private declarations move<br>436 `3c6156eff8` Start a rename's proof from the revision's compile, and compile after the edit only what it reaches<br>449 `cea7f8029f` After a rename's edit, compile only the modules depending on the edited ones that it can reach | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `5dd69302b6` Safely delete an unused public or protected member<br>214 `e50c27d419` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `f2d5ec7691` Extract statements that use an injected local, passing its value<br>177 `29f35ff951` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `34664c07ae` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `b4ef2f25e0` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `3a472a6918` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `c7db385ec9` Prove a rename past a register the semantic model cannot place<br>394 `a078214d95` Refuse a plain rename that also changes the configured source modules, and say why<br>411 `cd022fd011` Prove a rename in a workspace module that replaces a bundled library as the workspace's sources<br>415 `a5bfffdb64` Find a text's line breaks once per comparison in a rename's proof, not once per position<br>416 `8097043431` Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes<br>417 `3b26dd6fac` Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy<br>459 `366a55ed2d` Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element<br>603 `9e5f2b69fc` Say in VS Code why a rename is refused, as IntelliJ's plugin does, instead of VS Code's "No result."<br>604 `7e1c98cb5b` Answer xtc/rename's refusals with their reason, as textDocument/rename does, instead of as an internal error<br>610 `4a0c265304` Answer a rename that can't be proven, or that a change overtook, for what it is, and let a cancel reach a plain rename's proof<br>612 `29fc3b1735` Show nothing in IntelliJ for a rename that the user cancels, not "Rename failed: null"<br>630 `1f0140961a` Let a rename or a move in VS Code outlast the release of a document whose text its file has, as the server's proof does | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `fa64f1df42` Offer code actions in a module that has a const with a toString<br>176 `643bbd349b` Offer Create class for the diagnostic of a statement that constructs it<br>211 `995f071aa7` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |
| Say what a change to a public declaration reaches outside the workspace | 435 `e7f867f53c` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed<br>444 `213b7d4785` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed<br>566 `b8d6e3a12f` Say, when a rename leaves string literals and comments that spell the old name, how many and where | renames, safe deletes and file moves say when a public declaration changes for modules outside it |
| A client's cancellation reaches the adapter's work | 437 `b4b9008ca4` Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work<br>450 `d7fb82e22b` Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done | cancelling a rename or xtc/build stops the adapter's work, also once a result is converted |
| Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | 479 `e451063f62` Prove an anonymous class's this and construction by the span of the type it constructs<br>481 `86bf610968` Prove a union receiver's call of a method that the runtime implements, as a const's appendTo<br>482 `eaa8353f14` Prove a call on a union receiver whose legs share one route, as toString on a Point?<br>485 `095987f677` Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method<br>487 `1879ca581b` Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name<br>503 `25400e2f9d` Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property | an anonymous class's this and constructors by the span and the constructor they pass to, calls whose union legs share a route or a runtime body, methods merged for an annotation into a union, the Referent the compiler adds to a property by its property, and tests that the refusals that must stay are the proof's |

Kept as they are:

- 73 `7c9d8e7700` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 79 now, about 28 after squashing. **Added lines:** 10,551 main, 6,767 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `d76b951e0e` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83)<br>495 `d371bd3fc2` Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `e1e0cc85e9` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `4340f99b26` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `8ec5cd1948` Review the internal LSP4IJ APIs for 0.30.0<br>392 `4e407c04ee` Name LSP4IJ 0.30.1 in the plugin's README and two test comments<br>398 `77f73e73b1` Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83)<br>400 `5baf0462b9` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `bd74b42e20` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `affba1ca6e` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `dd3da38a3e` Let snippet completions insert as they are (UP18)<br>339 `dddaebecb3` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `0af438f651` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `2361e709ac` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `0b3c9f773d` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `ffe2b1bf2f` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `483ca607c2` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `cfa23ff3a3` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `7a9cdc027c` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `09443f0c6d` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `c2bb4e7be9` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `4d77af558a` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `0eb8a29829` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `01d71f15a3` Drop the change a restart's retired document synchronizer still sends<br>345 `d58be68ec3` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `171cb432ca` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `12dca7985d` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `15a0e3b2b9` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `aed24e48a4` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `94f730140d` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `5721a67f2a` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `55f6403da5` Put the embedded build's errors in the Problems window's Project Errors<br>292 `f6b260782e` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `9bea8371de` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `122febd310` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `c1e78eb82d` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `53fac0615a` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `0337f6f6e9` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `ffcc621be2` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `9b87f2af1c` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `8cba9eff09` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `ce327ac3bc` Give Ecstasy files a PSI of the Ecstasy language<br>153 `5d7c53de3a` Close a block that Enter opens after a { in an Ecstasy file<br>155 `db7bd1cc91` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `64684a9516` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `9a75e6294b` Give Ecstasy its Code Style settings page<br>140 `46afad5f61` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `34d57ef729` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `4a6f11c184` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `383c5fcc37` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `fcee12236b` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `c2efc071f6` Keep each light test's stub server and wrappers to that test<br>209 `a920e4d554` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `121d6547d0` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `292259e6fa` Keep the stale instrumented classes of an older build off the plugin's unit tests<br>558 `7b4edd6e65` Hold ClosedFileProblemsTest's later workspace pulls until its next refresh, so that none lands after its build | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `969dc1a796` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `a3fbf0f097` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04)<br>619 `8d0b93692b` Drop the IntelliJ plugin's own save of the closed documents a rename changes, which LSP4IJ 0.30.1 does (UP21) | Two comments that explain why kept workarounds stay (UP21, UP04). |
| The language server JVM's memory setting | 420 `1b91ba48bd` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive | a setting in both editors with its server side; divide by path |
| Every IntelliJ settings page under one Ecstasy node | 424 `f692c55ad8` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy<br>426 `cb8115f2d3` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide | one Ecstasy node in IntelliJ's settings, its pages fitted to the dialog |
| Marks that LSP4IJ made before it connected a file's document (UP86) | 472 `099232cb5f` Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86)<br>488 `fdc6e90c7a` Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap | refreshes an editor's marks once the connected document holds its own report; lsp4ij#2231 and our fix #2232 would retire it |
| Callers that a write action interrupted in the Call Hierarchy (UP88) | 506 `a13bae9669` Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88)<br>520 `c13e75b13a` Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy<br>530 `3a3b3336bd` Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed | LSP4IJ 0.30.1's call hierarchy returns no children when a write action cancels its wait for the server; the provider rethrows the cancellation so IntelliJ asks again; redhat-developer/lsp4ij#2278 would retire it |
| Installable in every JetBrains IDE: no Java dependency, Gradle optional | 527 `bb44321275` Make the IntelliJ plugin installable in every JetBrains IDE: no Java dependency, and Gradle only where the IDE has it<br>531 `2ff72941cc` Name the plugin's required-dependency test as master's #810 does, with the test runner among them | the plugin required the Java and Gradle plugins, which kept the Marketplace to IntelliJ IDEA, Android Studio and MPS; master's #810 dropped them where nothing used them, and here Gradle's three uses are optional (xtc-gradle.xml); 534 gives the dependency test master's name |

Kept as they are:

- 14 `8983fb81f4` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `3c4366d8c4` Restart editor-owned language servers from a project-scoped CLI
- 32 `b19cf3776e` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `385667796c` Cancel IntelliJ requests whose connection ends
- 99 `b1e0728c27` Keep the language server running while a run or debug session uses it
- 130 `59b02ab1e1` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `6ba2b56cfc` Build an embedded run's process handler in one place
- 154 `82f4191ae9` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `8a80a84ff9` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `1df0057ddb` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 12 now, about 7 after squashing. **Added lines:** 2,731 main, 379 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `f5aa694b8b` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `57cbf1ed1c` Say when a move leaves the uses of its sources as they were<br>250 `0b06c881d7` Ask the server again for a move's edits when a change overtook its answer<br>613 `6652dfc072` Ask the server again for a move's edits when a document's release cancelled or overtook its proof, not move without them | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `54f981a734` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `c07907dec6` Compile the VS Code extension's TypeScript into an empty out/<br>272 `bb14e2daf7` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |
| npm dependencies of master's #805 | 497 `fe719d5e8d` Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm | the rebase onto master kept the branch's package.json and lock; this brings in #805's glob 14 and minimatch 11 overrides and regenerates the lock |

Kept as they are:

- 119 `adaf82fe0a` List every VS Code command in the extension README, by its own title
- 234 `fb30f73943` Trace the language server's messages when xtc.trace.server says so
- 304 `ddbe8b6104` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `b9ab0a20e4` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 54 now, about 22 after squashing. **Added lines:** 10,422 main, 9,680 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `4b4b92ccf3` Report a Future callback that throws or that a terminated service drops<br>198 `f203d2967b` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `421f62862d` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `5b5f7186cc` Build and run with the embedded compiler in the server and both editors<br>27 `48301c955d` Make embedded the default build mode, chosen per editor<br>179 `363e5f36e8` Build a Gradle-imported project embedded in embedded mode<br>568 `e062d20888` Terminate the extension's tasks also when the terminate comes before VS Code has started them<br>601 `9bb15b730e` Tag the extension's repeated task terminate with UP91, VS Code's dropped early terminate (microsoft/vscode#350588) | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `d5a2663aca` Let embedded runs read console input in both editors<br>34 `08eeb51b22` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `661412f540` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `f0107251c6` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `0fe7ab0560` Run programs side by side in supervised execution workers<br>33 `15351c1238` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `430d39399c` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `70af0b11f9` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `9f50bbe4ef` Lift the lazy-value time limit for the embedded execution tests' worker<br>463 `2a28fd82b9` Take the execution worker's events off its standard output, onto a loopback socket of their own | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `57c6ee327f` Let deduction find a named module in src/main/x beside other sources<br>178 `2d6ee15e1d` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `1e8fe89c79` Run and debug a module's xunit tests from the editors, embedded<br>112 `f4dd9b73cf` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `40ead327ad` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `9afabde840` Rerun only the failed Ecstasy tests from the test console<br>202 `61078a6b23` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `c49adf3962` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `7c1cf5a8e3` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `99ea5a4a52` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `ea98cfc184` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders<br>427 `1116f8e775` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own<br>432 `149c2e7a4c` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `c6624931a2` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `f1c65b5100` Pass a run task's args in Gradle and command-line mode<br>236 `4a2e8cfc11` Run the editor's module from its Run button and context menu | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `1c63805973` Don't reuse a module compiled in another copy of the project<br>204 `a69616df8f` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `2afd1c8e21` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `bf13a710e4` Describe running and debugging tests from both editors in the READMEs<br>121 `78ebe5c9ae` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |
| Library replacement in Gradle mode, per workspace folder, with the editors' setting | 406 `5b57017ff2` Build, run and debug with Gradle while the workspace's own modules replace bundled libraries<br>414 `8bca79e541` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries<br>421 `255918295e` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded<br>422 `0a296dc230` Show the embedded compiler as not available where the project's own modules replace bundled libraries | the workspace's own modules replacing bundled libraries: in Gradle mode, kept to the folder that replaces them, the editors' setting, and the embedded compiler shown as not available; the editor parts need L6 and L7, so the group goes to L8 |
| Gradle tasks in each module's own project (#774) | 407 `2c1cd9c8bb` Tell the client each module's Gradle project from the imported build model (#774)<br>408 `adddfe5170` Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774)<br>409 `e29e550024` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774)<br>410 `24665bf617` Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) | the server names each module's Gradle project, and both editors run its tasks there, reaching a composite's included builds |
| Embedded runs with their Gradle project's xtcRun and xtcTest settings | 464 `8473885213` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors | embedded runs take the Gradle project's method, arguments and JVM options, from 465's export |

Kept as they are:

- 30 `e049178a8d` Find entry methods through the module's type and pass them with their arguments
- 38 `24a79f91c8` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `2794159b6b` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `5b7592350c` Report a stopped run's end without the failure its stop causes
- 199 `e6d30c0bbf` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `bc5c629297` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 62 now, about 36 after squashing. **Added lines:** 17,343 main, 15,307 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `3572746910` Attach the debugger to a program run with --debug<br>105 `7dc03e8622` Move the debugger agent into javatools, with the protocol as records<br>114 `57f1863a64` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `c230cb1098` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `ed7ef9c04e` Let the debugger find a module's sources where the language server says<br>383 `3620a3623f` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `b3358a2fe3` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it<br>405 `32887095cd` Find a debugged module's sources in the workspace when no source graph names it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `585018f763` Debug programs from VS Code and IntelliJ through the language server<br>72 `540640f2de` Send a step's response before the stop where the step ends<br>594 `d3ad93fd15` Tell a debug session where a debugged file's breakpoints are as an edit moves them, so VS Code moves its markers without a save | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `cb4213598d` Stop at exceptions, pause a running program, and stop on entry<br>75 `a1902734bb` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `15010c4043` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `da53fe349b` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `fac6e6bd5a` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `16082c57c2` Let a host give lazy variables' code longer than ON_DEMAND<br>430 `9db798c44a` Let a debug launch or attach set how long evaluations and lazy values may run | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `e0ea192f8a` Restart a stopped frame, which its caller then calls again<br>215 `ebf6f0e1ce` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `3ef4949969` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `0bdcc9c834` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `caa3cc64ad` Lift SessionDebuggerTest's time limit to an hour<br>166 `e5700128b9` Make SessionDebuggerTest independent of how its threads are scheduled<br>611 `1e223f8155` Print a stalled close's fibers before the watchdog fails it, which in CI they never were | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `c934aa08d4` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `78b51ab5cb` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `860142c745` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `3e67aa1251` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `0daa0c3580` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `b9c7997529` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `816a5eaf27` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `f8dfab767b` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `26c808fcee` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `d083457102` Debug a command-line run: xtc run runs the program, which connects<br>149 `1db9f198f8` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `bf92b84411` Let VS Code set breakpoints in Ecstasy files<br>232 `655413fa12` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `7b90f26bb7` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `5473649161` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |
| End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | 401 `8ce6137469` End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) | LSP4IJ 0.30.1 keeps the exit code from the exited event; one commit |
| Debugged stacks end at the host's fiber; failed requests say why | 438 `790d36bfa7` End a debugged program's stack at the host's fiber, also when it has moved on from its call<br>439 `1b12a1739e` Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." | the runtime ends a debugged program's stack at the host's fiber (X4), the DAP server answers a failed request with its reason (L9) |

Kept as they are:

- 47 `3f4ac8da03` Show a stopped frame's variables, and step over, into and out of calls
- 49 `3abf355362` Evaluate watches and hovers on paths in a stopped frame
- 51 `25adb6b433` Evaluate expressions with operators without running the program's code
- 52 `403ecac33b` Leave the runner's frames out of the debugger's stacks and traces
- 53 `eba60a0540` Run code typed in the Debug Console on the stopped fiber
- 54 `5f4fe96c00` Stop at the program's first line from IntelliJ's configuration
- 56 `2215832f5b` Place breakpoints on the lines a debugged program was built from
- 59 `300b55c094` Show library frames in their bundled source, every fiber, and modules
- 60 `bc9647039d` Step into a chosen call of the stopped line
- 61 `fcb3b097fb` Stop where a method of a name is entered
- 63 `7bbf7ae9b7` Stop once a watched field of an object has changed
- 65 `b343ddc0c7` Answer inline values with the stopped method's variables
- 101 `39fa555bf9` Continue, step or pause one fiber while the others stay stopped
- 103 `07065489eb` Test that a part of a value's variables from past its end is empty
- 106 `dabcf4d549` Keep a debugged task's last output, and X325's queued fiber
- 141 `98e46a0877` Debug a program that has an interface
- 169 `a13cbb31f4` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `cfd7f06a66` Let a debugger condition name an enum value by its enumeration
- 195 `b0f80dd278` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 24 now, about 9 after squashing. **Added lines:** 3,538 main, 3,504 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `1627f253e2` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `add8c00987` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `2089f2ddcc` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `d1df3bab58` Compile a library source's ecstasy-library content with its library, as its copy is<br>588 `85bb8aade2` Find the XDK's declarations in Go to Symbol in Workspace, after the workspace's own, so that X356 passes in both editors | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `b354a84b86` Report no diagnostics for a library source's read-only copy<br>249 `6cc2d6e54e` Compile a library source's copy on its own even once a compile has named it<br>462 `1f2ade29d1` Keep diagnostics working when a restart opens another server's library copy again | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `d78a6a5320` Delete the language server's temporary directories once no server needs them<br>277 `d3a7a13e1c` Keep a server directory's lock beside it, where reading the directory cannot release it<br>494 `3ae0e51d2a` Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `2e0b692229` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `88e1653ec9` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `921b73204c` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `9557882943` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |
| Replaced libraries: their import candidates and their errors | 412 `c7a26f47b7` Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's<br>413 `84b6f1d260` Say on a replaced library with errors that the server's own compiler may be older than the repository's | two server follow-ups of library replacement |
| Compile only what an edit changes: declaration digests and the member-use tracker | 447 `3b64412b22` Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies<br>448 `899fdf7227` Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do<br>460 `7e45521d85` Digest the API of each declaration of a module apart (DeclarationDigests)<br>461 `0b8115eb72` Compile again, after an edit, only the modules that use what it changed (member-use tracker)<br>467 `bd9626cca4` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own | DeclarationDigests in javatools (X2), the artifact API fingerprint and the member-use tracker in the server (L5); 469 fixes 461's Javadoc, fold it in |

Kept as they are:

- 45 `c9d28673fe` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `4be0cd67ed` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 192 now, about 22 after squashing. **Added lines:** 3,809 main, 65,325 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `0e378a3683` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `fc9c845446` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `b27ce81b15` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `c6d2f621b2` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `8272462e22` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `6c0d32f800` Report the written video's real length from the tour composer<br>276 `dc59831618` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `ebf758202d` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `8c1ed42baa` Record a playbook run's screen as video in the container<br>228 `05611930cb` Delete a playbook run's IDE folder once its logs are copied, in the container<br>532 `152274e874` Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `b75769bbeb` Press the keymap's own Undo key in the IntelliJ tour<br>253 `60e4acb50f` Undo in the IntelliJ tour from the editor as the change left it<br>255 `7422f3f93f` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `a65917b539` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `b39b80495c` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `d3741b0c25` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `b9778ebe39` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `17dabf5b15` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `e607df1acd` Edit in the tour's kill step only once the restarted server has the file open (UP83)<br>403 `0f6b39362f` Check that one server comes back when the tour kills the server's process (UP72)<br>404 `73eee4c133` Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will<br>446 `b99972cd3b` Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames<br>473 `2f7faf18e1` Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place<br>489 `11a2e8f228` Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does<br>496 `b2b51edbbb` Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does<br>591 `68fc9a476d` Check Rebuild Project, Ctrl+Shift+F10 and a Gradle-mode Build Project in the IntelliJ tour<br>596 `efc8d9ddfa` Bring the IntelliJ tour's coverage map up to LSP4IJ 0.30.1, and list the plugin's build mode, restart and Run All Tests actions<br>605 `5d43c78c12` Report a tour wait in IntelliJ about once a minute, as the VS Code tour's waits do<br>606 `462d2bf520` Read the IntelliJ tour's negative checks once what could cause them has run, instead of waiting out the hang's bound<br>607 `f083106ab4` Report the IntelliJ tour's start-up waits about once a minute, as its other waits now do<br>608 `29ac132203` Report a tour wait once a minute, not in a burst after a blocked call, and leave no wait after a probe timeout silent<br>621 `3f57dee86d` Count as a tour wait's progress only what could make its condition hold, and give the start-up waits the idle check<br>623 `b450151bfc` Show the IntelliJ tour's conditional breakpoint balloon once Deck.x's lenses are in place, past the build mode's balloon<br>626 `dc535840c5` Say in the IntelliJ tour's Ctrl+Alt+X, L check's failure what the IDE got of the chord's keys | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `db66822e4d` Record a tour of the VS Code extension that checks what it shows<br>251 `2d19250699` Make the VS Code tour pass as one whole run, unrecorded<br>257 `06fbb072a9` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `96d33a9bc3` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `332b157875` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `acfa77f707` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `fc786bb5fa` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `53940f3857` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop<br>569 `7d347fdf55` Stop tasks again while any are left in the VS Code tour's stopAll, and wait for the embedded runs' ends<br>570 `c93b50a750` Count no silent embedded run as work in the VS Code tour's waits, so a stop that does not take fails within the hang window<br>571 `b5eecd410e` Wait for the VS Code tour's clicks and focus as its waits do, and close the context menu that a failed step leaves<br>589 `491115ac1f` Open the VS Code tour's Explorer name box in Rename a file once the tree has the keyboard on Hand.x, and again until it shows<br>590 `a58225e5d1` Press Ctrl+Alt+X, B and Ctrl+Alt+X, R as keys in both editor tours, and check what each does<br>592 `ae5c999740` Check in the VS Code tour that the language server comes back when its process dies<br>593 `a2d04a8964` Show refactoring's refusals in both editor tours: a rename that would change what the code means, and one of the XDK's name<br>595 `a9f572162c` Show breakpoints while you edit in both editor tours: one that follows its line as a line is typed above it<br>597 `29bcb6d334` Show Go to Definition from a named argument into the XDK's source, read-only, in both editor tours<br>598 `7a2e46596f` Choose a context menu's item by Enter in the VS Code tour when its click only gave it the keyboard<br>616 `bdb5505cc2` Open a file by Quick Open in the VS Code tour again until it takes, typing only once the box has the keyboard<br>617 `919006cb35` Count no edit of a log as work under way in the VS Code tour's waits<br>633 `40db2c5511` Press Enter on a VS Code tour menu's item only once the window has run what the click started, not into what it opened | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `89116399f4` Let tests wait for a hang only, not for a fast machine<br>194 `b4da44b5c1` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `65e8210b18` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `9db7c62ef1` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `a61d28ceb4` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `7fea2e2a6c` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `f48c9f637c` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `cdbd0545db` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `6a897f526f` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `c1fc6d19b9` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `39ce34948f` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `77670056f8` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `a03819e749` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `1ebd6e11c9` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `e49baaee7e` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `05ec855f06` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `f394eb9f3e` Run the editor playbooks in the container from Gradle, with any engine<br>146 `b325533d98` Queue playbook container runs on one machine lock, each with its own export<br>147 `b10edd3676` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `4a3d9f2206` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3<br>431 `80b0e059f8` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode<br>522 `b1d2d02586` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume<br>523 `1e0ab80fbc` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share<br>525 `246aa464ce` Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed<br>526 `321ee9208d` Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too<br>567 `fd9b77d1fc` Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording<br>572 `270eb086d8` Build the playbook container's XDK first when the slot's build is of other sources than the checkout's<br>573 `65a50826ca` Let playbook container runs take the first free slot, in the order they asked, instead of their checkout's slot<br>600 `2da9be1be8` Remove the Gradle caches and wrappers of other Gradle versions from a container slot before its run<br>609 `8b86c30c02` Copy back only the VS Code runs that a container made, not every run its slot's work volume keeps<br>618 `7016c1e810` Run the server's suites before a full VS Code playbook run only when asked, and print the icon task's files only on request<br>620 `06056b52e9` Give the playbook container a session bus and a stand-in system bus, so VS Code's Chromium stops logging D-Bus errors<br>624 `82941c6019` Run the IntelliJ playbook's harness tests in a category of their own, first in every playbook and tour run<br>635 `c1d59e449d` Run VS Code's playbook and tour harness tests in a group of their own, first in every playbook and tour run, not in the extension suite | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `8c2c5022f0` Offer server save formatting in IntelliJ (UP02)<br>330 `564051c033` Open bundled library sources as the server's virtual documents (UP25)<br>331 `a4ecde904d` Check inline completion's invocation and selection natively (UP26)<br>332 `3d26d47b9d` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `cfe1efe279` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `85912d2c07` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `b2f612c8f6` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `f3db019ded` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `743579b228` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `0e9fe25920` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `65521c13ff` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `4eaec8e0b0` Read a signature without parameters in the playbook<br>351 `949ccc35b8` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `1b8d61abc5` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `228cbea337` Check X254's library view through the server<br>354 `3ab3903330` Read X5's library target from the server until LSP4IJ connects it<br>355 `da89812827` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `58405337d9` Expect X20's signature without parameters in VS Code<br>365 `c7a36ba132` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `41a18d2f67` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `28ea61c69e` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `f219063e7a` Ask X254's definition again when the restarted server retires its analysis<br>376 `2ce4553839` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `075fc2ed33` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view<br>399 `bc51c62559` Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81)<br>402 `42aeff8f89` Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53)<br>627 `6b6825019e` Say what the IntelliJ playbook's UP07 waits are, and reopen the intention menu once nothing could show it | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `b5cbb61e10` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `249c75924f` Send X259's library edit to the old server before the restart<br>87 `07cb907bff` Wait for X257's Invoked request to reach the LSP console<br>88 `ee48f775e4` Reopen a popup under inspection after the IDE was deactivated<br>175 `f8ca52a918` Give X145 a reference search that waits until it is canceled<br>282 `80b82de346` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `41fa62173c` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `6520e5f183` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `c733393bdb` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `ee3e605708` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open<br>445 `4525937a0e` Give the VS Code client its own handler of the server's messages back after the playbook's CFG2<br>539 `78d6f41e7e` Open a companion destination in IntelliJ's playbook after the quick fix's edit has changed it (X214)<br>614 `bc88caba40` Wait for a moved type's files in VS Code's playbook while VS Code's events of them come, not for a fixed 30 s<br>615 `831234d823` Let one case's failure in VS Code's playbook fail that case alone, and restore the editor, not end the run<br>622 `d700cb153b` Wait for the IntelliJ playbook's Structure tree as the tour's waits do, not with the driver's fixed ten and five seconds<br>625 `6be74e57bf` Find the IntelliJ playbook's Structure window by its own title, not by names that the Project window can have<br>632 `6f46f8ae74` Give the editor the keyboard in VS Code's playbook again until it takes, and wait for the workbench's widgets while it works, not for fixed seconds<br>634 `3afc061b12` Report a VS Code playbook case that fails with a known host bug's exact symptom as a known host failure, apart, and leave the run green<br>636 `e1e72e41b3` Press X130's Redo only once VS Code's Undo has ended with its didRenameFiles, not as soon as the folders are back on disk | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `87afab0662` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `1b5eb12281` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `70f7b66e0d` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `c6590ecdc9` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `1564ecce29` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `11665a0b91` Check Run to Cursor in both editors<br>64 `b573998280` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `0bccc0ccf4` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `364f718e41` Check a document link's Ctrl+Click in IntelliJ's X131<br>577 `bd48e963ed` Check in both editors' X269 that the memory policy and the maximum heap reach the restarted server, and that JVM options win<br>578 `1b82cb957c` Add X352 to both editors' playbooks: a loop's label, its hover, highlights, references, definition and renames<br>579 `99204c8446` Add X353 to both editors' playbooks: a constructor's parameter with a finally block, from its body and a named argument<br>580 `8424a2d0f6` Add X354 to both editors' playbooks: Build and Run in the automatic build mode, beside a Gradle build and without one<br>581 `5fc1251620` Add X355 to both editors' playbooks: tests that don't show, of a module that doesn't parse and of one whose dependency has an error<br>582 `d9d9774686` Add X356 to both editors' playbooks: Go to Symbol in Workspace finds the XDK's declarations, read-only<br>583 `71cb3d4e18` Add X357 to both editors' playbooks: Organize Imports removes an unused import and sorts the rest, in one edit<br>584 `6d4f6400ea` Add X358 to both editors' playbooks: hover and Quick Documentation of the XDK's types and methods show their doc comments<br>585 `6b118a8220` Add X359 to both editors' playbooks: Go to Definition on a named argument of an XDK method opens its parameter, read-only<br>586 `6733cda035` Add X360 to both editors' playbooks: a method's type parameter named in a lambda is the method's | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `c906af22ef` Give the playbook catalog's size as its 342 cases everywhere<br>122 `1564395d42` Point the playbook instructions at the playbook container<br>127 `cfe32df426` List the test and debug-task messages and a test row in the playbook's 18a<br>529 `c47e59d413` Drop the retired X273-X275 from the scope that the VS Code playbook's results.json reports | Fold into the catalog's documentation. |
| What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | 470 `7c4789b66b` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered<br>471 `9bd76e3598` Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it | the playbook keeps each server's whole trace, with each pull's answer at DEBUG only, and the tour's waits and popup steps say what LSP4IJ holds and what could close them |
| Playbook traces: compressed, kept whole for failures and the newest pass | 490 `d7ab4b5b74` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run | the pruning compresses a finished run's traces, keeps the newest 10 failed runs and the newest passing run of each mode whole, and judges pass and fail from results.json |
| Test code shared instead of copied | 540 `10c75a5945` Share the IntelliJ playbook executors' repeated steps instead of copying them per case<br>541 `8120a4ff3a` Share the playbook probe's breakpoint states, variable paths and child nodes<br>542 `ea3b9451e8` Share the IntelliJ tour's repeated test-run, attach and adapter steps<br>543 `c4cd6ff9ec` Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps<br>544 `a95cb6dde2` Share the playbook's semantic-token reads, workspace models and xunit case heads<br>545 `b57ca5ab3b` Share the plugin unit tests' disk files, connection, waits and opens through LightEditor<br>546 `9179e78505` Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup<br>547 `6b7f429f17` Share the lsp-server adapter tests' source writer and text edit application<br>548 `7e3e14c591` Share the embedding's incomplete analysis in the header tests, and merge their identical cases<br>549 `3775838e0c` Shorten the code action tests with the shared edits and per-class helpers<br>550 `da21c70dbc` Share an adapter with a module compile hook among the tests that count or hold compiles<br>551 `46dc903069` Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures<br>552 `91cc7ca9c4` Merge FormattingConfigRoundTripTest's mock clients and server setup<br>553 `e145f9a239` Apply the missing member, extraction and initializer tests' edits with the shared apply<br>554 `35cb21e69e` Check XdkMemberUseTrackerTest's renames of foo through one helper<br>555 `00e218b221` Share the marker comment position of the navigation, lookup and occurrence tests<br>556 `2b026fca0c` Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file<br>574 `080e5d0bb5` Share the VS Code playbook's fixture steps across its files: one module, a Gradle model, the server's status, the outside-uses message<br>575 `8f1f8586aa` Run the VS Code playbook's debug cases through one Debugger, its runs through Executions, and its tests through one TestWatch<br>576 `602b84c2b2` Share the VS Code playbook's completion and argument steps: names without snippets, a replaced range, a signature, an accepted name<br>602 `d7f2e11f6b` Make lang's three copies of Awaiting.kt one test fixture, org.xvm.testing's, in a new project, lang/test-support | the duplicated test code CPD found in lsp-server, dap-server, the plugin's unit tests, the playbook, its probe and the IntelliJ tour, shared as helpers and fixtures without dropping a check |

Kept as they are:

- 43 `0ce37813fd` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `7adb270873` Apply X103's rename as a refactoring and check the member after Undo
- 187 `1a840a4d86` Check that Debug of Current File launches an Ecstasy configuration
- 189 `e0b9b7c673` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `6a091bd8b9` Run the VS Code playbook after the extension suite, not with it
- 200 `416457b559` Count the code actions at the tour's unused import and unused local
- 247 `36434b0860` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `13dc5021b5` Print both compiler playbooks' progress per case in the build's output
- 279 `2d0092188c` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 50 now, about 6 after squashing. **Added lines:** 6,886 main, 376 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `a865f32d30` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `327c752e79` Drop the references to documents and plans this branch does not carry<br>69 `9662bcc75a` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `7790311f49` Fix lang/README's task paths, versions and feature summary<br>123 `a301db1e07` Use American spelling in the shippable docs<br>124 `55f060bbd7` Name testing in the embedded build and run index entry<br>125 `0b5fae0cf8` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `9e56dd05e8` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `768e29ef75` Say that every adapter needs Java 25 in editor-setup.md<br>283 `8b6e4ccb8e` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `51be6d0c34` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `9e613d5cb4` Point the docs' line citations at the lines today's changes moved<br>642 `b45c401d96` Pad the aligned Markdown tables under lang/ so their columns line up again | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `859b1584e3` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `5e7a4ff54a` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `5f48ca3367` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `b53dffa15e` Leave the lang build out of the root build by default again<br>379 `098aaa7e0e` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `ba12fd9dc6` Say in AGENTS.md that gradle.properties turns the lang build flags on<br>564 `46f8cf7605` Say what MarketplaceListingTest's listing numbers are: a real listing's, and which of them the reader uses | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |
| The user guide, with screenshots from the tours | 423 `b9407e6bbe` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours<br>425 `4a84f94273` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run<br>428 `671bdc54c5` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature<br>429 `6ac3777c8a` Move the user guide into lang/doc/user-guide, with its screenshots in images<br>433 `fab600127c` Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's<br>434 `0f8d335976` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting<br>451 `ca97b34781` Say in the user guide what a rename doesn't change or check<br>468 `d8ef35d736` Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs<br>469 `285bb8f019` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages<br>474 `801c5ba313` Mark the user guide's keys up as keys, and the names it left plain as code<br>475 `090cf45fc3` Add an index of every page and section of the user guide to its landing page<br>476 `1a5521b6dd` Give the user guide's long sections subsections, and its index the third level<br>477 `6ba91cf948` Box the user guide's single screenshots with their captions, as its pairs are<br>528 `0489501183` Correct the user guide where the playbooks show it wrong, and add links and implement or override<br>599 `699a213eac` Say in the user guide that only a parse error hides a test module's tests, and how IntelliJ finds the XDK's symbols | the guide, its testing chapter and its screenshots, made from tagged tour steps |
| Opt-in static analysis of lang: detekt and CPD | 498 `c240ee67cd` Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript<br>507 `2c60fbbd28` Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings<br>508 `109d4b0309` Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too<br>524 `5842ec04a8` Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates | lang's tasks for detekt over its Kotlin and CPD over its Kotlin and TypeScript, reporting only, not in check; the two tools' version pins in the root catalog go to X5 |
| Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | 509 `157e9c9c52` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed<br>510 `57c5bc9fa7` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops<br>511 `d7ad594473` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate<br>512 `47e952c331` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair<br>513 `eaa43fb0c2` Share the language server's location requests and edit conversion, and say the semantic tokens setting once<br>514 `5c2679a233` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end<br>515 `bc109a6fb1` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias<br>516 `adec6e2deb` Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over<br>517 `90f4c4347b` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode<br>518 `3257254215` Share the language server's cancellation-owning future and its formatter's parenthesis search<br>519 `cadc4c1352` Generate TextMate's operator patterns from one list of categories, not seven copies of the same block | detekt's and the duplicate checks' (CPD, IntelliJ's) findings fixed in lang's main code; it touches files of L1-L9, so it goes last, in L10, or is divided by path into each file's PR when the PRs are cut |

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
| 1 | `f597130694` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `0393125d13` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `6e5d586776` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `c5d57e2ddd` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `5ba27d04a3` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `494329601d` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `66ae1540e8` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `359a9f70f0` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `6e2df65490` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `63880cbd47` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `f6dd130142` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `a6d6423461` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `4cb63b37a7` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `8983fb81f4` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `37fff18f97` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `3a472a6918` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `3c4366d8c4` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `7945dc72ee` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `c7262be202` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `5b5f7186cc` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `a865f32d30` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `87afab0662` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `199f1d584c` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `7baa796386` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `d5a2663aca` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `661412f540` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `48301c955d` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `60f640d5cc` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `0fe7ab0560` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `e049178a8d` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `d25c1f903d` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `b19cf3776e` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `15351c1238` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `08eeb51b22` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `dbeda2c987` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `097234afa2` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `34664c07ae` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `24a79f91c8` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `430d39399c` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `385667796c` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `171c45ff62` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `4385bc396d` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `0ce37813fd` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `1228371c75` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `c9d28673fe` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `585018f763` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `3f4ac8da03` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `cb4213598d` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `3abf355362` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `15010c4043` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `25adb6b433` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `403ecac33b` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `eba60a0540` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `5f4fe96c00` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `fac6e6bd5a` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `2215832f5b` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `70af0b11f9` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `11665a0b91` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `300b55c094` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `bc9647039d` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `fcb3b097fb` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `e0ea192f8a` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `7bbf7ae9b7` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `b573998280` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `b343ddc0c7` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `0bccc0ccf4` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `364f718e41` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `327c752e79` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `9662bcc75a` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `c7db385ec9` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `54f981a734` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `540640f2de` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `7c9d8e7700` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `7adb270873` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `a1902734bb` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `5dd69302b6` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `f5aa694b8b` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `16082c57c2` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `3572746910` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `3ef4949969` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `4ab9c038be` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `3add636d22` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `ae4ba88494` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `01d71f15a3` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `b5cbb61e10` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `249c75924f` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `07cb907bff` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `ee48f775e4` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `816a5eaf27` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `e49baaee7e` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `0bdcc9c834` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `57a3c77d1b` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `7fea2e2a6c` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `05ec855f06` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `2117bbe99f` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `26c808fcee` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `0c7ba9871c` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `1c63805973` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `b1e0728c27` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `f394eb9f3e` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `39fa555bf9` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `0b65ed3a67` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `07065489eb` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `d083457102` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `7dc03e8622` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `dabcf4d549` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `57c6ee327f` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `1e8fe89c79` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `7b1d233a5c` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `40ead327ad` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `c6624931a2` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `f4dd9b73cf` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `9f50bbe4ef` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `57f1863a64` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `a7e4cc983b` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `7b90f26bb7` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `bf13a710e4` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `c906af22ef` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `adaf82fe0a` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `7790311f49` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `78ebe5c9ae` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `1564395d42` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `a301db1e07` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `55f060bbd7` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `0b5fae0cf8` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `9e56dd05e8` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `cfe32df426` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `768e29ef75` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `5473649161` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `59b02ab1e1` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `be8c73f809` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `b723c56b71` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `2794159b6b` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `a7ceb0b726` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `aa33d43bb2` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `6ba2b56cfc` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `88fbab689b` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `9b043fca58` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `9a75e6294b` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `46afad5f61` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `98e46a0877` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `caa3cc64ad` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `f82e7942a2` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `13514d8870` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `fa64f1df42` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `b325533d98` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `b10edd3676` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `89116399f4` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `1db9f198f8` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `0e378a3683` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `4be0cd67ed` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `ce327ac3bc` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `5d7c53de3a` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `82f4191ae9` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `db7bd1cc91` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `76a0f91877` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `d38f7f8fa9` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `9afabde840` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `c934aa08d4` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `b151849ea0` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `d31b6933fd` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `c70ffc89aa` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `f399c3d6a1` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `6e090858d2` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `53fac0615a` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `e5700128b9` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `b4ef2f25e0` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `f2d5ec7691` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `a13cbb31f4` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `4b4b92ccf3` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `1a7b7d5463` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `dcbc19a9ab` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `726fb08200` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `cfd7f06a66` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `f8ca52a918` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `643bbd349b` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `29f35ff951` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `2d6ee15e1d` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `363e5f36e8` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `e5fb2068e8` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `171cb432ca` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `64684a9516` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `da53fe349b` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `c07fb3c6df` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `4a983f76f6` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `95a2b41cbb` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `1a840a4d86` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `5b7592350c` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `e0b9b7c673` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `6a091bd8b9` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `5721a67f2a` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `bd7dc672f2` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `6bc7982817` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `b4da44b5c1` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `b0f80dd278` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `5ab49b4d23` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `860142c745` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `f203d2967b` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `e6d30c0bbf` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `416457b559` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `a7dfe21c24` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `61078a6b23` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `bbc9b65898` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `a69616df8f` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `df7737a62d` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `c49adf3962` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `c2efc071f6` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `885e9b43e5` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `a920e4d554` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `d4b287588d` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `995f071aa7` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `0337f6f6e9` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `ffcc621be2` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `e50c27d419` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `ebf6f0e1ce` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `bc5c629297` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `c07907dec6` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `b354a84b86` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `7c1cf5a8e3` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `76d52117de` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `55f6403da5` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `15a0e3b2b9` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `9b87f2af1c` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `99ea5a4a52` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `0daa0c3580` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `4864c5debe` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `8c1ed42baa` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `05611930cb` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `fc9c845446` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `b27ce81b15` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `bf92b84411` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `655413fa12` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `f1c65b5100` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `fb30f73943` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `6a43011635` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `4a2e8cfc11` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `246ff7b1d1` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `57cbf1ed1c` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `421f62862d` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `f48c9f637c` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `1b5eb12281` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `c6d2f621b2` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `db66822e4d` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `2afd1c8e21` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `aed24e48a4` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `eb9c40056a` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `36434b0860` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `ad2b8d203a` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `6cc2d6e54e` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `0b06c881d7` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `2d19250699` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `b75769bbeb` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `60e4acb50f` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `8cba9eff09` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `7422f3f93f` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `a65917b539` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `06fbb072a9` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `96d33a9bc3` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `332b157875` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `b39b80495c` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `974fa463b3` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `8272462e22` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `13dc5021b5` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `6c0d32f800` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `65e8210b18` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `0c7a277f5d` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `1627f253e2` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `ea98cfc184` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `f0107251c6` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `d3741b0c25` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `acfa77f707` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `bb14e2daf7` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `5f48ca3367` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `add8c00987` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `d78a6a5320` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `dc59831618` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `d3a7a13e1c` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `2089f2ddcc` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `2d0092188c` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `d1df3bab58` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `70f7b66e0d` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `80b82de346` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `8b6e4ccb8e` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `ba12fd9dc6` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `121d6547d0` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `ebf758202d` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `b9778ebe39` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `fc786bb5fa` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `84dd89c231` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `51be6d0c34` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `41fa62173c` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `f6b260782e` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `c6590ecdc9` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `9bea8371de` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `122febd310` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `1564ecce29` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `292259e6fa` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `c7913f445c` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `8a80a84ff9` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `cdbd0545db` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `e9ea54368f` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `34d57ef729` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `6520e5f183` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `ddbe8b6104` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `9e613d5cb4` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `6a897f526f` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `4a6f11c184` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `c1fc6d19b9` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `4a3d9f2206` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `9db7c62ef1` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `2e0b692229` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `921b73204c` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `88e1653ec9` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `9557882943` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `859b1584e3` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `5e7a4ff54a` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `b53dffa15e` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `ed7ef9c04e` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `39ce34948f` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `c733393bdb` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `a61d28ceb4` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `77670056f8` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `a03819e749` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `1ebd6e11c9` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `e1e0cc85e9` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `bd74b42e20` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `affba1ca6e` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `dd3da38a3e` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `8c2c5022f0` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `564051c033` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `a4ecde904d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `3d26d47b9d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `cfe1efe279` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `85912d2c07` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `b2f612c8f6` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `f3db019ded` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `743579b228` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `0e9fe25920` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `dddaebecb3` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `0af438f651` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `2361e709ac` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `0b3c9f773d` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `ffe2b1bf2f` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `483ca607c2` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `d58be68ec3` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `cfa23ff3a3` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `61d291a926` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `7a9cdc027c` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `65521c13ff` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `4eaec8e0b0` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `949ccc35b8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `1b8d61abc5` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `228cbea337` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `3ab3903330` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `da89812827` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `8ec5cd1948` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `1df0057ddb` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `58405337d9` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `09443f0c6d` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `3e67aa1251` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `c2bb4e7be9` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `78b51ab5cb` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `12dca7985d` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `4340f99b26` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `c7a36ba132` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `f8dfab767b` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `b9c7997529` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `c230cb1098` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `94f730140d` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `41a18d2f67` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `28ea61c69e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `4d77af558a` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `c1e78eb82d` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `cb33d5d448` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `f219063e7a` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `2ce4553839` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `969dc1a796` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `075fc2ed33` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `098aaa7e0e` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `383c5fcc37` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `b9ab0a20e4` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `ee3e605708` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `3620a3623f` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `444710c1a0` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `1771a8f8d5` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `15b7606972` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `a6b0d958d9` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `fcee12236b` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `53940f3857` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `17dabf5b15` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `e607df1acd` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `4e407c04ee` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `0eb8a29829` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `a078214d95` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `a3fbf0f097` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `b3358a2fe3` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `d76b951e0e` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
| 398 | `77f73e73b1` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83) |
| 399 | `bc51c62559` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81) |
| 400 | `5baf0462b9` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) |
| 401 | `8ce6137469` | P10 | — | L9 | End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) |
| 402 | `42aeff8f89` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) |
| 403 | `0f6b39362f` | P12 | — | L10 | IntelliJ tour step fixes | Check that one server comes back when the tour kills the server's process (UP72) |
| 404 | `73eee4c133` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will |
| 405 | `32887095cd` | P10 | — | L9 | The debugger finds sources where its host names them | Find a debugged module's sources in the workspace when no source graph names it |
| 406 | `5b57017ff2` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build, run and debug with Gradle while the workspace's own modules replace bundled libraries |
| 407 | `2c1cd9c8bb` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Tell the client each module's Gradle project from the imported build model (#774) |
| 408 | `adddfe5170` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774) |
| 409 | `e29e550024` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774) |
| 410 | `24665bf617` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) |
| 411 | `cd022fd011` | P6 | — | L4 | Rename guards and proofs | Prove a rename in a workspace module that replaces a bundled library as the workspace's sources |
| 412 | `c7a26f47b7` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's |
| 413 | `84b6f1d260` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Say on a replaced library with errors that the server's own compiler may be older than the repository's |
| 414 | `8bca79e541` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Add an editor setting for whether the workspace's own modules replace bundled XDK libraries |
| 415 | `a5bfffdb64` | P6 | — | L4 | Rename guards and proofs | Find a text's line breaks once per comparison in a rename's proof, not once per position |
| 416 | `8097043431` | P6 | — | L4 | Rename guards and proofs | Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes |
| 417 | `3b26dd6fac` | P6 | — | L4 | Rename guards and proofs | Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy |
| 418 | `1a2a67aa8a` | P4 | — | L1, L2 | Time and heap logs of the server's work | Log each project query's and each compile's time and heap |
| 419 | `be82af4e15` | P4 | — | L1 | Time and heap logs of the server's work | Log what the server did in each minute in which it worked, and over its session |
| 420 | `1b91ba48bd` | P7 | — | L6 | The language server JVM's memory setting | Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive |
| 421 | `255918295e` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded |
| 422 | `0a296dc230` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Show the embedded compiler as not available where the project's own modules replace bundled libraries |
| 423 | `b9407e6bbe` | P13 | — | L10 | The user guide, with screenshots from the tours | Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours |
| 424 | `f692c55ad8` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy |
| 425 | `4a84f94273` | P13 | — | L10 | The user guide, with screenshots from the tours | Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run |
| 426 | `cb8115f2d3` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide |
| 427 | `1116f8e775` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own |
| 428 | `671bdc54c5` | P13 | — | L10 | The user guide, with screenshots from the tours | Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature |
| 429 | `6ac3777c8a` | P13 | — | L10 | The user guide, with screenshots from the tours | Move the user guide into lang/doc/user-guide, with its screenshots in images |
| 430 | `9db798c44a` | P10 | X4 | L9 | Lazy properties and their time limit | Let a debug launch or attach set how long evaluations and lazy values may run |
| 431 | `80b0e059f8` | P12 | X5 | L10 | The playbook container and the IDE it drives | Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode |
| 432 | `149c2e7a4c` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc |
| 433 | `fab600127c` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's |
| 434 | `0f8d335976` | P13 | — | L10 | The user guide, with screenshots from the tours | Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting |
| 435 | `e7f867f53c` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed |
| 436 | `3c6156eff8` | P6 | — | L4 | Prove code actions with incremental compiles | Start a rename's proof from the revision's compile, and compile after the edit only what it reaches |
| 437 | `b4b9008ca4` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work |
| 438 | `790d36bfa7` | P10 | X4 | — | Debugged stacks end at the host's fiber; failed requests say why | End a debugged program's stack at the host's fiber, also when it has moved on from its call |
| 439 | `1b12a1739e` | P10 | — | L9 | Debugged stacks end at the host's fiber; failed requests say why | Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." |
| 440 | `30cd2e38ec` | P5 | X2 | L3 | Bind more names in the semantic model | Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration |
| 441 | `6bb9656a71` | P5 | — | L3 | Bind more names in the semantic model | Make the _ placeholder no occurrence in the semantic model |
| 442 | `eb687dd113` | P5 | X2 | L3 | Bind more names in the semantic model | Bind the names of a method's multiple return values to the values they declare |
| 443 | `4fb6a51b41` | P5 | X2 | L3 | Bind more names in the semantic model | Bind this in a lazy property's calc() and super in a method reference to what they name |
| 444 | `213b7d4785` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed |
| 445 | `4525937a0e` | P12 | — | L10 | Individual playbook case fixes | Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 |
| 446 | `b99972cd3b` | P12 | — | L10 | IntelliJ tour step fixes | Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames |
| 447 | `3b64412b22` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies |
| 448 | `899fdf7227` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do |
| 449 | `cea7f8029f` | P6 | — | L4 | Prove code actions with incremental compiles | After a rename's edit, compile only the modules depending on the edited ones that it can reach |
| 450 | `d7fb82e22b` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done |
| 451 | `ca97b34781` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide what a rename doesn't change or check |
| 452 | `2f74ea0f01` | P5 | — | L3 | Bind more names in the semantic model | Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares |
| 453 | `372c51b60b` | P5 | X2 | L3 | Bind more names in the semantic model | Keep the initializer facts of a property declared in a loop's body, such as a static local |
| 454 | `51407e782b` | P5 | — | L3 | Bind more names in the semantic model | Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration |
| 455 | `44abbb283a` | P5 | — | L3 | Bind more names in the semantic model | Declare the names of a function type's values in the semantic model, as of its return values in a typedef |
| 456 | `392434b1c8` | P5 | — | L3 | Bind more names in the semantic model | Bind a reserved name such as this:class in a property's accessor, as in a method of the class |
| 457 | `bddecb9375` | P5 | — | L3 | Bind more names in the semantic model | Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells |
| 458 | `a52c397838` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Report constructor arguments and default values that no constructor uses |
| 459 | `366a55ed2d` | P6 | — | L4 | Rename guards and proofs | Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element |
| 460 | `7e45521d85` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Digest the API of each declaration of a module apart (DeclarationDigests) |
| 461 | `0b8115eb72` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Compile again, after an edit, only the modules that use what it changed (member-use tracker) |
| 462 | `1f2ade29d1` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Keep diagnostics working when a restart opens another server's library copy again |
| 463 | `2a28fd82b9` | P9 | — | L8 | Supervised execution workers | Take the execution worker's events off its standard output, onto a loopback socket of their own |
| 464 | `8473885213` | P9 | — | L8 | Embedded runs with their Gradle project's xtcRun and xtcTest settings | Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors |
| 465 | `6d62bb081e` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType() |
| 466 | `9352626a2e` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them |
| 467 | `bd9626cca4` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own |
| 468 | `d8ef35d736` | P13 | — | L10 | The user guide, with screenshots from the tours | Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs |
| 469 | `285bb8f019` | P13 | — | L10 | The user guide, with screenshots from the tours | Split the user guide into a landing page, one page per chapter, and features and shortcuts pages |
| 470 | `7c4789b66b` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered |
| 471 | `9bd76e3598` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it |
| 472 | `099232cb5f` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) |
| 473 | `2f7faf18e1` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place |
| 474 | `801c5ba313` | P13 | — | L10 | The user guide, with screenshots from the tours | Mark the user guide's keys up as keys, and the names it left plain as code |
| 475 | `090cf45fc3` | P13 | — | L10 | The user guide, with screenshots from the tours | Add an index of every page and section of the user guide to its landing page |
| 476 | `1a5521b6dd` | P13 | — | L10 | The user guide, with screenshots from the tours | Give the user guide's long sections subsections, and its index the third level |
| 477 | `6ba91cf948` | P13 | — | L10 | The user guide, with screenshots from the tours | Box the user guide's single screenshots with their captions, as its pairs are |
| 478 | `54dceba041` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence |
| 479 | `e451063f62` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove an anonymous class's this and construction by the span of the type it constructs |
| 480 | `ac76b333a8` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares |
| 481 | `86bf610968` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a union receiver's call of a method that the runtime implements, as a const's appendTo |
| 482 | `eaa8353f14` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a call on a union receiver whose legs share one route, as toString on a Point? |
| 483 | `15763bc07b` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the dispatch chains that hold an unproven identity in the semantic model's census |
| 484 | `2878697574` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder |
| 485 | `095987f677` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method |
| 486 | `f1b5c5e07c` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the bindings that no proof can place in the semantic model's census, and fail on one |
| 487 | `1879ca581b` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name |
| 488 | `fdc6e90c7a` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap |
| 489 | `11a2e8f228` | P12 | — | L10 | IntelliJ tour step fixes | Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does |
| 490 | `d7ab4b5b74` | P12 | X5 | L10 | Playbook traces: compressed, kept whole for failures and the newest pass | Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run |
| 491 | `85f28eb8de` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase |
| 492 | `5ba5691bf4` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count |
| 493 | `6ecf818b9c` | P4 | — | L1, L2 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG |
| 494 | `3ae0e51d2a` | P11 | — | L5 | Temporary directories and their locks | Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time |
| 495 | `d371bd3fc2` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) |
| 496 | `b2b51edbbb` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does |
| 497 | `fe719d5e8d` | P8 | — | L7 | npm dependencies of master's #805 | Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm |
| 498 | `c240ee67cd` | P13 | X5 | L10 | Opt-in static analysis of lang: detekt and CPD | Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript |
| 499 | `e1601172eb` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Allow contribution arguments and shorthand defaults that match the explicit constructor |
| 500 | `21289fc18e` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the semantic model's census what hover, definition, references and highlights miss at a name |
| 501 | `ba6c59ffb4` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations |
| 502 | `b59891d491` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to |
| 503 | `25400e2f9d` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property |
| 504 | `7624486f3b` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the editor's census the references whose declaration is in no source of the workspace |
| 505 | `c2112d8f4d` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Compile the census's libraries as one workspace folder, as an editor that opens the repository does |
| 506 | `a13bae9669` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88) |
| 507 | `2c60fbbd28` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings |
| 508 | `109d4b0309` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too |
| 509 | `157e9c9c52` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Keep the cause of a refused settings change, undo or range format, which detekt found swallowed |
| 510 | `57c5bc9fa7` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops |
| 511 | `d7ad594473` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate |
| 512 | `47e952c331` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair |
| 513 | `eaa43fb0c2` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's location requests and edit conversion, and say the semantic tokens setting once |
| 514 | `5c2679a233` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end |
| 515 | `bc109a6fb1` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias |
| 516 | `adec6e2deb` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over |
| 517 | `90f4c4347b` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode |
| 518 | `3257254215` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's cancellation-owning future and its formatter's parenthesis search |
| 519 | `cadc4c1352` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Generate TextMate's operator patterns from one list of categories, not seven copies of the same block |
| 520 | `c13e75b13a` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy |
| 521 | `8687101044` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Test that a class without constructors compiles and uses its contribution's arguments |
| 522 | `b1d2d02586` | P12 | — | L10 | The playbook container and the IDE it drives | Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume |
| 523 | `1e0ab80fbc` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share |
| 524 | `5842ec04a8` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates |
| 525 | `246aa464ce` | P12 | — | L10 | The playbook container and the IDE it drives | Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed |
| 526 | `321ee9208d` | P12 | — | L10 | The playbook container and the IDE it drives | Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too |
| 527 | `bb44321275` | P7 | — | L6 | Installable in every JetBrains IDE: no Java dependency, Gradle optional | Make the IntelliJ plugin installable in every JetBrains IDE: no Java dependency, and Gradle only where the IDE has it |
| 528 | `0489501183` | P13 | — | L10 | The user guide, with screenshots from the tours | Correct the user guide where the playbooks show it wrong, and add links and implement or override |
| 529 | `c47e59d413` | P12 | — | L10 | Playbook documentation | Drop the retired X273-X275 from the scope that the VS Code playbook's results.json reports |
| 530 | `3a3b3336bd` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed |
| 531 | `2ff72941cc` | P7 | — | L6 | Installable in every JetBrains IDE: no Java dependency, Gradle optional | Name the plugin's required-dependency test as master's #810 does, with the test runner among them |
| 532 | `152274e874` | P12 | — | L10 | The IntelliJ tour, its recording and video | Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times |
| 533 | `c996e30737` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent |
| 534 | `eaea2681f0` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire |
| 535 | `65bbada907` | P4 | — | L1, L2 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Share the xtc.* keys and a build's summary text through lang/protocol |
| 536 | `632af00e4a` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Share the compiler's library settings through lang/protocol: their shape and how a path names a file |
| 537 | `7ca88c80cb` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer xtc/tests when a document's close retires the compile it waits for (X342) |
| 538 | `2c2a3e6a17` | P4 | — | L1 | Keep documents current and the server responsive | Keep listed code actions resolvable across a close that leaves a document's text as on disk |
| 539 | `78d6f41e7e` | P12 | — | L10 | Individual playbook case fixes | Open a companion destination in IntelliJ's playbook after the quick fix's edit has changed it (X214) |
| 540 | `10c75a5945` | P12 | — | L10 | Test code shared instead of copied | Share the IntelliJ playbook executors' repeated steps instead of copying them per case |
| 541 | `8120a4ff3a` | P12 | — | L10 | Test code shared instead of copied | Share the playbook probe's breakpoint states, variable paths and child nodes |
| 542 | `ea3b9451e8` | P12 | — | L10 | Test code shared instead of copied | Share the IntelliJ tour's repeated test-run, attach and adapter steps |
| 543 | `c4cd6ff9ec` | P12 | — | L10 | Test code shared instead of copied | Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps |
| 544 | `a95cb6dde2` | P12 | — | L10 | Test code shared instead of copied | Share the playbook's semantic-token reads, workspace models and xunit case heads |
| 545 | `b57ca5ab3b` | P12 | — | L10 | Test code shared instead of copied | Share the plugin unit tests' disk files, connection, waits and opens through LightEditor |
| 546 | `9179e78505` | P12 | — | L10 | Test code shared instead of copied | Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup |
| 547 | `6b7f429f17` | P12 | — | L10 | Test code shared instead of copied | Share the lsp-server adapter tests' source writer and text edit application |
| 548 | `7e3e14c591` | P12 | — | L10 | Test code shared instead of copied | Share the embedding's incomplete analysis in the header tests, and merge their identical cases |
| 549 | `3775838e0c` | P12 | — | L10 | Test code shared instead of copied | Shorten the code action tests with the shared edits and per-class helpers |
| 550 | `da21c70dbc` | P12 | — | L10 | Test code shared instead of copied | Share an adapter with a module compile hook among the tests that count or hold compiles |
| 551 | `46dc903069` | P12 | — | L10 | Test code shared instead of copied | Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures |
| 552 | `91cc7ca9c4` | P12 | — | L10 | Test code shared instead of copied | Merge FormattingConfigRoundTripTest's mock clients and server setup |
| 553 | `e145f9a239` | P12 | — | L10 | Test code shared instead of copied | Apply the missing member, extraction and initializer tests' edits with the shared apply |
| 554 | `35cb21e69e` | P12 | — | L10 | Test code shared instead of copied | Check XdkMemberUseTrackerTest's renames of foo through one helper |
| 555 | `00e218b221` | P12 | — | L10 | Test code shared instead of copied | Share the marker comment position of the navigation, lookup and occurrence tests |
| 556 | `2b026fca0c` | P12 | — | L10 | Test code shared instead of copied | Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file |
| 557 | `9c0f19a122` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know |
| 558 | `7b4edd6e65` | P7 | — | L6 | Light test fixes | Hold ClosedFileProblemsTest's later workspace pulls until its next refresh, so that none lands after its build |
| 559 | `b30c2d1f55` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Read XdkPullDiagnosticsTest's traced answers in the order their queries came, not the order their ends were written |
| 560 | `b51cce3bef` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a named argument of another module's method, as inPlace in freeze(inPlace=True), to its parameter in that module's source |
| 561 | `ed869bddf3` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that a lambda names, as To in n -> n.toIntN().as(To), to the method's or the class's formal type |
| 562 | `e62c69be14` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a parameter of a constructor with a finally block that only lambdas in its body name to its declaration |
| 563 | `71a53db34d` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Place a label written with unicode escapes where it is written, in its declaration, a break and a continue |
| 564 | `46f8cf7605` | P13 | X5 | — | Packaging tasks and build defaults | Say what MarketplaceListingTest's listing numbers are: a real listing's, and which of them the reader uses |
| 565 | `4f4bc23983` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7) |
| 566 | `b8d6e3a12f` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename leaves string literals and comments that spell the old name, how many and where |
| 567 | `fd9b77d1fc` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording |
| 568 | `e062d20888` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Terminate the extension's tasks also when the terminate comes before VS Code has started them |
| 569 | `7d347fdf55` | P12 | — | L10 | The VS Code tour | Stop tasks again while any are left in the VS Code tour's stopAll, and wait for the embedded runs' ends |
| 570 | `c93b50a750` | P12 | — | L10 | The VS Code tour | Count no silent embedded run as work in the VS Code tour's waits, so a stop that does not take fails within the hang window |
| 571 | `b5eecd410e` | P12 | — | L10 | The VS Code tour | Wait for the VS Code tour's clicks and focus as its waits do, and close the context menu that a failed step leaves |
| 572 | `270eb086d8` | P12 | X5 | L10 | The playbook container and the IDE it drives | Build the playbook container's XDK first when the slot's build is of other sources than the checkout's |
| 573 | `65a50826ca` | P12 | — | L10 | The playbook container and the IDE it drives | Let playbook container runs take the first free slot, in the order they asked, instead of their checkout's slot |
| 574 | `080e5d0bb5` | P12 | — | L10 | Test code shared instead of copied | Share the VS Code playbook's fixture steps across its files: one module, a Gradle model, the server's status, the outside-uses message |
| 575 | `8f1f8586aa` | P12 | — | L10 | Test code shared instead of copied | Run the VS Code playbook's debug cases through one Debugger, its runs through Executions, and its tests through one TestWatch |
| 576 | `602b84c2b2` | P12 | — | L10 | Test code shared instead of copied | Share the VS Code playbook's completion and argument steps: names without snippets, a replaced range, a signature, an accepted name |
| 577 | `bd48e963ed` | P12 | — | L10 | Playbook cases added after the catalog | Check in both editors' X269 that the memory policy and the maximum heap reach the restarted server, and that JVM options win |
| 578 | `1b82cb957c` | P12 | — | L10 | Playbook cases added after the catalog | Add X352 to both editors' playbooks: a loop's label, its hover, highlights, references, definition and renames |
| 579 | `99204c8446` | P12 | — | L10 | Playbook cases added after the catalog | Add X353 to both editors' playbooks: a constructor's parameter with a finally block, from its body and a named argument |
| 580 | `8424a2d0f6` | P12 | — | L10 | Playbook cases added after the catalog | Add X354 to both editors' playbooks: Build and Run in the automatic build mode, beside a Gradle build and without one |
| 581 | `5fc1251620` | P12 | — | L10 | Playbook cases added after the catalog | Add X355 to both editors' playbooks: tests that don't show, of a module that doesn't parse and of one whose dependency has an error |
| 582 | `d9d9774686` | P12 | — | L10 | Playbook cases added after the catalog | Add X356 to both editors' playbooks: Go to Symbol in Workspace finds the XDK's declarations, read-only |
| 583 | `71cb3d4e18` | P12 | — | L10 | Playbook cases added after the catalog | Add X357 to both editors' playbooks: Organize Imports removes an unused import and sorts the rest, in one edit |
| 584 | `6d4f6400ea` | P12 | — | L10 | Playbook cases added after the catalog | Add X358 to both editors' playbooks: hover and Quick Documentation of the XDK's types and methods show their doc comments |
| 585 | `6b118a8220` | P12 | — | L10 | Playbook cases added after the catalog | Add X359 to both editors' playbooks: Go to Definition on a named argument of an XDK method opens its parameter, read-only |
| 586 | `6733cda035` | P12 | — | L10 | Playbook cases added after the catalog | Add X360 to both editors' playbooks: a method's type parameter named in a lambda is the method's |
| 587 | `18f35f8b15` | P4 | — | L1 | Keep documents current and the server responsive | Answer Find References again when documents only opened or closed while it ran, not with the cancellation that showed X251 none |
| 588 | `85bb8aade2` | P11 | — | L5 | Navigate the XDK's library sources | Find the XDK's declarations in Go to Symbol in Workspace, after the workspace's own, so that X356 passes in both editors |
| 589 | `491115ac1f` | P12 | — | L10 | The VS Code tour | Open the VS Code tour's Explorer name box in Rename a file once the tree has the keyboard on Hand.x, and again until it shows |
| 590 | `a58225e5d1` | P12 | — | L10 | The VS Code tour | Press Ctrl+Alt+X, B and Ctrl+Alt+X, R as keys in both editor tours, and check what each does |
| 591 | `68fc9a476d` | P12 | — | L10 | IntelliJ tour step fixes | Check Rebuild Project, Ctrl+Shift+F10 and a Gradle-mode Build Project in the IntelliJ tour |
| 592 | `ae5c999740` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that the language server comes back when its process dies |
| 593 | `a2d04a8964` | P12 | — | L10 | The VS Code tour | Show refactoring's refusals in both editor tours: a rename that would change what the code means, and one of the XDK's name |
| 594 | `d3ad93fd15` | P10 | — | L9 | Debug through the language server | Tell a debug session where a debugged file's breakpoints are as an edit moves them, so VS Code moves its markers without a save |
| 595 | `a9f572162c` | P12 | — | L10 | The VS Code tour | Show breakpoints while you edit in both editor tours: one that follows its line as a line is typed above it |
| 596 | `efc8d9ddfa` | P12 | — | L10 | IntelliJ tour step fixes | Bring the IntelliJ tour's coverage map up to LSP4IJ 0.30.1, and list the plugin's build mode, restart and Run All Tests actions |
| 597 | `29bcb6d334` | P12 | — | L10 | The VS Code tour | Show Go to Definition from a named argument into the XDK's source, read-only, in both editor tours |
| 598 | `7a2e46596f` | P12 | — | L10 | The VS Code tour | Choose a context menu's item by Enter in the VS Code tour when its click only gave it the keyboard |
| 599 | `699a213eac` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that only a parse error hides a test module's tests, and how IntelliJ finds the XDK's symbols |
| 600 | `2da9be1be8` | P12 | — | L10 | The playbook container and the IDE it drives | Remove the Gradle caches and wrappers of other Gradle versions from a container slot before its run |
| 601 | `9bb15b730e` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Tag the extension's repeated task terminate with UP91, VS Code's dropped early terminate (microsoft/vscode#350588) |
| 602 | `d7f2e11f6b` | P12 | — | L10 | Test code shared instead of copied | Make lang's three copies of Awaiting.kt one test fixture, org.xvm.testing's, in a new project, lang/test-support |
| 603 | `9e5f2b69fc` | P6 | — | L4 | Rename guards and proofs | Say in VS Code why a rename is refused, as IntelliJ's plugin does, instead of VS Code's "No result." |
| 604 | `7e1c98cb5b` | P6 | — | L4 | Rename guards and proofs | Answer xtc/rename's refusals with their reason, as textDocument/rename does, instead of as an internal error |
| 605 | `5d43c78c12` | P12 | — | L10 | IntelliJ tour step fixes | Report a tour wait in IntelliJ about once a minute, as the VS Code tour's waits do |
| 606 | `462d2bf520` | P12 | — | L10 | IntelliJ tour step fixes | Read the IntelliJ tour's negative checks once what could cause them has run, instead of waiting out the hang's bound |
| 607 | `f083106ab4` | P12 | — | L10 | IntelliJ tour step fixes | Report the IntelliJ tour's start-up waits about once a minute, as its other waits now do |
| 608 | `29ac132203` | P12 | — | L10 | IntelliJ tour step fixes | Report a tour wait once a minute, not in a burst after a blocked call, and leave no wait after a probe timeout silent |
| 609 | `8b86c30c02` | P12 | — | L10 | The playbook container and the IDE it drives | Copy back only the VS Code runs that a container made, not every run its slot's work volume keeps |
| 610 | `4a0c265304` | P6 | — | L4 | Rename guards and proofs | Answer a rename that can't be proven, or that a change overtook, for what it is, and let a cancel reach a plain rename's proof |
| 611 | `1e223f8155` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Print a stalled close's fibers before the watchdog fails it, which in CI they never were |
| 612 | `29fc3b1735` | P6 | — | L4 | Rename guards and proofs | Show nothing in IntelliJ for a rename that the user cancels, not "Rename failed: null" |
| 613 | `6652dfc072` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a document's release cancelled or overtook its proof, not move without them |
| 614 | `bc88caba40` | P12 | — | L10 | Individual playbook case fixes | Wait for a moved type's files in VS Code's playbook while VS Code's events of them come, not for a fixed 30 s |
| 615 | `831234d823` | P12 | — | L10 | Individual playbook case fixes | Let one case's failure in VS Code's playbook fail that case alone, and restore the editor, not end the run |
| 616 | `bdb5505cc2` | P12 | — | L10 | The VS Code tour | Open a file by Quick Open in the VS Code tour again until it takes, typing only once the box has the keyboard |
| 617 | `919006cb35` | P12 | — | L10 | The VS Code tour | Count no edit of a log as work under way in the VS Code tour's waits |
| 618 | `7016c1e810` | P12 | — | L10 | The playbook container and the IDE it drives | Run the server's suites before a full VS Code playbook run only when asked, and print the icon task's files only on request |
| 619 | `8d0b93692b` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Drop the IntelliJ plugin's own save of the closed documents a rename changes, which LSP4IJ 0.30.1 does (UP21) |
| 620 | `06056b52e9` | P12 | — | L10 | The playbook container and the IDE it drives | Give the playbook container a session bus and a stand-in system bus, so VS Code's Chromium stops logging D-Bus errors |
| 621 | `3f57dee86d` | P12 | — | L10 | IntelliJ tour step fixes | Count as a tour wait's progress only what could make its condition hold, and give the start-up waits the idle check |
| 622 | `d700cb153b` | P12 | — | L10 | Individual playbook case fixes | Wait for the IntelliJ playbook's Structure tree as the tour's waits do, not with the driver's fixed ten and five seconds |
| 623 | `b450151bfc` | P12 | — | L10 | IntelliJ tour step fixes | Show the IntelliJ tour's conditional breakpoint balloon once Deck.x's lenses are in place, past the build mode's balloon |
| 624 | `82941c6019` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook's harness tests in a category of their own, first in every playbook and tour run |
| 625 | `6be74e57bf` | P12 | — | L10 | Individual playbook case fixes | Find the IntelliJ playbook's Structure window by its own title, not by names that the Project window can have |
| 626 | `dc535840c5` | P12 | — | L10 | IntelliJ tour step fixes | Say in the IntelliJ tour's Ctrl+Alt+X, L check's failure what the IDE got of the chord's keys |
| 627 | `6b6825019e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Say what the IntelliJ playbook's UP07 waits are, and reopen the intention menu once nothing could show it |
| 628 | `188f211161` | P4 | — | L1, L2 | Keep documents current and the server responsive | End no workspace request when the editor releases a document outside the graph, or closes one the server does not have open |
| 629 | `d21f6a699a` | P4 | — | L1, L2 | Keep documents current and the server responsive | Pull a workspace document's diagnostics without waiting for a library source's compile, which Go to Definition starts |
| 630 | `1f0140961a` | P6 | — | L4 | Rename guards and proofs | Let a rename or a move in VS Code outlast the release of a document whose text its file has, as the server's proof does |
| 631 | `7a2db0b7d8` | P4 | — | L1 | Keep documents current and the server responsive | End no workspace request when VS Code releases a library's source that Go to Definition opened |
| 632 | `6f46f8ae74` | P12 | — | L10 | Individual playbook case fixes | Give the editor the keyboard in VS Code's playbook again until it takes, and wait for the workbench's widgets while it works, not for fixed seconds |
| 633 | `40db2c5511` | P12 | — | L10 | The VS Code tour | Press Enter on a VS Code tour menu's item only once the window has run what the click started, not into what it opened |
| 634 | `3afc061b12` | P12 | X5 | L10 | Individual playbook case fixes | Report a VS Code playbook case that fails with a known host bug's exact symptom as a known host failure, apart, and leave the run green |
| 635 | `c1d59e449d` | P12 | — | L10 | The playbook container and the IDE it drives | Run VS Code's playbook and tour harness tests in a group of their own, first in every playbook and tour run, not in the extension suite |
| 636 | `e1e72e41b3` | P12 | — | L10 | Individual playbook case fixes | Press X130's Redo only once VS Code's Undo has ended with its didRenameFiles, not as soon as the folders are back on disk |
| 637 | `7172e96506` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Format each file with the settings its .editorconfig files give, over the editor's, and describe the formatting settings in the user guide |
| 638 | `9f4c85e4d8` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Read .editorconfig files in IntelliJ only while IntelliJ applies them itself, as its EditorConfig switch says |
| 639 | `36a985f68c` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Trim trailing whitespace in the Tree-sitter and base formatters only as the options ask, for a whole document too |
| 640 | `a8989dcd12` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Apply a file's end_of_line from .editorconfig in VS Code's Format Document, which no formatting edit can do |
| 641 | `18f12985f6` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep the lines between IntelliJ's formatter markers as they are, and read the wiki's continuation_indent_size too |
| 642 | `b45c401d96` | P13 | — | L10 | The lang documentation | Pad the aligned Markdown tables under lang/ so their columns line up again |
| 643 | `5029fdc600` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Ask for the trim in the server test that formats a document whose only fault is its trailing whitespace |
