# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 528 commits of `errs/full-testable-lsp-20261009` over `master` (tip `6e8ee381a9`, master `fb7ba82052`, 2026-11-26), numbered 1 to 528 from the oldest, with their short hashes. Commits 1-397 were planned on 2026-11-23; the rebase onto master of 2026-11-25 kept their numbers and gave them new hashes. Commits 398-528 came after, and were placed by the same rules. One is open on master as its own PR: 459, with 502 and 524 its review round, is #802, which leaves #693 when it merges. #804's four commits, 467, 471, 473 and 474 then, merged to master as 2e26663249 on 2026-11-25; #693 was rebased onto it without them, which renumbered the commits after 467. #808's three, 481-483 then, merged as fb7ba82052 on 2026-11-26, and #693 was rebased onto it without them, which renumbered the commits after 480.

It proposes 15 pull requests, each of which changes either only files outside lang/ (xvm only: X1-X5) or only files in lang/ (lang only: L1-L10), merged in one order. To get there, the commits are first sorted into 13 topics (P1-P13), the commits of a topic that implement or fix the same thing, however far apart, are grouped to be joined, and every commit is then divided at lang/: its other files go to its topic's xvm PR, its lang/ files to its topic's lang PR.

## The pull requests, in merge order

| Order | PR | Changes only | Title | Prerequisites | Built from topics | Commits with a part in it | Added lines: main / tests |
|---|---|---|---|---|---|---|---|
| 1 | **X1** | files outside lang/ | Compiler diagnostics: the error-listener contract and repository read failures | — (master) | P1 | 12 | 2.9k / 2.8k |
| 2 | **X2** | files outside lang/ | Compiler host API: compilation outcomes, snapshots, syntax and semantic facts | X1 | P2, P11 | 15 | 5.7k / 1.1k |
| 3 | **X3** | files outside lang/ | Compiler analysis of incomplete source: recovery, cursor fitting, declarations | X2 | P3, P4 | 8 | 5.8k / 1.7k |
| 4 | **X4** | files outside lang/ | Runtime support for running, testing and debugging from the editors: the debugger and its agent, xtc, xunit and the Gradle plugin | X3 | P9, P10 | 55 | 9.5k / 5.2k |
| 5 | **X5** | files outside lang/ | Build, CI and test tooling outside lang: CI test tiers, build checks, test hang detection, the LSP4IJ and IntelliJ versions, PR previews | X4 | P4, P7, P12, P13 | 26 | 1.7k / 985 |
| 6 | **L1** | lang/ | Language server: the server, its protocol, document synchronization, Tree-sitter and the mock adapter | X3, X5 | P4 | 38 | 10.1k / 13.0k |
| 7 | **L2** | lang/ | Language server: the compiler adapter's core, compiling, discovery, diagnostics and workspace pulls | L1 | P1, P2, P3, P4 | 39 | 9.3k / 17.6k |
| 8 | **L3** | lang/ | Language server: the semantic model and read-only editor features | L2 | P5 | 33 | 6.0k / 7.1k |
| 9 | **L4** | lang/ | Language server: code actions and refactorings | L3 | P6 | 39 | 9.3k / 13.1k |
| 10 | **L5** | lang/ | Language server: the XDK's library sources and the XDK workspace | L3 | P11 | 22 | 3.0k / 3.2k |
| 11 | **L6** | lang/ | IntelliJ plugin, on LSP4IJ 0.30.1 | L4, L5, X5 | P7 | 75 | 10.4k / 6.6k |
| 12 | **L7** | lang/ | VS Code extension | L4, L5 | P8 | 12 | 2.7k / 341 |
| 13 | **L8** | lang/ | Embedded build, run and tests in the language server and both editors | X4, L6, L7 | P9 | 49 | 9.6k / 8.8k |
| 14 | **L9** | lang/ | Debugging: the DAP server, debug sessions, launch and attach, and both editors' debug UI | X4, L8 | P10 | 48 | 8.6k / 11.0k |
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 162 | 8.5k / 57.1k |

Already open on master, apart from these: #802 (unused constructor arguments, commits 459, 502 and 524), which leaves #693 when it merges. #804 (runXtc keeps xtcRun's settings) and #808 (the plugin tests' one network download dropped) are merged.

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
| P4 | Language server core: protocol, document sync, compile pipeline, discovery, diagnostics | P3 | yes, once 5 commits' other files move | 42 | 15 | 19.1k / 28.0k |
| P5 | Language server: semantic model and read-only editor features | P4 | yes | 32 | 8 | 6.1k / 7.1k |
| P6 | Language server: code actions and refactorings | P5 | yes | 38 | 10 | 9.3k / 13.1k |
| P7 | IntelliJ plugin, built against LSP4IJ 0.30.1 | P6 | nearly | 74 | 27 | 10.5k / 6.7k |
| P8 | VS Code extension | P6 | yes | 11 | 7 | 2.7k / 341 |
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 55 | 23 | 10.6k / 9.7k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 60 | 36 | 17.2k / 15.2k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 23 | 9 | 3.4k / 3.4k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 118 | 21 | 3.3k / 57.5k |
| P13 | Documentation, build and CI | the PRs it documents | no | 46 | 6 | 6.8k / 372 |
| | **Total** | | | **528** | **172** | |

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

