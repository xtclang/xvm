# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 570 commits of `errs/full-testable-lsp-20261009` over `master` (tip `96173de96e`, master `4350e40af0`, 2026-11-26), numbered 1 to 570 from the oldest, with their short hashes. Commits 1-397 were planned on 2026-11-23; the rebase onto master of 2026-11-25 kept their numbers and gave them new hashes. Commits 398-570 came after, and were placed by the same rules. One is open on master as its own PR: 459, with 502 and 524 its review round, is #802, which leaves #693 when it merges. #804's four commits, 467, 471, 473 and 474 then, merged to master as 2e26663249 on 2026-11-25; #693 was rebased onto it without them, which renumbered the commits after 467. #808's three, 481-483 then, merged as fb7ba82052 on 2026-11-26, and #693 was rebased onto it without them, which renumbered the commits after 480. #810, the plugin installable in every JetBrains IDE, merged as 4350e40af0 the same day; #693 was rebased onto it with no commit dropped, as its own fuller change (Gradle optional, not removed) is commit 530.

It proposes 15 pull requests, each of which changes either only files outside lang/ (xvm only: X1-X5) or only files in lang/ (lang only: L1-L10), merged in one order. To get there, the commits are first sorted into 13 topics (P1-P13), the commits of a topic that implement or fix the same thing, however far apart, are grouped to be joined, and every commit is then divided at lang/: its other files go to its topic's xvm PR, its lang/ files to its topic's lang PR.

## The pull requests, in merge order

| Order | PR | Changes only | Title | Prerequisites | Built from topics | Commits with a part in it | Added lines: main / tests |
|---|---|---|---|---|---|---|---|
| 1 | **X1** | files outside lang/ | Compiler diagnostics: the error-listener contract and repository read failures | — (master) | P1 | 12 | 2.9k / 2.8k |
| 2 | **X2** | files outside lang/ | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | X1 | P2, P11 | 15 | 5.7k / 1.1k |
| 3 | **X3** | files outside lang/ | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | X2 | P3, P4 | 8 | 5.8k / 1.7k |
| 4 | **X4** | files outside lang/ | Runtime support for running, testing and debugging from the editors: the debugger and its agent, xtc, xunit and the Gradle plugin | X3 | P9, P10 | 55 | 9.5k / 5.2k |
| 5 | **X5** | files outside lang/ | Build, CI and test tooling outside lang: CI test tiers, build checks, test hang detection, the LSP4IJ and IntelliJ versions, PR previews | X4 | P4, P7, P12, P13 | 27 | 1.7k / 989 |
| 6 | **L1** | lang/ | Language server: the server, its protocol, document synchronization, Tree-sitter and the mock adapter | X3, X5 | P4 | 46 | 11.3k / 13.8k |
| 7 | **L2** | lang/ | Language server: the compiler adapter's core, compiling, discovery, diagnostics and workspace pulls | L1 | P1, P2, P3, P4 | 41 | 9.3k / 17.6k |
| 8 | **L3** | lang/ | Language server: the semantic model and read-only editor features | L2 | P5 | 38 | 6.5k / 7.7k |
| 9 | **L4** | lang/ | Language server: code actions and refactorings | L3 | P6 | 40 | 9.4k / 13.2k |
| 10 | **L5** | lang/ | Language server: the XDK's library sources and the XDK workspace | L3 | P11 | 22 | 3.0k / 3.2k |
| 11 | **L6** | lang/ | IntelliJ plugin, on LSP4IJ 0.30.1 | L4, L5, X5 | P7 | 79 | 10.5k / 6.6k |
| 12 | **L7** | lang/ | VS Code extension | L4, L5 | P8 | 12 | 2.7k / 341 |
| 13 | **L8** | lang/ | Embedded build, run and tests in the language server and both editors | X4, L6, L7 | P9 | 49 | 9.6k / 8.8k |
| 14 | **L9** | lang/ | Debugging: the DAP server, debug sessions, launch and attach, and both editors' debug UI | X4, L8 | P10 | 48 | 8.6k / 11.0k |
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 185 | 8.6k / 59.6k |

Already open on master, apart from these: #802 (unused constructor arguments, commits 459, 502 and 524), which leaves #693 when it merges. #804 (runXtc keeps xtcRun's settings), #808 (the plugin tests' one network download dropped) and #810 (the plugin installable in every JetBrains IDE) are merged.

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
| P4 | Language server core: protocol, document sync, compile pipeline, discovery, diagnostics | P3 | yes, once 5 commits' other files move | 50 | 16 | 20.3k / 28.8k |
| P5 | Language server: semantic model and read-only editor features | P4 | yes | 37 | 8 | 6.6k / 7.7k |
| P6 | Language server: code actions and refactorings | P5 | yes | 39 | 10 | 9.4k / 13.2k |
| P7 | IntelliJ plugin, built against LSP4IJ 0.30.1 | P6 | nearly | 78 | 28 | 10.6k / 6.7k |
| P8 | VS Code extension | P6 | yes | 11 | 7 | 2.7k / 341 |
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 55 | 23 | 10.6k / 9.7k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 60 | 36 | 17.2k / 15.2k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 23 | 9 | 3.4k / 3.4k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 140 | 22 | 3.3k / 60.0k |
| P13 | Documentation, build and CI | the PRs it documents | no | 48 | 6 | 6.8k / 376 |
| | **Total** | | | **570** | **175** | |

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

