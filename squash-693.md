# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 482 commits of `errs/full-testable-lsp-20261009` over `master` (tip `a10580fcaf`, master `8b5257f10b`, 2026-11-25), numbered 1 to 482 from the oldest, with their short hashes. Commits 1-397 were planned on 2026-11-23; the rebase onto master of 2026-11-25 kept their numbers and gave them new hashes. Commits 398-482 came after, and were placed by the same rules. Five of them are already open on master as their own PRs: 459 as #802, and 467, 471, 473 and 474 as #804; each drops out of #693 when its PR merges.

It proposes 15 pull requests, each of which changes either only files outside lang/ (xvm only: X1-X5) or only files in lang/ (lang only: L1-L10), merged in one order. To get there, the commits are first sorted into 13 topics (P1-P13), the commits of a topic that implement or fix the same thing, however far apart, are grouped to be joined, and every commit is then divided at lang/: its other files go to its topic's xvm PR, its lang/ files to its topic's lang PR.

## The pull requests, in merge order

| Order | PR | Changes only | Title | Prerequisites | Built from topics | Commits with a part in it | Added lines: main / tests |
|---|---|---|---|---|---|---|---|
| 1 | **X1** | files outside lang/ | Compiler diagnostics: the error-listener contract and repository read failures | — (master) | P1 | 12 | 2.9k / 2.8k |
| 2 | **X2** | files outside lang/ | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | X1 | P2, P11 | 15 | 5.7k / 1.1k |
| 3 | **X3** | files outside lang/ | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | X2 | P3, P4 | 8 | 5.8k / 1.7k |
| 4 | **X4** | files outside lang/ | Runtime support for running, testing and debugging from the editors: the debugger and its agent, xtc, xunit and the Gradle plugin | X3 | P9, P10 | 55 | 9.5k / 5.2k |
| 5 | **X5** | files outside lang/ | Build, CI and test tooling outside lang: CI test tiers, build checks, test hang detection, the LSP4IJ and IntelliJ versions, PR previews | X4 | P4, P7, P12, P13 | 24 | 1.6k / 806 |
| 6 | **L1** | lang/ | Language server: the server, its protocol, document synchronization, Tree-sitter and the mock adapter | X3, X5 | P4 | 35 | 10.0k / 12.9k |
| 7 | **L2** | lang/ | Language server: the compiler adapter's core, compiling, discovery, diagnostics and workspace pulls | L1 | P1, P2, P3, P4 | 38 | 9.2k / 17.6k |
| 8 | **L3** | lang/ | Language server: the semantic model and read-only editor features | L2 | P5 | 23 | 5.8k / 6.2k |
| 9 | **L4** | lang/ | Language server: code actions and refactorings | L3 | P6 | 33 | 9.2k / 12.8k |
| 10 | **L5** | lang/ | Language server: the XDK's library sources and the XDK workspace | L3 | P11 | 21 | 2.9k / 3.0k |
| 11 | **L6** | lang/ | IntelliJ plugin, on LSP4IJ 0.30.1 | L4, L5, X5 | P7 | 71 | 10.2k / 6.3k |
| 12 | **L7** | lang/ | VS Code extension | L4, L5 | P8 | 11 | 2.7k / 341 |
| 13 | **L8** | lang/ | Embedded build, run and tests in the language server and both editors | X4, L6, L7 | P9 | 49 | 9.6k / 8.8k |
| 14 | **L9** | lang/ | Debugging: the DAP server, debug sessions, launch and attach, and both editors' debug UI | X4, L8 | P10 | 48 | 8.6k / 11.0k |
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 139 | 7.1k / 57.0k |

Already open on master, apart from these: #802 (unused constructor arguments, commit 459) and #804 (runXtc keeps xtcRun's settings, commits 467, 471, 473 and 474). Their commits leave #693 when they merge.

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
| P1 | Compiler diagnostics: the error-listener contract and repository read failures | master | no | 13 | 4 | 2.9k / 3.0k |
| P2 | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | P1 | no | 8 | 4 | 5.8k / 3.1k |
| P3 | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | P2 | no | 6 | 2 | 5.7k / 2.0k |
| P4 | Language server core: protocol, document sync, compile pipeline, discovery, diagnostics | P3 | yes, once 5 commits' other files move | 39 | 14 | 18.8k / 27.9k |
| P5 | Language server: semantic model and read-only editor features | P4 | yes | 22 | 7 | 5.9k / 6.2k |
| P6 | Language server: code actions and refactorings | P5 | yes | 32 | 9 | 9.2k / 12.8k |
| P7 | IntelliJ plugin, built against LSP4IJ 0.30.1 | P6 | nearly | 70 | 26 | 10.3k / 6.4k |
| P8 | VS Code extension | P6 | yes | 10 | 6 | 2.7k / 341 |
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 59 | 24 | 10.8k / 9.8k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 60 | 36 | 17.2k / 15.2k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 22 | 9 | 3.3k / 3.2k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 112 | 20 | 3.0k / 57.3k |
| P13 | Documentation, build and CI | the PRs it documents | no | 29 | 4 | 5.5k / 372 |
| | **Total** | | | **482** | **165** | |

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