Commit 8, `3acaf0d4ca` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `a84a2c8f6f` | 366 `5f22316c5c` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `c47460fe24` | 345 `b36dac84e1` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `ec9470605f` | 374 `80099dbb0a` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `e7ea45d14d` | 369 `fb2d8579ab` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `4109a579d8` | 367 `37270fb26e` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `3e467b77e4` | 363 `98364c27a6` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `81ca8a31a0` | 373 `f6035bc4b2` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `b760701003` | 362 `4dcd0fc621` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `8f216cbdfa` | 360 `7f0d039a85` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `44dd4fdf30` | 112 `7cd008ef1d` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `aa08cb109d` | 105 `d90b8d9a11` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `3acaf0d4ca` | 36 `11c63f2cb0` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `3acaf0d4ca` | 387 `19883b5da3` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `992bc587ef` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `217f951551` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `d46cc91cec` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `d1aea30100` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `fcea8a89e7` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `cb1fa5a931` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `049410fde0` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `47a48c5814` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `eeb607b90e` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `fdf3fbcdfb` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `d8e2af6fcc` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `10cd011e15` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `1c0946a679` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `8e56ed5af1` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `781751c33e` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `d5f21bc2ab` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `54dbc973b7` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `c6883e0b1a` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `4855cfcf51` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `0477466f61` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `31b3486a82` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `c3e504fae0` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `0b93590719` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `4be63ad02b` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `20f573e570` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `3917a40e81` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `de391fd9b0` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `f42dfb1e53` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `23bb526587` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `eece2b5ee3` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `1157ada596` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `4d3a4a1a54` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `ea588be521` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `570db764cc` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `c56f002a40` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `3b36ca52f6` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `ede3ea5d27` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `27daf7f1bb` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `1872fc15f2` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `320363bb99` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `bcad0787b7` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `35c106b097` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `7d44862f9c` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `a6b273c75b` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `aa08cb109d` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `eb435e7128` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `8572661c79` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `f0a473cd6e` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `836a90ac54` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `d90b8d9a11` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `44dd4fdf30` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `28608d5740` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `d503d98309` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `7cd008ef1d` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `1ceef2b64d` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `ef1c33714d` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `19ccf45100` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `76aae77096` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `42f1fddd56` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `fd042bb210` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `ff1245eda9` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `8850706e98` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `fdaf461e55` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `81ca8a31a0` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `ff6406ac0b` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `ec9470605f` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `ef44f96a47` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `982453dd3f` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `c3d5bb1f07` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `0692b91449` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `d31744402e` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `5a217c8bd8` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `0e3b3e2686` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `ab0a3eea77` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `7021322f4a` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `79a500fabf` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.
- 400 `dfe500d1c7` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82), in P7 (+272; also touches: playbook)
- 410 `c07082e80f` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774), in P9 (+114; also touches: playbook)
- 415 `50ea128648` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries, in P9 (+55; also touches: playbook)
- 421 `0e3dba96e1` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive, in P7 (+141; also touches: playbook, server, vscode)
- 422 `744676f467` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded, in P9 (+220; also touches: playbook)
- 423 `f453d56666` Show the embedded compiler as not available where the project's own modules replace bundled libraries, in P9 (+133; also touches: playbook)
- 424 `46153a73ca` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours, in P13 (+754; also touches: intellij, vscode)
- 425 `d558e9a095` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy, in P7 (+277; also touches: playbook, server)
- 426 `8fec91d67b` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run, in P13 (+193; also touches: intellij, playbook)
- 427 `1f0db784b7` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide, in P7 (+73; also touches: playbook)
- 428 `6d38fc4201` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own, in P9 (+447; also touches: playbook)
- 429 `37d5d73fa9` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature, in P13 (+251; also touches: playbook)
- 430 `9ec501dbb3` Move the user guide into lang/doc/user-guide, with its screenshots in images, in P13 (+996; also touches: intellij, playbook, vscode)
- 431 `9112b55f14` Let a debug launch or attach set how long evaluations and lazy values may run, in P10 (+716; also touches: playbook)
- 432 `29b923ae63` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode, in P12 (+5; also touches: build, intellij)
- 433 `90f691e469` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc, in P9 (+485; also touches: playbook)
- 435 `44cbf3ede6` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting, in P13 (+125; also touches: playbook)
- 436 `71225c7dd5` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed, in P6 (+589; also touches: intellij, playbook, vscode)
- 441 `2043b408dd` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration, in P5 (+110; also touches: javatools)
- 443 `9f9f80c4c4` Bind the names of a method's multiple return values to the values they declare, in P5 (+65; also touches: javatools)
- 444 `d342a434a5` Bind this in a lazy property's calc() and super in a method reference to what they name, in P5 (+147; also touches: javatools)
- 445 `dc2b9be3ab` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed, in P6 (+275; also touches: playbook)
- 454 `e1394ddc89` Keep the initializer facts of a property declared in a loop's body, such as a static local, in P5 (+47; also touches: javatools)
- 459 `2c24fb20c0` Report constructor arguments and default values that no constructor uses, in P1 (+129; also touches: xdk)
- 461 `32413601d1` Digest the API of each declaration of a module apart (DeclarationDigests), in P11 (+622; also touches: javatools)
- 463 `79b0354b35` Keep diagnostics working when a restart opens another server's library copy again, in P11 (+294; also touches: playbook)
- 466 `616923df49` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors, in P9 (+1,453; also touches: playbook)
- 469 `9606f98b96` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own, in P11 (+1; also touches: javatools)
- 472 `4c2133388c` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages, in P13 (+1,264; also touches: intellij)
- 473 `6527cf1f9b` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered, in P12 (+141; also touches: intellij, server)
- 493 `07ed1be157` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run, in P12 (+367; also touches: build, intellij, vscode)
- 502 `4a746eb12e` Allow contribution arguments and shorthand defaults that match the explicit constructor, in P1 (+400; also touches: xdk)
- 512 `f2a93b4846` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed, in P13 (+17; also touches: intellij, server)
- 513 `a56f748564` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops, in P13 (+16; also touches: server)
- 514 `1902fe1b37` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate, in P13 (+2; also touches: server)
- 515 `d4ece11d83` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair, in P13 (+60; also touches: intellij)
- 516 `4fe7772dc1` Share the language server's location requests and edit conversion, and say the semantic tokens setting once, in P13 (+32; also touches: server)
- 517 `6498c282ce` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end, in P13 (+40; also touches: server)
- 518 `7891e3e0bb` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias, in P13 (+68; also touches: server)
- 520 `9030a62ed2` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode, in P13 (+7; also touches: vscode)
- 521 `f0a118b838` Share the language server's cancellation-owning future and its formatter's parenthesis search, in P13 (+23; also touches: server)
- 525 `dcf39f1672` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume, in P12 (+57; also touches: intellij)
- 526 `a2a2dd782d` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share, in P12 (+14; also touches: intellij)

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
| Keep, cache and report module repository read failures | 1 `bbd50129ce` Preserve module repository read failures without blocking usable candidates<br>9 `d1aea30100` Report repository read attempts without inferring dependency failures<br>11 `cb1fa5a931` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `4241a9d32d` Separate diagnostic reporting from listener abort policy<br>3 `5b48062dfe` Require explicit compiler listeners and reasons for silence<br>4 `6aecc7106c` Scope parser diagnostics and restore validation listeners on every exit<br>19 `1b729a944a` Route repository and runtime reports through error listeners<br>41 `213b8bb002` Stop the lexer at a source's end and keep diagnostics that only look like duplicates<br>467 `b0ed28d07e` Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType()<br>468 `fa48926084` Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `197c7ce1f0` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `e0839181e9` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |
| Unused constructor arguments and defaults (open PR #802) | 459 `2c24fb20c0` Report constructor arguments and default values that no constructor uses<br>502 `4a746eb12e` Allow contribution arguments and shorthand defaults that match the explicit constructor<br>524 `f91f0f8842` Test that a class without constructors compiles and uses its contribution's arguments | already open on master as #802, with 502 and 524 its review round; they drop out of #693 when #802 merges |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `39f24f2e3d` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `992bc587ef` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `bceb1f2f98` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `217f951551` Expose compilation-owned syntax and resolved semantic facts<br>10 `fcea8a89e7` Clarify tooling contracts and isolate partial AST helpers<br>12 `f856e99fe4` Verify compiler output without binary AST dependencies<br>115 `a44d0592e0` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `049410fde0` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `d46cc91cec` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `72cbfb4a8a` Complete a member access typed at the end of a line<br>144 `8c45e082ce` Complete a member with the cursor right after its dot<br>161 `76aae77096` Complete names and show a call's signature where they are typed at a line's end<br>385 `99f601f387` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `fdf3fbcdfb` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 42 now, about 15 after squashing. **Added lines:** 19,039 main, 28,014 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `2ed4668b4e` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `e979c6d1ef` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `f99737a874` Let two run tests check what they claim instead of how long they wait<br>301 `02f7129868` Make the retention test's cancellations and the library content test's refresh check independent of timing | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `3acaf0d4ca` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `11c63f2cb0` Remove the document-color prototype<br>266 `a0eb34a5c3` Remove the unused xtc/runnableTargets request<br>387 `19883b5da3` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `1c0946a679` Keep documents current and the server responsive, and name its own diagnostics<br>28 `4f10232307` Judge file events against a running analysis and apply them in order<br>92 `21c51c2d55` Analyze again an open document whose analysis was retired without a replacement<br>184 `586c00813b` Answer requests that a restarted client sends while it opens its files again | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `7fdc04e34d` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `1d1d4dac47` List a closed test module's tests from the workspace pull's compile<br>237 `cb400e3793` Clear a deleted file's diagnostics in the next workspace pull<br>248 `127b14d8f6` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `9383e651d9` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `9763554c72` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `840829374a` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `cc433a9e98` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `2a29244de5` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `66afbdefad` Split the LSP tests into a fast default tier and complete runs<br>143 `9c2cae7f30` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `0477466f61` Declare fields first and use plain names in the new classes<br>95 `2306808c58` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `0be36a312d` Compile a closure as a fold, without mutable accumulators<br>137 `b60a49bac4` Name the properties that hold behavior or mutable state in camel case<br>44 `7862604e0d` Synchronize the semantic token history that concurrent queries share<br>163 `6382f8a652` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `4be63ad02b` Keep the build's jars whole and its test runs focused<br>97 `88a31d2a78` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |
| Time and heap logs of the server's work | 419 `8df085cf6d` Log each project query's and each compile's time and heap<br>420 `49ded20b5e` Log what the server did in each minute in which it worked, and over its session | the server's own logs of what each compile, query and minute of work cost |
| Trace less: one end per request, answers only when changed, one start and end per discovery | 494 `266814c630` Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase<br>495 `15171f4e77` Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count<br>496 `122e893eb6` Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG | the INFO trace keeps a start and one end with the phases' times; DEBUG keeps the phases and writes a pull's answer only when it differs |

Kept as they are:

- 24 `54d400b4dc` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `42f1fddd56` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `37317acf77` Keep the execution trace's events out of stderr and server.log
- 173 `27ca396224` Let one source that discovery cannot read cost only itself
- 180 `7062c6a4bd` Keep a project's module, source and library order the same in every JVM
- 261 `bb9a0b37e8` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 32 now, about 8 after squashing. **Added lines:** 6,077 main, 7,114 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `be0c88cbf9` Complete with the language model's keywords and built-in types<br>162 `c816423d66` Complete a type of another module where a statement starts, with its import<br>185 `06adceaf1b` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `6ab8c3de37` Name a type by what it is when hovering its name<br>156 `7add8b1385` Give a constructor's finally block no references lens of its own<br>157 `5a331cc495` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `c6883e0b1a` Keep indentation while typing, and lay out switches as the XDK does<br>135 `ff5def42b4` Share the formatter's empty-block skeleton and whitespace edits<br>186 `5c2da6b8d9` Answer on-type formatting without waiting for the change to compile | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |
| Bind more names in the semantic model | 441 `2043b408dd` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration<br>442 `803c777033` Make the _ placeholder no occurrence in the semantic model<br>443 `9f9f80c4c4` Bind the names of a method's multiple return values to the values they declare<br>444 `d342a434a5` Bind this in a lazy property's calc() and super in a method reference to what they name<br>453 `3835882b93` Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares<br>454 `e1394ddc89` Keep the initializer facts of a property declared in a loop's body, such as a static local<br>455 `5654db07ad` Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration<br>456 `cd5790e82e` Declare the names of a function type's values in the semantic model, as of its return values in a typedef<br>457 `1b6ab24cda` Bind a reserved name such as this:class in a property's accessor, as in a method of the class<br>458 `47ed5ab915` Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells | each adds a kind of binding; the compiler facts that 441, 443, 444 and 454 add go to X2 |
| The semantic model's censuses, and the bindings they found missing | 481 `7c2a268023` Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence<br>483 `7cb276a8bc` Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares<br>486 `1298a24dcb` Count the dispatch chains that hold an unproven identity in the semantic model's census<br>487 `f79a8adb7e` Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder<br>489 `63b67a2278` Count the bindings that no proof can place in the semantic model's census, and fail on one<br>503 `fab927046b` Count in the semantic model's census what hover, definition, references and highlights miss at a name<br>504 `743f6944a3` Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations<br>505 `938326f32e` Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to<br>507 `6811b1e41c` Count in the editor's census the references whose declaration is in no source of the workspace<br>508 `5390b9e736` Compile the census's libraries as one workspace folder, as an editor that opens the repository does | the census of the 21 libraries as a heavy test, counting dispatch chains and unplaceable bindings, and the formal children and implicit formals it found unbound; the editor's census of what hover, definition, references and highlights miss, over the repository as one workspace folder, and the parameters of a constructor with a finally block and labels it found unbound |

Kept as they are:

- 102 `228f941aaf` Link a local's ranges when the caret is just after its name
- 235 `6a5938bb36` Fold comments and runs of imports with the compiler adapter
- 347 `6cdfc58d9c` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 38 now, about 10 after squashing. **Added lines:** 9,361 main, 13,163 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `ec9470605f` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `9451282e27` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `80099dbb0a` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `ff1245eda9` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `71667f51ae` Prove a code action by compiling only the modules its edit reaches<br>193 `de8fad9ab8` Start a revision's code action compile from the previous one's, and log its cost<br>205 `d4d06fd07d` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `fc9ee644e0` Keep the compiles of modules that compiled when only private declarations move<br>437 `dc61e61ee7` Start a rename's proof from the revision's compile, and compile after the edit only what it reaches<br>450 `805c1e65e2` After a rename's edit, compile only the modules depending on the edited ones that it can reach | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `91c66df3cd` Safely delete an unused public or protected member<br>214 `4fcf1f84bd` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `53aba7fede` Extract statements that use an injected local, passing its value<br>177 `5570446e65` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `31b3486a82` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `353251e709` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `47a48c5814` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `43b19b2e0d` Prove a rename past a register the semantic model cannot place<br>394 `2e75a9ff84` Refuse a plain rename that also changes the configured source modules, and say why<br>412 `8967f3f90c` Prove a rename in a workspace module that replaces a bundled library as the workspace's sources<br>416 `a0ca9f5a5d` Find a text's line breaks once per comparison in a rename's proof, not once per position<br>417 `aff8ec0537` Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes<br>418 `5825f40802` Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy<br>460 `9a3856f2ba` Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `82000ffa28` Offer code actions in a module that has a const with a toString<br>176 `af943787c7` Offer Create class for the diagnostic of a statement that constructs it<br>211 `54d79ab46f` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |
| Say what a change to a public declaration reaches outside the workspace | 436 `71225c7dd5` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed<br>445 `dc2b9be3ab` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed | renames, safe deletes and file moves say when a public declaration changes for modules outside it |
| A client's cancellation reaches the adapter's work | 438 `6713328b8d` Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work<br>451 `92d12985fc` Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done | cancelling a rename or xtc/build stops the adapter's work, also once a result is converted |
| Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | 482 `bf11748204` Prove an anonymous class's this and construction by the span of the type it constructs<br>484 `d4a9fa96db` Prove a union receiver's call of a method that the runtime implements, as a const's appendTo<br>485 `6cb431e844` Prove a call on a union receiver whose legs share one route, as toString on a Point?<br>488 `6792b111ae` Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method<br>490 `f7a679e1ed` Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name<br>506 `3224b9de22` Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property | an anonymous class's this and constructors by the span and the constructor they pass to, calls whose union legs share a route or a runtime body, methods merged for an annotation into a union, the Referent the compiler adds to a property by its property, and tests that the refusals that must stay are the proof's |

Kept as they are:

- 73 `a6b273c75b` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 74 now, about 27 after squashing. **Added lines:** 10,416 main, 6,714 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `705f64f6f5` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83)<br>498 `fe79b2d484` Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `f484685c5a` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `a4839298ef` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `1399bb16a4` Review the internal LSP4IJ APIs for 0.30.0<br>392 `8339aa2c8e` Name LSP4IJ 0.30.1 in the plugin's README and two test comments<br>398 `706d3d5a82` Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83)<br>400 `dfe500d1c7` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `0ca226c58a` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `2e1b4e21a4` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `5559fa9860` Let snippet completions insert as they are (UP18)<br>339 `3d2d9af23f` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `42bd9b3187` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `6469c85252` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `bde8e85803` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `26bc900989` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `b79f3b27d7` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `898412b040` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `1993b67731` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `966a2e0d0c` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `39b5c4d868` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `6e2e43c58a` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `204ead6de4` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `c47460fe24` Drop the change a restart's retired document synchronizer still sends<br>345 `b36dac84e1` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `3e467b77e4` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `98364c27a6` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `0e786506a8` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `e7ea45d14d` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `fb2d8579ab` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `81ca8a31a0` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `ae4061da98` Put the embedded build's errors in the Problems window's Project Errors<br>292 `6084715202` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `8e8cad47fb` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `de034816ea` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `f6035bc4b2` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `4ca0eb4d2a` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `f8ce9bbae7` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `5f4275694c` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `e5c5af549b` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `3d83688976` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `1df2d68ccb` Give Ecstasy files a PSI of the Ecstasy language<br>153 `654e51865e` Close a block that Enter opens after a { in an Ecstasy file<br>155 `5996c5b2c9` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `a1d09b93ce` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `70f2941c46` Give Ecstasy its Code Style settings page<br>140 `a8193a2978` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `999105a1b4` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `470faec78e` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `6150ee6889` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `79a500fabf` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `2b592d0085` Keep each light test's stub server and wrappers to that test<br>209 `9e2aec20bf` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `a589baf1b1` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `945e04eee9` Keep the stale instrumented classes of an older build off the plugin's unit tests | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `6f42b8c79b` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `51bc7dad24` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) | Two comments that explain why kept workarounds stay (UP21, UP04). |
| The language server JVM's memory setting | 421 `0e3dba96e1` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive | a setting in both editors with its server side; divide by path |
| Every IntelliJ settings page under one Ecstasy node | 425 `d558e9a095` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy<br>427 `1f0db784b7` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide | one Ecstasy node in IntelliJ's settings, its pages fitted to the dialog |
| Marks that LSP4IJ made before it connected a file's document (UP86) | 475 `071d15b31c` Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86)<br>491 `bdb3ed8101` Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap | refreshes an editor's marks once the connected document holds its own report; lsp4ij#2231 and our fix #2232 would retire it |
| Callers that a write action interrupted in the Call Hierarchy (UP88) | 509 `67235f011c` Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88)<br>523 `2c4e48e7bd` Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy | LSP4IJ 0.30.1's call hierarchy returns no children when a write action cancels its wait for the server; the provider rethrows the cancellation so IntelliJ asks again; redhat-developer/lsp4ij#2278 would retire it |