Commit 8, `669cc976fe` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `4d9b3658de` | 366 `69c89471e9` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `ecd5d00626` | 345 `e56d10aafe` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `bb60cc0bbb` | 374 `8eff243364` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `d824cf55d2` | 369 `bd0455b649` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `c287d79bf9` | 367 `b7aa73cf37` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `153e9a9343` | 363 `5030add8e5` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `52a369f512` | 373 `02c53dcf72` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `d4cb3b40e9` | 362 `afb77fd141` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `c7d32a561c` | 360 `9be5fda3cb` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `5dda613f15` | 112 `8f6da58e4b` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `ba2cfe1d63` | 105 `153bb3465c` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `669cc976fe` | 36 `ba9698f71a` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `669cc976fe` | 387 `7012866854` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `65e84d24d0` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `4ef8c974e0` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `92215b0c30` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `cae73bddb2` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `ac3cf2645c` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `7be04050f0` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `708d86ffc0` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `937159ba6f` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `9b6781f5a4` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `a1fbe8b7f9` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `150e82f185` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `0bcdc811bc` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `423abecfe9` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `3163a2f081` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `0e44c52361` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `56ff17d343` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `2b7408f310` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `ca5b7485e3` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `276fc135b5` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `88d117e5d2` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `d6618d5ac9` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `5bfaea3b82` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `6b6803c878` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `e74c4938a0` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `48418d1076` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `b23fad3704` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `744b31e5f5` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `168f12fd6b` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `269a352cfe` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `7dec2a8726` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `3cba4d6654` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `ed397c1a78` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `472fc335c9` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `0a6cc3669f` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `7fde51f870` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `1fe53493aa` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `89b3fa820e` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `64ff7c85bc` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `29483933ea` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `7b3bc048cd` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `97ba9198c2` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `94fe753779` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `bc8a5ed94c` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `93b584fe2c` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `ba2cfe1d63` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `a8e301a02c` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `144cf7704a` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `ccc0afd4ce` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `bfed459938` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `153bb3465c` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `5dda613f15` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `c2a16b26f4` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `a4a2e1d5f0` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `8f6da58e4b` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `c3ce0051dc` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `0aede95b40` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `823759d50a` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `d9e2cdeaea` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `ca091e4f22` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `7a6d483815` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `d5fd568332` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `2c1cbccf5f` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `4842112efb` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `52a369f512` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `867f4f4633` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `bb60cc0bbb` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `aafa922c2b` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `2921860de3` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `565a24a3ad` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `3f0a967d46` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `b104954e1b` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `e1e3f66dcd` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `a901b41a86` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `e52e15b185` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `dae13c2ef2` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `e7f13a269a` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.
- 400 `cfe05c3c2f` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82), in P7 (+272; also touches: playbook)
- 410 `baadfd1215` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774), in P9 (+114; also touches: playbook)
- 415 `4bd073219d` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries, in P9 (+55; also touches: playbook)
- 421 `440b592c64` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive, in P7 (+141; also touches: playbook, server, vscode)
- 422 `cc1a143dcc` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded, in P9 (+220; also touches: playbook)
- 423 `ecdfd2614c` Show the embedded compiler as not available where the project's own modules replace bundled libraries, in P9 (+133; also touches: playbook)
- 424 `0a26a96167` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours, in P13 (+754; also touches: intellij, vscode)
- 425 `4df94023af` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy, in P7 (+277; also touches: playbook, server)
- 426 `896597b098` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run, in P13 (+193; also touches: intellij, playbook)
- 427 `7823f64a35` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide, in P7 (+73; also touches: playbook)
- 428 `92b0b1dfab` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own, in P9 (+447; also touches: playbook)
- 429 `43c004cc0c` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature, in P13 (+251; also touches: playbook)
- 430 `ecdaf40992` Move the user guide into lang/doc/user-guide, with its screenshots in images, in P13 (+996; also touches: intellij, playbook, vscode)
- 431 `11a3f96670` Let a debug launch or attach set how long evaluations and lazy values may run, in P10 (+716; also touches: playbook)
- 432 `d58b875f02` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode, in P12 (+5; also touches: build, intellij)
- 433 `8bef41b634` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc, in P9 (+485; also touches: playbook)
- 435 `ef76bd197c` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting, in P13 (+125; also touches: playbook)
- 436 `d536a66bb8` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed, in P6 (+589; also touches: intellij, playbook, vscode)
- 441 `8b575d8caf` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration, in P5 (+110; also touches: javatools)
- 443 `95903b713c` Bind the names of a method's multiple return values to the values they declare, in P5 (+65; also touches: javatools)
- 444 `0811e25caa` Bind this in a lazy property's calc() and super in a method reference to what they name, in P5 (+147; also touches: javatools)
- 445 `52597c7f21` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed, in P6 (+275; also touches: playbook)
- 454 `1babd1ea63` Keep the initializer facts of a property declared in a loop's body, such as a static local, in P5 (+47; also touches: javatools)
- 459 `07e8ef72f0` Report constructor arguments and default values that no constructor uses, in P1 (+129; also touches: xdk)
- 461 `2d8ce6096e` Digest the API of each declaration of a module apart (DeclarationDigests), in P11 (+622; also touches: javatools)
- 463 `7974261291` Keep diagnostics working when a restart opens another server's library copy again, in P11 (+294; also touches: playbook)
- 466 `ba160fac63` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors, in P9 (+1,453; also touches: playbook)
- 469 `0bd63247c0` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own, in P11 (+1; also touches: javatools)
- 472 `85cd467c18` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages, in P13 (+1,264; also touches: intellij)
- 473 `c1292bbae2` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered, in P12 (+141; also touches: intellij, server)
- 493 `61e6b7c80a` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run, in P12 (+367; also touches: build, intellij, vscode)
- 502 `8d17b5700f` Allow contribution arguments and shorthand defaults that match the explicit constructor, in P1 (+400; also touches: xdk)
- 512 `0e5a03dfdf` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed, in P13 (+17; also touches: intellij, server)
- 513 `e8487e3437` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops, in P13 (+16; also touches: server)
- 514 `21bb6432c7` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate, in P13 (+2; also touches: server)
- 515 `b2d7b649d5` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair, in P13 (+60; also touches: intellij)
- 516 `41989e6507` Share the language server's location requests and edit conversion, and say the semantic tokens setting once, in P13 (+32; also touches: server)
- 517 `6f9d175081` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end, in P13 (+40; also touches: server)
- 518 `e0bb12ba4d` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias, in P13 (+68; also touches: server)
- 520 `3c26376d06` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode, in P13 (+7; also touches: vscode)
- 521 `6ce8950eda` Share the language server's cancellation-owning future and its formatter's parenthesis search, in P13 (+23; also touches: server)
- 525 `cf9a8cda9d` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume, in P12 (+57; also touches: intellij)
- 526 `e382b769ed` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share, in P12 (+14; also touches: intellij)
- 529 `ed9dc72011` Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too, in P12 (+23; also touches: intellij)
- 533 `6093aab714` Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed, in P7 (+10; also touches: playbook)
- 535 `6024324e3e` Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times, in P12 (+15; also touches: intellij)
- 536 `82a22b8fd7` Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent, in P4 (+285; also touches: dap, intellij)
- 537 `d3b0bbbab1` Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire, in P4 (+1,129; also touches: dap, intellij, playbook)
- 538 `210d339a2e` Share the xtc.* keys and a build's summary text through lang/protocol, in P4 (+188; also touches: intellij, playbook)
- 539 `9647e5fcf9` Share the compiler's library settings through lang/protocol: their shape and how a path names a file, in P4 (+224; also touches: intellij)
- 546 `5b4a40e40d` Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps, in P12 (+52; also touches: dap)
- 548 `ea3df0c5af` Share the plugin unit tests' disk files, connection, waits and opens through LightEditor, in P12 (+125; also touches: intellij)
- 549 `4af7f15cc4` Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup, in P12 (+201; also touches: server)
- 550 `1bb4fc68f5` Share the lsp-server adapter tests' source writer and text edit application, in P12 (+190; also touches: server)
- 551 `3d97bf7d44` Share the embedding's incomplete analysis in the header tests, and merge their identical cases, in P12 (+137; also touches: server)
- 552 `227589fd39` Shorten the code action tests with the shared edits and per-class helpers, in P12 (+81; also touches: server)
- 553 `d158e62550` Share an adapter with a module compile hook among the tests that count or hold compiles, in P12 (+84; also touches: server)
- 554 `3224d3fa6b` Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures, in P12 (+121; also touches: server)
- 555 `a57fad023b` Merge FormattingConfigRoundTripTest's mock clients and server setup, in P12 (+48; also touches: server)
- 556 `3dc6a5bced` Apply the missing member, extraction and initializer tests' edits with the shared apply, in P12 (+7; also touches: server)
- 557 `ada2f381eb` Check XdkMemberUseTrackerTest's renames of foo through one helper, in P12 (+36; also touches: server)
- 558 `d8f869a573` Share the marker comment position of the navigation, lookup and occurrence tests, in P12 (+77; also touches: server)
- 559 `62fa0f5e06` Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file, in P12 (+91; also touches: server)
- 560 `0012213f67` Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know, in P4 (+31; also touches: intellij)
- 568 `74e290956b` Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7), in P5 (+677; also touches: intellij, vscode)
- 569 `1c14a091c7` Say, when a rename leaves string literals and comments that spell the old name, how many and where, in P6 (+232; also touches: playbook)
- 570 `96173de96e` Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording, in P12 (+10; also touches: intellij)

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
| Keep, cache and report module repository read failures | 1 `a471ce114a` Preserve module repository read failures without blocking usable candidates<br>9 `cae73bddb2` Report repository read attempts without inferring dependency failures<br>11 `7be04050f0` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `673083e804` Separate diagnostic reporting from listener abort policy<br>3 `545d84a8ca` Require explicit compiler listeners and reasons for silence<br>4 `1c6a506e55` Scope parser diagnostics and restore validation listeners on every exit<br>19 `33a511a9a2` Route repository and runtime reports through error listeners<br>41 `f8f3810d64` Stop the lexer at a source's end and keep diagnostics that only look like duplicates<br>467 `f97c58e948` Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType()<br>468 `160ba48973` Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `bcf54b569d` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `01c1d2bb7b` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |
| Unused constructor arguments and defaults (open PR #802) | 459 `07e8ef72f0` Report constructor arguments and default values that no constructor uses<br>502 `8d17b5700f` Allow contribution arguments and shorthand defaults that match the explicit constructor<br>524 `3395e838f3` Test that a class without constructors compiles and uses its contribution's arguments | already open on master as #802, with 502 and 524 its review round; they drop out of #693 when #802 merges |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `249ec75c1a` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `65e84d24d0` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `17d544eff7` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `4ef8c974e0` Expose compilation-owned syntax and resolved semantic facts<br>10 `ac3cf2645c` Clarify tooling contracts and isolate partial AST helpers<br>12 `5b445e4c41` Verify compiler output without binary AST dependencies<br>115 `788d45382d` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `708d86ffc0` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `92215b0c30` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `6a142e0411` Complete a member access typed at the end of a line<br>144 `0bcb49ea35` Complete a member with the cursor right after its dot<br>161 `d9e2cdeaea` Complete names and show a call's signature where they are typed at a line's end<br>385 `74d966fe1f` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `a1fbe8b7f9` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 50 now, about 16 after squashing. **Added lines:** 20,247 main, 28,818 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `a61f279298` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `23efc297bb` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `cbf922a39f` Let two run tests check what they claim instead of how long they wait<br>301 `fe6f4dc1fc` Make the retention test's cancellations and the library content test's refresh check independent of timing<br>562 `41666d7cee` Read XdkPullDiagnosticsTest's traced answers in the order their queries came, not the order their ends were written | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `669cc976fe` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `ba9698f71a` Remove the document-color prototype<br>266 `ce6bf485e6` Remove the unused xtc/runnableTargets request<br>387 `7012866854` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `423abecfe9` Keep documents current and the server responsive, and name its own diagnostics<br>28 `f0fe605dad` Judge file events against a running analysis and apply them in order<br>92 `890bca8d63` Analyze again an open document whose analysis was retired without a replacement<br>184 `56abe3576e` Answer requests that a restarted client sends while it opens its files again<br>540 `5eceb78f6b` Answer xtc/tests when a document's close retires the compile it waits for (X342)<br>541 `5bb8bba88e` Keep listed code actions resolvable across a close that leaves a document's text as on disk | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `6f33bb895c` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `e3406e5d54` List a closed test module's tests from the workspace pull's compile<br>237 `476e7c9a0d` Clear a deleted file's diagnostics in the next workspace pull<br>248 `95157ef002` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `2515436c97` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `5b368e0ffe` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `21927d94b5` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `31b91ad3b9` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `19a18ca486` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `30eaf0d2b9` Split the LSP tests into a fast default tier and complete runs<br>143 `32b799d1b2` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `88d117e5d2` Declare fields first and use plain names in the new classes<br>95 `ec23fae95a` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `a0c4379211` Compile a closure as a fold, without mutable accumulators<br>137 `bdc9918a50` Name the properties that hold behavior or mutable state in camel case<br>44 `761e219437` Synchronize the semantic token history that concurrent queries share<br>163 `17a10b2941` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `e74c4938a0` Keep the build's jars whole and its test runs focused<br>97 `00e53a421d` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |
| Time and heap logs of the server's work | 419 `5123ca6b47` Log each project query's and each compile's time and heap<br>420 `13f1a39583` Log what the server did in each minute in which it worked, and over its session | the server's own logs of what each compile, query and minute of work cost |
| Trace less: one end per request, answers only when changed, one start and end per discovery | 494 `5b2f657b81` Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase<br>495 `fe52e26427` Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count<br>496 `8234a69838` Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG | the INFO trace keeps a start and one end with the phases' times; DEBUG keeps the phases and writes a pull's answer only when it differs |
| One xtc/ protocol, shared by the plugin and the servers: lang/protocol | 536 `82a22b8fd7` Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent<br>537 `d3b0bbbab1` Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire<br>538 `210d339a2e` Share the xtc.* keys and a build's summary text through lang/protocol<br>539 `9647e5fcf9` Share the compiler's library settings through lang/protocol: their shape and how a path names a file<br>560 `0012213f67` Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know | the plugin and the servers each kept their own copy of the xtc/ extension's interfaces and wire types, TestEvent, the xtc.* keys, a build's summary text and the library settings, and the copies had drifted; lang/protocol is the one copy (compileOnly on LSP4J and Gson), which the plugin ships as its own jar; 560 lets the plugin's Compiler page open when the library settings hold a key it doesn't know |

Kept as they are:

- 24 `46ccfe7f00` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `ca091e4f22` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `51aebba216` Keep the execution trace's events out of stderr and server.log
- 173 `c7ed338dbe` Let one source that discovery cannot read cost only itself
- 180 `150518c24b` Keep a project's module, source and library order the same in every JVM
- 261 `198464c4ae` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 37 now, about 8 after squashing. **Added lines:** 6,629 main, 7,680 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `ec85295eb2` Complete with the language model's keywords and built-in types<br>162 `e7b25fa321` Complete a type of another module where a statement starts, with its import<br>185 `dac46f17d0` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `013dc9e897` Name a type by what it is when hovering its name<br>156 `7eac698554` Give a constructor's finally block no references lens of its own<br>157 `b72f35dbf8` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `ca5b7485e3` Keep indentation while typing, and lay out switches as the XDK does<br>135 `39e7a751d9` Share the formatter's empty-block skeleton and whitespace edits<br>186 `8820152d3a` Answer on-type formatting without waiting for the change to compile<br>568 `74e290956b` Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7) | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |
| Bind more names in the semantic model | 441 `8b575d8caf` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration<br>442 `ca3da72a82` Make the _ placeholder no occurrence in the semantic model<br>443 `95903b713c` Bind the names of a method's multiple return values to the values they declare<br>444 `0811e25caa` Bind this in a lazy property's calc() and super in a method reference to what they name<br>453 `16c797ee6b` Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares<br>454 `1babd1ea63` Keep the initializer facts of a property declared in a loop's body, such as a static local<br>455 `6d365ded5e` Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration<br>456 `e09e711c4f` Declare the names of a function type's values in the semantic model, as of its return values in a typedef<br>457 `4955020e80` Bind a reserved name such as this:class in a property's accessor, as in a method of the class<br>458 `8ff307f1a6` Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells | each adds a kind of binding; the compiler facts that 441, 443, 444 and 454 add go to X2 |
| The semantic model's censuses, and the bindings they found missing | 481 `2b00866a29` Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence<br>483 `f9309d858a` Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares<br>486 `67f90ec854` Count the dispatch chains that hold an unproven identity in the semantic model's census<br>487 `65f400e3b0` Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder<br>489 `c07a16b01a` Count the bindings that no proof can place in the semantic model's census, and fail on one<br>503 `ea0dc4f81c` Count in the semantic model's census what hover, definition, references and highlights miss at a name<br>504 `f8ea759b89` Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations<br>505 `e2cecf6c15` Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to<br>507 `9e08e2ce76` Count in the editor's census the references whose declaration is in no source of the workspace<br>508 `8c11516f24` Compile the census's libraries as one workspace folder, as an editor that opens the repository does<br>563 `3fbf656481` Bind a named argument of another module's method, as inPlace in freeze(inPlace=True), to its parameter in that module's source<br>564 `34c24e5c47` Bind a formal type that a lambda names, as To in n -> n.toIntN().as(To), to the method's or the class's formal type<br>565 `f563f44a2a` Bind a parameter of a constructor with a finally block that only lambdas in its body name to its declaration<br>566 `5846d98654` Place a label written with unicode escapes where it is written, in its declaration, a break and a continue | the census of the 21 libraries as a heavy test, counting dispatch chains and unplaceable bindings, and the formal children and implicit formals it found unbound; the editor's census of what hover, definition, references and highlights miss, over the repository as one workspace folder, and the parameters of a constructor with a finally block and labels it found unbound |

Kept as they are:

- 102 `35b495a2e7` Link a local's ranges when the caret is just after its name
- 235 `5eaff3a3a1` Fold comments and runs of imports with the compiler adapter
- 347 `c9a544eb3a` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 39 now, about 10 after squashing. **Added lines:** 9,486 main, 13,270 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `bb60cc0bbb` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `a4391c233b` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `8eff243364` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `d5fd568332` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `df1ddd7d77` Prove a code action by compiling only the modules its edit reaches<br>193 `fb9b35c03f` Start a revision's code action compile from the previous one's, and log its cost<br>205 `ab0cd59dbb` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `815dc36bab` Keep the compiles of modules that compiled when only private declarations move<br>437 `518c2e2fc8` Start a rename's proof from the revision's compile, and compile after the edit only what it reaches<br>450 `711f4da076` After a rename's edit, compile only the modules depending on the edited ones that it can reach | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `717233fe9b` Safely delete an unused public or protected member<br>214 `a9b4696250` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `76fd05727f` Extract statements that use an injected local, passing its value<br>177 `ea126a4aa3` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `d6618d5ac9` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `360af1b0ab` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `937159ba6f` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `db639a11f8` Prove a rename past a register the semantic model cannot place<br>394 `1460c6b48c` Refuse a plain rename that also changes the configured source modules, and say why<br>412 `9e60b8b3f9` Prove a rename in a workspace module that replaces a bundled library as the workspace's sources<br>416 `8268b5ad82` Find a text's line breaks once per comparison in a rename's proof, not once per position<br>417 `dc7ad6baf9` Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes<br>418 `15c8dc1d98` Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy<br>460 `09ffc95572` Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `45a1d2c667` Offer code actions in a module that has a const with a toString<br>176 `3e8ec2315e` Offer Create class for the diagnostic of a statement that constructs it<br>211 `b52901a692` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |
| Say what a change to a public declaration reaches outside the workspace | 436 `d536a66bb8` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed<br>445 `52597c7f21` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed<br>569 `1c14a091c7` Say, when a rename leaves string literals and comments that spell the old name, how many and where | renames, safe deletes and file moves say when a public declaration changes for modules outside it |
| A client's cancellation reaches the adapter's work | 438 `6ffd778c67` Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work<br>451 `466b3932cf` Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done | cancelling a rename or xtc/build stops the adapter's work, also once a result is converted |
| Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | 482 `c98ffc14e9` Prove an anonymous class's this and construction by the span of the type it constructs<br>484 `96258bc2f0` Prove a union receiver's call of a method that the runtime implements, as a const's appendTo<br>485 `76be5bb106` Prove a call on a union receiver whose legs share one route, as toString on a Point?<br>488 `9defab311d` Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method<br>490 `56639f1082` Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name<br>506 `dd60d27e62` Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property | an anonymous class's this and constructors by the span and the constructor they pass to, calls whose union legs share a route or a runtime body, methods merged for an annotation into a union, the Referent the compiler adds to a property by its property, and tests that the refusals that must stay are the proof's |

Kept as they are:

- 73 `93b584fe2c` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 78 now, about 28 after squashing. **Added lines:** 10,515 main, 6,766 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `f7221f59c8` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83)<br>498 `d365f0dc5b` Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `5dd347766a` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `2e2abebac1` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `7fe8e78c95` Review the internal LSP4IJ APIs for 0.30.0<br>392 `db99333b72` Name LSP4IJ 0.30.1 in the plugin's README and two test comments<br>398 `a8b05a1f95` Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83)<br>400 `cfe05c3c2f` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `f997957232` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `1ed389d899` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `b0c4e4874b` Let snippet completions insert as they are (UP18)<br>339 `3196c1cf58` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `42bee87d10` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `f21a8dbcb9` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `bf1f0d1aa7` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `42fa3c9ce9` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `2962d52139` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `800a7618f9` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `835b003843` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `5b553e30f8` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `2bc4acf1f1` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `5c60c6fe78` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `2d654086f0` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `ecd5d00626` Drop the change a restart's retired document synchronizer still sends<br>345 `e56d10aafe` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `153e9a9343` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `5030add8e5` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `bcdeb4feb2` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `d824cf55d2` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `bd0455b649` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `52a369f512` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `c624ee9961` Put the embedded build's errors in the Problems window's Project Errors<br>292 `0ce0afab27` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `5e81a3761f` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `08c33cac36` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `02c53dcf72` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `be5f53297a` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `3bb766d03c` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `41c96a93a8` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `8d7e30fc60` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `3dffdbe584` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `8ca06cba00` Give Ecstasy files a PSI of the Ecstasy language<br>153 `90cfd2ba66` Close a block that Enter opens after a { in an Ecstasy file<br>155 `2a05391519` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `145985b937` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `18bcac0d22` Give Ecstasy its Code Style settings page<br>140 `bb656d5333` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `0d0a7a04cb` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `3e70d44d6d` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `4858a208ac` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `e7f13a269a` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `05324b9b63` Keep each light test's stub server and wrappers to that test<br>209 `ea5e50bd2c` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `5d88e9940c` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `17d7e270fd` Keep the stale instrumented classes of an older build off the plugin's unit tests<br>561 `3132b2167d` Hold ClosedFileProblemsTest's later workspace pulls until its next refresh, so that none lands after its build | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `b01afc11be` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `f3ed38636c` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) | Two comments that explain why kept workarounds stay (UP21, UP04). |
| The language server JVM's memory setting | 421 `440b592c64` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive | a setting in both editors with its server side; divide by path |
| Every IntelliJ settings page under one Ecstasy node | 425 `4df94023af` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy<br>427 `7823f64a35` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide | one Ecstasy node in IntelliJ's settings, its pages fitted to the dialog |
| Marks that LSP4IJ made before it connected a file's document (UP86) | 475 `4ac1da2d18` Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86)<br>491 `49e0320947` Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap | refreshes an editor's marks once the connected document holds its own report; lsp4ij#2231 and our fix #2232 would retire it |
| Callers that a write action interrupted in the Call Hierarchy (UP88) | 509 `188ba1b195` Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88)<br>523 `7b58e42542` Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy<br>533 `6093aab714` Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed | LSP4IJ 0.30.1's call hierarchy returns no children when a write action cancels its wait for the server; the provider rethrows the cancellation so IntelliJ asks again; redhat-developer/lsp4ij#2278 would retire it |
| Installable in every JetBrains IDE: no Java dependency, Gradle optional | 530 `73fdef526f` Make the IntelliJ plugin installable in every JetBrains IDE: no Java dependency, and Gradle only where the IDE has it<br>534 `d8d19cc748` Name the plugin's required-dependency test as master's #810 does, with the test runner among them | the plugin required the Java and Gradle plugins, which kept the Marketplace to IntelliJ IDEA, Android Studio and MPS; master's #810 dropped them where nothing used them, and here Gradle's three uses are optional (xtc-gradle.xml); 534 gives the dependency test master's name |