Commit 8, `50fcd8e32d` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `8c2945095d` | 366 `efcbea0a3f` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `f167a9924c` | 345 `42be107325` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `05a3170ca0` | 374 `75f2e8f0c5` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `7ad302c426` | 369 `cfd5475843` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `0d9f6921e1` | 367 `435242e681` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `a314a18f86` | 363 `8db1fc1931` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `af007477ee` | 373 `d4471e1783` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `da996a1112` | 362 `cb281aa96c` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `793d35da57` | 360 `211a02e25a` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `c1977b37d5` | 112 `1061907408` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `253e7ee601` | 105 `d73771b5fe` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `50fcd8e32d` | 36 `4f1a40d312` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `50fcd8e32d` | 387 `b37a31b952` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `0a7f9f5cc9` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `b608e64535` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `7b43afa997` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `90ad9a88b0` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `b4222b09d7` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `d85a147110` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `79c0147476` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `3c2b080b64` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `5e806225b5` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `0de393e93d` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `175e02f510` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `d0e423ac49` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `1905680c0e` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `cabceae27c` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `23fed761bd` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `62ef26ac4d` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `39f47e8168` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `81d7ad5a35` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `89bead30f9` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `61e369b94c` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `080fb71244` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `150739664d` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `e62df115e3` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `76237d9959` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `311905ec99` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `72cef1abb1` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `d5ee203417` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `bf45953b4a` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `18be706cc2` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `723b8c7258` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `93fed869b0` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `d93b3bc1ea` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `3803bce68d` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `ffc0acdad4` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `81d586a063` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `2efeb8338b` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `e1f559cc28` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `6d1a139041` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `fcfaec2374` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `db90cc8501` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `326fbf868d` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `9aa981ca81` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `7560c3931c` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `f95959f1b8` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `253e7ee601` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `d9e247c306` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `ee7bfc6999` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `ba8ede4b46` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `c9532fceef` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `d73771b5fe` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `c1977b37d5` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `985d2ece4d` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `8e3927a005` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `1061907408` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `5e195bc7cb` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `17c0f1bc7b` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `703247c0d2` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `a7c739acbb` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `210f8fdb24` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `ed3c3c646e` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `895869036d` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `6640b59833` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `8c5ae632c9` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `af007477ee` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `7d3efd827a` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `05a3170ca0` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `0b634daaeb` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `bb5fec87c8` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `4c23d5e62b` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `ccfa1a4e01` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `40158ffdef` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `cf2ab7f16a` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `8135ba3f1c` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `b4527831f0` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `b5e3ace999` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `0cc66e078e` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.
- 400 `92e0f668db` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82), in P7 (+272; also touches: playbook)
- 410 `0a1787a322` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774), in P9 (+114; also touches: playbook)
- 415 `853809c7ed` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries, in P9 (+55; also touches: playbook)
- 421 `794fcd0839` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive, in P7 (+141; also touches: playbook, server, vscode)
- 422 `5644763dce` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded, in P9 (+220; also touches: playbook)
- 423 `27b958c207` Show the embedded compiler as not available where the project's own modules replace bundled libraries, in P9 (+133; also touches: playbook)
- 424 `42737304d7` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours, in P13 (+754; also touches: intellij, vscode)
- 425 `91582ff299` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy, in P7 (+277; also touches: playbook, server)
- 426 `7b19169cb1` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run, in P13 (+193; also touches: intellij, playbook)
- 427 `83c8023c84` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide, in P7 (+73; also touches: playbook)
- 428 `c681af316c` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own, in P9 (+447; also touches: playbook)
- 429 `4fb790479a` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature, in P13 (+251; also touches: playbook)
- 430 `3c0ca5612e` Move the user guide into lang/doc/user-guide, with its screenshots in images, in P13 (+996; also touches: intellij, playbook, vscode)
- 431 `55b2d56f41` Let a debug launch or attach set how long evaluations and lazy values may run, in P10 (+716; also touches: playbook)
- 432 `75b81bc095` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode, in P12 (+5; also touches: build, intellij)
- 433 `19b81740f7` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc, in P9 (+485; also touches: playbook)
- 435 `2083f1a99f` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting, in P13 (+125; also touches: playbook)
- 436 `226b629b82` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed, in P6 (+589; also touches: intellij, playbook, vscode)
- 441 `ded64de57d` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration, in P5 (+110; also touches: javatools)
- 443 `c7750aadd9` Bind the names of a method's multiple return values to the values they declare, in P5 (+65; also touches: javatools)
- 444 `e73e885a7b` Bind this in a lazy property's calc() and super in a method reference to what they name, in P5 (+147; also touches: javatools)
- 445 `03bfd34081` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed, in P6 (+275; also touches: playbook)
- 454 `77761cf5e5` Keep the initializer facts of a property declared in a loop's body, such as a static local, in P5 (+47; also touches: javatools)
- 459 `db12fbc31f` Report constructor arguments and default values that no constructor uses, in P1 (+129; also touches: xdk)
- 461 `7725f9036a` Digest the API of each declaration of a module apart (DeclarationDigests), in P11 (+622; also touches: javatools)
- 463 `b0335336e2` Keep diagnostics working when a restart opens another server's library copy again, in P11 (+294; also touches: playbook)
- 466 `6a08fea89c` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors, in P9 (+1,453; also touches: playbook)
- 470 `a2184011d3` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own, in P11 (+1; also touches: javatools)
- 476 `bbe845ba74` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages, in P13 (+1,264; also touches: intellij)
- 477 `5c75702791` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered, in P12 (+141; also touches: intellij, server)

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

**Builds on** master. **Commits:** 13 now, about 4 after squashing. **Added lines:** 2,976 main, 3,047 tests.