Kept as they are:

- 14 `a7ca9203cf` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `eeb607b90e` Restart editor-owned language servers from a project-scoped CLI
- 32 `25f018eca5` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `b365fa52af` Cancel IntelliJ requests whose connection ends
- 99 `0ffc5a1ab4` Keep the language server running while a run or debug session uses it
- 130 `9140d61655` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `98c88fab81` Build an embedded run's process handler in one place
- 154 `19ccf45100` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `09f3e9efae` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `86035ba960` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 11 now, about 7 after squashing. **Added lines:** 2,695 main, 341 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `017510ac54` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `1a4bcb7fc5` Say when a move leaves the uses of its sources as they were<br>250 `1655484565` Ask the server again for a move's edits when a change overtook its answer | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `aa2d045a14` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `dd61720b70` Compile the VS Code extension's TypeScript into an empty out/<br>272 `c544054788` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |
| npm dependencies of master's #805 | 500 `d042ec2845` Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm | the rebase onto master kept the branch's package.json and lock; this brings in #805's glob 14 and minimatch 11 overrides and regenerates the lock |

Kept as they are:

- 119 `7f8452ac36` List every VS Code command in the extension README, by its own title
- 234 `8335e435b9` Trace the language server's messages when xtc.trace.server says so
- 304 `553a1f3da0` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `caaadaaf0d` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 55 now, about 23 after squashing. **Added lines:** 10,541 main, 9,770 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `d9e8c29798` Report a Future callback that throws or that a terminated service drops<br>198 `b863c4143b` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `f3974fbd33` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `d8e2af6fcc` Build and run with the embedded compiler in the server and both editors<br>27 `781751c33e` Make embedded the default build mode, chosen per editor<br>179 `9e45f95e64` Build a Gradle-imported project embedded in embedded mode | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `8e56ed5af1` Let embedded runs read console input in both editors<br>34 `4855cfcf51` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `a51f4aab76` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `2be99a6f00` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `d5f21bc2ab` Run programs side by side in supervised execution workers<br>33 `249c1ba748` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `0b93590719` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `c56f002a40` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `cac29b15b6` Lift the lazy-value time limit for the embedded execution tests' worker<br>464 `84809067a1` Take the execution worker's events off its standard output, onto a loopback socket of their own | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `a36a16ecc4` Let deduction find a named module in src/main/x beside other sources<br>178 `fdaf461e55` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `44dd4fdf30` Run and debug a module's xunit tests from the editors, embedded<br>112 `7cd008ef1d` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `28608d5740` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `ca61569f3b` Rerun only the failed Ecstasy tests from the test console<br>202 `23148e75bf` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `9d5b965d2b` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `28e6039b6c` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `3c201c4c91` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `4d9eea7dc5` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders<br>428 `6d38fc4201` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own<br>433 `90f691e469` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `d503d98309` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `c711f8f134` Pass a run task's args in Gradle and command-line mode<br>236 `b6fcac2306` Run the editor's module from its Run button and context menu | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `5e071fadfc` Don't reuse a module compiled in another copy of the project<br>204 `8cc62ffa91` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `f5aeba7365` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `77428294e8` Describe running and debugging tests from both editors in the READMEs<br>121 `694fa223b7` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |
| Library replacement in Gradle mode, per workspace folder, with the editors' setting | 406 `35f90dbf84` Build, run and debug with Gradle while the workspace's own modules replace bundled libraries<br>415 `50ea128648` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries<br>422 `744676f467` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded<br>423 `f453d56666` Show the embedded compiler as not available where the project's own modules replace bundled libraries | the workspace's own modules replacing bundled libraries: in Gradle mode, kept to the folder that replaces them, the editors' setting, and the embedded compiler shown as not available; the editor parts need L6 and L7, so the group goes to L8 |
| The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | 407 `cc21092389` Name each source set's project by its path in the build tree in the IDE's Gradle model (#774)<br>465 `61fe7b63ce` Export each project's xtcRun and xtcTest settings in the language server's Gradle model<br>470 `5766adfc03` Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's | the plugin's model export that the server reads; 470 exports the JVM arguments that #804's allJvmArgs give |
| Gradle tasks in each module's own project (#774) | 408 `b95c5704e4` Tell the client each module's Gradle project from the imported build model (#774)<br>409 `60be693111` Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774)<br>410 `c07082e80f` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774)<br>411 `be1f52c0ae` Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) | the server names each module's Gradle project, and both editors run its tasks there, reaching a composite's included builds |
| Embedded runs with their Gradle project's xtcRun and xtcTest settings | 466 `616923df49` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors | embedded runs take the Gradle project's method, arguments and JVM options, from 465's export |

Kept as they are:

- 30 `54dbc973b7` Find entry methods through the module's type and pass them with their arguments
- 38 `c3e504fae0` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `0d0e59664d` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `f49141cb99` Report a stopped run's end without the failure its stop causes
- 199 `a4a980163e` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `bfcc10c1a2` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 60 now, about 36 after squashing. **Added lines:** 17,235 main, 15,203 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `aa08cb109d` Attach the debugger to a program run with --debug<br>105 `d90b8d9a11` Move the debugger agent into javatools, with the protocol as records<br>114 `90bf8f0669` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `85afb62557` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `53f3b1ad1d` Let the debugger find a module's sources where the language server says<br>383 `225b1d46db` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `dac265afb6` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it<br>405 `9fa428609b` Find a debugged module's sources in the workspace when no source graph names it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `3917a40e81` Debug programs from VS Code and IntelliJ through the language server<br>72 `af7b74be7f` Send a step's response before the stop where the step ends | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `f42dfb1e53` Stop at exceptions, pause a running program, and stop on entry<br>75 `471e05695b` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `eece2b5ee3` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `5848616ad6` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `ea588be521` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `51289da08e` Let a host give lazy variables' code longer than ON_DEMAND<br>431 `9112b55f14` Let a debug launch or attach set how long evaluations and lazy values may run | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `320363bb99` Restart a stopped frame, which its caller then calls again<br>215 `ca404feca1` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `571de71ad7` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `2c554a1902` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `e883dfc7ea` Lift SessionDebuggerTest's time limit to an hour<br>166 `fd042bb210` Make SessionDebuggerTest independent of how its threads are scheduled | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `b760701003` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `4dcd0fc621` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `8f216cbdfa` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `7f0d039a85` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `4109a579d8` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `37270fb26e` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `a84a2c8f6f` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `5f22316c5c` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `8572661c79` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `836a90ac54` Debug a command-line run: xtc run runs the program, which connects<br>149 `c00662b22c` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `4738e5d32f` Let VS Code set breakpoints in Ecstasy files<br>232 `f55ce0657b` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `4e0b559b91` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `6321ab7d59` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |
| End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | 401 `59fe726369` End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) | LSP4IJ 0.30.1 keeps the exit code from the exited event; one commit |
| Debugged stacks end at the host's fiber; failed requests say why | 439 `32afed96ca` End a debugged program's stack at the host's fiber, also when it has moved on from its call<br>440 `0e43640055` Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." | the runtime ends a debugged program's stack at the host's fiber (X4), the DAP server answers a failed request with its reason (L9) |

Kept as they are:

- 47 `de391fd9b0` Show a stopped frame's variables, and step over, into and out of calls
- 49 `23bb526587` Evaluate watches and hovers on paths in a stopped frame
- 51 `1157ada596` Evaluate expressions with operators without running the program's code
- 52 `8f5e15b9c5` Leave the runner's frames out of the debugger's stacks and traces
- 53 `4d3a4a1a54` Run code typed in the Debug Console on the stopped fiber
- 54 `86a1a5dab8` Stop at the program's first line from IntelliJ's configuration
- 56 `570db764cc` Place breakpoints on the lines a debugged program was built from
- 59 `ede3ea5d27` Show library frames in their bundled source, every fiber, and modules
- 60 `27daf7f1bb` Step into a chosen call of the stopped line
- 61 `1872fc15f2` Stop where a method of a name is entered
- 63 `bcad0787b7` Stop once a watched field of an object has changed
- 65 `7d44862f9c` Answer inline values with the stopped method's variables
- 101 `f0a473cd6e` Continue, step or pause one fiber while the others stay stopped
- 103 `ab16329c87` Test that a part of a value's variables from past its end is empty
- 106 `a1a156d6d3` Keep a debugged task's last output, and X325's queued fiber
- 141 `56adc727e7` Debug a program that has an interface
- 169 `3f163d92a7` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `8850706e98` Let a debugger condition name an enum value by its enumeration
- 195 `4e173639e2` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 23 now, about 9 after squashing. **Added lines:** 3,394 main, 3,385 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `a2738e5611` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `5a217c8bd8` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `0e3b3e2686` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `b0cb727887` Compile a library source's ecstasy-library content with its library, as its copy is | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `bf3f6a5160` Report no diagnostics for a library source's read-only copy<br>249 `6c86144fc4` Compile a library source's copy on its own even once a compile has named it<br>463 `79b0354b35` Keep diagnostics working when a restart opens another server's library copy again | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `306810c921` Delete the language server's temporary directories once no server needs them<br>277 `9f31f3550f` Keep a server directory's lock beside it, where reading the directory cannot release it<br>497 `90ab684344` Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `1e6e0dc127` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `cae660d034` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `291c559e0a` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `73e8d5fe49` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |
| Replaced libraries: their import candidates and their errors | 413 `4142d2ee0d` Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's<br>414 `9d69632f72` Say on a replaced library with errors that the server's own compiler may be older than the repository's | two server follow-ups of library replacement |
| Compile only what an edit changes: declaration digests and the member-use tracker | 448 `ad1d9eba35` Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies<br>449 `3f9aa546c6` Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do<br>461 `32413601d1` Digest the API of each declaration of a module apart (DeclarationDigests)<br>462 `68b0bf06a0` Compile again, after an edit, only the modules that use what it changed (member-use tracker)<br>469 `9606f98b96` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own | DeclarationDigests in javatools (X2), the artifact API fingerprint and the member-use tracker in the server (L5); 469 fixes 461's Javadoc, fold it in |

Kept as they are:

- 45 `39022f443a` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `b6b07c7232` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 118 now, about 21 after squashing. **Added lines:** 3,208 main, 57,631 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `ef1c33714d` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `ef44f96a47` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `982453dd3f` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `25d18c8cf4` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `f856cf0652` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `680d225b78` Report the written video's real length from the tour composer<br>276 `693f3a30a5` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `9600e578bc` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `cdef78fa45` Record a playbook run's screen as video in the container<br>228 `74d049db6a` Delete a playbook run's IDE folder once its logs are copied, in the container | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `552af747cf` Press the keymap's own Undo key in the IntelliJ tour<br>253 `5d145f00c4` Undo in the IntelliJ tour from the editor as the change left it<br>255 `9122b78a81` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `0298896fb3` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `fe70f045f2` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `814a7d95f5` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `56c0977fe3` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `908a02449a` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `27478e37a8` Edit in the tour's kill step only once the restarted server has the file open (UP83)<br>403 `e7dcde02ae` Check that one server comes back when the tour kills the server's process (UP72)<br>404 `0c44140333` Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will<br>447 `e8b212edcf` Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames<br>476 `6aba24cabf` Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place<br>492 `8f1b46c770` Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does<br>499 `7b8bece3c3` Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `d31744402e` Record a tour of the VS Code extension that checks what it shows<br>251 `449856a934` Make the VS Code tour pass as one whole run, unrecorded<br>257 `27aa1e3ada` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `9abbf6073e` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `5d84d0ec93` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `7062d849aa` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `242821733e` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `5f7702f7a1` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `1ceef2b64d` Let tests wait for a hang only, not for a fast machine<br>194 `ff6406ac0b` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `6d4ae4d0dc` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `c02232d71b` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `2d9d9df482` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `eb435e7128` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `c3d5bb1f07` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `400bdb9a11` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `f4a67139c9` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `6fff324ef9` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `7021322f4a` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `8f21d72b0b` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `9c8d5131ed` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `05e25274e1` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `7e80e3b93d` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `4e2058c2f4` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `56ae0fccf2` Run the editor playbooks in the container from Gradle, with any engine<br>146 `50513b438e` Queue playbook container runs on one machine lock, each with its own export<br>147 `f92c3cf6f5` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `ab0a3eea77` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3<br>432 `29b923ae63` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode<br>525 `dcf39f1672` Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume<br>526 `a2a2dd782d` Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share<br>528 `6e8ee381a9` Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `9f30b295c4` Offer server save formatting in IntelliJ (UP02)<br>330 `f9ea5b6954` Open bundled library sources as the server's virtual documents (UP25)<br>331 `f33ff312b5` Check inline completion's invocation and selection natively (UP26)<br>332 `e781046413` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `9ab0c65ce3` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `4ef1d567c9` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `51d31667e8` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `8156ef08bb` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `28cfd60f62` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `81d18bd5e7` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `6a387660ef` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `08f1caa2f4` Read a signature without parameters in the playbook<br>351 `510c219350` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `c8578f4333` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `f4fc62daca` Check X254's library view through the server<br>354 `6ae3e2766a` Read X5's library target from the server until LSP4IJ connects it<br>355 `343caad643` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `3e52c69900` Expect X20's signature without parameters in VS Code<br>365 `1f7bf100c8` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `035162b1e3` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `22c164ac9f` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `2e6b34fc92` Ask X254's definition again when the restarted server retires its analysis<br>376 `4e6da9883e` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `ef2dba88db` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view<br>399 `660102e346` Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81)<br>402 `cf855b6491` Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `5565450c57` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `fb571b4e47` Send X259's library edit to the old server before the restart<br>87 `aca1b4ce0c` Wait for X257's Invoked request to reach the LSP console<br>88 `907e59ad3c` Reopen a popup under inspection after the IDE was deactivated<br>175 `8811209874` Give X145 a reference search that waits until it is canceled<br>282 `d75c30f2b5` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `78bd158c15` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `71b5ca40ab` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `25a87960b7` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `5457415b22` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open<br>446 `7c8f68a52f` Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `10cd011e15` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `0692b91449` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `df418594ac` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `90b17092bc` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `d99ee96d47` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `3b36ca52f6` Check Run to Cursor in both editors<br>64 `35c106b097` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `2ab34987ec` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `8b2adee4c0` Check a document link's Ctrl+Click in IntelliJ's X131 | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `6a5523fba2` Give the playbook catalog's size as its 342 cases everywhere<br>122 `e7427eee67` Point the playbook instructions at the playbook container<br>127 `6d04f1dd5e` List the test and debug-task messages and a test row in the playbook's 18a | Fold into the catalog's documentation. |
| What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | 473 `6527cf1f9b` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered<br>474 `0bb2acdbfb` Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it | the playbook keeps each server's whole trace, with each pull's answer at DEBUG only, and the tour's waits and popup steps say what LSP4IJ holds and what could close them |
| Playbook traces: compressed, kept whole for failures and the newest pass | 493 `07ed1be157` Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run | the pruning compresses a finished run's traces, keeps the newest 10 failed runs and the newest passing run of each mode whole, and judges pass and fail from results.json |