Kept as they are:

- 14 `2bfc0c3e09` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `9b6781f5a4` Restart editor-owned language servers from a project-scoped CLI
- 32 `50246dba08` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `ec571f3663` Cancel IntelliJ requests whose connection ends
- 99 `2ef02b427e` Keep the language server running while a run or debug session uses it
- 130 `148b4d7dab` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `5c80f46191` Build an embedded run's process handler in one place
- 154 `823759d50a` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `bdeb2a4d2c` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `56c1780d78` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 11 now, about 7 after squashing. **Added lines:** 2,695 main, 341 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `284f3ef057` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `7ed78c806c` Say when a move leaves the uses of its sources as they were<br>250 `bf4b012b03` Ask the server again for a move's edits when a change overtook its answer | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `62a29d4825` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `f8b222df79` Compile the VS Code extension's TypeScript into an empty out/<br>272 `cda63b47d8` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |
| npm dependencies of master's #805 | 500 `e9685858ba` Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm | the rebase onto master kept the branch's package.json and lock; this brings in #805's glob 14 and minimatch 11 overrides and regenerates the lock |

Kept as they are:

- 119 `c670d1db0e` List every VS Code command in the extension README, by its own title
- 234 `9325b43a14` Trace the language server's messages when xtc.trace.server says so
- 304 `3eedba476c` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `a87edd2e84` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 55 now, about 23 after squashing. **Added lines:** 10,541 main, 9,770 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `1208c6d2eb` Report a Future callback that throws or that a terminated service drops<br>198 `4cc53f0827` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `ba2d93d631` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `150e82f185` Build and run with the embedded compiler in the server and both editors<br>27 `0e44c52361` Make embedded the default build mode, chosen per editor<br>179 `ca2aa36aae` Build a Gradle-imported project embedded in embedded mode | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `3163a2f081` Let embedded runs read console input in both editors<br>34 `276fc135b5` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `a9db65d5c1` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `758a91df79` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `56ff17d343` Run programs side by side in supervised execution workers<br>33 `c08ab3f6dc` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `6b6803c878` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `7fde51f870` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `20648db97a` Lift the lazy-value time limit for the embedded execution tests' worker<br>464 `aa635db8df` Take the execution worker's events off its standard output, onto a loopback socket of their own | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `9fe3e61529` Let deduction find a named module in src/main/x beside other sources<br>178 `4842112efb` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `5dda613f15` Run and debug a module's xunit tests from the editors, embedded<br>112 `8f6da58e4b` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `c2a16b26f4` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `f59cacc0a4` Rerun only the failed Ecstasy tests from the test console<br>202 `2e1cd67265` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `3753136c58` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `02b254e9d3` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `3a33447592` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `e810fc0dc8` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders<br>428 `92b0b1dfab` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own<br>433 `8bef41b634` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `a4a2e1d5f0` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `1b90e4c737` Pass a run task's args in Gradle and command-line mode<br>236 `e6e40c308e` Run the editor's module from its Run button and context menu | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `0decb86b3a` Don't reuse a module compiled in another copy of the project<br>204 `751e6f5c72` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `05ed97434e` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `165a57084b` Describe running and debugging tests from both editors in the READMEs<br>121 `000fc09f82` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |
| Library replacement in Gradle mode, per workspace folder, with the editors' setting | 406 `ea49f75c71` Build, run and debug with Gradle while the workspace's own modules replace bundled libraries<br>415 `4bd073219d` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries<br>422 `cc1a143dcc` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded<br>423 `ecdfd2614c` Show the embedded compiler as not available where the project's own modules replace bundled libraries | the workspace's own modules replacing bundled libraries: in Gradle mode, kept to the folder that replaces them, the editors' setting, and the embedded compiler shown as not available; the editor parts need L6 and L7, so the group goes to L8 |
| The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | 407 `59d2ce57fb` Name each source set's project by its path in the build tree in the IDE's Gradle model (#774)<br>465 `562c4ac388` Export each project's xtcRun and xtcTest settings in the language server's Gradle model<br>470 `31e194930f` Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's | the plugin's model export that the server reads; 470 exports the JVM arguments that #804's allJvmArgs give |
| Gradle tasks in each module's own project (#774) | 408 `84b8cc34f9` Tell the client each module's Gradle project from the imported build model (#774)<br>409 `d034abf98d` Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774)<br>410 `baadfd1215` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774)<br>411 `5c600b935c` Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) | the server names each module's Gradle project, and both editors run its tasks there, reaching a composite's included builds |
| Embedded runs with their Gradle project's xtcRun and xtcTest settings | 466 `ba160fac63` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors | embedded runs take the Gradle project's method, arguments and JVM options, from 465's export |

Kept as they are:

- 30 `2b7408f310` Find entry methods through the module's type and pass them with their arguments
- 38 `5bfaea3b82` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `5549630b7c` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `ceff2ec26d` Report a stopped run's end without the failure its stop causes
- 199 `be325fb6dd` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `ab09f049e6` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 60 now, about 36 after squashing. **Added lines:** 17,235 main, 15,203 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `ba2cfe1d63` Attach the debugger to a program run with --debug<br>105 `153bb3465c` Move the debugger agent into javatools, with the protocol as records<br>114 `853cdfddf9` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `c2f73ad240` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `d03a2efa7d` Let the debugger find a module's sources where the language server says<br>383 `27270da9f9` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `d6deaf71ed` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it<br>405 `bef5c69b18` Find a debugged module's sources in the workspace when no source graph names it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `b23fad3704` Debug programs from VS Code and IntelliJ through the language server<br>72 `be9a1cab0f` Send a step's response before the stop where the step ends | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `168f12fd6b` Stop at exceptions, pause a running program, and stop on entry<br>75 `ad47f5bbef` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `7dec2a8726` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `b995ed7623` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `472fc335c9` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `79185ffa8f` Let a host give lazy variables' code longer than ON_DEMAND<br>431 `11a3f96670` Let a debug launch or attach set how long evaluations and lazy values may run | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `7b3bc048cd` Restart a stopped frame, which its caller then calls again<br>215 `3ae8ffa957` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `de6a76b286` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `0b77ea36d4` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `e7f14fa47b` Lift SessionDebuggerTest's time limit to an hour<br>166 `7a6d483815` Make SessionDebuggerTest independent of how its threads are scheduled | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `d4cb3b40e9` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `afb77fd141` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `c7d32a561c` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `9be5fda3cb` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `c287d79bf9` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `b7aa73cf37` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `4d9b3658de` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `69c89471e9` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `144cf7704a` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `bfed459938` Debug a command-line run: xtc run runs the program, which connects<br>149 `8156449adc` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `93160c318b` Let VS Code set breakpoints in Ecstasy files<br>232 `6b64fa2d1c` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `5492a8d5b4` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `27aae5704d` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |
| End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | 401 `d17cd9bb20` End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) | LSP4IJ 0.30.1 keeps the exit code from the exited event; one commit |
| Debugged stacks end at the host's fiber; failed requests say why | 439 `2e298af8eb` End a debugged program's stack at the host's fiber, also when it has moved on from its call<br>440 `a31fd30407` Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." | the runtime ends a debugged program's stack at the host's fiber (X4), the DAP server answers a failed request with its reason (L9) |

Kept as they are:

- 47 `744b31e5f5` Show a stopped frame's variables, and step over, into and out of calls
- 49 `269a352cfe` Evaluate watches and hovers on paths in a stopped frame
- 51 `3cba4d6654` Evaluate expressions with operators without running the program's code
- 52 `57855123e0` Leave the runner's frames out of the debugger's stacks and traces
- 53 `ed397c1a78` Run code typed in the Debug Console on the stopped fiber
- 54 `ee120c73bd` Stop at the program's first line from IntelliJ's configuration
- 56 `0a6cc3669f` Place breakpoints on the lines a debugged program was built from
- 59 `89b3fa820e` Show library frames in their bundled source, every fiber, and modules
- 60 `64ff7c85bc` Step into a chosen call of the stopped line
- 61 `29483933ea` Stop where a method of a name is entered
- 63 `97ba9198c2` Stop once a watched field of an object has changed
- 65 `bc8a5ed94c` Answer inline values with the stopped method's variables
- 101 `ccc0afd4ce` Continue, step or pause one fiber while the others stay stopped
- 103 `ea4dcba878` Test that a part of a value's variables from past its end is empty
- 106 `b459830cd4` Keep a debugged task's last output, and X325's queued fiber
- 141 `76f3b079bb` Debug a program that has an interface
- 169 `a65df1a02d` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `2c1cbccf5f` Let a debugger condition name an enum value by its enumeration
- 195 `cabc811d76` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 23 now, about 9 after squashing. **Added lines:** 3,394 main, 3,385 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `8d82acb5d5` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `e1e3f66dcd` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `a901b41a86` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `297ac2d688` Compile a library source's ecstasy-library content with its library, as its copy is | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `b25dabe97e` Report no diagnostics for a library source's read-only copy<br>249 `356aad598c` Compile a library source's copy on its own even once a compile has named it<br>463 `7974261291` Keep diagnostics working when a restart opens another server's library copy again | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `c296eb5da6` Delete the language server's temporary directories once no server needs them<br>277 `9c2c3028ed` Keep a server directory's lock beside it, where reading the directory cannot release it<br>497 `b5d6bcc385` Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `97ead200a6` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `859ea3a8e0` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `1681d21c38` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `0fe7d60df1` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |
| Replaced libraries: their import candidates and their errors | 413 `337ebc4ff5` Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's<br>414 `30525d482a` Say on a replaced library with errors that the server's own compiler may be older than the repository's | two server follow-ups of library replacement |
| Compile only what an edit changes: declaration digests and the member-use tracker | 448 `182be15322` Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies<br>449 `7e4d758277` Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do<br>461 `2d8ce6096e` Digest the API of each declaration of a module apart (DeclarationDigests)<br>462 `e83597f68e` Compile again, after an edit, only the modules that use what it changed (member-use tracker)<br>469 `0bd63247c0` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own | DeclarationDigests in javatools (X2), the artifact API fingerprint and the member-use tracker in the server (L5); 469 fixes 461's Javadoc, fold it in |

Kept as they are:

- 45 `fa7798e2cf` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `9e3e7a97b4` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 140 now, about 22 after squashing. **Added lines:** 3,256 main, 60,165 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `0aede95b40` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `aafa922c2b` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `2921860de3` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `b48d0449bb` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `e6cf39f255` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `4c6a948ab2` Report the written video's real length from the tour composer<br>276 `8bc3a9518c` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `3c76588e41` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `d052954e3c` Record a playbook run's screen as video in the container<br>228 `6b6761e5a6` Delete a playbook run's IDE folder once its logs are copied, in the container<br>535 `6024324e3e` Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `e1bf3089bf` Press the keymap's own Undo key in the IntelliJ tour<br>253 `08ee641713` Undo in the IntelliJ tour from the editor as the change left it<br>255 `ee7bf8b914` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `38ee2f21f8` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `f0d16a9c7d` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `e9e500c470` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `47e986cd74` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `a59bf486eb` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `e7afd940c1` Edit in the tour's kill step only once the restarted server has the file open (UP83)<br>403 `c9393506cd` Check that one server comes back when the tour kills the server's process (UP72)<br>404 `911c09e8fe` Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will<br>447 `02cd417138` Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames<br>476 `f86766e1f8` Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place<br>492 `455f1c54c9` Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does<br>499 `1c429bf7e5` Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `b104954e1b` Record a tour of the VS Code extension that checks what it shows<br>251 `2c9819b517` Make the VS Code tour pass as one whole run, unrecorded<br>257 `2536bf1f1a` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `59dbd6caef` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `efcb90c53b` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `87de944e44` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `05a248bc38` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `12effd3df9` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `c3ce0051dc` Let tests wait for a hang only, not for a fast machine<br>194 `867f4f4633` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `1857e94604` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `43a0e64e13` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `c050bb754c` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `a8e301a02c` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `565a24a3ad` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `4ee1c80901` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `ed7d6e40ea` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `f4c7faae2f` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `dae13c2ef2` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `d212ec4c1e` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `473bd9d444` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `74b707614a` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `12df7622e9` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `4c24a1ce95` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `2dd76d1759` Run the editor playbooks in the container from Gradle, with any engine<br>146 `e2a32b998e` Queue playbook container runs on one machine lock, each with its own export<br>147 `0d1e960967` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `e52e15b185` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3<br>432 `d58b875f02` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode<br>525 `cf9a8cda9d` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume<br>526 `e382b769ed` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share<br>528 `e837686419` Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed<br>529 `ed9dc72011` Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too<br>570 `96173de96e` Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `7d94c0c8ba` Offer server save formatting in IntelliJ (UP02)<br>330 `1fe641af5b` Open bundled library sources as the server's virtual documents (UP25)<br>331 `c9ec160693` Check inline completion's invocation and selection natively (UP26)<br>332 `0223821f1d` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `c849bd06e7` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `370a0e37a3` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `309cc4e8a4` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `974ee898aa` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `6685c64fa8` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `5624fc6984` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `22c5e17a98` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `b16a0207a8` Read a signature without parameters in the playbook<br>351 `79d7e7b632` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `0dc30b829e` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `8a745eb2dc` Check X254's library view through the server<br>354 `ada1a187cb` Read X5's library target from the server until LSP4IJ connects it<br>355 `e7c0f27bb2` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `6e8fe42567` Expect X20's signature without parameters in VS Code<br>365 `b3e914d6b8` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `4fead0cf1c` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `c7b79c8248` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `31297b9da6` Ask X254's definition again when the restarted server retires its analysis<br>376 `f6c7348bdc` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `984c5bc32c` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view<br>399 `4cd64bd557` Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81)<br>402 `4cb33688cf` Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `e6bf6e75e2` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `3309c84900` Send X259's library edit to the old server before the restart<br>87 `ada365dd37` Wait for X257's Invoked request to reach the LSP console<br>88 `9bf95a1fe6` Reopen a popup under inspection after the IDE was deactivated<br>175 `83c453acf8` Give X145 a reference search that waits until it is canceled<br>282 `03a1054d77` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `f0766d5bc2` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `bedfe5cccb` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `3c6f87bca0` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `794b52904c` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open<br>446 `9f08f709a0` Give the VS Code client its own handler of the server's messages back after the playbook's CFG2<br>542 `15eeb8dc74` Open a companion destination in IntelliJ's playbook after the quick fix's edit has changed it (X214) | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `0bcdc811bc` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `3f0a967d46` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `f352609c11` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `362ca48296` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `a647395298` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `1fe53493aa` Check Run to Cursor in both editors<br>64 `94fe753779` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `5a608e1bb6` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `54445fdd6f` Check a document link's Ctrl+Click in IntelliJ's X131 | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `56234c8a81` Give the playbook catalog's size as its 342 cases everywhere<br>122 `57b5ad2074` Point the playbook instructions at the playbook container<br>127 `a6231d6231` List the test and debug-task messages and a test row in the playbook's 18a<br>532 `c5c138f6d8` Drop the retired X273-X275 from the scope that the VS Code playbook's results.json reports | Fold into the catalog's documentation. |
| What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | 473 `c1292bbae2` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered<br>474 `7d9d269330` Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it | the playbook keeps each server's whole trace, with each pull's answer at DEBUG only, and the tour's waits and popup steps say what LSP4IJ holds and what could close them |
| Playbook traces: compressed, kept whole for failures and the newest pass | 493 `61e6b7c80a` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run | the pruning compresses a finished run's traces, keeps the newest 10 failed runs and the newest passing run of each mode whole, and judges pass and fail from results.json |
| Test code shared instead of copied | 543 `b59d63a6ac` Share the IntelliJ playbook executors' repeated steps instead of copying them per case<br>544 `b22f94af81` Share the playbook probe's breakpoint states, variable paths and child nodes<br>545 `c6c9dc5442` Share the IntelliJ tour's repeated test-run, attach and adapter steps<br>546 `5b4a40e40d` Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps<br>547 `266bd39bfe` Share the playbook's semantic-token reads, workspace models and xunit case heads<br>548 `ea3df0c5af` Share the plugin unit tests' disk files, connection, waits and opens through LightEditor<br>549 `4af7f15cc4` Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup<br>550 `1bb4fc68f5` Share the lsp-server adapter tests' source writer and text edit application<br>551 `3d97bf7d44` Share the embedding's incomplete analysis in the header tests, and merge their identical cases<br>552 `227589fd39` Shorten the code action tests with the shared edits and per-class helpers<br>553 `d158e62550` Share an adapter with a module compile hook among the tests that count or hold compiles<br>554 `3224d3fa6b` Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures<br>555 `a57fad023b` Merge FormattingConfigRoundTripTest's mock clients and server setup<br>556 `3dc6a5bced` Apply the missing member, extraction and initializer tests' edits with the shared apply<br>557 `ada2f381eb` Check XdkMemberUseTrackerTest's renames of foo through one helper<br>558 `d8f869a573` Share the marker comment position of the navigation, lookup and occurrence tests<br>559 `62fa0f5e06` Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file | the duplicated test code CPD found in lsp-server, dap-server, the plugin's unit tests, the playbook, its probe and the IntelliJ tour, shared as helpers and fixtures without dropping a check |