| Join into | Commits | Why |
|---|---|---|
| Keep, cache and report module repository read failures | 1 `5d747e3a60` Preserve module repository read failures without blocking usable candidates<br>9 `90ad9a88b0` Report repository read attempts without inferring dependency failures<br>11 `d85a147110` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `37dbb04ffa` Separate diagnostic reporting from listener abort policy<br>3 `1ddea3e0be` Require explicit compiler listeners and reasons for silence<br>4 `fe43afb01d` Scope parser diagnostics and restore validation listeners on every exit<br>19 `0c836e4d5a` Route repository and runtime reports through error listeners<br>41 `cb4949b9ae` Stop the lexer at a source's end and keep diagnostics that only look like duplicates<br>468 `ef370304b5` Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType()<br>469 `4149ac20f8` Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `09868d0a51` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `af5b972e35` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |
| Unused constructor arguments and defaults (open PR #802) | 459 `db12fbc31f` Report constructor arguments and default values that no constructor uses | already open on master as #802; it drops out of #693 when #802 merges |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `94831084af` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `0a7f9f5cc9` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `3e28be637c` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `b608e64535` Expose compilation-owned syntax and resolved semantic facts<br>10 `b4222b09d7` Clarify tooling contracts and isolate partial AST helpers<br>12 `45ef4e5a94` Verify compiler output without binary AST dependencies<br>115 `58a5e71e24` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `79c0147476` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `7b43afa997` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `2a39c4b938` Complete a member access typed at the end of a line<br>144 `16a17c8d61` Complete a member with the cursor right after its dot<br>161 `a7c739acbb` Complete names and show a call's signature where they are typed at a line's end<br>385 `b650223f5b` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `0de393e93d` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 39 now, about 14 after squashing. **Added lines:** 18,788 main, 27,911 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `703bd8ea77` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `8234ff1b1b` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `76ab810be4` Let two run tests check what they claim instead of how long they wait<br>301 `afa5765ab4` Make the retention test's cancellations and the library content test's refresh check independent of timing | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `50fcd8e32d` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `4f1a40d312` Remove the document-color prototype<br>266 `66d35ca465` Remove the unused xtc/runnableTargets request<br>387 `b37a31b952` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `1905680c0e` Keep documents current and the server responsive, and name its own diagnostics<br>28 `70ce7edf0b` Judge file events against a running analysis and apply them in order<br>92 `ab65adcf09` Analyze again an open document whose analysis was retired without a replacement<br>184 `33faed26d4` Answer requests that a restarted client sends while it opens its files again | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `5833436a18` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `307aedab5e` List a closed test module's tests from the workspace pull's compile<br>237 `2d3f8f257b` Clear a deleted file's diagnostics in the next workspace pull<br>248 `0b62399828` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `e6706b3ded` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `6836d42067` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `59440ea709` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `65744e932d` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `f28a823e45` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `5f98caeb3f` Split the LSP tests into a fast default tier and complete runs<br>143 `05034bd1bf` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `61e369b94c` Declare fields first and use plain names in the new classes<br>95 `adac80fd86` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `f5c3f3c63d` Compile a closure as a fold, without mutable accumulators<br>137 `04df09597a` Name the properties that hold behavior or mutable state in camel case<br>44 `0ca33da040` Synchronize the semantic token history that concurrent queries share<br>163 `9f64cab7e7` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `76237d9959` Keep the build's jars whole and its test runs focused<br>97 `61501ffdf7` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |
| Time and heap logs of the server's work | 419 `8d2b034dab` Log each project query's and each compile's time and heap<br>420 `de7482c05d` Log what the server did in each minute in which it worked, and over its session | the server's own logs of what each compile, query and minute of work cost |

Kept as they are:

- 24 `200c190586` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `210f8fdb24` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `18e4d1423a` Keep the execution trace's events out of stderr and server.log
- 173 `f9e3f2e4b5` Let one source that discovery cannot read cost only itself
- 180 `81d4930ca7` Keep a project's module, source and library order the same in every JVM
- 261 `b0571ff946` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 22 now, about 7 after squashing. **Added lines:** 5,888 main, 6,244 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `f89d55f74d` Complete with the language model's keywords and built-in types<br>162 `141be96008` Complete a type of another module where a statement starts, with its import<br>185 `0e9fe53d0d` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `01a92e3621` Name a type by what it is when hovering its name<br>156 `2cb6d23de4` Give a constructor's finally block no references lens of its own<br>157 `2c8e2b3f05` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `81d7ad5a35` Keep indentation while typing, and lay out switches as the XDK does<br>135 `8a8e847ca2` Share the formatter's empty-block skeleton and whitespace edits<br>186 `c614868729` Answer on-type formatting without waiting for the change to compile | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |
| Bind more names in the semantic model | 441 `ded64de57d` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration<br>442 `a4312fa294` Make the _ placeholder no occurrence in the semantic model<br>443 `c7750aadd9` Bind the names of a method's multiple return values to the values they declare<br>444 `e73e885a7b` Bind this in a lazy property's calc() and super in a method reference to what they name<br>453 `f12f36c760` Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares<br>454 `77761cf5e5` Keep the initializer facts of a property declared in a loop's body, such as a static local<br>455 `d4d445504f` Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration<br>456 `44e1adc9de` Declare the names of a function type's values in the semantic model, as of its return values in a typedef<br>457 `afb8910de2` Bind a reserved name such as this:class in a property's accessor, as in a method of the class<br>458 `867bbc740d` Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells | each adds a kind of binding; the compiler facts that 441, 443, 444 and 454 add go to X2 |

Kept as they are:

- 102 `54dcd88f02` Link a local's ranges when the caret is just after its name
- 235 `920f62d3a1` Fold comments and runs of imports with the compiler adapter
- 347 `483e167b41` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 32 now, about 9 after squashing. **Added lines:** 9,228 main, 12,804 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `05a3170ca0` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `083e336f9a` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `75f2e8f0c5` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `895869036d` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `039db79e31` Prove a code action by compiling only the modules its edit reaches<br>193 `fde83dce54` Start a revision's code action compile from the previous one's, and log its cost<br>205 `585e2faed3` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `9b8b41787d` Keep the compiles of modules that compiled when only private declarations move<br>437 `bc62272a7b` Start a rename's proof from the revision's compile, and compile after the edit only what it reaches<br>450 `7a4cf63e57` After a rename's edit, compile only the modules depending on the edited ones that it can reach | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `d2e739212a` Safely delete an unused public or protected member<br>214 `7c853bccae` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `029dc1d68f` Extract statements that use an injected local, passing its value<br>177 `d5ceb77182` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `080fb71244` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `da5cfe53a9` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `3c2b080b64` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `4a15f662a5` Prove a rename past a register the semantic model cannot place<br>394 `8bbab95d79` Refuse a plain rename that also changes the configured source modules, and say why<br>412 `0f4e3bd804` Prove a rename in a workspace module that replaces a bundled library as the workspace's sources<br>416 `99132b93c9` Find a text's line breaks once per comparison in a rename's proof, not once per position<br>417 `5dfa309be6` Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes<br>418 `af4a0f49ab` Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy<br>460 `baba2d0981` Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `642f56d8a0` Offer code actions in a module that has a const with a toString<br>176 `1a7b694e3a` Offer Create class for the diagnostic of a statement that constructs it<br>211 `a99067fa6a` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |
| Say what a change to a public declaration reaches outside the workspace | 436 `226b629b82` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed<br>445 `03bfd34081` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed | renames, safe deletes and file moves say when a public declaration changes for modules outside it |
| A client's cancellation reaches the adapter's work | 438 `1147ab7351` Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work<br>451 `6c95324c52` Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done | cancelling a rename or xtc/build stops the adapter's work, also once a result is converted |

Kept as they are:

- 73 `f95959f1b8` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 70 now, about 26 after squashing. **Added lines:** 10,268 main, 6,424 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `59a5148469` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `abe6303503` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `f9e536fee3` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `36616eaf44` Review the internal LSP4IJ APIs for 0.30.0<br>392 `3b99cad997` Name LSP4IJ 0.30.1 in the plugin's README and two test comments<br>398 `07bfebb35d` Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83)<br>400 `92e0f668db` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `ef0e8d1cff` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `d817a8cc93` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `5852147d93` Let snippet completions insert as they are (UP18)<br>339 `6af8121b04` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `f122f57646` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `db152bbec2` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `dcba76f973` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `71d44be145` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `0a7e6fe24a` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `d832a51637` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `fd88936cae` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `e0f66cc0e6` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `07fe821f9c` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `681fa53fde` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `6c0eac4dfd` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `f167a9924c` Drop the change a restart's retired document synchronizer still sends<br>345 `42be107325` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `a314a18f86` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `8db1fc1931` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `16e3af567f` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `7ad302c426` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `cfd5475843` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `af007477ee` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `48b25f45de` Put the embedded build's errors in the Problems window's Project Errors<br>292 `faee74c7e4` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `5c1b784261` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `323ea7df34` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `d4471e1783` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `30515d4832` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `d7dc4d6450` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `2ca1bb4f97` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `d115d5f9a9` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `9390f8ae95` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `9a91fb508c` Give Ecstasy files a PSI of the Ecstasy language<br>153 `58f8baa19f` Close a block that Enter opens after a { in an Ecstasy file<br>155 `e07a1ccf7f` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `7104da3864` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `6f7eb2b3b7` Give Ecstasy its Code Style settings page<br>140 `409c2c27dc` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `393f53b50c` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `26d5f5b84a` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `1d3ba121bc` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `0cc66e078e` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `ed5f135739` Keep each light test's stub server and wrappers to that test<br>209 `516f47adec` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `f701417b36` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `7a647a73e1` Keep the stale instrumented classes of an older build off the plugin's unit tests | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `82f4fa0086` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `1399e0c620` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) | Two comments that explain why kept workarounds stay (UP21, UP04). |
| The language server JVM's memory setting | 421 `794fcd0839` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive | a setting in both editors with its server side; divide by path |
| Every IntelliJ settings page under one Ecstasy node | 425 `91582ff299` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy<br>427 `83c8023c84` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide | one Ecstasy node in IntelliJ's settings, its pages fitted to the dialog |
| Marks that LSP4IJ made before it connected a file's document (UP86) | 479 `dda839d0f1` Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) | refreshes an editor's marks once the connected document holds its own report; lsp4ij#2231 and our fix #2232 would retire it |

Kept as they are:

- 14 `e9469213f1` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `5e806225b5` Restart editor-owned language servers from a project-scoped CLI
- 32 `b126a50202` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `6e497cfd8b` Cancel IntelliJ requests whose connection ends
- 99 `53b075bf5f` Keep the language server running while a run or debug session uses it
- 130 `792317e4b1` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `b19a02eec4` Build an embedded run's process handler in one place
- 154 `703247c0d2` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `f67ddd7e88` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `993e057d35` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 10 now, about 6 after squashing. **Added lines:** 2,678 main, 341 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `022e626559` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `ba06273a79` Say when a move leaves the uses of its sources as they were<br>250 `e01549ee49` Ask the server again for a move's edits when a change overtook its answer | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `a8b99bc641` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `ffaac07108` Compile the VS Code extension's TypeScript into an empty out/<br>272 `68f92c3707` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |

Kept as they are:

- 119 `cec4eae1bc` List every VS Code command in the extension README, by its own title
- 234 `add6ec816f` Trace the language server's messages when xtc.trace.server says so
- 304 `ae4a12fe3a` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `fd4ebe3a0b` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 59 now, about 24 after squashing. **Added lines:** 10,760 main, 9,879 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `7fc45a0121` Report a Future callback that throws or that a terminated service drops<br>198 `2f6a6438b0` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `2926518e9c` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `175e02f510` Build and run with the embedded compiler in the server and both editors<br>27 `23fed761bd` Make embedded the default build mode, chosen per editor<br>179 `c5f9725537` Build a Gradle-imported project embedded in embedded mode | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `cabceae27c` Let embedded runs read console input in both editors<br>34 `89bead30f9` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `75136799c6` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `438440ed63` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `62ef26ac4d` Run programs side by side in supervised execution workers<br>33 `2864f88a14` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `e62df115e3` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `81d586a063` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `bf766d0a0e` Lift the lazy-value time limit for the embedded execution tests' worker<br>464 `f22f17c124` Take the execution worker's events off its standard output, onto a loopback socket of their own | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `38976ecb50` Let deduction find a named module in src/main/x beside other sources<br>178 `8c5ae632c9` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `c1977b37d5` Run and debug a module's xunit tests from the editors, embedded<br>112 `1061907408` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `985d2ece4d` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `2a338c654c` Rerun only the failed Ecstasy tests from the test console<br>202 `ecc540a1ed` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `1f4e93ff60` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `ba8e4763f0` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `e372c231e7` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `c1ea0a55b2` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders<br>428 `c681af316c` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own<br>433 `19b81740f7` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `8e3927a005` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `53578fc67a` Pass a run task's args in Gradle and command-line mode<br>236 `e401d8d251` Run the editor's module from its Run button and context menu | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `ae786c39b7` Don't reuse a module compiled in another copy of the project<br>204 `f6dff9c895` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `1fbfbb1b22` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `f9e4a9047d` Describe running and debugging tests from both editors in the READMEs<br>121 `35c8c22f62` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |
| Library replacement in Gradle mode, per workspace folder, with the editors' setting | 406 `4e034c2717` Build, run and debug with Gradle while the workspace's own modules replace bundled libraries<br>415 `853809c7ed` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries<br>422 `5644763dce` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded<br>423 `27b958c207` Show the embedded compiler as not available where the project's own modules replace bundled libraries | the workspace's own modules replacing bundled libraries: in Gradle mode, kept to the folder that replaces them, the editors' setting, and the embedded compiler shown as not available; the editor parts need L6 and L7, so the group goes to L8 |
| The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | 407 `5e409bf946` Name each source set's project by its path in the build tree in the IDE's Gradle model (#774)<br>465 `b8a7893a87` Export each project's xtcRun and xtcTest settings in the language server's Gradle model<br>472 `4eeec66eef` Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's | the plugin's model export that the server reads; 472 exports the JVM arguments that #804's allJvmArgs give |
| Gradle tasks in each module's own project (#774) | 408 `d6cc7169d5` Tell the client each module's Gradle project from the imported build model (#774)<br>409 `4c78afe42e` Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774)<br>410 `0a1787a322` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774)<br>411 `34fb147660` Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) | the server names each module's Gradle project, and both editors run its tasks there, reaching a composite's included builds |
| Embedded runs with their Gradle project's xtcRun and xtcTest settings | 466 `6a08fea89c` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors | embedded runs take the Gradle project's method, arguments and JVM options, from 465's export |
| runXtc keeps xtcRun's settings (open PR #804) | 467 `00541114a9` Keep xtcRun's settings in runXtc: the default JVM options beside jvmArgs, and a module's method and arguments with --module<br>471 `ff0491c67a` Give jvmArgs Gradle's semantics on the extension and the task alike, and assertions an enableAssertions property<br>473 `780e7eb31b` Say in the plugin README that a task's own modules replace its extension's<br>474 `ab5521d2a3` Correct the plugin README's DSL, classes and execution modes that no longer exist | already open on master as #804; its four commits drop out of #693 when #804 merges |

Kept as they are:

- 30 `39f47e8168` Find entry methods through the module's type and pass them with their arguments
- 38 `150739664d` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `74a3474977` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `5dec0977f8` Report a stopped run's end without the failure its stop causes
- 199 `d757586b80` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `0c59fa0a48` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 60 now, about 36 after squashing. **Added lines:** 17,235 main, 15,203 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `253e7ee601` Attach the debugger to a program run with --debug<br>105 `d73771b5fe` Move the debugger agent into javatools, with the protocol as records<br>114 `ae072b8de2` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `50b4d3b743` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `bb8a36fd20` Let the debugger find a module's sources where the language server says<br>383 `e592edc27d` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `3a6921c15e` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it<br>405 `57068b179b` Find a debugged module's sources in the workspace when no source graph names it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `72cef1abb1` Debug programs from VS Code and IntelliJ through the language server<br>72 `cb66c600c7` Send a step's response before the stop where the step ends | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `bf45953b4a` Stop at exceptions, pause a running program, and stop on entry<br>75 `d5f4dc4078` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `723b8c7258` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `af7bd8828a` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `3803bce68d` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `0f77d853ff` Let a host give lazy variables' code longer than ON_DEMAND<br>431 `55b2d56f41` Let a debug launch or attach set how long evaluations and lazy values may run | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `db90cc8501` Restart a stopped frame, which its caller then calls again<br>215 `daef2bd742` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `70788dc3d7` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `853dbb054a` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `75d940b2e8` Lift SessionDebuggerTest's time limit to an hour<br>166 `ed3c3c646e` Make SessionDebuggerTest independent of how its threads are scheduled | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `da996a1112` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `cb281aa96c` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `793d35da57` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `211a02e25a` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `0d9f6921e1` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `435242e681` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `8c2945095d` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `efcbea0a3f` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `ee7bfc6999` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `c9532fceef` Debug a command-line run: xtc run runs the program, which connects<br>149 `381607352d` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `b5778da8f5` Let VS Code set breakpoints in Ecstasy files<br>232 `473566b44a` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `87c632c56c` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `76486a127f` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |
| End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | 401 `b4b5032312` End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) | LSP4IJ 0.30.1 keeps the exit code from the exited event; one commit |
| Debugged stacks end at the host's fiber; failed requests say why | 439 `c9a57e252d` End a debugged program's stack at the host's fiber, also when it has moved on from its call<br>440 `726591ecbd` Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." | the runtime ends a debugged program's stack at the host's fiber (X4), the DAP server answers a failed request with its reason (L9) |

Kept as they are:

- 47 `d5ee203417` Show a stopped frame's variables, and step over, into and out of calls
- 49 `18be706cc2` Evaluate watches and hovers on paths in a stopped frame
- 51 `93fed869b0` Evaluate expressions with operators without running the program's code
- 52 `bde81972a7` Leave the runner's frames out of the debugger's stacks and traces
- 53 `d93b3bc1ea` Run code typed in the Debug Console on the stopped fiber
- 54 `23e4ac813b` Stop at the program's first line from IntelliJ's configuration
- 56 `ffc0acdad4` Place breakpoints on the lines a debugged program was built from
- 59 `e1f559cc28` Show library frames in their bundled source, every fiber, and modules
- 60 `6d1a139041` Step into a chosen call of the stopped line
- 61 `fcfaec2374` Stop where a method of a name is entered
- 63 `326fbf868d` Stop once a watched field of an object has changed
- 65 `7560c3931c` Answer inline values with the stopped method's variables
- 101 `ba8ede4b46` Continue, step or pause one fiber while the others stay stopped
- 103 `58a993e71d` Test that a part of a value's variables from past its end is empty
- 106 `e042fe710e` Keep a debugged task's last output, and X325's queued fiber
- 141 `af0177fff9` Debug a program that has an interface
- 169 `67559c3e7e` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `6640b59833` Let a debugger condition name an enum value by its enumeration
- 195 `14d794906e` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 22 now, about 9 after squashing. **Added lines:** 3,318 main, 3,199 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `66e79a25eb` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `cf2ab7f16a` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `8135ba3f1c` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `d24d09e963` Compile a library source's ecstasy-library content with its library, as its copy is | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `e1532ab177` Report no diagnostics for a library source's read-only copy<br>249 `c05924add9` Compile a library source's copy on its own even once a compile has named it<br>463 `b0335336e2` Keep diagnostics working when a restart opens another server's library copy again | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `04381b5dc0` Delete the language server's temporary directories once no server needs them<br>277 `1f1d32b2bf` Keep a server directory's lock beside it, where reading the directory cannot release it | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `2afb8d3e57` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `3144a84ef1` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `48e99b5eb9` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `6f20ea1721` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |
| Replaced libraries: their import candidates and their errors | 413 `4b6b8d92c4` Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's<br>414 `7cec24f124` Say on a replaced library with errors that the server's own compiler may be older than the repository's | two server follow-ups of library replacement |
| Compile only what an edit changes: declaration digests and the member-use tracker | 448 `3f79ceef9a` Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies<br>449 `d1a1e170e7` Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do<br>461 `7725f9036a` Digest the API of each declaration of a module apart (DeclarationDigests)<br>462 `a3d47502b0` Compile again, after an edit, only the modules that use what it changed (member-use tracker)<br>470 `a2184011d3` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own | DeclarationDigests in javatools (X2), the artifact API fingerprint and the member-use tracker in the server (L5); 470 fixes 461's Javadoc, fold it in |

Kept as they are:

- 45 `3528ba1bf5` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `c33bca4693` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 112 now, about 20 after squashing. **Added lines:** 2,967 main, 57,358 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `17c0f1bc7b` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `0b634daaeb` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `bb5fec87c8` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `17e5842253` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `04f85343f5` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `eb7aed4ebe` Report the written video's real length from the tour composer<br>276 `057abcdcef` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `94e906197c` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `75125a4c13` Record a playbook run's screen as video in the container<br>228 `08030a2e09` Delete a playbook run's IDE folder once its logs are copied, in the container | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `0f871de513` Press the keymap's own Undo key in the IntelliJ tour<br>253 `f474f2870e` Undo in the IntelliJ tour from the editor as the change left it<br>255 `1ceaae077b` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `ab5581d5cb` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `9b2ffc9bae` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `d7a03e483e` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `10912cf2ed` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `83835c3b37` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `913fc84a04` Edit in the tour's kill step only once the restarted server has the file open (UP83)<br>403 `644261d919` Check that one server comes back when the tour kills the server's process (UP72)<br>404 `1c842cdaee` Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will<br>447 `59edc064bf` Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames<br>480 `dbd853e176` Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `40158ffdef` Record a tour of the VS Code extension that checks what it shows<br>251 `31254efc1f` Make the VS Code tour pass as one whole run, unrecorded<br>257 `eaf9af36b6` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `f68e19d694` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `d7b8f607fd` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `b6c2fef9d0` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `a2f8a24086` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `c6ce0c9bc3` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `5e195bc7cb` Let tests wait for a hang only, not for a fast machine<br>194 `7d3efd827a` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `103d5d8087` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `e857457cf2` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `c2b63d3563` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `d9e247c306` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `4c23d5e62b` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `317a9e7125` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `1f98b0c966` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `3f945bcb7c` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `b5e3ace999` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `9d52c16de4` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `1bc1c26915` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `128bec9e37` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `bf3122e76c` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `ed5dbba1a9` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `e3a8d91aac` Run the editor playbooks in the container from Gradle, with any engine<br>146 `ea5a7ecb28` Queue playbook container runs on one machine lock, each with its own export<br>147 `7bbb5405bc` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `b4527831f0` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3<br>432 `75b81bc095` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `b3ffcdc5ac` Offer server save formatting in IntelliJ (UP02)<br>330 `57283b6149` Open bundled library sources as the server's virtual documents (UP25)<br>331 `aeb076ff99` Check inline completion's invocation and selection natively (UP26)<br>332 `cd1dc90920` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `7f15d60dd6` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `e94db9a951` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `72a3787703` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `d0990ee0ee` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `5b92227c93` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `db9518c02a` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `becc29e4ca` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `efc424207f` Read a signature without parameters in the playbook<br>351 `759dd3109f` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `d359b1104e` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `cce99cff8b` Check X254's library view through the server<br>354 `6a3eb0eb18` Read X5's library target from the server until LSP4IJ connects it<br>355 `bb7738a697` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `4b4b702e4f` Expect X20's signature without parameters in VS Code<br>365 `c4712b7338` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `2d218903ae` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `3996ca699b` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `aac4a04c2f` Ask X254's definition again when the restarted server retires its analysis<br>376 `414d04762f` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `b92c852fc6` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view<br>399 `88863de8a2` Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81)<br>402 `a5193ac01d` Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `3477c3030c` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `24a51bc7bb` Send X259's library edit to the old server before the restart<br>87 `683839e7ec` Wait for X257's Invoked request to reach the LSP console<br>88 `73b85a600b` Reopen a popup under inspection after the IDE was deactivated<br>175 `05994b8656` Give X145 a reference search that waits until it is canceled<br>282 `86afd54b68` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `ad84cfa4f6` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `cc5dd299eb` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `03599d7bd2` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `35dea8d302` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open<br>446 `dc33f0ae02` Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `d0e423ac49` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `ccfa1a4e01` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `496cde6f31` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `e87e4eaaf4` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `ae6fe49ba4` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `2efeb8338b` Check Run to Cursor in both editors<br>64 `9aa981ca81` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `29a9e2e259` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `5a13a0a0bf` Check a document link's Ctrl+Click in IntelliJ's X131 | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `8f864619db` Give the playbook catalog's size as its 342 cases everywhere<br>122 `d94e28a619` Point the playbook instructions at the playbook container<br>127 `20caf0295a` List the test and debug-task messages and a test row in the playbook's 18a | Fold into the catalog's documentation. |
| What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | 477 `5c75702791` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered<br>478 `174f88a9a0` Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it | the playbook keeps each server's whole trace, with each pull's answer at DEBUG only, and the tour's waits and popup steps say what LSP4IJ holds and what could close them |

Kept as they are:

- 43 `311905ec99` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `79b135c6b0` Apply X103's rename as a refactoring and check the member after Undo
- 187 `0bb100f3f7` Check that Debug of Current File launches an Ecstasy configuration
- 189 `277096a5c6` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `e4dd908880` Run the VS Code playbook after the extension suite, not with it
- 200 `f833533f57` Count the code actions at the tour's unused import and unused local
- 247 `a9c97fe2dd` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `bb8f606d1b` Print both compiler playbooks' progress per case in the build's output
- 279 `8ce9e01b63` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 29 now, about 4 after squashing. **Added lines:** 5,516 main, 372 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `14ebae667c` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `cd0826a511` Drop the references to documents and plans this branch does not carry<br>69 `b992d273af` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `d8c2818706` Fix lang/README's task paths, versions and feature summary<br>123 `9878f1174c` Use American spelling in the shippable docs<br>124 `598efca5d2` Name testing in the embedded build and run index entry<br>125 `cddd9ba40a` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `9b6dc61542` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `714c15f290` Say that every adapter needs Java 25 in editor-setup.md<br>283 `e8c271d9da` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `502db6167a` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `2a931e8277` Point the docs' line citations at the lines today's changes moved | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `446140d22c` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `8168ca7dbc` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `7a775fc8b7` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `0550f55747` Leave the lang build out of the root build by default again<br>379 `31bff41a58` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `d9c471af7b` Say in AGENTS.md that gradle.properties turns the lang build flags on | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |
| The user guide, with screenshots from the tours | 424 `42737304d7` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours<br>426 `7b19169cb1` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run<br>429 `4fb790479a` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature<br>430 `3c0ca5612e` Move the user guide into lang/doc/user-guide, with its screenshots in images<br>434 `d9f42da19d` Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's<br>435 `2083f1a99f` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting<br>452 `96da927596` Say in the user guide what a rename doesn't change or check<br>475 `8290e4ca82` Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs<br>476 `bbe845ba74` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages<br>481 `efc3648dc1` Mark the user guide's keys up as keys, and the names it left plain as code<br>482 `a10580fcaf` Add an index of every page and section of the user guide to its landing page | the guide, its testing chapter and its screenshots, made from tagged tour steps |

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
| 1 | `5d747e3a60` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `37dbb04ffa` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `1ddea3e0be` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `fe43afb01d` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `0a7f9f5cc9` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `b608e64535` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `7b43afa997` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `50fcd8e32d` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `90ad9a88b0` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `b4222b09d7` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `d85a147110` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `45ef4e5a94` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `f28a823e45` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `e9469213f1` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `79c0147476` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `3c2b080b64` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `5e806225b5` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `0de393e93d` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `0c836e4d5a` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `175e02f510` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `14ebae667c` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `d0e423ac49` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `1905680c0e` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `200c190586` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `cabceae27c` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `75136799c6` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `23fed761bd` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `70ce7edf0b` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `62ef26ac4d` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `39f47e8168` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `81d7ad5a35` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `b126a50202` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `2864f88a14` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `89bead30f9` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `61e369b94c` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `4f1a40d312` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `080fb71244` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `150739664d` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `e62df115e3` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `6e497cfd8b` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `cb4949b9ae` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `76237d9959` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `311905ec99` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `0ca33da040` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `3528ba1bf5` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `72cef1abb1` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `d5ee203417` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `bf45953b4a` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `18be706cc2` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `723b8c7258` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `93fed869b0` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `bde81972a7` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `d93b3bc1ea` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `23e4ac813b` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `3803bce68d` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `ffc0acdad4` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `81d586a063` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `2efeb8338b` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `e1f559cc28` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `6d1a139041` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `fcfaec2374` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `db90cc8501` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `326fbf868d` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `9aa981ca81` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `7560c3931c` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `29a9e2e259` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `5a13a0a0bf` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `cd0826a511` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `b992d273af` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `4a15f662a5` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `a8b99bc641` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `cb66c600c7` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `f95959f1b8` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `79b135c6b0` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `d5f4dc4078` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `d2e739212a` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `022e626559` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `0f77d853ff` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `253e7ee601` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `70788dc3d7` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `703bd8ea77` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `8234ff1b1b` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `76ab810be4` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `f167a9924c` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `3477c3030c` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `24a51bc7bb` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `683839e7ec` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `73b85a600b` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `8c2945095d` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `bf3122e76c` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `853dbb054a` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `ab65adcf09` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `d9e247c306` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `ed5dbba1a9` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `adac80fd86` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `ee7bfc6999` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `61501ffdf7` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `ae786c39b7` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `53b075bf5f` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `e3a8d91aac` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `ba8ede4b46` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `54dcd88f02` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `58a993e71d` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `c9532fceef` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `d73771b5fe` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `e042fe710e` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `38976ecb50` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `c1977b37d5` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `f5c3f3c63d` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `985d2ece4d` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `8e3927a005` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `1061907408` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `bf766d0a0e` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `ae072b8de2` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `58a5e71e24` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `87c632c56c` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `f9e4a9047d` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `8f864619db` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `cec4eae1bc` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `d8c2818706` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `35c8c22f62` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `d94e28a619` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `9878f1174c` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `598efca5d2` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `cddd9ba40a` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `9b6dc61542` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `20caf0295a` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `714c15f290` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `76486a127f` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `792317e4b1` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `2a39c4b938` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `01a92e3621` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `74a3474977` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `f89d55f74d` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `8a8e847ca2` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `b19a02eec4` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `04df09597a` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `5f98caeb3f` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `6f7eb2b3b7` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `409c2c27dc` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `af0177fff9` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `75d940b2e8` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `05034bd1bf` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `16a17c8d61` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `642f56d8a0` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `ea5a7ecb28` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `7bbb5405bc` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `5e195bc7cb` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `381607352d` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `17c0f1bc7b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `c33bca4693` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `9a91fb508c` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `58f8baa19f` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `703247c0d2` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `e07a1ccf7f` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `2cb6d23de4` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `2c8e2b3f05` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `2a338c654c` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `da996a1112` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `3e28be637c` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `a7c739acbb` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `141be96008` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `9f64cab7e7` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `210f8fdb24` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `30515d4832` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `ed3c3c646e` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `da5cfe53a9` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `029dc1d68f` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `67559c3e7e` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `7fc45a0121` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `895869036d` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `18e4d1423a` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `f9e3f2e4b5` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `6640b59833` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `05994b8656` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `1a7b694e3a` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `d5ceb77182` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `8c5ae632c9` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `c5f9725537` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `81d4930ca7` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `a314a18f86` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `7104da3864` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `af7bd8828a` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `33faed26d4` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `0e9fe53d0d` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `c614868729` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `0bb100f3f7` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `5dec0977f8` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `277096a5c6` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `e4dd908880` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `af007477ee` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `039db79e31` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `fde83dce54` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `7d3efd827a` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `14d794906e` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `94831084af` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `793d35da57` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `2f6a6438b0` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `d757586b80` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `f833533f57` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `5833436a18` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `ecc540a1ed` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `05a3170ca0` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `f6dff9c895` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `585e2faed3` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `1f4e93ff60` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `ed5f135739` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `6836d42067` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `516f47adec` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `307aedab5e` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `a99067fa6a` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `d7dc4d6450` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `2ca1bb4f97` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `7c853bccae` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `daef2bd742` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `0c59fa0a48` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `ffaac07108` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `e1532ab177` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `ba8e4763f0` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `083e336f9a` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `48b25f45de` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `16e3af567f` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `d115d5f9a9` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `e372c231e7` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `0d9f6921e1` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `59440ea709` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `75125a4c13` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `08030a2e09` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `0b634daaeb` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `bb5fec87c8` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `b5778da8f5` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `473566b44a` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `53578fc67a` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `add6ec816f` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `920f62d3a1` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `e401d8d251` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `2d3f8f257b` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `ba06273a79` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `2926518e9c` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `4c23d5e62b` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `ccfa1a4e01` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `17e5842253` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `40158ffdef` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `1fbfbb1b22` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `7ad302c426` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `9b8b41787d` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `a9c97fe2dd` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `0b62399828` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `c05924add9` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `e01549ee49` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `31254efc1f` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `0f871de513` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `f474f2870e` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `9390f8ae95` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `1ceaae077b` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `ab5581d5cb` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `eaf9af36b6` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `f68e19d694` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `d7b8f607fd` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `9b2ffc9bae` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `b0571ff946` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `04f85343f5` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `bb8f606d1b` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `eb7aed4ebe` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `103d5d8087` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `66d35ca465` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `66e79a25eb` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `c1ea0a55b2` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `438440ed63` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `d7a03e483e` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `b6c2fef9d0` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `68f92c3707` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `7a775fc8b7` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `cf2ab7f16a` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `04381b5dc0` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `057abcdcef` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `1f1d32b2bf` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `8135ba3f1c` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `8ce9e01b63` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `d24d09e963` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `496cde6f31` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `86afd54b68` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `e8c271d9da` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `d9c471af7b` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `f701417b36` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `94e906197c` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `10912cf2ed` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `a2f8a24086` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `e6706b3ded` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `502db6167a` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `ad84cfa4f6` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `faee74c7e4` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `e87e4eaaf4` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `5c1b784261` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `323ea7df34` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `ae6fe49ba4` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `7a647a73e1` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `65744e932d` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `f67ddd7e88` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `317a9e7125` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `afa5765ab4` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `393f53b50c` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `cc5dd299eb` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `ae4a12fe3a` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `2a931e8277` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `1f98b0c966` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `26d5f5b84a` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `3f945bcb7c` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `b4527831f0` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `e857457cf2` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `2afb8d3e57` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `48e99b5eb9` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `3144a84ef1` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `6f20ea1721` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `446140d22c` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `8168ca7dbc` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `0550f55747` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `bb8a36fd20` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `b5e3ace999` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `03599d7bd2` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `c2b63d3563` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `9d52c16de4` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `1bc1c26915` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `128bec9e37` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `abe6303503` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `ef0e8d1cff` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `d817a8cc93` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `5852147d93` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `b3ffcdc5ac` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `57283b6149` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `aeb076ff99` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `cd1dc90920` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `7f15d60dd6` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `e94db9a951` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `72a3787703` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `d0990ee0ee` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `5b92227c93` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `db9518c02a` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `6af8121b04` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `f122f57646` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `db152bbec2` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `dcba76f973` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `71d44be145` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `0a7e6fe24a` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `42be107325` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `d832a51637` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `483e167b41` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `fd88936cae` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `becc29e4ca` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `efc424207f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `759dd3109f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `d359b1104e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `cce99cff8b` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `6a3eb0eb18` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `bb7738a697` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `36616eaf44` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `993e057d35` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `4b4b702e4f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `e0f66cc0e6` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `211a02e25a` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `07fe821f9c` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `cb281aa96c` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `8db1fc1931` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `f9e536fee3` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `c4712b7338` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `efcbea0a3f` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `435242e681` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `50b4d3b743` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `cfd5475843` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `2d218903ae` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `3996ca699b` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `681fa53fde` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `d4471e1783` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `75f2e8f0c5` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `aac4a04c2f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `414d04762f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `82f4fa0086` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `b92c852fc6` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `31bff41a58` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `1d3ba121bc` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `fd4ebe3a0b` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `35dea8d302` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `e592edc27d` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `09868d0a51` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `b650223f5b` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `af5b972e35` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `b37a31b952` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `0cc66e078e` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `c6ce0c9bc3` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `83835c3b37` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `913fc84a04` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `3b99cad997` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `6c0eac4dfd` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `8bbab95d79` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `1399e0c620` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `3a6921c15e` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `59a5148469` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
| 398 | `07bfebb35d` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83) |
| 399 | `88863de8a2` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81) |
| 400 | `92e0f668db` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) |
| 401 | `b4b5032312` | P10 | — | L9 | End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) |
| 402 | `a5193ac01d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) |
| 403 | `644261d919` | P12 | — | L10 | IntelliJ tour step fixes | Check that one server comes back when the tour kills the server's process (UP72) |
| 404 | `1c842cdaee` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will |
| 405 | `57068b179b` | P10 | — | L9 | The debugger finds sources where its host names them | Find a debugged module's sources in the workspace when no source graph names it |
| 406 | `4e034c2717` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build, run and debug with Gradle while the workspace's own modules replace bundled libraries |
| 407 | `5e409bf946` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Name each source set's project by its path in the build tree in the IDE's Gradle model (#774) |
| 408 | `d6cc7169d5` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Tell the client each module's Gradle project from the imported build model (#774) |
| 409 | `4c78afe42e` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774) |
| 410 | `0a1787a322` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774) |
| 411 | `34fb147660` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) |
| 412 | `0f4e3bd804` | P6 | — | L4 | Rename guards and proofs | Prove a rename in a workspace module that replaces a bundled library as the workspace's sources |
| 413 | `4b6b8d92c4` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's |
| 414 | `7cec24f124` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Say on a replaced library with errors that the server's own compiler may be older than the repository's |
| 415 | `853809c7ed` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Add an editor setting for whether the workspace's own modules replace bundled XDK libraries |
| 416 | `99132b93c9` | P6 | — | L4 | Rename guards and proofs | Find a text's line breaks once per comparison in a rename's proof, not once per position |
| 417 | `5dfa309be6` | P6 | — | L4 | Rename guards and proofs | Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes |
| 418 | `af4a0f49ab` | P6 | — | L4 | Rename guards and proofs | Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy |
| 419 | `8d2b034dab` | P4 | — | L1, L2 | Time and heap logs of the server's work | Log each project query's and each compile's time and heap |
| 420 | `de7482c05d` | P4 | — | L1 | Time and heap logs of the server's work | Log what the server did in each minute in which it worked, and over its session |
| 421 | `794fcd0839` | P7 | — | L6 | The language server JVM's memory setting | Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive |
| 422 | `5644763dce` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded |
| 423 | `27b958c207` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Show the embedded compiler as not available where the project's own modules replace bundled libraries |
| 424 | `42737304d7` | P13 | — | L10 | The user guide, with screenshots from the tours | Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours |
| 425 | `91582ff299` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy |
| 426 | `7b19169cb1` | P13 | — | L10 | The user guide, with screenshots from the tours | Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run |
| 427 | `83c8023c84` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide |
| 428 | `c681af316c` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own |
| 429 | `4fb790479a` | P13 | — | L10 | The user guide, with screenshots from the tours | Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature |
| 430 | `3c0ca5612e` | P13 | — | L10 | The user guide, with screenshots from the tours | Move the user guide into lang/doc/user-guide, with its screenshots in images |
| 431 | `55b2d56f41` | P10 | X4 | L9 | Lazy properties and their time limit | Let a debug launch or attach set how long evaluations and lazy values may run |
| 432 | `75b81bc095` | P12 | X5 | L10 | The playbook container and the IDE it drives | Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode |
| 433 | `19b81740f7` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc |
| 434 | `d9f42da19d` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's |
| 435 | `2083f1a99f` | P13 | — | L10 | The user guide, with screenshots from the tours | Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting |
| 436 | `226b629b82` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed |
| 437 | `bc62272a7b` | P6 | — | L4 | Prove code actions with incremental compiles | Start a rename's proof from the revision's compile, and compile after the edit only what it reaches |
| 438 | `1147ab7351` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work |
| 439 | `c9a57e252d` | P10 | X4 | — | Debugged stacks end at the host's fiber; failed requests say why | End a debugged program's stack at the host's fiber, also when it has moved on from its call |
| 440 | `726591ecbd` | P10 | — | L9 | Debugged stacks end at the host's fiber; failed requests say why | Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." |
| 441 | `ded64de57d` | P5 | X2 | L3 | Bind more names in the semantic model | Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration |
| 442 | `a4312fa294` | P5 | — | L3 | Bind more names in the semantic model | Make the _ placeholder no occurrence in the semantic model |
| 443 | `c7750aadd9` | P5 | X2 | L3 | Bind more names in the semantic model | Bind the names of a method's multiple return values to the values they declare |
| 444 | `e73e885a7b` | P5 | X2 | L3 | Bind more names in the semantic model | Bind this in a lazy property's calc() and super in a method reference to what they name |
| 445 | `03bfd34081` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed |
| 446 | `dc33f0ae02` | P12 | — | L10 | Individual playbook case fixes | Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 |
| 447 | `59edc064bf` | P12 | — | L10 | IntelliJ tour step fixes | Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames |
| 448 | `3f79ceef9a` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies |
| 449 | `d1a1e170e7` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do |
| 450 | `7a4cf63e57` | P6 | — | L4 | Prove code actions with incremental compiles | After a rename's edit, compile only the modules depending on the edited ones that it can reach |
| 451 | `6c95324c52` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done |
| 452 | `96da927596` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide what a rename doesn't change or check |
| 453 | `f12f36c760` | P5 | — | L3 | Bind more names in the semantic model | Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares |
| 454 | `77761cf5e5` | P5 | X2 | L3 | Bind more names in the semantic model | Keep the initializer facts of a property declared in a loop's body, such as a static local |
| 455 | `d4d445504f` | P5 | — | L3 | Bind more names in the semantic model | Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration |
| 456 | `44e1adc9de` | P5 | — | L3 | Bind more names in the semantic model | Declare the names of a function type's values in the semantic model, as of its return values in a typedef |
| 457 | `afb8910de2` | P5 | — | L3 | Bind more names in the semantic model | Bind a reserved name such as this:class in a property's accessor, as in a method of the class |
| 458 | `867bbc740d` | P5 | — | L3 | Bind more names in the semantic model | Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells |
| 459 | `db12fbc31f` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Report constructor arguments and default values that no constructor uses |
| 460 | `baba2d0981` | P6 | — | L4 | Rename guards and proofs | Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element |
| 461 | `7725f9036a` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Digest the API of each declaration of a module apart (DeclarationDigests) |
| 462 | `a3d47502b0` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Compile again, after an edit, only the modules that use what it changed (member-use tracker) |
| 463 | `b0335336e2` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Keep diagnostics working when a restart opens another server's library copy again |
| 464 | `f22f17c124` | P9 | — | L8 | Supervised execution workers | Take the execution worker's events off its standard output, onto a loopback socket of their own |
| 465 | `b8a7893a87` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export each project's xtcRun and xtcTest settings in the language server's Gradle model |
| 466 | `6a08fea89c` | P9 | — | L8 | Embedded runs with their Gradle project's xtcRun and xtcTest settings | Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors |
| 467 | `00541114a9` | P9 | #804 | — | runXtc keeps xtcRun's settings (open PR #804) | Keep xtcRun's settings in runXtc: the default JVM options beside jvmArgs, and a module's method and arguments with --module |
| 468 | `ef370304b5` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType() |
| 469 | `4149ac20f8` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them |
| 470 | `a2184011d3` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own |
| 471 | `ff0491c67a` | P9 | #804 | — | runXtc keeps xtcRun's settings (open PR #804) | Give jvmArgs Gradle's semantics on the extension and the task alike, and assertions an enableAssertions property |
| 472 | `4eeec66eef` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's |
| 473 | `780e7eb31b` | P9 | #804 | — | runXtc keeps xtcRun's settings (open PR #804) | Say in the plugin README that a task's own modules replace its extension's |
| 474 | `ab5521d2a3` | P9 | #804 | — | runXtc keeps xtcRun's settings (open PR #804) | Correct the plugin README's DSL, classes and execution modes that no longer exist |
| 475 | `8290e4ca82` | P13 | — | L10 | The user guide, with screenshots from the tours | Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs |
| 476 | `bbe845ba74` | P13 | — | L10 | The user guide, with screenshots from the tours | Split the user guide into a landing page, one page per chapter, and features and shortcuts pages |
| 477 | `5c75702791` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered |
| 478 | `174f88a9a0` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it |
| 479 | `dda839d0f1` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) |
| 480 | `dbd853e176` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place |
| 481 | `efc3648dc1` | P13 | — | L10 | The user guide, with screenshots from the tours | Mark the user guide's keys up as keys, and the names it left plain as code |
| 482 | `a10580fcaf` | P13 | — | L10 | The user guide, with screenshots from the tours | Add an index of every page and section of the user guide to its landing page |