Kept as they are:

- 43 `20f573e570` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `98216657b1` Apply X103's rename as a refactoring and check the member after Undo
- 187 `00dd751a5f` Check that Debug of Current File launches an Ecstasy configuration
- 189 `4218c54e91` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `27038dbbef` Run the VS Code playbook after the extension suite, not with it
- 200 `8d39c2d499` Count the code actions at the tour's unused import and unused local
- 247 `765353da51` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `abaaa725b6` Print both compiler playbooks' progress per case in the build's output
- 279 `584aee48ac` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 46 now, about 6 after squashing. **Added lines:** 6,768 main, 372 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `fb9b5e1ee6` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `5eedf3da60` Drop the references to documents and plans this branch does not carry<br>69 `be743725d7` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `9bdf4b90b3` Fix lang/README's task paths, versions and feature summary<br>123 `6da6511bc0` Use American spelling in the shippable docs<br>124 `924079d5a1` Name testing in the embedded build and run index entry<br>125 `e187059a0d` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `2cca2ac252` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `a67b66f204` Say that every adapter needs Java 25 in editor-setup.md<br>283 `05051f3cb6` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `6b733a52dc` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `5d984085bb` Point the docs' line citations at the lines today's changes moved | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `ab5a68454d` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `35224fec1a` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `0f00d6041f` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `5c1ef62c0d` Leave the lang build out of the root build by default again<br>379 `1f32cc6bf8` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `6ee2752a81` Say in AGENTS.md that gradle.properties turns the lang build flags on | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |
| The user guide, with screenshots from the tours | 424 `46153a73ca` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours<br>426 `8fec91d67b` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run<br>429 `37d5d73fa9` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature<br>430 `9ec501dbb3` Move the user guide into lang/doc/user-guide, with its screenshots in images<br>434 `7ebb1c4b6a` Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's<br>435 `44cbf3ede6` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting<br>452 `b91745b809` Say in the user guide what a rename doesn't change or check<br>471 `2311f339f3` Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs<br>472 `4c2133388c` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages<br>477 `9b3275821f` Mark the user guide's keys up as keys, and the names it left plain as code<br>478 `f152591cbd` Add an index of every page and section of the user guide to its landing page<br>479 `e898c62217` Give the user guide's long sections subsections, and its index the third level<br>480 `8069b702c8` Box the user guide's single screenshots with their captions, as its pairs are | the guide, its testing chapter and its screenshots, made from tagged tour steps |
| Opt-in static analysis of lang: detekt and CPD | 501 `fbed837613` Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript<br>510 `a78314576d` Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings<br>511 `26e6e21b59` Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too<br>527 `5e46404680` Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates | lang's tasks for detekt over its Kotlin and CPD over its Kotlin and TypeScript, reporting only, not in check; the two tools' version pins in the root catalog go to X5 |
| Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | 512 `f2a93b4846` Keep the cause of a refused settings change, undo or range format, which detekt found swallowed<br>513 `a56f748564` Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops<br>514 `1902fe1b37` Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate<br>515 `d4ece11d83` Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair<br>516 `4fe7772dc1` Share the language server's location requests and edit conversion, and say the semantic tokens setting once<br>517 `6498c282ce` Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end<br>518 `7891e3e0bb` Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias<br>519 `052d37e783` Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over<br>520 `9030a62ed2` Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode<br>521 `f0a118b838` Share the language server's cancellation-owning future and its formatter's parenthesis search<br>522 `891e093150` Generate TextMate's operator patterns from one list of categories, not seven copies of the same block | detekt's and the duplicate checks' (CPD, IntelliJ's) findings fixed in lang's main code; it touches files of L1-L9, so it goes last, in L10, or is divided by path into each file's PR when the PRs are cut |

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
| 1 | `bbd50129ce` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `4241a9d32d` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `5b48062dfe` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `6aecc7106c` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `992bc587ef` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `217f951551` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `d46cc91cec` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `3acaf0d4ca` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `d1aea30100` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `fcea8a89e7` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `cb1fa5a931` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `f856e99fe4` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `2a29244de5` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `a7ca9203cf` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `049410fde0` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `47a48c5814` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `eeb607b90e` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `fdf3fbcdfb` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `1b729a944a` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `d8e2af6fcc` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `fb9b5e1ee6` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `10cd011e15` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `1c0946a679` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `54d400b4dc` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `8e56ed5af1` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `a51f4aab76` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `781751c33e` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `4f10232307` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `d5f21bc2ab` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `54dbc973b7` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `c6883e0b1a` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `25f018eca5` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `249c1ba748` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `4855cfcf51` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `0477466f61` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `11c63f2cb0` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `31b3486a82` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `c3e504fae0` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `0b93590719` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `b365fa52af` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `213b8bb002` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `4be63ad02b` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `20f573e570` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `7862604e0d` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `39022f443a` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `3917a40e81` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `de391fd9b0` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `f42dfb1e53` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `23bb526587` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `eece2b5ee3` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `1157ada596` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `8f5e15b9c5` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `4d3a4a1a54` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `86a1a5dab8` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `ea588be521` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `570db764cc` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `c56f002a40` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `3b36ca52f6` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `ede3ea5d27` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `27daf7f1bb` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `1872fc15f2` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `320363bb99` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `bcad0787b7` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `35c106b097` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `7d44862f9c` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `2ab34987ec` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `8b2adee4c0` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `5eedf3da60` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `be743725d7` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `43b19b2e0d` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `aa2d045a14` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `af7b74be7f` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `a6b273c75b` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `98216657b1` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `471e05695b` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `91c66df3cd` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `017510ac54` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `51289da08e` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `aa08cb109d` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `571de71ad7` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `2ed4668b4e` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `e979c6d1ef` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `f99737a874` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `c47460fe24` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `5565450c57` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `fb571b4e47` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `aca1b4ce0c` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `907e59ad3c` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `a84a2c8f6f` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `7e80e3b93d` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `2c554a1902` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `21c51c2d55` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `eb435e7128` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `4e2058c2f4` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `2306808c58` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `8572661c79` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `88a31d2a78` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `5e071fadfc` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `0ffc5a1ab4` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `56ae0fccf2` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `f0a473cd6e` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `228f941aaf` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `ab16329c87` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `836a90ac54` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `d90b8d9a11` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `a1a156d6d3` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `a36a16ecc4` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `44dd4fdf30` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `0be36a312d` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `28608d5740` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `d503d98309` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `7cd008ef1d` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `cac29b15b6` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `90bf8f0669` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `a44d0592e0` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `4e0b559b91` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `77428294e8` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `6a5523fba2` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `7f8452ac36` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `9bdf4b90b3` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `694fa223b7` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `e7427eee67` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `6da6511bc0` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `924079d5a1` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `e187059a0d` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `2cca2ac252` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `6d04f1dd5e` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `a67b66f204` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `6321ab7d59` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `9140d61655` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `72cbfb4a8a` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `6ab8c3de37` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `0d0e59664d` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `be0c88cbf9` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `ff5def42b4` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `98c88fab81` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `b60a49bac4` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `66afbdefad` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `70f2941c46` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `a8193a2978` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `56adc727e7` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `e883dfc7ea` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `9c2cae7f30` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `8c45e082ce` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `82000ffa28` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `50513b438e` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `f92c3cf6f5` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `1ceef2b64d` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `c00662b22c` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `ef1c33714d` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `b6b07c7232` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `1df2d68ccb` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `654e51865e` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `19ccf45100` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `5996c5b2c9` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `7add8b1385` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `5a331cc495` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `ca61569f3b` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `b760701003` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `bceb1f2f98` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `76aae77096` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `c816423d66` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `6382f8a652` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `42f1fddd56` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `4ca0eb4d2a` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `fd042bb210` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `353251e709` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `53aba7fede` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `3f163d92a7` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `d9e8c29798` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `ff1245eda9` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `37317acf77` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `27ca396224` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `8850706e98` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `8811209874` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `af943787c7` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `5570446e65` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `fdaf461e55` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `9e45f95e64` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `7062c6a4bd` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `3e467b77e4` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `a1d09b93ce` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `5848616ad6` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `586c00813b` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `06adceaf1b` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `5c2da6b8d9` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `00dd751a5f` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `f49141cb99` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `4218c54e91` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `27038dbbef` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `81ca8a31a0` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `71667f51ae` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `de8fad9ab8` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `ff6406ac0b` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `4e173639e2` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `39f24f2e3d` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `8f216cbdfa` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `b863c4143b` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `a4a980163e` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `8d39c2d499` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `7fdc04e34d` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `23148e75bf` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `ec9470605f` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `8cc62ffa91` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `d4d06fd07d` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `9d5b965d2b` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `2b592d0085` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `9763554c72` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `9e2aec20bf` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `1d1d4dac47` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `54d79ab46f` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `f8ce9bbae7` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `5f4275694c` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `4fcf1f84bd` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `ca404feca1` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `bfcc10c1a2` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `dd61720b70` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `bf3f6a5160` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `28e6039b6c` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `9451282e27` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `ae4061da98` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `0e786506a8` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `e5c5af549b` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `3c201c4c91` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `4109a579d8` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `840829374a` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `cdef78fa45` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `74d049db6a` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `ef44f96a47` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `982453dd3f` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `4738e5d32f` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `f55ce0657b` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `c711f8f134` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `8335e435b9` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `6a5938bb36` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `b6fcac2306` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `cb400e3793` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `1a4bcb7fc5` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `f3974fbd33` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `c3d5bb1f07` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `0692b91449` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `25d18c8cf4` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `d31744402e` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `f5aeba7365` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `e7ea45d14d` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `fc9ee644e0` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `765353da51` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `127b14d8f6` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `6c86144fc4` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `1655484565` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `449856a934` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `552af747cf` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `5d145f00c4` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `3d83688976` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `9122b78a81` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `0298896fb3` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `27aa1e3ada` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `9abbf6073e` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `5d84d0ec93` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `fe70f045f2` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `bb9a0b37e8` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `f856cf0652` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `abaaa725b6` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `680d225b78` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `6d4ae4d0dc` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `a0eb34a5c3` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `a2738e5611` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `4d9eea7dc5` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `2be99a6f00` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `814a7d95f5` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `7062d849aa` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `c544054788` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `0f00d6041f` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `5a217c8bd8` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `306810c921` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `693f3a30a5` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `9f31f3550f` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `0e3b3e2686` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `584aee48ac` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `b0cb727887` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `df418594ac` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `d75c30f2b5` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `05051f3cb6` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `6ee2752a81` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `a589baf1b1` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `9600e578bc` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `56c0977fe3` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `242821733e` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `9383e651d9` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `6b733a52dc` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `78bd158c15` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `6084715202` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `90b17092bc` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `8e8cad47fb` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `de034816ea` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `d99ee96d47` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `945e04eee9` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `cc433a9e98` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `09f3e9efae` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `400bdb9a11` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `02f7129868` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `999105a1b4` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `71b5ca40ab` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `553a1f3da0` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `5d984085bb` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `f4a67139c9` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `470faec78e` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `6fff324ef9` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `ab0a3eea77` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `c02232d71b` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `1e6e0dc127` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `291c559e0a` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `cae660d034` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `73e8d5fe49` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `ab5a68454d` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `35224fec1a` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `5c1ef62c0d` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `53f3b1ad1d` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `7021322f4a` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `25a87960b7` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `2d9d9df482` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `8f21d72b0b` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `9c8d5131ed` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `05e25274e1` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `f484685c5a` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `0ca226c58a` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `2e1b4e21a4` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `5559fa9860` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `9f30b295c4` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `f9ea5b6954` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `f33ff312b5` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `e781046413` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `9ab0c65ce3` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `4ef1d567c9` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `51d31667e8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `8156ef08bb` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `28cfd60f62` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `81d18bd5e7` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `3d2d9af23f` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `42bd9b3187` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `6469c85252` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `bde8e85803` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `26bc900989` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `b79f3b27d7` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `b36dac84e1` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `898412b040` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `6cdfc58d9c` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `1993b67731` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `6a387660ef` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `08f1caa2f4` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `510c219350` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `c8578f4333` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `f4fc62daca` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `6ae3e2766a` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `343caad643` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `1399bb16a4` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `86035ba960` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `3e52c69900` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `966a2e0d0c` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `7f0d039a85` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `39b5c4d868` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `4dcd0fc621` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `98364c27a6` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `a4839298ef` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `1f7bf100c8` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `5f22316c5c` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `37270fb26e` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `85afb62557` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `fb2d8579ab` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `035162b1e3` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `22c164ac9f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `6e2e43c58a` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `f6035bc4b2` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `80099dbb0a` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `2e6b34fc92` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `4e6da9883e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `6f42b8c79b` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `ef2dba88db` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `1f32cc6bf8` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `6150ee6889` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `caaadaaf0d` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `5457415b22` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `225b1d46db` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `197c7ce1f0` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `99f601f387` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `e0839181e9` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `19883b5da3` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `79a500fabf` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `5f7702f7a1` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `908a02449a` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `27478e37a8` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `8339aa2c8e` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `204ead6de4` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `2e75a9ff84` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `51bc7dad24` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `dac265afb6` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `705f64f6f5` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
| 398 | `706d3d5a82` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83) |
| 399 | `660102e346` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81) |
| 400 | `dfe500d1c7` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) |
| 401 | `59fe726369` | P10 | — | L9 | End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) |
| 402 | `cf855b6491` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) |
| 403 | `e7dcde02ae` | P12 | — | L10 | IntelliJ tour step fixes | Check that one server comes back when the tour kills the server's process (UP72) |
| 404 | `0c44140333` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will |
| 405 | `9fa428609b` | P10 | — | L9 | The debugger finds sources where its host names them | Find a debugged module's sources in the workspace when no source graph names it |
| 406 | `35f90dbf84` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build, run and debug with Gradle while the workspace's own modules replace bundled libraries |
| 407 | `cc21092389` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Name each source set's project by its path in the build tree in the IDE's Gradle model (#774) |
| 408 | `b95c5704e4` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Tell the client each module's Gradle project from the imported build model (#774) |
| 409 | `60be693111` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774) |
| 410 | `c07082e80f` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774) |
| 411 | `be1f52c0ae` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) |
| 412 | `8967f3f90c` | P6 | — | L4 | Rename guards and proofs | Prove a rename in a workspace module that replaces a bundled library as the workspace's sources |
| 413 | `4142d2ee0d` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's |
| 414 | `9d69632f72` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Say on a replaced library with errors that the server's own compiler may be older than the repository's |
| 415 | `50ea128648` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Add an editor setting for whether the workspace's own modules replace bundled XDK libraries |
| 416 | `a0ca9f5a5d` | P6 | — | L4 | Rename guards and proofs | Find a text's line breaks once per comparison in a rename's proof, not once per position |
| 417 | `aff8ec0537` | P6 | — | L4 | Rename guards and proofs | Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes |
| 418 | `5825f40802` | P6 | — | L4 | Rename guards and proofs | Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy |
| 419 | `8df085cf6d` | P4 | — | L1, L2 | Time and heap logs of the server's work | Log each project query's and each compile's time and heap |
| 420 | `49ded20b5e` | P4 | — | L1 | Time and heap logs of the server's work | Log what the server did in each minute in which it worked, and over its session |
| 421 | `0e3dba96e1` | P7 | — | L6 | The language server JVM's memory setting | Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive |
| 422 | `744676f467` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded |
| 423 | `f453d56666` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Show the embedded compiler as not available where the project's own modules replace bundled libraries |
| 424 | `46153a73ca` | P13 | — | L10 | The user guide, with screenshots from the tours | Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours |
| 425 | `d558e9a095` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy |
| 426 | `8fec91d67b` | P13 | — | L10 | The user guide, with screenshots from the tours | Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run |
| 427 | `1f0db784b7` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide |
| 428 | `6d38fc4201` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own |
| 429 | `37d5d73fa9` | P13 | — | L10 | The user guide, with screenshots from the tours | Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature |
| 430 | `9ec501dbb3` | P13 | — | L10 | The user guide, with screenshots from the tours | Move the user guide into lang/doc/user-guide, with its screenshots in images |
| 431 | `9112b55f14` | P10 | X4 | L9 | Lazy properties and their time limit | Let a debug launch or attach set how long evaluations and lazy values may run |
| 432 | `29b923ae63` | P12 | X5 | L10 | The playbook container and the IDE it drives | Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode |
| 433 | `90f691e469` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc |
| 434 | `7ebb1c4b6a` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's |
| 435 | `44cbf3ede6` | P13 | — | L10 | The user guide, with screenshots from the tours | Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting |
| 436 | `71225c7dd5` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed |
| 437 | `dc61e61ee7` | P6 | — | L4 | Prove code actions with incremental compiles | Start a rename's proof from the revision's compile, and compile after the edit only what it reaches |
| 438 | `6713328b8d` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work |
| 439 | `32afed96ca` | P10 | X4 | — | Debugged stacks end at the host's fiber; failed requests say why | End a debugged program's stack at the host's fiber, also when it has moved on from its call |
| 440 | `0e43640055` | P10 | — | L9 | Debugged stacks end at the host's fiber; failed requests say why | Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." |
| 441 | `2043b408dd` | P5 | X2 | L3 | Bind more names in the semantic model | Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration |
| 442 | `803c777033` | P5 | — | L3 | Bind more names in the semantic model | Make the _ placeholder no occurrence in the semantic model |
| 443 | `9f9f80c4c4` | P5 | X2 | L3 | Bind more names in the semantic model | Bind the names of a method's multiple return values to the values they declare |
| 444 | `d342a434a5` | P5 | X2 | L3 | Bind more names in the semantic model | Bind this in a lazy property's calc() and super in a method reference to what they name |
| 445 | `dc2b9be3ab` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed |
| 446 | `7c8f68a52f` | P12 | — | L10 | Individual playbook case fixes | Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 |
| 447 | `e8b212edcf` | P12 | — | L10 | IntelliJ tour step fixes | Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames |
| 448 | `ad1d9eba35` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies |
| 449 | `3f9aa546c6` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do |
| 450 | `805c1e65e2` | P6 | — | L4 | Prove code actions with incremental compiles | After a rename's edit, compile only the modules depending on the edited ones that it can reach |
| 451 | `92d12985fc` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done |
| 452 | `b91745b809` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide what a rename doesn't change or check |
| 453 | `3835882b93` | P5 | — | L3 | Bind more names in the semantic model | Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares |
| 454 | `e1394ddc89` | P5 | X2 | L3 | Bind more names in the semantic model | Keep the initializer facts of a property declared in a loop's body, such as a static local |
| 455 | `5654db07ad` | P5 | — | L3 | Bind more names in the semantic model | Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration |
| 456 | `cd5790e82e` | P5 | — | L3 | Bind more names in the semantic model | Declare the names of a function type's values in the semantic model, as of its return values in a typedef |
| 457 | `1b6ab24cda` | P5 | — | L3 | Bind more names in the semantic model | Bind a reserved name such as this:class in a property's accessor, as in a method of the class |
| 458 | `47ed5ab915` | P5 | — | L3 | Bind more names in the semantic model | Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells |
| 459 | `2c24fb20c0` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Report constructor arguments and default values that no constructor uses |
| 460 | `9a3856f2ba` | P6 | — | L4 | Rename guards and proofs | Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element |
| 461 | `32413601d1` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Digest the API of each declaration of a module apart (DeclarationDigests) |
| 462 | `68b0bf06a0` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Compile again, after an edit, only the modules that use what it changed (member-use tracker) |
| 463 | `79b0354b35` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Keep diagnostics working when a restart opens another server's library copy again |
| 464 | `84809067a1` | P9 | — | L8 | Supervised execution workers | Take the execution worker's events off its standard output, onto a loopback socket of their own |
| 465 | `61fe7b63ce` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export each project's xtcRun and xtcTest settings in the language server's Gradle model |
| 466 | `616923df49` | P9 | — | L8 | Embedded runs with their Gradle project's xtcRun and xtcTest settings | Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors |
| 467 | `b0ed28d07e` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType() |
| 468 | `fa48926084` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them |
| 469 | `9606f98b96` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own |
| 470 | `5766adfc03` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's |
| 471 | `2311f339f3` | P13 | — | L10 | The user guide, with screenshots from the tours | Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs |
| 472 | `4c2133388c` | P13 | — | L10 | The user guide, with screenshots from the tours | Split the user guide into a landing page, one page per chapter, and features and shortcuts pages |
| 473 | `6527cf1f9b` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered |
| 474 | `0bb2acdbfb` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it |
| 475 | `071d15b31c` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) |
| 476 | `6aba24cabf` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place |
| 477 | `9b3275821f` | P13 | — | L10 | The user guide, with screenshots from the tours | Mark the user guide's keys up as keys, and the names it left plain as code |
| 478 | `f152591cbd` | P13 | — | L10 | The user guide, with screenshots from the tours | Add an index of every page and section of the user guide to its landing page |
| 479 | `e898c62217` | P13 | — | L10 | The user guide, with screenshots from the tours | Give the user guide's long sections subsections, and its index the third level |
| 480 | `8069b702c8` | P13 | — | L10 | The user guide, with screenshots from the tours | Box the user guide's single screenshots with their captions, as its pairs are |
| 481 | `7c2a268023` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Keep the semantic model's census of the libraries as a heavy test, which fails on an unbound occurrence |
| 482 | `bf11748204` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove an anonymous class's this and construction by the span of the type it constructs |
| 483 | `7cb276a8bc` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type's child, as Element in CompileType.Element, to the formal type its constraint declares |
| 484 | `d4a9fa96db` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a union receiver's call of a method that the runtime implements, as a const's appendTo |
| 485 | `6cb431e844` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove a call on a union receiver whose legs share one route, as toString on a Point? |
| 486 | `1298a24dcb` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the dispatch chains that hold an unproven identity in the semantic model's census |
| 487 | `f79a8adb7e` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind a formal type that the compiler adds to a mixin or annotation to the contribution's, as Inner in annotation Cap into Holder |
| 488 | `6792b111ae` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the methods that the compiler merges over a union, as those of an annotation into Class \| Method |
| 489 | `63b67a2278` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count the bindings that no proof can place in the semantic model's census, and fail on one |
| 490 | `f7a679e1ed` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Make the binding tests' must-stay-refused renames show that the proof refuses them, and for the reason they name |
| 491 | `bdb3ed8101` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Count an unchanged diagnostic report's items from the report it names, which closes the UP86 bridge's gap |
| 492 | `8f1b46c770` | P12 | — | L10 | IntelliJ tour step fixes | Press the tour's Parameter info once the file's code lenses are in place, as the Warnings step's popup does |
| 493 | `07ed1be157` | P12 | X5 | L10 | Playbook traces: compressed, kept whole for failures and the newest pass | Compress the playbook runs' traces once a run ends, and keep whole traces only for failures and the newest passing run |
| 494 | `266814c630` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a request and its query as a start and one end that carries the phases' times, in place of an event per phase |
| 495 | `15171f4e77` | P4 | — | L1 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a pull's answer at DEBUG only when it differs from the document's previous one, and name related reports by count |
| 496 | `122e893eb6` | P4 | — | L1, L2 | Trace less: one end per request, answers only when changed, one start and end per discovery | Trace a source discovery as a start and one end with its parses' count and times, and each parse only at DEBUG |
| 497 | `90ab684344` | P11 | — | L5 | Temporary directories and their locks | Never look at a lock that this process holds, whose look releases it, and take a kind's guard one thread at a time |
| 498 | `fe79b2d484` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Type in XtcCompletionTest's typed prefix cases once LSP4IJ has connected the file, as LSP4IJ loses an edit typed before (UP83) |
| 499 | `7b8bece3c3` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's error-message step once the edit's code lenses are in place, as the Warnings step does |
| 500 | `d042ec2845` | P8 | — | L7 | npm dependencies of master's #805 | Bring #805's npm changes into the VS Code extension's package.json, and its package-lock.json from npm |
| 501 | `fbed837613` | P13 | X5 | L10 | Opt-in static analysis of lang: detekt and CPD | Add opt-in static analysis to lang: detekt over its Kotlin, and CPD over its Kotlin and TypeScript |
| 502 | `4a746eb12e` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Allow contribution arguments and shorthand defaults that match the explicit constructor |
| 503 | `fab927046b` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the semantic model's census what hover, definition, references and highlights miss at a name |
| 504 | `743f6944a3` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Bind the parameters of a constructor with a finally block, in its body and named arguments, to their declarations |
| 505 | `938326f32e` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Declare a label as a symbol, which a break, a continue and its variable, as Loop in Loop.first, refer to |
| 506 | `3224b9de22` | P6 | — | L4 | Proofs the census found missing: anonymous classes, runtime-implemented and union-merged methods, Referents | Prove the Referent that the compiler adds to a property with code or a Ref annotation by its property |
| 507 | `6811b1e41c` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Count in the editor's census the references whose declaration is in no source of the workspace |
| 508 | `5390b9e736` | P5 | — | L3 | The semantic model's censuses, and the bindings they found missing | Compile the census's libraries as one workspace folder, as an editor that opens the repository does |
| 509 | `67235f011c` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Show a method's callers when a write action interrupts the Call hierarchy's wait for them (UP88) |
| 510 | `a78314576d` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Configure lang's detekt for its style, and baseline what is left, so that a run reports only new findings |
| 511 | `26e6e21b59` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Find duplicated code in lang as IntelliJ's "Duplicated code fragment" inspection does, renamed copies too |
| 512 | `f2a93b4846` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Keep the cause of a refused settings change, undo or range format, which detekt found swallowed |
| 513 | `a56f748564` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Fix detekt's small findings in the language server: an unused parameter, an empty flush, shadowed its, range loops |
| 514 | `1902fe1b37` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Narrow two of the 55 catch-alls that detekt found in lang's main code, and keep the rest, which are deliberate |
| 515 | `d4ece11d83` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the IntelliJ plugin's duplicated action and Enter-repair code: XtcProjectAction, blockSplit and repair |
| 516 | `4fe7772dc1` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's location requests and edit conversion, and say the semantic tokens setting once |
| 517 | `6498c282ce` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the tree-sitter code's duplicated helpers: shorthand properties, typed declarations, a template's end |
| 518 | `7891e3e0bb` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the compiler adapter's duplicated refactoring code: a source's plan and edit, a removal, a module's alias |
| 519 | `052d37e783` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Escape a C char literal in one place in the scanner generator: escapeForC, which CCodeDsl had twice over |
| 520 | `9030a62ed2` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Build the extension's invalid-settings message once, and offer a Gradle build's Test and Clean tasks once for either build mode |
| 521 | `f0a118b838` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Share the language server's cancellation-owning future and its formatter's parenthesis search |
| 522 | `891e093150` | P13 | — | L10 | Static analysis fixes across lang: causes kept, catch-alls narrowed, duplicates shared | Generate TextMate's operator patterns from one list of categories, not seven copies of the same block |
| 523 | `2c4e48e7bd` | P7 | — | L6 | Callers that a write action interrupted in the Call Hierarchy (UP88) | Wait in XtcCallHierarchyProviderTest until LSP4IJ has connected the file, before asking for the call hierarchy |
| 524 | `f91f0f8842` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Test that a class without constructors compiles and uses its contribution's arguments |
| 525 | `dcf39f1672` | P12 | — | L10 | The playbook container and the IDE it drives | Run as many playbook containers at once as the engine's memory holds, each slot with its own lock and work volume |
| 526 | `a2a2dd782d` | P12 | — | L10 | The playbook container and the IDE it drives | Give each playbook container slot its own Gradle and Maven volumes, which two containers can't share |
| 527 | `5e46404680` | P13 | — | L10 | Opt-in static analysis of lang: detekt and CPD | Declare lang's detekt and PMD tool configurations with create(), not the delegated property Gradle 9.9 deprecates |
| 528 | `6e8ee381a9` | P12 | — | L10 | The playbook container and the IDE it drives | Print, at most every two minutes, how many of a playbook run's cases have run, are left, passed and failed |