Kept as they are:

- 43 `48418d1076` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `0199b0076a` Apply X103's rename as a refactoring and check the member after Undo
- 187 `47259c64b2` Check that Debug of Current File launches an Ecstasy configuration
- 189 `b5a0a300ad` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `72f515c023` Run the VS Code playbook after the extension suite, not with it
- 200 `7dcf364d48` Count the code actions at the tour's unused import and unused local
- 247 `7a0be665f3` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `c202011b8d` Print both compiler playbooks' progress per case in the build's output
- 279 `c404533ca4` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 48 now, about 6 after squashing. **Added lines:** 6,780 main, 376 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `2e132b8e6f` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `6a621c8bc0` Drop the references to documents and plans this branch does not carry<br>69 `67deb5994c` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `f3a39ae16a` Fix lang/README's task paths, versions and feature summary<br>123 `24d6daf154` Use American spelling in the shippable docs<br>124 `68f6b9a8f5` Name testing in the embedded build and run index entry<br>125 `b3d57ff161` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `809096d7e5` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `5688ef5547` Say that every adapter needs Java 25 in editor-setup.md<br>283 `b5be4d35f6` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `5e5fbef4e0` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `ac899d5fd3` Point the docs' line citations at the lines today's changes moved | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `04de77cf73` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `9e875a5da1` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `9b322a039f` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `c1a7270480` Leave the lang build out of the root build by default again<br>379 `d591b02773` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `dcd974c108` Say in AGENTS.md that gradle.properties turns the lang build flags on<br>567 `3e8d920149` Say what MarketplaceListingTest's listing numbers are: a real listing's, and which of them the reader uses | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |
| The user guide, with screenshots from the tours | 424 `0a26a96167` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours<br>426 `896597b098` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run<br>429 `43c004cc0c` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature<br>430 `ecdaf40992` Move the user guide into lang/doc/user-guide, with its screenshots in images<br>434 `6b5b6a351a` Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's<br>435 `ef76bd197c` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting<br>452 `ea2aeea1ef` Say in the user guide what a rename doesn't change or check<br>471 `740001f06a` Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs<br>472 `85cd467c18` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages<br>477 `aa886bb5a5` Mark the user guide's keys up as keys, and the names it left plain as code<br>478 `9bade8b0f0` Add an index of every page and section of the user guide to its landing page<br>479 `4e10268df2` Give the user guide's long sections subsections, and its index the third level<br>480 `ea6bc493fa` Box the user guide's single screenshots with their captions, as its pairs are<br>531 `3a0d79fb5d` Correct the user guide where the playbooks show it wrong, and add links and implement or override | the guide, its testing chapter and its screenshots, made from tagged tour steps |
| Opt-in static analysis of lang: detekt and CPD | 501 `47d167899e` Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript<br>510 `3960f6b8bd` Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings<br>511 `6f9be7e44a` Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too<br>527 `5b596b5c94` Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates | lang's tasks for detekt over its Kotlin and CPD over its Kotlin and TypeScript, reporting only, not in check; the two tools' version pins in the root catalog go to X5 |
| Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | 512 `0e5a03dfdf` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed<br>513 `e8487e3437` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops<br>514 `21bb6432c7` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate<br>515 `b2d7b649d5` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair<br>516 `41989e6507` Share the language server's location requests and edit conversion, and say the semantic tokens setting once<br>517 `6f9d175081` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end<br>518 `e0bb12ba4d` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias<br>519 `5abc036f53` Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over<br>520 `3c26376d06` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode<br>521 `6ce8950eda` Share the language server's cancellation-owning future and its formatter's parenthesis search<br>522 `883524e6c2` Generate TextMate's operator patterns from one list of categories, not seven copies of the same block | detekt's and the duplicate checks' (CPD, IntelliJ's) findings fixed in lang's main code; it touches files of L1-L9, so it goes last, in L10, or is divided by path into each file's PR when the PRs are cut |

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
| 1 | `a471ce114a` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `673083e804` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `545d84a8ca` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `1c6a506e55` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `65e84d24d0` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `4ef8c974e0` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `92215b0c30` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `669cc976fe` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `cae73bddb2` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `ac3cf2645c` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `7be04050f0` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `5b445e4c41` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `19a18ca486` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `2bfc0c3e09` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `708d86ffc0` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `937159ba6f` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `9b6781f5a4` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `a1fbe8b7f9` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `33a511a9a2` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `150e82f185` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `2e132b8e6f` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `0bcdc811bc` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `423abecfe9` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `46ccfe7f00` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `3163a2f081` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `a9db65d5c1` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `0e44c52361` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `f0fe605dad` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `56ff17d343` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `2b7408f310` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `ca5b7485e3` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `50246dba08` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `c08ab3f6dc` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `276fc135b5` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `88d117e5d2` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `ba9698f71a` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `d6618d5ac9` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `5bfaea3b82` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `6b6803c878` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `ec571f3663` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `f8f3810d64` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `e74c4938a0` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `48418d1076` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `761e219437` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `fa7798e2cf` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `b23fad3704` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `744b31e5f5` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `168f12fd6b` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `269a352cfe` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `7dec2a8726` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `3cba4d6654` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `57855123e0` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `ed397c1a78` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `ee120c73bd` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `472fc335c9` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `0a6cc3669f` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `7fde51f870` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `1fe53493aa` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `89b3fa820e` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `64ff7c85bc` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `29483933ea` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `7b3bc048cd` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `97ba9198c2` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `94fe753779` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `bc8a5ed94c` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `5a608e1bb6` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `54445fdd6f` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `6a621c8bc0` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `67deb5994c` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `db639a11f8` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `62a29d4825` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `be9a1cab0f` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `93b584fe2c` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `0199b0076a` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `ad47f5bbef` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `717233fe9b` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `284f3ef057` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `79185ffa8f` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `ba2cfe1d63` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `de6a76b286` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `a61f279298` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `23efc297bb` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `cbf922a39f` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `ecd5d00626` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `e6bf6e75e2` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `3309c84900` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `ada365dd37` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `9bf95a1fe6` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `4d9b3658de` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `12df7622e9` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `0b77ea36d4` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `890bca8d63` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `a8e301a02c` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `4c24a1ce95` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `ec23fae95a` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `144cf7704a` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `00e53a421d` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `0decb86b3a` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `2ef02b427e` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `2dd76d1759` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `ccc0afd4ce` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `35b495a2e7` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `ea4dcba878` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `bfed459938` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `153bb3465c` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `b459830cd4` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `9fe3e61529` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `5dda613f15` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `a0c4379211` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `c2a16b26f4` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `a4a2e1d5f0` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `8f6da58e4b` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `20648db97a` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `853cdfddf9` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `788d45382d` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `5492a8d5b4` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `165a57084b` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `56234c8a81` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `c670d1db0e` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `f3a39ae16a` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `000fc09f82` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `57b5ad2074` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `24d6daf154` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `68f6b9a8f5` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `b3d57ff161` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `809096d7e5` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `a6231d6231` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `5688ef5547` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `27aae5704d` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `148b4d7dab` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `6a142e0411` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `013dc9e897` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `5549630b7c` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `ec85295eb2` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `39e7a751d9` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `5c80f46191` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `bdc9918a50` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `30eaf0d2b9` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `18bcac0d22` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `bb656d5333` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `76f3b079bb` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `e7f14fa47b` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `32b799d1b2` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `0bcb49ea35` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `45a1d2c667` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `e2a32b998e` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `0d1e960967` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `c3ce0051dc` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `8156449adc` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `0aede95b40` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `9e3e7a97b4` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `8ca06cba00` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `90cfd2ba66` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `823759d50a` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `2a05391519` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `7eac698554` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `b72f35dbf8` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `f59cacc0a4` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `d4cb3b40e9` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `17d544eff7` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `d9e2cdeaea` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `e7b25fa321` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `17a10b2941` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `ca091e4f22` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `be5f53297a` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `7a6d483815` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `360af1b0ab` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `76fd05727f` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `a65df1a02d` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `1208c6d2eb` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `d5fd568332` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `51aebba216` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `c7ed338dbe` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `2c1cbccf5f` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `83c453acf8` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `3e8ec2315e` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `ea126a4aa3` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `4842112efb` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `ca2aa36aae` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `150518c24b` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `153e9a9343` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `145985b937` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `b995ed7623` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `56abe3576e` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `dac46f17d0` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `8820152d3a` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `47259c64b2` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `ceff2ec26d` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `b5a0a300ad` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `72f515c023` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `52a369f512` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `df1ddd7d77` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `fb9b35c03f` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `867f4f4633` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `cabc811d76` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `249ec75c1a` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `c7d32a561c` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `4cc53f0827` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `be325fb6dd` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `7dcf364d48` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `6f33bb895c` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `2e1cd67265` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `bb60cc0bbb` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `751e6f5c72` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `ab0cd59dbb` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `3753136c58` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `05324b9b63` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `5b368e0ffe` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `ea5e50bd2c` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `e3406e5d54` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `b52901a692` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `3bb766d03c` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `41c96a93a8` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `a9b4696250` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `3ae8ffa957` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `ab09f049e6` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `f8b222df79` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `b25dabe97e` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `02b254e9d3` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `a4391c233b` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `c624ee9961` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `bcdeb4feb2` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `8d7e30fc60` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `3a33447592` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `c287d79bf9` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `21927d94b5` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `d052954e3c` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `6b6761e5a6` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `aafa922c2b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `2921860de3` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `93160c318b` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `6b64fa2d1c` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `1b90e4c737` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `9325b43a14` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `5eaff3a3a1` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `e6e40c308e` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `476e7c9a0d` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `7ed78c806c` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `ba2d93d631` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `565a24a3ad` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `3f0a967d46` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `b48d0449bb` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `b104954e1b` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `05ed97434e` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `d824cf55d2` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `815dc36bab` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `7a0be665f3` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `95157ef002` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `356aad598c` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `bf4b012b03` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `2c9819b517` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `e1bf3089bf` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `08ee641713` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `3dffdbe584` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `ee7bf8b914` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `38ee2f21f8` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `2536bf1f1a` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `59dbd6caef` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `efcb90c53b` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `f0d16a9c7d` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `198464c4ae` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `e6cf39f255` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `c202011b8d` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `4c6a948ab2` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `1857e94604` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `ce6bf485e6` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `8d82acb5d5` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `e810fc0dc8` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `758a91df79` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `e9e500c470` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `87de944e44` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `cda63b47d8` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `9b322a039f` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `e1e3f66dcd` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `c296eb5da6` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `8bc3a9518c` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `9c2c3028ed` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `a901b41a86` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `c404533ca4` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `297ac2d688` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `f352609c11` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `03a1054d77` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `b5be4d35f6` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `dcd974c108` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `5d88e9940c` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `3c76588e41` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `47e986cd74` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `05a248bc38` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `2515436c97` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `5e5fbef4e0` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `f0766d5bc2` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `0ce0afab27` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `362ca48296` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `5e81a3761f` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `08c33cac36` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `a647395298` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `17d7e270fd` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `31b91ad3b9` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `bdeb2a4d2c` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `4ee1c80901` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `fe6f4dc1fc` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `0d0a7a04cb` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `bedfe5cccb` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `3eedba476c` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `ac899d5fd3` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `ed7d6e40ea` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `3e70d44d6d` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `f4c7faae2f` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `e52e15b185` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `43a0e64e13` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `97ead200a6` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `1681d21c38` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `859ea3a8e0` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `0fe7d60df1` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `04de77cf73` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `9e875a5da1` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `c1a7270480` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `d03a2efa7d` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `dae13c2ef2` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `3c6f87bca0` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `c050bb754c` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `d212ec4c1e` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `473bd9d444` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `74b707614a` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `5dd347766a` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `f997957232` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `1ed389d899` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `b0c4e4874b` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `7d94c0c8ba` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `1fe641af5b` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `c9ec160693` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `0223821f1d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `c849bd06e7` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `370a0e37a3` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `309cc4e8a4` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `974ee898aa` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `6685c64fa8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `5624fc6984` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `3196c1cf58` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `42bee87d10` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `f21a8dbcb9` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `bf1f0d1aa7` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `42fa3c9ce9` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `2962d52139` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `e56d10aafe` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `800a7618f9` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `c9a544eb3a` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `835b003843` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `22c5e17a98` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `b16a0207a8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `79d7e7b632` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `0dc30b829e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `8a745eb2dc` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `ada1a187cb` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `e7c0f27bb2` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `7fe8e78c95` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `56c1780d78` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `6e8fe42567` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `5b553e30f8` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `9be5fda3cb` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `2bc4acf1f1` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `afb77fd141` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `5030add8e5` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `2e2abebac1` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `b3e914d6b8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `69c89471e9` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `b7aa73cf37` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `c2f73ad240` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `bd0455b649` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `4fead0cf1c` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `c7b79c8248` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `5c60c6fe78` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `02c53dcf72` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `8eff243364` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `31297b9da6` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `f6c7348bdc` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `b01afc11be` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `984c5bc32c` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `d591b02773` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `4858a208ac` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `a87edd2e84` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `794b52904c` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `27270da9f9` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `bcf54b569d` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `74d966fe1f` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `01c1d2bb7b` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `7012866854` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `e7f13a269a` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `12effd3df9` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `a59bf486eb` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `e7afd940c1` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `db99333b72` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `2d654086f0` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `1460c6b48c` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `f3ed38636c` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `d6deaf71ed` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `f7221f59c8` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
| 398 | `a8b05a1f95` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83) |
| 399 | `4cd64bd557` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81) |
| 400 | `cfe05c3c2f` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) |
| 401 | `d17cd9bb20` | P10 | — | L9 | End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) |
| 402 | `4cb33688cf` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) |
| 403 | `c9393506cd` | P12 | — | L10 | IntelliJ tour step fixes | Check that one server comes back when the tour kills the server's process (UP72) |
| 404 | `911c09e8fe` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will |
| 405 | `bef5c69b18` | P10 | — | L9 | The debugger finds sources where its host names them | Find a debugged module's sources in the workspace when no source graph names it |
| 406 | `ea49f75c71` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build, run and debug with Gradle while the workspace's own modules replace bundled libraries |
| 407 | `59d2ce57fb` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Name each source set's project by its path in the build tree in the IDE's Gradle model (#774) |
| 408 | `84b8cc34f9` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Tell the client each module's Gradle project from the imported build model (#774) |
| 409 | `d034abf98d` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774) |
| 410 | `baadfd1215` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774) |
| 411 | `5c600b935c` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) |
| 412 | `9e60b8b3f9` | P6 | — | L4 | Rename guards and proofs | Prove a rename in a workspace module that replaces a bundled library as the workspace's sources |
| 413 | `337ebc4ff5` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's |
| 414 | `30525d482a` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Say on a replaced library with errors that the server's own compiler may be older than the repository's |
| 415 | `4bd073219d` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Add an editor setting for whether the workspace's own modules replace bundled XDK libraries |
| 416 | `8268b5ad82` | P6 | — | L4 | Rename guards and proofs | Find a text's line breaks once per comparison in a rename's proof, not once per position |
| 417 | `dc7ad6baf9` | P6 | — | L4 | Rename guards and proofs | Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes |
| 418 | `15c8dc1d98` | P6 | — | L4 | Rename guards and proofs | Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy |
| 419 | `5123ca6b47` | P4 | — | L1, L2 | Time and heap logs of the server's work | Log each project query's and each compile's time and heap |
| 420 | `13f1a39583` | P4 | — | L1 | Time and heap logs of the server's work | Log what the server did in each minute in which it worked, and over its session |
| 421 | `440b592c64` | P7 | — | L6 | The language server JVM's memory setting | Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive |
| 422 | `cc1a143dcc` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded |
| 423 | `ecdfd2614c` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Show the embedded compiler as not available where the project's own modules replace bundled libraries |
| 424 | `0a26a96167` | P13 | — | L10 | The user guide, with screenshots from the tours | Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours |
| 425 | `4df94023af` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy |
| 426 | `896597b098` | P13 | — | L10 | The user guide, with screenshots from the tours | Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run |
| 427 | `7823f64a35` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide |
| 428 | `92b0b1dfab` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own |
| 429 | `43c004cc0c` | P13 | — | L10 | The user guide, with screenshots from the tours | Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature |
| 430 | `ecdaf40992` | P13 | — | L10 | The user guide, with screenshots from the tours | Move the user guide into lang/doc/user-guide, with its screenshots in images |
| 431 | `11a3f96670` | P10 | X4 | L9 | Lazy properties and their time limit | Let a debug launch or attach set how long evaluations and lazy values may run |
| 432 | `d58b875f02` | P12 | X5 | L10 | The playbook container and the IDE it drives | Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode |
| 433 | `8bef41b634` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc |
| 434 | `6b5b6a351a` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's |
| 435 | `ef76bd197c` | P13 | — | L10 | The user guide, with screenshots from the tours | Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting |
| 436 | `d536a66bb8` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed |
| 437 | `518c2e2fc8` | P6 | — | L4 | Prove code actions with incremental compiles | Start a rename's proof from the revision's compile, and compile after the edit only what it reaches |
| 438 | `6ffd778c67` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work |
| 439 | `2e298af8eb` | P10 | X4 | — | Debugged stacks end at the host's fiber; failed requests say why | End a debugged program's stack at the host's fiber, also when it has moved on from its call |
| 440 | `a31fd30407` | P10 | — | L9 | Debugged stacks end at the host's fiber; failed requests say why | Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." |
| 441 | `8b575d8caf` | P5 | X2 | L3 | Bind more names in the semantic model | Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration |
| 442 | `ca3da72a82` | P5 | — | L3 | Bind more names in the semantic model | Make the _ placeholder no occurrence in the semantic model |
| 443 | `95903b713c` | P5 | X2 | L3 | Bind more names in the semantic model | Bind the names of a method's multiple return values to the values they declare |
| 444 | `0811e25caa` | P5 | X2 | L3 | Bind more names in the semantic model | Bind this in a lazy property's calc() and super in a method reference to what they name |
| 445 | `52597c7f21` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed |
| 446 | `9f08f709a0` | P12 | — | L10 | Individual playbook case fixes | Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 |
| 447 | `02cd417138` | P12 | — | L10 | IntelliJ tour step fixes | Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames |
| 448 | `182be15322` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies |
| 449 | `7e4d758277` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do |
| 450 | `711f4da076` | P6 | — | L4 | Prove code actions with incremental compiles | After a rename's edit, compile only the modules depending on the edited ones that it can reach |
| 451 | `466b3932cf` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done |
| 452 | `ea2aeea1ef` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide what a rename doesn't change or check |
| 453 | `16c797ee6b` | P5 | — | L3 | Bind more names in the semantic model | Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares |
| 454 | `1babd1ea63` | P5 | X2 | L3 | Bind more names in the semantic model | Keep the initializer facts of a property declared in a loop's body, such as a static local |
| 455 | `6d365ded5e` | P5 | — | L3 | Bind more names in the semantic model | Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration |
| 456 | `e09e711c4f` | P5 | — | L3 | Bind more names in the semantic model | Declare the names of a function type's values in the semantic model, as of its return values in a typedef |
| 457 | `4955020e80` | P5 | — | L3 | Bind more names in the semantic model | Bind a reserved name such as this:class in a property's accessor, as in a method of the class |
| 458 | `8ff307f1a6` | P5 | — | L3 | Bind more names in the semantic model | Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells |
| 459 | `07e8ef72f0` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Report constructor arguments and default values that no constructor uses |
| 460 | `09ffc95572` | P6 | — | L4 | Rename guards and proofs | Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element |
| 461 | `2d8ce6096e` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Digest the API of each declaration of a module apart (DeclarationDigests) |
| 462 | `e83597f68e` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Compile again, after an edit, only the modules that use what it changed (member-use tracker) |
| 463 | `7974261291` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Keep diagnostics working when a restart opens another server's library copy again |
| 464 | `aa635db8df` | P9 | — | L8 | Supervised execution workers | Take the execution worker's events off its standard output, onto a loopback socket of their own |
| 465 | `562c4ac388` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export each project's xtcRun and xtcTest settings in the language server's Gradle model |
| 466 | `ba160fac63` | P9 | — | L8 | Embedded runs with their Gradle project's xtcRun and xtcTest settings | Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors |
| 467 | `f97c58e948` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType() |
| 468 | `160ba48973` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them |
| 469 | `0bd63247c0` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own |
| 470 | `31e194930f` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's |
| 471 | `740001f06a` | P13 | — | L10 | The user guide, with screenshots from the tours | Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs |
| 472 | `85cd467c18` | P13 | — | L10 | The user guide, with screenshots from the tours | Split the user guide into a landing page, one page per chapter, and features and shortcuts pages |
| 473 | `c1292bbae2` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered |
| 474 | `7d9d269330` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it |
| 475 | `4ac1da2d18` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) |
| 476 | `f86766e1f8` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place |
| 477 | `aa886bb5a5` | P13 | — | L10 | The user guide, with screenshots from the tours | Mark the user guide's keys up as keys, and the names it left plain as code |
| 478 | `9bade8b0f0` | P13 | — | L10 | The user guide, with screenshots from the tours | Add an index of every page and section of the user guide to its landing page |
| 479 | `4e10268df2` | P13 | — | L10 | The user guide, with screenshots from the tours | Give the user guide's long sections subsections, and its index the third level |
| 480 | `ea6bc493fa` | P13 | — | L10 | The user guide, with screenshots from the tours | Box the user guide's single screenshots with their captions, as its pairs are |
| 481 | `2b00866a29` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence |
| 482 | `c98ffc14e9` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove an anonymous class's this and construction by the span of the type it constructs |
| 483 | `f9309d858a` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares |
| 484 | `96258bc2f0` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a union receiver's call of a method that the runtime implements, as a const's appendTo |
| 485 | `76be5bb106` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a call on a union receiver whose legs share one route, as toString on a Point? |
| 486 | `67f90ec854` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the dispatch chains that hold an unproven identity in the semantic model's census |
| 487 | `65f400e3b0` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder |
| 488 | `9defab311d` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method |
| 489 | `c07a16b01a` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the bindings that no proof can place in the semantic model's census, and fail on one |
| 490 | `56639f1082` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name |
| 491 | `49e0320947` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap |
| 492 | `455f1c54c9` | P12 | — | L10 | IntelliJ tour step fixes | Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does |
| 493 | `61e6b7c80a` | P12 | X5 | L10 | Playbook traces: compressed, kept whole for failures and the newest pass | Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run |
| 494 | `5b2f657b81` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase |
| 495 | `fe52e26427` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count |
| 496 | `8234a69838` | P4 | — | L1, L2 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG |
| 497 | `b5d6bcc385` | P11 | — | L5 | Temporary directories and their locks | Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time |
| 498 | `d365f0dc5b` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) |
| 499 | `1c429bf7e5` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does |
| 500 | `e9685858ba` | P8 | — | L7 | npm dependencies of master's #805 | Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm |
| 501 | `47d167899e` | P13 | X5 | L10 | Opt-in static analysis of lang: detekt and CPD | Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript |
| 502 | `8d17b5700f` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Allow contribution arguments and shorthand defaults that match the explicit constructor |
| 503 | `ea0dc4f81c` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the semantic model's census what hover, definition, references and highlights miss at a name |
| 504 | `f8ea759b89` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations |
| 505 | `e2cecf6c15` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to |
| 506 | `dd60d27e62` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property |
| 507 | `9e08e2ce76` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the editor's census the references whose declaration is in no source of the workspace |
| 508 | `8c11516f24` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Compile the census's libraries as one workspace folder, as an editor that opens the repository does |
| 509 | `188ba1b195` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88) |
| 510 | `3960f6b8bd` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings |
| 511 | `6f9be7e44a` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too |
| 512 | `0e5a03dfdf` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Keep the cause of a refused settings change, undo or range format, which detekt found swallowed |
| 513 | `e8487e3437` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops |
| 514 | `21bb6432c7` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate |
| 515 | `b2d7b649d5` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair |
| 516 | `41989e6507` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's location requests and edit conversion, and say the semantic tokens setting once |
| 517 | `6f9d175081` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end |
| 518 | `e0bb12ba4d` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias |
| 519 | `5abc036f53` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over |
| 520 | `3c26376d06` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode |
| 521 | `6ce8950eda` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's cancellation-owning future and its formatter's parenthesis search |
| 522 | `883524e6c2` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Generate TextMate's operator patterns from one list of categories, not seven copies of the same block |
| 523 | `7b58e42542` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy |
| 524 | `3395e838f3` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Test that a class without constructors compiles and uses its contribution's arguments |
| 525 | `cf9a8cda9d` | P12 | — | L10 | The playbook container and the IDE it drives | Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume |
| 526 | `e382b769ed` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share |
| 527 | `5b596b5c94` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates |
| 528 | `e837686419` | P12 | — | L10 | The playbook container and the IDE it drives | Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed |
| 529 | `ed9dc72011` | P12 | — | L10 | The playbook container and the IDE it drives | Fit playbook container runs to a smaller machine: a run's memory at most the machine's, and slots by CPUs too |
| 530 | `73fdef526f` | P7 | — | L6 | Installable in every JetBrains IDE: no Java dependency, Gradle optional | Make the IntelliJ plugin installable in every JetBrains IDE: no Java dependency, and Gradle only where the IDE has it |
| 531 | `3a0d79fb5d` | P13 | — | L10 | The user guide, with screenshots from the tours | Correct the user guide where the playbooks show it wrong, and add links and implement or override |
| 532 | `c5c138f6d8` | P12 | — | L10 | Playbook documentation | Drop the retired X273-X275 from the scope that the VS Code playbook's results.json reports |
| 533 | `6093aab714` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Find the plugin's Call Hierarchy browser in playbook case X39, which UP88's workaround renamed |
| 534 | `d8d19cc748` | P7 | — | L6 | Installable in every JetBrains IDE: no Java dependency, Gradle optional | Name the plugin's required-dependency test as master's #810 does, with the test runner among them |
| 535 | `6024324e3e` | P12 | — | L10 | The IntelliJ tour, its recording and video | Slow the tour videos down for a viewer to follow: clips at a third of their speed, longer holds and reading times |
| 536 | `82a22b8fd7` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Add lang/protocol, the module the IntelliJ plugin and the servers share, with xunit's TestEvent |
| 537 | `d3b0bbbab1` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Move the xtc/ extension's interfaces and types into lang/protocol, as the server puts them on the wire |
| 538 | `210d339a2e` | P4 | — | L1, L2 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Share the xtc.* keys and a build's summary text through lang/protocol |
| 539 | `9647e5fcf9` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Share the compiler's library settings through lang/protocol: their shape and how a path names a file |
| 540 | `5eceb78f6b` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer xtc/tests when a document's close retires the compile it waits for (X342) |
| 541 | `5bb8bba88e` | P4 | — | L1 | Keep documents current and the server responsive | Keep listed code actions resolvable across a close that leaves a document's text as on disk |
| 542 | `15eeb8dc74` | P12 | — | L10 | Individual playbook case fixes | Open a companion destination in IntelliJ's playbook after the quick fix's edit has changed it (X214) |
| 543 | `b59d63a6ac` | P12 | — | L10 | Test code shared instead of copied | Share the IntelliJ playbook executors' repeated steps instead of copying them per case |
| 544 | `b22f94af81` | P12 | — | L10 | Test code shared instead of copied | Share the playbook probe's breakpoint states, variable paths and child nodes |
| 545 | `c6c9dc5442` | P12 | — | L10 | Test code shared instead of copied | Share the IntelliJ tour's repeated test-run, attach and adapter steps |
| 546 | `5b4a40e40d` | P12 | — | L10 | Test code shared instead of copied | Launch XtcDebugServerTest's session through shared helpers instead of repeating the steps |
| 547 | `266bd39bfe` | P12 | — | L10 | Test code shared instead of copied | Share the playbook's semantic-token reads, workspace models and xunit case heads |
| 548 | `ea3df0c5af` | P12 | — | L10 | Test code shared instead of copied | Share the plugin unit tests' disk files, connection, waits and opens through LightEditor |
| 549 | `4af7f15cc4` | P12 | — | L10 | Test code shared instead of copied | Start the lsp-server run tests' debug sessions through one fixture instead of copying the setup |
| 550 | `1bb4fc68f5` | P12 | — | L10 | Test code shared instead of copied | Share the lsp-server adapter tests' source writer and text edit application |
| 551 | `3d97bf7d44` | P12 | — | L10 | Test code shared instead of copied | Share the embedding's incomplete analysis in the header tests, and merge their identical cases |
| 552 | `227589fd39` | P12 | — | L10 | Test code shared instead of copied | Shorten the code action tests with the shared edits and per-class helpers |
| 553 | `d158e62550` | P12 | — | L10 | Test code shared instead of copied | Share an adapter with a module compile hook among the tests that count or hold compiles |
| 554 | `3224d3fa6b` | P12 | — | L10 | Test code shared instead of copied | Share the rename tests' App.x workspace, rename-at-offset and library/consumer fixtures |
| 555 | `a57fad023b` | P12 | — | L10 | Test code shared instead of copied | Merge FormattingConfigRoundTripTest's mock clients and server setup |
| 556 | `3dc6a5bced` | P12 | — | L10 | Test code shared instead of copied | Apply the missing member, extraction and initializer tests' edits with the shared apply |
| 557 | `ada2f381eb` | P12 | — | L10 | Test code shared instead of copied | Check XdkMemberUseTrackerTest's renames of foo through one helper |
| 558 | `d8f869a573` | P12 | — | L10 | Test code shared instead of copied | Share the marker comment position of the navigation, lookup and occurrence tests |
| 559 | `62fa0f5e06` | P12 | — | L10 | Test code shared instead of copied | Fix the ten detekt findings the test deduplication brought: a named analysis, two commented overrides, TestConsole's file |
| 560 | `0012213f67` | P4 | — | L1 | One xtc/ protocol, shared by the plugin and the servers: lang/protocol | Let the IntelliJ plugin's Compiler settings page open when the library settings hold a key it doesn't know |
| 561 | `3132b2167d` | P7 | — | L6 | Light test fixes | Hold ClosedFileProblemsTest's later workspace pulls until its next refresh, so that none lands after its build |
| 562 | `41666d7cee` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Read XdkPullDiagnosticsTest's traced answers in the order their queries came, not the order their ends were written |
| 563 | `3fbf656481` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a named argument of another module's method, as inPlace in freeze(inPlace=True), to its parameter in that module's source |
| 564 | `34c24e5c47` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that a lambda names, as To in n -> n.toIntN().as(To), to the method's or the class's formal type |
| 565 | `f563f44a2a` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a parameter of a constructor with a finally block that only lambdas in its body name to its declaration |
| 566 | `5846d98654` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Place a label written with unicode escapes where it is written, in its declaration, a break and a continue |
| 567 | `3e8d920149` | P13 | X5 | — | Packaging tasks and build defaults | Say what MarketplaceListingTest's listing numbers are: a real listing's, and which of them the reader uses |
| 568 | `74e290956b` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Refill doc and block comment paragraphs that run past the margin, with a wrapComments formatting setting (K7) |
| 569 | `1c14a091c7` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename leaves string literals and comments that spell the old name, how many and where |
| 570 | `96173de96e` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own /out, so that one checkout's runs in two slots keep each other's recording |
