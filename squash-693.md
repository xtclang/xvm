# Squashing #693 into reviewable pull requests

A plan only: making it changed nothing in #693. It covers the 480 commits of `errs/full-testable-lsp-20261009` over `master` (tip `244f824635`, master `2e26663249`, 2026-11-25), numbered 1 to 480 from the oldest, with their short hashes. Commits 1-397 were planned on 2026-11-23; the rebase onto master of 2026-11-25 kept their numbers and gave them new hashes. Commits 398-480 came after, and were placed by the same rules. One of them is open on master as its own PR: 459 is #802, and leaves #693 when it merges. #804's four commits, 467, 471, 473 and 474 then, merged to master as 2e26663249 on 2026-11-25; #693 was rebased onto it without them, which renumbered the commits after 467.

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
| 15 | **L10** | lang/ | Test infrastructure: the playbooks, the scenario catalog, the manual test plan and the tours | L9, X5 | P12, P13 | 141 | 7.2k / 57.0k |

Already open on master, apart from these: #802 (unused constructor arguments, commit 459), which leaves #693 when it merges. #804 (runXtc keeps xtcRun's settings) is merged.

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
| P9 | Embedded build, run and tests from the editors | P4 (and P7, P8 for the editor parts) | no | 55 | 23 | 10.6k / 9.7k |
| P10 | Debugger: runtime debugger, DAP server and the editors' debugging | P9 | no | 60 | 36 | 17.2k / 15.2k |
| P11 | The XDK's library sources and the XDK workspace | P5 | yes, once 1 commit's other files move | 22 | 9 | 3.3k / 3.2k |
| P12 | Test infrastructure: playbooks, scenarios, tours and their container | P7-P11 | no | 112 | 20 | 3.0k / 57.3k |
| P13 | Documentation, build and CI | the PRs it documents | no | 31 | 4 | 5.7k / 372 |
| | **Total** | | | **480** | **164** | |

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

Commit 8, `c6781bd795` "Integrate compiler-backed language support with IntelliJ and VS Code", adds 109,353 lines: the whole language server, both editors and the playbooks, from the regroup of 2026-10-28. No squash makes it reviewable. Divided by package, it goes to the PRs below; see "Splitting commit 8".

### 2. Workarounds that later commits remove again

These pairs add code and then take most of it back, mostly LSP4IJ 0.21 workarounds that LSP4IJ 0.30.1 made unneeded. Joined, they leave little or nothing; built against 0.30.1 from the start, the workaround never needs to appear.

| Earlier commit | Later commit | Lines of the earlier one that the later one deletes |
|---|---|---|
| 89 `4cb6820ac6` | 366 `cfdf0286c1` Let LSP4IJ find a stack frame's source in a folder the VFS has not see | 89 of 94 |
| 84 `bcac61d569` | 345 `c4e16524b3` Let LSP4IJ order document notifications and check server edits (UP09,  | 66 of 70 |
| 203 `f46f2ae33f` | 374 `98718c75dd` Drop the server's code action budget, which no client asks for since L | 338 of 381 |
| 245 `6200692789` | 369 `a126b6d6c3` Let LSP4IJ document a completion item from its resolve (UP78) | 75 of 83 |
| 225 `02f566d112` | 367 `5f06796f9b` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP | 113 of 155 |
| 181 `fe0c8e29af` | 363 `bad5b2f9e7` Let LSP4IJ send the edits it holds before a request (UP73) | 136 of 234 |
| 191 `88b5af8274` | 373 `e644ff80a6` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69 | 381 of 910 |
| 159 `2032463673` | 362 `479d570a01` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 102 of 358 |
| 197 `3856d6f3e6` | 360 `f4df953e04` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 55 of 100 |
| 108 `e598612970` | 112 `5dd674db72` Report tests as xunit's own JSON events, which the language server rea | 197 of 1493 |
| 79 `9204f4d102` | 105 `0c0a9691a2` Move the debugger agent into javatools, with the protocol as records | 870 of 3104 |
| 8 `c6781bd795` | 36 `62606aaa28` Remove the document-color prototype | 1227 (the document-color prototype) |
| 8 `c6781bd795` | 387 `56380eac05` Remove monikers from the language server, as neither editor uses them | 884 (the monikers) |

### 3. The LSP4IJ 0.30 series folds away

Commits 325-378 (and 392, 393, 395) move the plugin and the playbook from LSP4IJ 0.21 to 0.30.0 and then 0.30.1: a version bump each, about fifteen commits that each remove one workaround (UP01-UP77), and the playbook cases adjusted to the new version. Since the PRs go to master after 0.30.1 is out, write P7 and P12 against 0.30.1 from their first commit: the bumps become one commit, and each "Let LSP4IJ ..." commit folds into the commit that added its workaround, which then simply doesn't add it.

### 4. Mixed commits to divide by path

These commits change files outside the components of the PR they are placed in, most often a test in another component (the playbook, the server's tests of a compiler API). When the PRs are cut, divide them by path, each part going to the PR of its files, or keep such a test with the change it tests if the PR it would go to comes later:

- 5 `3b5cfe68a5` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics, in P2 (+1,699; also touches: server)
- 6 `21f047a891` Expose compilation-owned syntax and resolved semantic facts, in P2 (+2,200; also touches: server)
- 7 `67ccd4448b` Expose recovery, cursor fitting and declaration analysis to compiler hosts, in P3 (+5,654; also touches: server)
- 9 `c5cf2f33cb` Report repository read attempts without inferring dependency failures, in P1 (+344; also touches: server)
- 10 `724c57eca6` Clarify tooling contracts and isolate partial AST helpers, in P2 (+3,383; also touches: intellij, playbook, server, vscode)
- 11 `d1f4c15478` Keep unchanged repository read failures cached until file metadata changes, in P1 (+215; also touches: server)
- 15 `867189adce` Document compiler support for the language server, in P2 (+1,345; also touches: playbook)
- 16 `9870255836` Guard code actions and renames at synthetic bodies and locked library sources, in P6 (+254; also touches: intellij)
- 17 `3b6cc7d992` Restart editor-owned language servers from a project-scoped CLI, in P7 (+607; also touches: playbook, vscode)
- 18 `f8d04c05b9` Group source bindings, clean up the compiler AST and share duplicated code, in P3 (+1,704; also touches: intellij, server)
- 20 `fda8e8963a` Build and run with the embedded compiler in the server and both editors, in P9 (+2,902; also touches: compiler, playbook)
- 22 `056ec44f6d` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook, in P12 (+1,165; also touches: intellij, vscode)
- 23 `9997bdd6f3` Keep documents current and the server responsive, and name its own diagnostics, in P4 (+1,211; also touches: intellij, playbook, vscode)
- 25 `9bfe8024d5` Let embedded runs read console input in both editors, in P9 (+719; also touches: playbook)
- 27 `01b20d7c01` Make embedded the default build mode, chosen per editor, in P9 (+636; also touches: playbook)
- 29 `e3e6d9eee0` Run programs side by side in supervised execution workers, in P9 (+1,282; also touches: playbook)
- 30 `672e43128b` Find entry methods through the module's type and pass them with their arguments, in P9 (+477; also touches: compiler)
- 31 `318585b181` Keep indentation while typing, and lay out switches as the XDK does, in P5 (+850; also touches: intellij, playbook, vscode)
- 34 `9309be121f` Build and run the editor's text unsaved, and report why a run cannot start, in P9 (+605; also touches: compiler, playbook)
- 35 `66397977b1` Declare fields first and use plain names in the new classes, in P4 (+699; also touches: compiler, intellij, playbook, runtime)
- 37 `316af5f0a6` Rename adopted properties, widen extraction, and move and rename discovered roots, in P6 (+1,270; also touches: intellij, playbook, vscode)
- 38 `76be500964` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle, in P9 (+682; also touches: playbook)
- 39 `0ccd3edad1` End runs and workers cleanly, and measure them without waiting for the collector, in P9 (+696; also touches: compiler)
- 42 `9a3c3570a6` Keep the build's jars whole and its test runs focused, in P4 (+405; also touches: compiler, intellij, playbook, vscode)
- 43 `2b19fc6d17` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean, in P12 (+356; also touches: intellij)
- 46 `0396f075ac` Debug programs from VS Code and IntelliJ through the language server, in P10 (+4,010; also touches: compiler, playbook)
- 47 `bbeb8b2920` Show a stopped frame's variables, and step over, into and out of calls, in P10 (+1,762; also touches: compiler, playbook)
- 48 `15cc5a339d` Stop at exceptions, pause a running program, and stop on entry, in P10 (+1,663; also touches: compiler, playbook)
- 49 `9cda9012e5` Evaluate watches and hovers on paths in a stopped frame, in P10 (+677; also touches: compiler, playbook)
- 50 `d74d25488f` Conditional breakpoints, hit counts, logpoints and exception classes, in P10 (+1,217; also touches: compiler, playbook)
- 51 `4989630717` Evaluate expressions with operators without running the program's code, in P10 (+1,263; also touches: compiler, playbook)
- 53 `4a8e95649b` Run code typed in the Debug Console on the stopped fiber, in P10 (+2,734; also touches: compiler, playbook)
- 55 `53b826d8e9` Lazy properties, hexadecimal values, paged arrays, Set Value, completions, in P10 (+3,135; also touches: compiler, playbook)
- 56 `6ba9609d74` Place breakpoints on the lines a debugged program was built from, in P10 (+636; also touches: playbook)
- 57 `2c2258cb83` Soak runs and debug sessions on one worker, and let finished runtimes go, in P9 (+902; also touches: compiler, playbook)
- 58 `81c3473897` Check Run to Cursor in both editors, in P12 (+223; also touches: server)
- 59 `71e1afae38` Show library frames in their bundled source, every fiber, and modules, in P10 (+1,266; also touches: compiler, playbook)
- 60 `1ea3737ec0` Step into a chosen call of the stopped line, in P10 (+704; also touches: compiler, playbook)
- 61 `d31a474ee5` Stop where a method of a name is entered, in P10 (+654; also touches: compiler, playbook)
- 62 `0c20a90704` Restart a stopped frame, which its caller then calls again, in P10 (+478; also touches: compiler, playbook)
- 63 `94758a7f98` Stop once a watched field of an object has changed, in P10 (+849; also touches: compiler, playbook)
- 64 `1ef69e690b` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes, in P12 (+302; also touches: server)
- 65 `80d6092466` Answer inline values with the stopped method's variables, in P10 (+459; also touches: playbook)
- 73 `ec09eb0fe0` Inline every use of a private member and remove it, in P6 (+496; also touches: intellij, playbook, vscode)
- 79 `9204f4d102` Attach the debugger to a program run with --debug, in P10 (+3,104; also touches: compiler, playbook)
- 93 `0aa134df86` Let the playbooks' diagnostics waits accept only a report for the current text, in P12 (+304; also touches: intellij, vscode)
- 96 `7a6c404fb4` Debug Gradle-mode runs in VS Code and IntelliJ, in P10 (+1,279; also touches: playbook)
- 101 `f8844b58ed` Continue, step or pause one fiber while the others stay stopped, in P10 (+751; also touches: compiler)
- 104 `0b25542f99` Debug a command-line run: xtc run runs the program, which connects, in P10 (+553; also touches: playbook)
- 105 `0c0a9691a2` Move the debugger agent into javatools, with the protocol as records, in P10 (+1,525; also touches: compiler, playbook)
- 108 `e598612970` Run and debug a module's xunit tests from the editors, embedded, in P9 (+1,493; also touches: dap, playbook)
- 110 `f910b34173` Run and debug Ecstasy tests from IntelliJ, in its test console, in P9 (+1,049; also touches: playbook)
- 111 `6b14581128` Run and debug tests in Gradle and command-line mode, from both editors, in P9 (+636; also touches: dap, playbook)
- 112 `5dd674db72` Report tests as xunit's own JSON events, which the language server reads once, in P9 (+891; also touches: dap, playbook)
- 148 `4126419fcc` Let tests wait for a hang only, not for a fast machine, in P12 (+1,748; also touches: compiler, dap, intellij, runtime, server)
- 150 `796dde1a22` Record a tour of the IntelliJ plugin on a card game, checking what it shows, in P12 (+4,329; also touches: intellij)
- 154 `70c25d0b0a` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports, in P7 (+518; also touches: playbook, server)
- 161 `b30d67ca6b` Complete names and show a call's signature where they are typed at a line's end, in P3 (+229; also touches: server)
- 164 `6e3f944a50` Name a server's log session by the server alone, and tell a live one by its lock, in P4 (+264; also touches: intellij)
- 166 `d513e313fd` Make SessionDebuggerTest independent of how its threads are scheduled, in P10 (+221; also touches: compiler)
- 171 `2aa62da092` Prove only the code actions a request asks for, and share a revision's proofs, in P6 (+726; also touches: intellij)
- 174 `6b1c6d3de2` Let a debugger condition name an enum value by its enumeration, in P10 (+242; also touches: compiler)
- 178 `23de8c34d2` Let xtc test, run and build take a project's test module with its modules, in P9 (+296; also touches: compiler)
- 191 `88b5af8274` Get a closed file's errors to IntelliJ from the server's workspace report, in P7 (+910; also touches: playbook, vscode)
- 194 `318ca5b8a1` Find a test's hang by the CPU it stops using, not by an hour on the clock, in P12 (+2,369; also touches: compiler, dap, intellij, runtime, server)
- 203 `f46f2ae33f` Wait for LSP4IJ's automatic code action answers only a budget, in P6 (+381; also touches: intellij)
- 229 `4b38f80016` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state, in P12 (+4,913; also touches: intellij)
- 230 `0d221e805b` Compose the tours' video from their recorded clips, each step from a run where it passed, in P12 (+298; also touches: intellij)
- 240 `4dc1492a13` Fail the playbooks' diagnostics waits once their reports are final, in P12 (+262; also touches: vscode)
- 241 `4b095e8da9` Add playbook cases X342-X349 for xunit tests, in both editors, in P12 (+875; also touches: vscode)
- 243 `35a442d4a0` Record a tour of the VS Code extension that checks what it shows, in P12 (+5,393; also touches: vscode)
- 274 `8b6ed673bf` Navigate the XDK's library sources: definitions from their names, frames at their methods, in P11 (+302; also touches: dap)
- 278 `de9a267667` Compile an opened XDK source with its whole library, for navigation in all of it, in P11 (+467; also touches: intellij, vscode)
- 309 `645780fc9c` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3, in P12 (+342; also touches: intellij)
- 319 `0a14104960` Wait in the VS Code playbook until the server has heard of a case's changes on disk, in P12 (+243; also touches: server)
- 388 `327de73b5f` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly, in P7 (+218; also touches: playbook)

76 commits of 200 added lines or more; smaller mixed commits divide the same way.
- 400 `43dfde02ca` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82), in P7 (+272; also touches: playbook)
- 410 `cce3b4f0d7` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774), in P9 (+114; also touches: playbook)
- 415 `60b393f7a3` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries, in P9 (+55; also touches: playbook)
- 421 `d2497070e2` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive, in P7 (+141; also touches: playbook, server, vscode)
- 422 `b9dda07133` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded, in P9 (+220; also touches: playbook)
- 423 `15bfb50c98` Show the embedded compiler as not available where the project's own modules replace bundled libraries, in P9 (+133; also touches: playbook)
- 424 `4610212d5c` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours, in P13 (+754; also touches: intellij, vscode)
- 425 `088a676cdc` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy, in P7 (+277; also touches: playbook, server)
- 426 `0ae33f4a07` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run, in P13 (+193; also touches: intellij, playbook)
- 427 `f073d9fc19` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide, in P7 (+73; also touches: playbook)
- 428 `5fd5523007` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own, in P9 (+447; also touches: playbook)
- 429 `d1641d1822` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature, in P13 (+251; also touches: playbook)
- 430 `544d88d7c3` Move the user guide into lang/doc/user-guide, with its screenshots in images, in P13 (+996; also touches: intellij, playbook, vscode)
- 431 `523b1d7dff` Let a debug launch or attach set how long evaluations and lazy values may run, in P10 (+716; also touches: playbook)
- 432 `22a8e6ccbc` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode, in P12 (+5; also touches: build, intellij)
- 433 `a66a7fba2c` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc, in P9 (+485; also touches: playbook)
- 435 `512e6a8bec` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting, in P13 (+125; also touches: playbook)
- 436 `1adc13a4b4` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed, in P6 (+589; also touches: intellij, playbook, vscode)
- 441 `0268310ee3` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration, in P5 (+110; also touches: javatools)
- 443 `a526cb43fb` Bind the names of a method's multiple return values to the values they declare, in P5 (+65; also touches: javatools)
- 444 `0e19edf0ba` Bind this in a lazy property's calc() and super in a method reference to what they name, in P5 (+147; also touches: javatools)
- 445 `b4d0ef6218` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed, in P6 (+275; also touches: playbook)
- 454 `de62643b50` Keep the initializer facts of a property declared in a loop's body, such as a static local, in P5 (+47; also touches: javatools)
- 459 `807419f1b7` Report constructor arguments and default values that no constructor uses, in P1 (+129; also touches: xdk)
- 461 `f992a7a0d2` Digest the API of each declaration of a module apart (DeclarationDigests), in P11 (+622; also touches: javatools)
- 463 `6f1818cfe0` Keep diagnostics working when a restart opens another server's library copy again, in P11 (+294; also touches: playbook)
- 466 `b4d7869f07` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors, in P9 (+1,453; also touches: playbook)
- 469 `a14af8d820` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own, in P11 (+1; also touches: javatools)
- 472 `1ff22d7e8d` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages, in P13 (+1,264; also touches: intellij)
- 473 `cc77837927` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered, in P12 (+141; also touches: intellij, server)


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
| Keep, cache and report module repository read failures | 1 `753b5ddb1c` Preserve module repository read failures without blocking usable candidates<br>9 `c5cf2f33cb` Report repository read attempts without inferring dependency failures<br>11 `d1f4c15478` Keep unchanged repository read failures cached until file metadata changes | Three steps of one change to ModuleRepository's failure handling: preserve the failures, report the attempts without inferring dependency failures, keep them cached until file metadata changes. |
| Require explicit error listeners, separate reporting from abort policy | 2 `5bfa4b9785` Separate diagnostic reporting from listener abort policy<br>3 `68dfafae88` Require explicit compiler listeners and reasons for silence<br>4 `98bf164e26` Scope parser diagnostics and restore validation listeners on every exit<br>19 `31780496db` Route repository and runtime reports through error listeners<br>41 `6ad5483f94` Stop the lexer at a source's end and keep diagnostics that only look like duplicates<br>467 `5b23815405` Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType()<br>468 `c73fcd8edd` Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them | One contract change (ErrorListener) made over five commits: separate reporting from abort, require listeners and reasons for silence, scope parser diagnostics, route repository and runtime reports, and the lexer fix that the new duplicate check exposed. |
| Regression tests for diagnostics, embedding, cancellation and duplicate types | 384 `5b3b418c30` Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types<br>386 `d796f614a2` Check duplicate source types through the command line, and implicitly abstract classes, in manualTests | Tests of the behaviour above, added later from errs; put them with the change they test. The embedding and cancellation test classes of 384 belong to P2 instead: split 384 by test class. |
| Unused constructor arguments and defaults (open PR #802) | 459 `807419f1b7` Report constructor arguments and default values that no constructor uses | already open on master as #802; it drops out of #693 when #802 merges |

## Topic P2: Compiler host API: compilation outcomes, snapshots, syntax and semantic facts

**Scope.** javatools api and compiler: compilation outcomes, snapshots, cancellation and cached diagnostics for hosts; compilation-owned syntax and resolved semantic facts; doc comments kept on structures.

**Builds on** P1. **Commits:** 8 now, about 4 after squashing. **Added lines:** 5,832 main, 3,116 tests.

| Join into | Commits | Why |
|---|---|---|
| A spare key per thread in the hasher map | 196 `c3f858d9ce` Give each thread its own spare key for a hasher map's lookups | A thread-safety fix that concurrent compiler hosts need; keep as its own commit. |
| Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | 5 `3b5cfe68a5` Expose host compilation outcomes, snapshots, cancellation and cached diagnostics<br>160 `41579b3056` Report every class's errors from an embedded compile, not only the first's | 160 completes 5: an embedded compile reports every class's errors, not only the first's. |
| Expose compilation-owned syntax and resolved semantic facts | 6 `21f047a891` Expose compilation-owned syntax and resolved semantic facts<br>10 `724c57eca6` Clarify tooling contracts and isolate partial AST helpers<br>12 `1536d3038c` Verify compiler output without binary AST dependencies<br>115 `6c6e1e1a82` Keep the doc comments of properties and methods on their structures | The facts API (6), its contracts and partial-AST helpers (10), the output check without binary AST dependencies (12) and the doc comments kept on structures for hover (115) are one API surface. |
| Document compiler support for the language server | 15 `867189adce` Document compiler support for the language server | Keep as the PR's documentation commit, or fold into the group above. |

## Topic P3: Compiler analysis of incomplete source: recovery, cursor fitting, declarations

**Scope.** javatools compiler: recovery, cursor fitting and declaration analysis for editors, with the completion fixes at a line's end and the AST cleanup.

**Builds on** P2. **Commits:** 6 now, about 2 after squashing. **Added lines:** 5,708 main, 2,022 tests.

| Join into | Commits | Why |
|---|---|---|
| Expose recovery, cursor fitting and declaration analysis to compiler hosts | 7 `67ccd4448b` Expose recovery, cursor fitting and declaration analysis to compiler hosts<br>131 `c8ce4ca806` Complete a member access typed at the end of a line<br>144 `595c47dd2f` Complete a member with the cursor right after its dot<br>161 `b30d67ca6b` Complete names and show a call's signature where they are typed at a line's end<br>385 `d4d9159d48` Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form | 131, 144 and 161 fix the same cursor-fitting cases (a member access or a name typed at a line's end, the cursor right after a dot) that 7 introduced; 385 makes testFit's probe silent, also 7's code. |
| Group source bindings and clean up the compiler AST | 18 `f8d04c05b9` Group source bindings, clean up the compiler AST and share duplicated code | Mixed commit: keep its compiler part here; its language server and IntelliJ parts go with P4 and P7. |

## Topic P4: Language server core: protocol, document sync, compile pipeline, discovery, diagnostics

**Scope.** lsp-server: the server and its text document service, document synchronization, the compiler adapter's compile queue, workspace discovery and build model, diagnostics (push and workspace pull), server status, the Tree-sitter and mock adapters, and the test tiers. Commit 8's server/, util/, index/, lexer/, treesitter/, mock/ and the adapter core (XdkAdapter, XdkSources, XdkSourceModule, XdkWorkspaceDiscovery, XdkBuildModel, XdkDependency, CompilerDispatch, CompilerQueueTrace, XdkAst, XdkLexical) with their tests. Large: review it as P4a (the server, its protocol and document synchronization, Tree-sitter and the mock adapter: commit 8's server/ and the groups on documents, status and tests) and P4b (the compiler adapter's core: compiling, discovery, diagnostics and workspace pulls).

**Builds on** P3. **Commits:** 39 now, about 14 after squashing. **Added lines:** 18,788 main, 27,911 tests.

| Join into | Commits | Why |
|---|---|---|
| Server tests that check what they claim, not how long they wait | 81 `6ce1cf62e5` Let XdkLanguageServerTest's pending analyses publish before finish() returns<br>82 `c43c91bf7e` Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply<br>83 `8331907146` Let two run tests check what they claim instead of how long they wait<br>301 `abb1e9a46e` Make the retention test's cancellations and the library content test's refresh check independent of timing | Four fixes of timing in existing tests: fold each into the test it fixes. |
| Integrate compiler-backed language support (split by package, see below) | 8 `c6781bd795` Integrate compiler-backed language support with IntelliJ and VS Code<br>36 `62606aaa28` Remove the document-color prototype<br>266 `f6dc890295` Remove the unused xtc/runnableTargets request<br>387 `56380eac05` Remove monikers from the language server, as neither editor uses them | Commit 8 is 109,353 added lines and cannot be reviewed as one commit. Split it by package across P4-P8 and P12 (see "Splitting commit 8"). 36 deletes the document-color prototype (1,227 of 8's lines), 387 the monikers (884 lines) and 266 the unused xtc/runnableTargets request: leave that code out of the split instead of adding and removing it. |
| Keep documents current and the server responsive | 23 `9997bdd6f3` Keep documents current and the server responsive, and name its own diagnostics<br>28 `a5ee7672cb` Judge file events against a running analysis and apply them in order<br>92 `8ba46d269d` Analyze again an open document whose analysis was retired without a replacement<br>184 `54d8fa6759` Answer requests that a restarted client sends while it opens its files again | One concern, document currency and analysis scheduling: keep documents current (23), judge file events against a running analysis (28), re-analyze a retired document (92), answer a restarted client while it reopens its files (184). |
| Workspace diagnostic pulls | 201 `2373bf350d` Answer an idle client's workspace pull of an unchanged graph without a job<br>210 `cc571d723a` List a closed test module's tests from the workspace pull's compile<br>237 `f5b46a23cc` Clear a deleted file's diagnostics in the next workspace pull<br>248 `e415bb0bea` Clear a removed file's report once in workspace pulls that name its result ID<br>289 `cde05d7740` Make a test added to an open test module a new report of its own diagnostic pull | The workspace pull's behaviour, fixed case by case: an unchanged graph without a job, a closed test module's tests, a deleted or removed file's report cleared once, a test added to an open module. |
| Report what the server is still doing | 208 `579da273e9` Say which requests the language server is still answering, and which diagnostics it has still to report<br>226 `e5f17cd4b3` Answer xtc/busy with the server's open requests and its compiler's jobs<br>298 `13c8ca99a8` Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus | xtc/busy, the open requests and diagnostics still to report, the heap and compile queue in xtc/languageServiceStatus: one status feature built in three steps. |
| Isolate the LSP test workers and split them into tiers | 13 `c5c1566d66` Isolate parallel LSP test workers and keep CI and lint checks clean<br>138 `dadaf68a7e` Split the LSP tests into a fast default tier and complete runs<br>143 `6b7a7431b3` Let the compiler-consumer gate accept the default tier's later cases | Test infrastructure of the server: parallel workers, the fast default tier and the CI gate that accepts it. |
| Style and concurrency fixes of the new classes | 35 `66397977b1` Declare fields first and use plain names in the new classes<br>95 `1969ec76d2` Spell XtcTextDocumentService's analyze and analyzeOne the American way<br>109 `b058623066` Compile a closure as a fold, without mutable accumulators<br>137 `216291190b` Name the properties that hold behavior or mutable state in camel case<br>44 `4838301acd` Synchronize the semantic token history that concurrent queries share<br>163 `5d1f963802` Copy a concurrent map's entries without failing when one goes meanwhile | Fold each into the commit that introduced the code it fixes (mostly commit 8): fields first and plain names, American spelling, a closure as a fold, camel case, and two concurrency fixes. |
| Keep the build's jars whole and fail on compiler warnings | 42 `9a3c3570a6` Keep the build's jars whole and its test runs focused<br>97 `cd9e3db6eb` Fail the lang build on compiler warnings, and fix the ten it had | Build hygiene of the lang build. |
| Time and heap logs of the server's work | 419 `e732aef7a5` Log each project query's and each compile's time and heap<br>420 `7bcbe3b407` Log what the server did in each minute in which it worked, and over its session | the server's own logs of what each compile, query and minute of work cost |

Kept as they are:

- 24 `240e97d8b4` Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter
- 164 `6e3f944a50` Name a server's log session by the server alone, and tell a live one by its lock
- 172 `85198dae0b` Keep the execution trace's events out of stderr and server.log
- 173 `30fb09d9e1` Let one source that discovery cannot read cost only itself
- 180 `3e7a85ff6c` Keep a project's module, source and library order the same in every JVM
- 261 `b956e87224` Keep prepareLibraryPlaybook's JVM to SLF4J's warnings

## Topic P5: Language server: semantic model and read-only editor features

**Scope.** lsp-server: the semantic model (SemanticModelBuilder, SemanticModel, PartialSemanticModel), cursor queries, hover, definition, references, symbols, semantic tokens, folding, inlay hints, code lens, completion, signature help, linked editing and formatting.

**Builds on** P4. **Commits:** 22 now, about 7 after squashing. **Added lines:** 5,888 main, 6,244 tests.

| Join into | Commits | Why |
|---|---|---|
| Completion: keywords, types of other modules with their import, method calls | 134 `6b03dd927d` Complete with the language model's keywords and built-in types<br>162 `7f97fe3ec1` Complete a type of another module where a statement starts, with its import<br>185 `da5d1eb839` Insert a method's call when its completion is chosen | Three completion features of the same provider. |
| Hover, inlay hint and code lens presentation fixes | 132 `6ea64032f0` Name a type by what it is when hovering its name<br>156 `c43311e438` Give a constructor's finally block no references lens of its own<br>157 `da8b935724` Name an inferred local's type in its hint as the source names it | Small presentation fixes of the read-only features: a type's hover, a constructor's finally block lens, an inferred local's hint. |
| Formatting: indentation while typing, switches, on-type formatting | 31 `318585b181` Keep indentation while typing, and lay out switches as the XDK does<br>135 `9956c7db87` Share the formatter's empty-block skeleton and whitespace edits<br>186 `816c3c1df6` Answer on-type formatting without waiting for the change to compile | The formatter's typing support, its shared whitespace edits and on-type formatting without waiting for a compile. |
| Bind more names in the semantic model | 441 `0268310ee3` Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration<br>442 `b6a7635f0c` Make the _ placeholder no occurrence in the semantic model<br>443 `a526cb43fb` Bind the names of a method's multiple return values to the values they declare<br>444 `0e19edf0ba` Bind this in a lazy property's calc() and super in a method reference to what they name<br>453 `0be2a5ddac` Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares<br>454 `de62643b50` Keep the initializer facts of a property declared in a loop's body, such as a static local<br>455 `4b1463832e` Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration<br>456 `7b3f229727` Declare the names of a function type's values in the semantic model, as of its return values in a typedef<br>457 `cdaa2f3207` Bind a reserved name such as this:class in a property's accessor, as in a method of the class<br>458 `0c33fd2e34` Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells | each adds a kind of binding; the compiler facts that 441, 443, 444 and 454 add go to X2 |

Kept as they are:

- 102 `91baa25918` Link a local's ranges when the caret is just after its name
- 235 `4ca5e508d8` Fold comments and runs of imports with the compiler adapter
- 347 `59059155d3` Send no signature parameters without their metadata

## Topic P6: Language server: code actions and refactorings

**Scope.** lsp-server: quick fixes, rename (with its facts and guards), moves, extraction, inlining, safe delete and the proof compiles that check code actions. Commit 8's XdkProjectQueries, XdkRename, CompilerRenameFacts, CompilerMissingMethod, XdkMissingMethods, XdkMissingDeclarations, XdkMethodExtraction, XdkLocalExtraction, XdkTypeMoves, XdkLocalInline, XdkMemberInline, XdkMemberActions and CompilerMemberAction with their tests.

**Builds on** P5. **Commits:** 32 now, about 9 after squashing. **Added lines:** 9,228 main, 12,804 tests.

| Join into | Commits | Why |
|---|---|---|
| No code action budget (drop all three) | 203 `f46f2ae33f` Wait for LSP4IJ's automatic code action answers only a budget<br>220 `9038152238` Ask the language server for full code action answers again, not ones within a budget, in IntelliJ<br>374 `98718c75dd` Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) | Net zero: 203 added a budget for automatic code action answers, 220 stopped IntelliJ asking for it and 374 removed it (it deletes 338 of 203's 381 lines). Squashed, the three leave almost nothing; check the residue and drop it if it is empty. |
| Prove code actions with incremental compiles | 171 `2aa62da092` Prove only the code actions a request asks for, and share a revision's proofs<br>192 `d5e1952233` Prove a code action by compiling only the modules its edit reaches<br>193 `b9c935eb5d` Start a revision's code action compile from the previous one's, and log its cost<br>205 `1932284f49` Keep the compiles of the modules that depend on an edited one whose API stayed<br>246 `1b2309ca05` Keep the compiles of modules that compiled when only private declarations move<br>437 `f5fc91bb8f` Start a rename's proof from the revision's compile, and compile after the edit only what it reaches<br>450 `e25a440b5e` After a rename's edit, compile only the modules depending on the edited ones that it can reach | One performance feature in five steps: prove only the requested actions and share a revision's proofs, compile only the modules an edit reaches, start from the previous revision, keep dependents whose API stayed, keep modules when only private declarations move. |
| Safely delete an unused member | 76 `8ecd396289` Safely delete an unused public or protected member<br>214 `1366d01183` Safely delete a member whose body has an annotation, as an injected local | 214 fixes 76 for a body with an annotation. |
| Extract statements | 168 `817698d2f8` Extract statements that use an injected local, passing its value<br>177 `afdeafbf48` Extract statements from a selection of whole lines | 177 extends 168's extraction to a selection of whole lines. |
| Moves: rename adopted properties, widen extraction, move and rename roots | 37 `316af5f0a6` Rename adopted properties, widen extraction, and move and rename discovered roots<br>167 `0ca1888625` Import a moved type where its new package's name is a property | 167 fixes the import of a moved type introduced with 37. |
| Rename guards and proofs | 16 `9870255836` Guard code actions and renames at synthetic bodies and locked library sources<br>70 `fdbd2f9467` Prove a rename past a register the semantic model cannot place<br>394 `bcebaee40e` Refuse a plain rename that also changes the configured source modules, and say why<br>412 `d8a27c0b61` Prove a rename in a workspace module that replaces a bundled library as the workspace's sources<br>416 `2eb0b4d163` Find a text's line breaks once per comparison in a rename's proof, not once per position<br>417 `10970d87cd` Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes<br>418 `5f44add78d` Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy<br>460 `8de836047e` Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element | Guards and proofs of the same rename: synthetic bodies and locked libraries (16), a register the model cannot place (70), the refusal of a plain rename that changes the source modules (394). |
| Quick fixes: a const with toString, Create class, declared action kinds | 145 `c73f17901f` Offer code actions in a module that has a const with a toString<br>176 `2fd170581a` Offer Create class for the diagnostic of a statement that constructs it<br>211 `9ff310cda2` Declare the code action kinds the language server's actions have | Small fixes and additions to the same quick-fix provider. |
| Say what a change to a public declaration reaches outside the workspace | 436 `1adc13a4b4` Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed<br>445 `b4d0ef6218` Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed | renames, safe deletes and file moves say when a public declaration changes for modules outside it |
| A client's cancellation reaches the adapter's work | 438 `e9beeed169` Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work<br>451 `9e39405f9c` Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done | cancelling a rename or xtc/build stops the adapter's work, also once a result is converted |

Kept as they are:

- 73 `ec09eb0fe0` Inline every use of a private member and remove it

## Topic P7: IntelliJ plugin, built against LSP4IJ 0.30.1

**Scope.** lang/intellij-plugin main and unit tests (not the playbook): the LSP4IJ integration, settings, restart, rename and move handlers, Undo of server edits, Project Errors, the Ecstasy PSI and editing, Code Style, live templates, tips and Got It tooltips. Commit 8's intellij-plugin/src/main and src/test, written against LSP4IJ 0.30.1 from the start.

**Builds on** P6. **Commits:** 70 now, about 26 after squashing. **Added lines:** 10,268 main, 6,424 tests.

| Join into | Commits | Why |
|---|---|---|
| Send the server a file's current text when its didOpen was older (UP83) | 397 `a9215634a0` Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) | A workaround that stays until a release has redhat-developer/lsp4ij#2127, unlike the ones that fold away: LSP4IJ 0.30.1 loses an edit made while it connects a file. Its TourScript part, which drops the kill step's wait (4 of the 7 lines 391 added), goes to P12 and joins 391. |
| Build against LSP4IJ 0.30.1 | 325 `3d46dea11b` Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for<br>364 `4f8d11debe` Build against LSP4IJ 0.30.1, which is 0.30.0's code<br>356 `81bf2290a8` Review the internal LSP4IJ APIs for 0.30.0<br>392 `a435dfbef5` Name LSP4IJ 0.30.1 in the plugin's README and two test comments<br>398 `28e0223afe` Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83)<br>400 `43dfde02ca` Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) | Two version bumps (0.30.0, then 0.30.1, the same code), the API review and the README naming them: one commit, first in the PR, so that nothing before it is written for 0.21. |
| Fold into the commits that added each workaround | 326 `fb33bc2c54` Decode related diagnostic reports with LSP4IJ's adapter (UP06)<br>327 `b9bd8413a8` Keep explicit null settings with LSP4IJ's adapter (UP08)<br>328 `8747b265b8` Let snippet completions insert as they are (UP18)<br>339 `08794343f1` Use LSP4IJ's own Parameter Info for Ecstasy (UP10)<br>340 `82052a01d8` Let LSP4IJ reconnect documents below a moved directory (UP19)<br>341 `b84640b640` Let LSP4IJ batch the editor refreshes (UP27)<br>342 `0e89d23fc8` Let LSP4IJ watch the compiler roots outside the project (UP05)<br>343 `ba7f464bff` Let LSP4IJ resolve and apply lazy code actions (UP11)<br>344 `14b2396d74` Let LSP4IJ keep pull results and quick fixes (UP07)<br>346 `fc17d62b35` Let LSP4IJ's process provider own its start and stop (UP01)<br>348 `a3f3824198` Let LSP4IJ save the documents a rename changes without an editor (UP21)<br>359 `ca24b41755` Let LSP4IJ keep a disposed language server stopped (UP76)<br>361 `bdbf2c159c` Let LSP4IJ keep the problem mark of a file that opens or closes (UP70)<br>372 `bda15073af` Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77)<br>393 `abc6184faf` Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement | Each removes a workaround for LSP4IJ 0.21 (UP06, UP08, UP18, UP10, UP19, UP27, UP05, UP11, UP07, UP01, UP21, UP76, UP70, UP77, UP14) that commit 8 or a later commit added. With the PR built against 0.30.1 from the start, neither the workaround nor its removal needs to be in the history. |
| Document notification order (net almost zero) | 84 `bcac61d569` Drop the change a restart's retired document synchronizer still sends<br>345 `c4e16524b3` Let LSP4IJ order document notifications and check server edits (UP09, UP04) | 345 deletes 66 of 84's 70 lines: 84's workaround for a retired synchronizer goes once LSP4IJ orders document notifications (UP09). |
| Held edits before completion and signature help (net small) | 181 `fe0c8e29af` Send LSP4IJ's held edits before it asks for completions or a call's signature<br>363 `bad5b2f9e7` Let LSP4IJ send the edits it holds before a request (UP73) | 363 deletes 136 of 181's 234 lines: LSP4IJ sends its held edits itself (UP73). |
| Completion item documentation on Ctrl+Q (net small) | 222 `e622254f40` Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve<br>245 `6200692789` Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ<br>369 `a126b6d6c3` Let LSP4IJ document a completion item from its resolve (UP78) | 245 rewrote 222 with public API, and 369 removes most of it (75 of 245's 83 lines): LSP4IJ documents an item from its resolve (UP78). |
| Project Errors: a closed file's errors and the embedded build's | 191 `88b5af8274` Get a closed file's errors to IntelliJ from the server's workspace report<br>221 `660f4b6ca8` Put the embedded build's errors in the Problems window's Project Errors<br>292 `b3cefcc6a9` List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one<br>294 `e2ddb1f89c` Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs<br>295 `cb55f2a6bc` Report the embedded build's errors to the Build window as messages to their file and line<br>373 `e644ff80a6` Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) | One feature built over six commits; 373 deletes 381 of 191's 910 lines once LSP4IJ pulls workspace diagnostics for closed documents (UP69). |
| Undo of a server's edits | 165 `90080aca8b` Undo a server's edit of one file without asking, as IntelliJ's quick fixes are<br>212 `880af0f3c0` Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog<br>213 `fb7d659ae4` Test that Undo takes a resolved code action's edit of one file back without asking<br>223 `cfc0517243` Check that Ctrl+Z's Undo action takes a resolved code action's edit back<br>254 `0ef798faec` Ask before Undo takes back a rename or move that changed several files | Undo of one file's edit without asking, no edit for a rename Undo makes, its tests, and the question before Undo takes back a rename or move of several files. |
| The Ecstasy PSI and typing in an Ecstasy file | 152 `d3917775f1` Give Ecstasy files a PSI of the Ecstasy language<br>153 `1c2a11ba2b` Close a block that Enter opens after a { in an Ecstasy file<br>155 `874bc44ea0` Keep an Ecstasy file's brackets and words as plain text had them<br>182 `a01c493962` Indent the rest of a line that Enter moves into the block it opens | The PSI, Enter after a {, plain-text brackets and words, and the indentation Enter moves. |
| Code Style page and live templates | 139 `fac24efb78` Give Ecstasy its Code Style settings page<br>140 `1730374c77` Load the Ecstasy live templates, and offer them in Ecstasy files only | Two small editor settings additions. |
| Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | 302 `bf48463a6e` Restart the Ecstasy language server with Ctrl+Alt+X, then R<br>307 `0d4f148d6e` Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors | The same key scheme; VS Code's half (304) goes with P8. |
| Tips of the Day and Got It tooltips | 380 `9a6d2d4271` Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first<br>388 `327de73b5f` Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly | Onboarding hints of the same kind. |
| Light test fixes | 207 `e9b8b3dde4` Keep each light test's stub server and wrappers to that test<br>209 `67c607b82d` Tag the light tests' workarounds of two LSP4IJ gaps<br>285 `efdaa408f0` Give a document gone from the stub server's workspace report an empty report, as the real server does<br>297 `513782f68f` Keep the stale instrumented classes of an older build off the plugin's unit tests | Fold into the tests they fix. |
| Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | 377 `b88642f6e0` Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116)<br>395 `b57b1f0e0f` Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) | Two comments that explain why kept workarounds stay (UP21, UP04). |
| The language server JVM's memory setting | 421 `d2497070e2` Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive | a setting in both editors with its server side; divide by path |
| Every IntelliJ settings page under one Ecstasy node | 425 `088a676cdc` Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy<br>427 `f073d9fc19` Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide | one Ecstasy node in IntelliJ's settings, its pages fitted to the dialog |
| Marks that LSP4IJ made before it connected a file's document (UP86) | 475 `eadc61d407` Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) | refreshes an editor's marks once the connected document holds its own report; lsp4ij#2231 and our fix #2232 would retire it |

Kept as they are:

- 14 `63bc04ce0f` Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox
- 17 `3b6cc7d992` Restart editor-owned language servers from a project-scoped CLI
- 32 `abf0302b28` Read in cancellable read actions where a long read could freeze IntelliJ
- 40 `1cea6defdc` Cancel IntelliJ requests whose connection ends
- 99 `768fab3872` Keep the language server running while a run or debug session uses it
- 130 `240a3c0892` Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix
- 136 `f789b18ffe` Build an embedded run's process handler in one place
- 154 `70c25d0b0a` Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports
- 299 `09e084bb12` Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise
- 357 `bf8f27c441` Describe both save formatting owners in IntelliJ (UP02)

## Topic P8: VS Code extension

**Scope.** lang/vscode-extension main: the language client, commands, settings, moves with refactoring auto-save off, Restart and build mode shortcuts, the Get Started walkthrough and the extension's build. Commit 8's vscode-extension/src (not its tests).

**Builds on** P6. **Commits:** 10 now, about 6 after squashing. **Added lines:** 2,678 main, 341 tests.

| Join into | Commits | Why |
|---|---|---|
| Moves in the Explorer | 77 `bfe6748010` Apply the Ecstasy edits of an Explorer move with refactoring auto-save off<br>238 `a7b6eba728` Say when a move leaves the uses of its sources as they were<br>250 `2061a784f2` Ask the server again for a move's edits when a change overtook its answer | Apply a move's edits with refactoring auto-save off, say when a move leaves uses as they were, ask again when a change overtook the answer: one feature. |
| Fold into the extension's client commit | 71 `d2b6cc293f` Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded | With vscode-languageclient 11 from the start, its startup workarounds need not appear. |
| Extension build: clean out/, dev tool advisories | 217 `b4ced017b1` Compile the VS Code extension's TypeScript into an empty out/<br>272 `1478639a6f` Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories | Build hygiene of the extension. |

Kept as they are:

- 119 `43abe61716` List every VS Code command in the extension README, by its own title
- 234 `62b4ed8962` Trace the language server's messages when xtc.trace.server says so
- 304 `b26c23a702` Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R
- 381 `94995180eb` Add a Get Started walkthrough to the VS Code extension

## Topic P9: Embedded build, run and tests from the editors

**Scope.** The server's run/ package (execution workers, embedded builds, console input), xunit runs and reports, xtc tool and Gradle plugin changes for test modules, and the run and test UI of both editors (run icons, run configurations, test consoles, Run All tests).

**Builds on** P4 (and P7, P8 for the editor parts). **Commits:** 55 now, about 23 after squashing. **Added lines:** 10,541 main, 9,770 tests.

| Join into | Commits | Why |
|---|---|---|
| Runtime: report a dropped Future callback and a failed service shutdown | 170 `44f24a745e` Report a Future callback that throws or that a terminated service drops<br>198 `6dc9eb14d2` Complete a service's shutdown however it goes, and report its failure | Two runtime reporting fixes that embedded runs exposed. |
| Report the embedded runs the server tracks | 239 `89c90d933a` Say how many embedded runs the server tracks, and where a test is | Fold into the workers group. |
| Build and run with the embedded compiler, the default build mode | 20 `fda8e8963a` Build and run with the embedded compiler in the server and both editors<br>27 `01b20d7c01` Make embedded the default build mode, chosen per editor<br>179 `8568b0fc32` Build a Gradle-imported project embedded in embedded mode | The embedded build and run, embedded as each editor's default, and a Gradle-imported project built embedded in embedded mode. |
| Run the editor's text unsaved, with console input | 25 `9bfe8024d5` Let embedded runs read console input in both editors<br>34 `9309be121f` Build and run the editor's text unsaved, and report why a run cannot start | Two halves of what a run reads. |
| Reuse unchanged modules, rebuild when files change while compiling | 26 `d8bb02ae62` Build the editor's snapshot and reuse unchanged modules in embedded builds<br>269 `4d4e4d936d` Compile a run's embedded build again when its files change on disk as it compiles | 269 fixes 26's snapshot reuse. |
| Supervised execution workers | 29 `e3e6d9eee0` Run programs side by side in supervised execution workers<br>33 `cf96081ddd` Fix two close races in embedded runs and cover the embedding API's runs<br>39 `0ccd3edad1` End runs and workers cleanly, and measure them without waiting for the collector<br>57 `2c2258cb83` Soak runs and debug sessions on one worker, and let finished runtimes go<br>113 `0d75d2ae97` Lift the lazy-value time limit for the embedded execution tests' worker<br>464 `ef681e7cff` Take the execution worker's events off its standard output, onto a loopback socket of their own | Workers (29), their close races (33), clean ends (39), soak runs and runtimes let go (57), the lazy-value time limit (113). |
| xtc deduction of a project's modules and its test module | 107 `b79c186a5c` Let deduction find a named module in src/main/x beside other sources<br>178 `23de8c34d2` Let xtc test, run and build take a project's test module with its modules | Both in xtc's tool code. |
| Run and debug a module's xunit tests, reported as xunit's JSON events | 108 `e598612970` Run and debug a module's xunit tests from the editors, embedded<br>112 `5dd674db72` Report tests as xunit's own JSON events, which the language server reads once | 112 replaced 108's report format (it deletes 197 of 108's lines). |
| IntelliJ's test console: run, debug, rerun failed tests | 110 `f910b34173` Run and debug Ecstasy tests from IntelliJ, in its test console<br>158 `18c008f9ba` Rerun only the failed Ecstasy tests from the test console<br>202 `31e85f6842` Show Rerun Failed Tests on an IntelliJ test run | One IntelliJ feature in three commits. |
| Run All Ecstasy Tests in IntelliJ | 206 `3b7e4d788b` Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests'<br>219 `33095b5afd` Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources<br>224 `06dd3c0487` Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it<br>268 `7126256dec` Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders<br>428 `5fd5523007` Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own<br>433 `a66a7fba2c` Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc | The action, where it is offered (Ecstasy-only and mixed test folders) and its configuration reuse. |
| Gradle and command-line runs and tests from both editors | 111 `6b14581128` Run and debug tests in Gradle and command-line mode, from both editors<br>233 `1232f03511` Pass a run task's args in Gradle and command-line mode<br>236 `740e9ac34a` Run the editor's module from its Run button and context menu | Task args and the Run button: the same mode support. |
| Gradle plugin fixes | 98 `2dd267a52d` Don't reuse a module compiled in another copy of the project<br>204 `a6e77c2797` Run testXtc whenever a tool reads its test report, never from the cache<br>244 `0b527759f4` Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal | Small plugin fixes needed by the editors' Gradle mode. |
| Describe running and testing in the READMEs | 117 `8cbe45a3ae` Describe running and debugging tests from both editors in the READMEs<br>121 `b7c5d177e3` List the editors' test, attach and debug-task sources, and when xtc is needed | Fold into this PR's docs. |
| Library replacement in Gradle mode, per workspace folder, with the editors' setting | 406 `3a82d3e6e7` Build, run and debug with Gradle while the workspace's own modules replace bundled libraries<br>415 `60b393f7a3` Add an editor setting for whether the workspace's own modules replace bundled XDK libraries<br>422 `b9dda07133` Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded<br>423 `15bfb50c98` Show the embedded compiler as not available where the project's own modules replace bundled libraries | the workspace's own modules replacing bundled libraries: in Gradle mode, kept to the folder that replaces them, the editors' setting, and the embedded compiler shown as not available; the editor parts need L6 and L7, so the group goes to L8 |
| The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | 407 `f2db77f964` Name each source set's project by its path in the build tree in the IDE's Gradle model (#774)<br>465 `e9e8be2f8c` Export each project's xtcRun and xtcTest settings in the language server's Gradle model<br>470 `6953dfb86b` Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's | the plugin's model export that the server reads; 470 exports the JVM arguments that #804's allJvmArgs give |
| Gradle tasks in each module's own project (#774) | 408 `d3c2ec5b91` Tell the client each module's Gradle project from the imported build model (#774)<br>409 `e729849350` Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774)<br>410 `cce3b4f0d7` Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774)<br>411 `3ef0e0e643` Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) | the server names each module's Gradle project, and both editors run its tasks there, reaching a composite's included builds |
| Embedded runs with their Gradle project's xtcRun and xtcTest settings | 466 `b4d7869f07` Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors | embedded runs take the Gradle project's method, arguments and JVM options, from 465's export |

Kept as they are:

- 30 `672e43128b` Find entry methods through the module's type and pass them with their arguments
- 38 `76be500964` Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle
- 133 `8c369c66d1` Deliver a run's events to dap-server's ProgramEvents, not a copy of it
- 188 `8645d1535a` Report a stopped run's end without the failure its stop causes
- 199 `29c84446ed` Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests
- 216 `ddc78304c2` End the tests of the modules a stopped VS Code test run did not reach

## Topic P10: Debugger: runtime debugger, DAP server and the editors' debugging

**Scope.** javatools' SessionDebugger and its agent, the dap-server, the language server's debug sessions, launch and attach (embedded, Gradle and command-line), and the debug UI of both editors. Large: review it as two PRs, P10a (javatools and dap-server: 46-53, 55, 59-63, 65, 72, 75, 78, 80, 91, 101, 103, 141, 142, 166, 169, 174, 183, 215) and P10b (launch, attach, agent, editors: the rest).

**Builds on** P9. **Commits:** 60 now, about 36 after squashing. **Added lines:** 17,235 main, 15,203 tests.

| Join into | Commits | Why |
|---|---|---|
| Attach the debugger to a program run with --debug, with the agent in javatools | 79 `9204f4d102` Attach the debugger to a program run with --debug<br>105 `0c0a9691a2` Move the debugger agent into javatools, with the protocol as records<br>114 `c7fa9aff96` Debug with the agent jar's provider by its name, and javatools' own without one<br>368 `03f642f0f1` Attach in LSP4IJ's attach mode, with the session's own process (UP64) | 105 moved 79's agent into javatools (it deletes 870 of 79's lines), 114 names the agent's provider, 368 attaches in LSP4IJ's attach mode (UP64). |
| The debugger finds sources where its host names them | 318 `b13c434a6f` Let the debugger find a module's sources where the language server says<br>383 `ce0ef95839` Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled<br>396 `26ef1105db` Name a launched module's source directory from the file the launch names it by, when the source graph lacks it<br>405 `445227624d` Find a debugged module's sources in the workspace when no source graph names it | One change in three commits: the host gives the source directories, nothing reads them from a binary, and a launch names its module's directory by its file (today's X337 fix). |
| Debug through the language server | 46 `0396f075ac` Debug programs from VS Code and IntelliJ through the language server<br>72 `f14885a215` Send a step's response before the stop where the step ends | 72 fixes the step response order of the DAP server. |
| Stop at exceptions, pause, stop on entry | 48 `15cc5a339d` Stop at exceptions, pause a running program, and stop on entry<br>75 `76d5eb57ca` Stop at an uncaught exception that only the runner would catch | 75 fixes 48 for an exception only the runner catches. |
| Conditional breakpoints, hit counts, logpoints | 50 `d74d25488f` Conditional breakpoints, hit counts, logpoints and exception classes<br>183 `1fc175f165` Say on the debug console why a breakpoint's condition cannot be evaluated | 183 explains an unevaluable condition. |
| Lazy properties and their time limit | 55 `53b826d8e9` Lazy properties, hexadecimal values, paged arrays, Set Value, completions<br>78 `b956ba8d71` Let a host give lazy variables' code longer than ON_DEMAND<br>431 `523b1d7dff` Let a debug launch or attach set how long evaluations and lazy values may run | 78 lets a host give lazy variables longer. |
| Restart a stopped frame | 62 `0c20a90704` Restart a stopped frame, which its caller then calls again<br>215 `6084f33e44` Restart a frame whose fiber ran code in its stop | 215 fixes it for a fiber that ran code in its stop. |
| SessionDebuggerTest made independent of timing | 80 `6f37a8f23d` Let SessionDebuggerTest's runs show they started before it ends them<br>91 `4ef8742974` Let two SessionDebuggerTest cases check what their sleeps waited for<br>142 `c0ee779c3c` Lift SessionDebuggerTest's time limit to an hour<br>166 `d513e313fd` Make SessionDebuggerTest independent of how its threads are scheduled | Four fixes of the same test class; fold into the commit that added it. |
| A line typed in the Debug console (net small) | 159 `2032463673` Take a line typed in IntelliJ's Debug console to the debug session<br>362 `479d570a01` Let LSP4IJ evaluate a line typed in the Debug console (UP71) | 362 deletes 102 of 159's 358 lines once LSP4IJ evaluates the line (UP71). |
| Physical expression fragments (net small) | 197 `3856d6f3e6` Make the debugger's expression fragment physical, which ends a paused session's daemon loop<br>360 `f4df953e04` Let LSP4IJ make the debugger's expression fragments physical (UP37) | 360 deletes 55 of 197's 100 lines once LSP4IJ makes them physical (UP37). |
| Run to Cursor's breakpoint (net small) | 225 `02f566d112` Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ<br>367 `5f06796f9b` Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) | 367 deletes 113 of 225's 155 lines once LSP4IJ keeps the breakpoint (UP44). |
| A library frame's source in a folder the VFS has not seen (net almost zero) | 89 `4cb6820ac6` Let the VFS find a library frame's source before LSP4IJ reads the frames<br>366 `cfdf0286c1` Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) | 366 deletes 89 of 89's 94 lines (UP65). |
| Debug Gradle-mode and command-line runs | 96 `7a6c404fb4` Debug Gradle-mode runs in VS Code and IntelliJ<br>104 `0b25542f99` Debug a command-line run: xtc run runs the program, which connects<br>149 `6768fbb784` Wait for configurationDone before a debug session starts its program | Gradle-mode and command-line launches through the same task path, and configurationDone before the program starts. |
| VS Code breakpoints and F5 | 231 `9afda7520b` Let VS Code set breakpoints in Ecstasy files<br>232 `accd06aead` Debug the module of a class's file with F5 | Two small VS Code debugging additions. |
| Describe debugging in the READMEs | 116 `2d9c0acb51` Describe the debugger's attach and R9-R12 features in the READMEs<br>129 `af14341f95` Name javatools' debugger, agent and provider in its README | Fold into this PR's docs. |
| End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | 401 `f64c680167` End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) | LSP4IJ 0.30.1 keeps the exit code from the exited event; one commit |
| Debugged stacks end at the host's fiber; failed requests say why | 439 `2b5287a637` End a debugged program's stack at the host's fiber, also when it has moved on from its call<br>440 `db2c9d725a` Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." | the runtime ends a debugged program's stack at the host's fiber (X4), the DAP server answers a failed request with its reason (L9) |

Kept as they are:

- 47 `bbeb8b2920` Show a stopped frame's variables, and step over, into and out of calls
- 49 `9cda9012e5` Evaluate watches and hovers on paths in a stopped frame
- 51 `4989630717` Evaluate expressions with operators without running the program's code
- 52 `5f407bff65` Leave the runner's frames out of the debugger's stacks and traces
- 53 `4a8e95649b` Run code typed in the Debug Console on the stopped fiber
- 54 `cee2155822` Stop at the program's first line from IntelliJ's configuration
- 56 `6ba9609d74` Place breakpoints on the lines a debugged program was built from
- 59 `71e1afae38` Show library frames in their bundled source, every fiber, and modules
- 60 `1ea3737ec0` Step into a chosen call of the stopped line
- 61 `d31a474ee5` Stop where a method of a name is entered
- 63 `94758a7f98` Stop once a watched field of an object has changed
- 65 `80d6092466` Answer inline values with the stopped method's variables
- 101 `f8844b58ed` Continue, step or pause one fiber while the others stay stopped
- 103 `e1ccde27ab` Test that a part of a value's variables from past its end is empty
- 106 `6184012f1c` Keep a debugged task's last output, and X325's queued fiber
- 141 `beb55a20c8` Debug a program that has an interface
- 169 `cc946d1ce6` Keep earlier debugger stops out of a clock alarm set for a given time
- 174 `6b1c6d3de2` Let a debugger condition name an enum value by its enumeration
- 195 `f1b8d24438` Show a stopped run as stopped, without what its stop made it print

## Topic P11: The XDK's library sources and the XDK workspace

**Scope.** Navigation into the bundled libraries' sources (ecstasy-library documents, hover and definition there, frames at their methods), the XDK's own sources as a workspace, duplicate module names, and the server's temporary directories. Commit 8's XdkLibrarySources and XdkAttachedSources.

**Builds on** P5. **Commits:** 22 now, about 9 after squashing. **Added lines:** 3,318 main, 3,199 tests.

| Join into | Commits | Why |
|---|---|---|
| Navigate the XDK's library sources | 267 `45dfa00770` Answer hover and Go to Definition in a library copy that the debugger opens<br>274 `8b6ed673bf` Navigate the XDK's library sources: definitions from their names, frames at their methods<br>278 `de9a267667` Compile an opened XDK source with its whole library, for navigation in all of it<br>280 `eea82770c9` Compile a library source's ecstasy-library content with its library, as its copy is | Hover and definition in a library copy, definitions from names and frames at methods, an opened XDK source compiled with its library, and ecstasy-library content compiled the same way. |
| Library copies: no diagnostics, compiled on their own | 218 `a8856e17fa` Report no diagnostics for a library source's read-only copy<br>249 `48385a095c` Compile a library source's copy on its own even once a compile has named it<br>463 `6f1818cfe0` Keep diagnostics working when a restart opens another server's library copy again | Two fixes of the same copies. |
| Temporary directories and their locks | 275 `14e42ccd1d` Delete the language server's temporary directories once no server needs them<br>277 `b11549955a` Keep a server directory's lock beside it, where reading the directory cannot release it | 277 fixes 275's lock placement. |
| Discovered modules that share a name | 311 `daf60a5108` Keep discovered modules that share a name out of the graph, instead of failing every file<br>313 `7d580a2d44` Warn of duplicate discovered module names only while the discovered graph is in use | 313 limits 311's warning to the discovered graph. |
| The XDK's own libraries from source | 312 `8ea37090c4` Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK<br>314 `13d06d234a` Replace a bundled library with any workspace module of its name, however the project is configured | 314 generalizes 312's replacement of the bundle. |
| Replaced libraries: their import candidates and their errors | 413 `f0d5e201a6` Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's<br>414 `5f31de3524` Say on a replaced library with errors that the server's own compiler may be older than the repository's | two server follow-ups of library replacement |
| Compile only what an edit changes: declaration digests and the member-use tracker | 448 `c9d943aa62` Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies<br>449 `362d7dcfc4` Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do<br>461 `f992a7a0d2` Digest the API of each declaration of a module apart (DeclarationDigests)<br>462 `36881faadc` Compile again, after an edit, only the modules that use what it changed (member-use tracker)<br>469 `a14af8d820` Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own | DeclarationDigests in javatools (X2), the artifact API fingerprint and the member-use tracker in the server (L5); 469 fixes 461's Javadoc, fold it in |

Kept as they are:

- 45 `b5e76dd608` Keep moved module containers as missing inputs, and stop refetching library documents
- 151 `f32bea2634` Show a library declaration's doc comment in hover, from its source

## Topic P12: Test infrastructure: playbooks, scenarios, tours and their container

**Scope.** Test code only: the IntelliJ and VS Code playbooks and their probes, the scenario catalog and the manual test plan, the editor tours and their video, and the playbook container. Commit 8's intellij-plugin/src/integrationTest, test-fixtures and lang/doc/manual-test-plan.md. Reviewers can skim it; it goes last because it tests everything above. Split into P12a (playbooks, scenarios, container) and P12b (tours and video) if it is too large to read.

**Builds on** P7-P11. **Commits:** 112 now, about 20 after squashing. **Added lines:** 2,967 main, 57,358 tests.

| Join into | Commits | Why |
|---|---|---|
| The IntelliJ tour, its recording and video | 150 `796dde1a22` Record a tour of the IntelliJ plugin on a card game, checking what it shows<br>229 `4b38f80016` Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state<br>230 `0d221e805b` Compose the tours' video from their recorded clips, each step from a run where it passed<br>242 `1b9891fcf8` Give a VS Code tour's run its screen recording, and every run only its own, in the container<br>262 `3914899a3b` Print the IntelliJ tour's progress in the build's output, step by step<br>264 `719593a1ba` Report the written video's real length from the tour composer<br>276 `432b93c38d` Name the tours' video and deck after their editor, tour-intellij and tour-vscode<br>286 `3d037b40c2` Compose recorded tours again, and write the deck to its own file, in the tour composer<br>227 `9355c49946` Record a playbook run's screen as video in the container<br>228 `0f5cb32d81` Delete a playbook run's IDE folder once its logs are copied, in the container | The tour (150), its feature checks and hang detection (229), the video composer (230, 264, 286), its progress output (262), names (276) and the container's recording (227, 228, 242). |
| IntelliJ tour step fixes | 252 `8d0a78084a` Press the keymap's own Undo key in the IntelliJ tour<br>253 `3a1789ae18` Undo in the IntelliJ tour from the editor as the change left it<br>255 `629f52477b` Answer Undo's question in the IntelliJ tour and playbook as it offers<br>256 `5a20d6c7a2` Rename a method of four files in the IntelliJ tour, and show Undo's question<br>260 `18bdf7d90a` Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor<br>270 `df5673dcdc` Wait in the IntelliJ tour for the new server's didOpen before File Structure<br>287 `b4779a6f68` Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour<br>390 `7153d75cdb` Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82)<br>391 `700cac8211` Edit in the tour's kill step only once the restarted server has the file open (UP83)<br>403 `ba51925a77` Check that one server comes back when the tour kills the server's process (UP72)<br>404 `4d59b37a62` Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will<br>447 `75f8d0d814` Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames<br>476 `39a446378d` Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place | Fixes of individual tour steps (Undo, Go to symbol, didOpen, Debug of Current File, UP82, UP83): fold into the tour. |
| The VS Code tour | 243 `35a442d4a0` Record a tour of the VS Code extension that checks what it shows<br>251 `d15fb6ee22` Make the VS Code tour pass as one whole run, unrecorded<br>257 `c78fdbb55b` Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z<br>258 `1dacbba13f` Check in the VS Code tour that Ctrl+Z takes a quick fix back whole<br>259 `63368314a0` Show the VS Code tour's Undo of the four-file rename as steps<br>271 `5219c458e4` Wait in the VS Code tour for the $xtc problem matcher's error after a failed build<br>288 `3ce094ef95` Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not<br>389 `f34925546b` Step X306 only once VS Code shows the stop, and read each step's stack from after its stop | The tour and its step fixes. |
| Detect hangs, not slowness | 148 `4126419fcc` Let tests wait for a hang only, not for a fast machine<br>194 `318ca5b8a1` Find a test's hang by the CPU it stops using, not by an hour on the clock<br>265 `925c1fcc37` Fail the debugger probe's waits when nothing is in progress for a minute<br>310 `0799fa6612` Let a playbook run shorten its hang bound with -PintellijPlaybookHang<br>321 `8a43ce5499` Count an IntelliJ playbook case as hung after ten minutes, not an hour | One rule applied in five commits: wait for a hang only, find it by the CPU a test stops using, fail the debugger probe after a minute of nothing, shorten the bound, count a case hung after ten minutes. |
| Diagnostics waits accept only a final report for the current text | 93 `0aa134df86` Let the playbooks' diagnostics waits accept only a report for the current text<br>240 `4dc1492a13` Fail the playbooks' diagnostics waits once their reports are final | 240 completes 93. |
| Tell the IDE of disk changes, and wait until the server has them | 300 `96899af636` Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340<br>306 `acec5aa6b4` Move and rename through the playbook's probes only once IntelliJ has finished indexing<br>308 `37c9135e45` Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them<br>319 `0a14104960` Wait in the VS Code playbook until the server has heard of a case's changes on disk<br>322 `b14afd4d1e` Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change<br>323 `ff2a02565c` Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet<br>324 `5e5675c80f` Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record | The same synchronization problem fixed case by case in both playbooks. |
| The playbook container and the IDE it drives | 90 `e9bf44ed62` Run the IntelliJ playbook in a Linux container on a virtual display<br>94 `3e25e1ef9f` Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal<br>100 `48dbd1f1bf` Run the editor playbooks in the container from Gradle, with any engine<br>146 `8cf093e9ff` Queue playbook container runs on one machine lock, each with its own export<br>147 `649be2d801` Start the playbook's IDE from one place, without the plugins it does not use<br>309 `645780fc9c` Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3<br>432 `22a8e6ccbc` Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode | The container (90, 94), Gradle tasks with any engine (100), the machine lock (146), the IDE start (147) and IntelliJ 2026.3 as the driven IDE (309). |
| The playbook on LSP4IJ 0.30.1 | 329 `86ecd91c25` Offer server save formatting in IntelliJ (UP02)<br>330 `94be2a2012` Open bundled library sources as the server's virtual documents (UP25)<br>331 `3282c34557` Check inline completion's invocation and selection natively (UP26)<br>332 `d444a40688` Open a document link from Ctrl+Click, and retire the replacement gates (UP52)<br>333 `27be81309c` Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50)<br>334 `047d6da067` Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55)<br>335 `6b6ecfd15e` Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46)<br>336 `dfc70eb4be` Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42)<br>337 `f01183901e` Check that IntelliJ rebuilds its debugger views on invalidated (UP40)<br>338 `b894d76c21` Check what a chosen completion inserts in Evaluate Expression (UP37)<br>349 `ee7d40b9e7` Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60)<br>350 `74583e7499` Read a signature without parameters in the playbook<br>351 `170c2ab192` Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0<br>352 `8c0290e358` Fix the native run's X254 for LSP4IJ 0.30.0<br>353 `dbfcb61716` Check X254's library view through the server<br>354 `e375ba370a` Read X5's library target from the server until LSP4IJ connects it<br>355 `a0907aa036` Wait for X131's Ctrl+Click resolve in the console's trace<br>358 `ec5af0c35a` Expect X20's signature without parameters in VS Code<br>365 `3611a12607` Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it<br>370 `2c50948d4c` Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75)<br>371 `ad1112c79f` Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09)<br>375 `4ffcaba78e` Ask X254's definition again when the restarted server retires its analysis<br>376 `02942c8ac3` Check X350 in IntelliJ through the server's library documents (UP25)<br>378 `a6a079a5f5` Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view<br>399 `c641f5133d` Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81)<br>402 `4456c878aa` Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) | The cases that check LSP4IJ 0.30's fixes natively, and the case fixes the new version needed. With the playbook written for 0.30.1 from the start, these fold into the cases they change. |
| Individual playbook case fixes | 85 `d67d3bd7e4` Wait for LSP4IJ to reopen X259's consumer after the restart<br>86 `9c81bd802a` Send X259's library edit to the old server before the restart<br>87 `feb20b5276` Wait for X257's Invoked request to reach the LSP console<br>88 `1445c8cecf` Reopen a popup under inspection after the IDE was deactivated<br>175 `0d978d828a` Give X145 a reference search that waits until it is canceled<br>282 `044721afa5` Wait for a fully decorated editor before the UP17 probe's decorated replacement<br>291 `9464241317` Make the UP17 probe's decorated replacement start past the highlighter tree's root<br>303 `1e2390fd99` Treat the driver's own Structure tree expansion timeout as not yet expanded<br>320 `9055993c43` Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols<br>382 `c71a0704bd` Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open<br>446 `0a519c3c89` Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 | Fold each into the case it fixes. 320 (an LSP4IJ before 0.30.0) can go: 0.30.1 is the baseline. |
| Playbook cases added after the catalog | 22 `056ec44f6d` Cover embedded build, run, debug launches and the adapter chooser in the shared playbook<br>241 `4b095e8da9` Add playbook cases X342-X349 for xunit tests, in both editors<br>281 `3e0bddc9dd` Add playbook case X350 for navigation inside an XDK library's source, in both editors<br>293 `0a04f005b3` Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one<br>296 `0f41104d2f` Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target<br>58 `81c3473897` Check Run to Cursor in both editors<br>64 `1ef69e690b` Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes<br>66 `2376f753b2` Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ<br>67 `efa467b8bb` Check a document link's Ctrl+Click in IntelliJ's X131 | New cases (run and debug, X342-X349, X350, X341, Run to Cursor, debugger checks, X131): keep with the catalog, or with the feature PR whose behaviour they check. |
| Playbook documentation | 118 `e63dfedb36` Give the playbook catalog's size as its 342 cases everywhere<br>122 `236051b22a` Point the playbook instructions at the playbook container<br>127 `85bfaa0998` List the test and debug-task messages and a test row in the playbook's 18a | Fold into the catalog's documentation. |
| What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | 473 `cc77837927` Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered<br>474 `450eaa3e97` Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it | the playbook keeps each server's whole trace, with each pull's answer at DEBUG only, and the tour's waits and popup steps say what LSP4IJ holds and what could close them |

Kept as they are:

- 43 `2b19fc6d17` Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean
- 74 `8b60782ee5` Apply X103's rename as a refactoring and check the member after Undo
- 187 `306e0cb7e3` Check that Debug of Current File launches an Ecstasy configuration
- 189 `160369ac85` Check 7a.9 against the compiler's error budget, which the server keeps
- 190 `493427d6d1` Run the VS Code playbook after the extension suite, not with it
- 200 `6e0f3f202f` Count the code actions at the tour's unused import and unused local
- 247 `46635b9e3a` Expect the card game's Hand to keep heading when Safe delete removes display
- 263 `fc6e889298` Print both compiler playbooks' progress per case in the build's output
- 279 `5b27f15145` Keep Python bytecode out of the repository

## Topic P13: Documentation, build and CI

**Scope.** The lang docs and READMEs, the configuration reference, packaging and install tasks, the PR previews, and build hygiene. Fold feature documentation into the feature PRs where it describes them; keep the rest here.

**Builds on** the PRs it documents. **Commits:** 31 now, about 4 after squashing. **Added lines:** 5,683 main, 372 tests.

| Join into | Commits | Why |
|---|---|---|
| The lang documentation | 21 `f59e9f3524` Rename the compiler guide and testing documents, and correct stale documentation<br>68 `792ae9d39f` Drop the references to documents and plans this branch does not carry<br>69 `e450c182b6` Describe debugging, and the run changes, in the server and editor READMEs<br>120 `6f8839b71f` Fix lang/README's task paths, versions and feature summary<br>123 `6e3eed1855` Use American spelling in the shippable docs<br>124 `a79370d53a` Name testing in the embedded build and run index entry<br>125 `373be002b2` Name the editors' UI as it reads, Ecstasy, where the docs said XTC<br>126 `f2027e0ac3` Call the server and the IntelliJ plugin by their names in the lang docs<br>128 `84882c33bc` Say that every adapter needs Java 25 in editor-setup.md<br>283 `117eb6ce1b` Add lang/doc/configuration.md, one reference for the lang tooling's configuration<br>290 `137dc6faae` Correct six statements in the lang docs that the configuration reference found out of date<br>305 `89118d7728` Point the docs' line citations at the lines today's changes moved | Twelve documentation commits, most of a few lines (names, spelling, task paths, line citations): one or two commits, or each folded into the PR it documents. |
| Publish previews of the editor plugins from a pull request | 315 `2f584ec288` Publish previews of the IntelliJ plugin and VS Code extension from a pull request<br>316 `3f6b7be77c` Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL | 316 names and checks 315's previews. |
| Packaging tasks and build defaults | 273 `98e148fe91` Add IDE plugin packaging and install tasks, and quiet vsce's file tree<br>317 `75873a0d79` Leave the lang build out of the root build by default again<br>379 `b45d05c3cc` Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed<br>284 `48310c186a` Say in AGENTS.md that gradle.properties turns the lang build flags on | Install tasks, the lang build left out of the root build by default, incubating API suppressions. Check 284 against 317: 284 says gradle.properties turns the lang flags on, 317 turns them off. |
| The user guide, with screenshots from the tours | 424 `4610212d5c` Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours<br>426 `0ae33f4a07` Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run<br>429 `d1641d1822` Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature<br>430 `544d88d7c3` Move the user guide into lang/doc/user-guide, with its screenshots in images<br>434 `9b96d6d4b4` Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's<br>435 `512e6a8bec` Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting<br>452 `cc674a23f3` Say in the user guide what a rename doesn't change or check<br>471 `e3d49dbce2` Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs<br>472 `1ff22d7e8d` Split the user guide into a landing page, one page per chapter, and features and shortcuts pages<br>477 `d7fdf47007` Mark the user guide's keys up as keys, and the names it left plain as code<br>478 `4863fade86` Add an index of every page and section of the user guide to its landing page<br>479 `7102a7234b` Give the user guide's long sections subsections, and its index the third level<br>480 `244f824635` Box the user guide's single screenshots with their captions, as its pairs are | the guide, its testing chapter and its screenshots, made from tagged tour steps |

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
| 1 | `753b5ddb1c` | P1 | X1 | — | Keep, cache and report module repository read failures | Preserve module repository read failures without blocking usable candidates |
| 2 | `5bfa4b9785` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Separate diagnostic reporting from listener abort policy |
| 3 | `68dfafae88` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Require explicit compiler listeners and reasons for silence |
| 4 | `98bf164e26` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Scope parser diagnostics and restore validation listeners on every exit |
| 5 | `3b5cfe68a5` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics |
| 6 | `21f047a891` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Expose compilation-owned syntax and resolved semantic facts |
| 7 | `67ccd4448b` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Expose recovery, cursor fitting and declaration analysis to compiler hosts |
| 8 | `c6781bd795` | P4 | X5 | L1, L2, L3, L4, L5, L6, L7, L8, L9, L10 | Integrate compiler-backed language support (split by package, see below) | Integrate compiler-backed language support with IntelliJ and VS Code |
| 9 | `c5cf2f33cb` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Report repository read attempts without inferring dependency failures |
| 10 | `724c57eca6` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Clarify tooling contracts and isolate partial AST helpers |
| 11 | `d1f4c15478` | P1 | X1 | L2 | Keep, cache and report module repository read failures | Keep unchanged repository read failures cached until file metadata changes |
| 12 | `1536d3038c` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Verify compiler output without binary AST dependencies |
| 13 | `c5c1566d66` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Isolate parallel LSP test workers and keep CI and lint checks clean |
| 14 | `63bc04ce0f` | P7 | X5 | L6 | — | Verify the LSP4IJ APIs the plugin uses, and prepare the runIde sandbox |
| 15 | `867189adce` | P2 | X2 | L2 | Document compiler support for the language server | Document compiler support for the language server |
| 16 | `9870255836` | P6 | — | L4 | Rename guards and proofs | Guard code actions and renames at synthetic bodies and locked library sources |
| 17 | `3b6cc7d992` | P7 | — | L6 | — | Restart editor-owned language servers from a project-scoped CLI |
| 18 | `f8d04c05b9` | P3 | X3 | L2 | Group source bindings and clean up the compiler AST | Group source bindings, clean up the compiler AST and share duplicated code |
| 19 | `31780496db` | P1 | X1 | L2 | Require explicit error listeners, separate reporting from abort policy | Route repository and runtime reports through error listeners |
| 20 | `fda8e8963a` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Build and run with the embedded compiler in the server and both editors |
| 21 | `f59e9f3524` | P13 | X5 | L10 | The lang documentation | Rename the compiler guide and testing documents, and correct stale documentation |
| 22 | `056ec44f6d` | P12 | — | L10 | Playbook cases added after the catalog | Cover embedded build, run, debug launches and the adapter chooser in the shared playbook |
| 23 | `9997bdd6f3` | P4 | — | L1, L2 | Keep documents current and the server responsive | Keep documents current and the server responsive, and name its own diagnostics |
| 24 | `240e97d8b4` | P4 | — | L1, L2 | — | Stop advertising features that do not work, and warn when Tree-sitter falls back to the Mock adapter |
| 25 | `9bfe8024d5` | P9 | — | L8 | Run the editor's text unsaved, with console input | Let embedded runs read console input in both editors |
| 26 | `d8bb02ae62` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Build the editor's snapshot and reuse unchanged modules in embedded builds |
| 27 | `01b20d7c01` | P9 | X4 | L8 | Build and run with the embedded compiler, the default build mode | Make embedded the default build mode, chosen per editor |
| 28 | `a5ee7672cb` | P4 | — | L1, L2 | Keep documents current and the server responsive | Judge file events against a running analysis and apply them in order |
| 29 | `e3e6d9eee0` | P9 | — | L8 | Supervised execution workers | Run programs side by side in supervised execution workers |
| 30 | `672e43128b` | P9 | X4 | L8 | — | Find entry methods through the module's type and pass them with their arguments |
| 31 | `318585b181` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Keep indentation while typing, and lay out switches as the XDK does |
| 32 | `abf0302b28` | P7 | — | L6 | — | Read in cancellable read actions where a long read could freeze IntelliJ |
| 33 | `cf96081ddd` | P9 | X4 | L8 | Supervised execution workers | Fix two close races in embedded runs and cover the embedding API's runs |
| 34 | `9309be121f` | P9 | X4 | L8 | Run the editor's text unsaved, with console input | Build and run the editor's text unsaved, and report why a run cannot start |
| 35 | `66397977b1` | P4 | X3, X5 | L1, L2 | Style and concurrency fixes of the new classes | Declare fields first and use plain names in the new classes |
| 36 | `62606aaa28` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the document-color prototype |
| 37 | `316af5f0a6` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Rename adopted properties, widen extraction, and move and rename discovered roots |
| 38 | `76be500964` | P9 | — | L8 | — | Show a Run icon in IntelliJ's gutter, and build a generated project with real Gradle |
| 39 | `0ccd3edad1` | P9 | X4 | L8 | Supervised execution workers | End runs and workers cleanly, and measure them without waiting for the collector |
| 40 | `1cea6defdc` | P7 | — | L6 | — | Cancel IntelliJ requests whose connection ends |
| 41 | `6ad5483f94` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Stop the lexer at a source's end and keep diagnostics that only look like duplicates |
| 42 | `9a3c3570a6` | P4 | X3, X5 | L1 | Keep the build's jars whole and fail on compiler warnings | Keep the build's jars whole and its test runs focused |
| 43 | `2b19fc6d17` | P12 | X5 | L10 | — | Take LSP4IJ from a Marketplace channel, and keep the playbooks' checks and roots clean |
| 44 | `4838301acd` | P4 | — | L1 | Style and concurrency fixes of the new classes | Synchronize the semantic token history that concurrent queries share |
| 45 | `b5e76dd608` | P11 | — | L5 | — | Keep moved module containers as missing inputs, and stop refetching library documents |
| 46 | `0396f075ac` | P10 | X4 | L9 | Debug through the language server | Debug programs from VS Code and IntelliJ through the language server |
| 47 | `bbeb8b2920` | P10 | X4 | L9 | — | Show a stopped frame's variables, and step over, into and out of calls |
| 48 | `15cc5a339d` | P10 | X4 | L9 | Stop at exceptions, pause, stop on entry | Stop at exceptions, pause a running program, and stop on entry |
| 49 | `9cda9012e5` | P10 | X4 | L9 | — | Evaluate watches and hovers on paths in a stopped frame |
| 50 | `d74d25488f` | P10 | X4 | L9 | Conditional breakpoints, hit counts, logpoints | Conditional breakpoints, hit counts, logpoints and exception classes |
| 51 | `4989630717` | P10 | X4 | L9 | — | Evaluate expressions with operators without running the program's code |
| 52 | `5f407bff65` | P10 | X4 | L9 | — | Leave the runner's frames out of the debugger's stacks and traces |
| 53 | `4a8e95649b` | P10 | X4 | L9 | — | Run code typed in the Debug Console on the stopped fiber |
| 54 | `cee2155822` | P10 | — | L9 | — | Stop at the program's first line from IntelliJ's configuration |
| 55 | `53b826d8e9` | P10 | X4 | L9 | Lazy properties and their time limit | Lazy properties, hexadecimal values, paged arrays, Set Value, completions |
| 56 | `6ba9609d74` | P10 | — | L9 | — | Place breakpoints on the lines a debugged program was built from |
| 57 | `2c2258cb83` | P9 | X4 | L8 | Supervised execution workers | Soak runs and debug sessions on one worker, and let finished runtimes go |
| 58 | `81c3473897` | P12 | — | L10 | Playbook cases added after the catalog | Check Run to Cursor in both editors |
| 59 | `71e1afae38` | P10 | X4 | L9 | — | Show library frames in their bundled source, every fiber, and modules |
| 60 | `1ea3737ec0` | P10 | X4 | L9 | — | Step into a chosen call of the stopped line |
| 61 | `d31a474ee5` | P10 | X4 | L9 | — | Stop where a method of a name is entered |
| 62 | `0c20a90704` | P10 | X4 | L9 | Restart a stopped frame | Restart a stopped frame, which its caller then calls again |
| 63 | `94758a7f98` | P10 | X4 | L9 | — | Stop once a watched field of an object has changed |
| 64 | `1ef69e690b` | P12 | — | L10 | Playbook cases added after the catalog | Check Reset Frame and function breakpoints in IntelliJ, for LSP4IJ's fixes |
| 65 | `80d6092466` | P10 | — | L9 | — | Answer inline values with the stopped method's variables |
| 66 | `2376f753b2` | P12 | — | L10 | Playbook cases added after the catalog | Check data breakpoints, Pause, exception filters and loaded sources in IntelliJ |
| 67 | `efa467b8bb` | P12 | — | L10 | Playbook cases added after the catalog | Check a document link's Ctrl+Click in IntelliJ's X131 |
| 68 | `792ae9d39f` | P13 | — | L10 | The lang documentation | Drop the references to documents and plans this branch does not carry |
| 69 | `e450c182b6` | P13 | — | L10 | The lang documentation | Describe debugging, and the run changes, in the server and editor READMEs |
| 70 | `fdbd2f9467` | P6 | — | L4 | Rename guards and proofs | Prove a rename past a register the semantic model cannot place |
| 71 | `d2b6cc293f` | P8 | — | L7 | Fold into the extension's client commit | Drop the startup workarounds that vscode-languageclient 11.0.0 makes unneeded |
| 72 | `f14885a215` | P10 | — | L9 | Debug through the language server | Send a step's response before the stop where the step ends |
| 73 | `ec09eb0fe0` | P6 | — | L4 | — | Inline every use of a private member and remove it |
| 74 | `8b60782ee5` | P12 | — | L10 | — | Apply X103's rename as a refactoring and check the member after Undo |
| 75 | `76d5eb57ca` | P10 | X4 | — | Stop at exceptions, pause, stop on entry | Stop at an uncaught exception that only the runner would catch |
| 76 | `8ecd396289` | P6 | — | L4 | Safely delete an unused member | Safely delete an unused public or protected member |
| 77 | `bfe6748010` | P8 | — | L7 | Moves in the Explorer | Apply the Ecstasy edits of an Explorer move with refactoring auto-save off |
| 78 | `b956ba8d71` | P10 | X4 | — | Lazy properties and their time limit | Let a host give lazy variables' code longer than ON_DEMAND |
| 79 | `9204f4d102` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach the debugger to a program run with --debug |
| 80 | `6f37a8f23d` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let SessionDebuggerTest's runs show they started before it ends them |
| 81 | `6ce1cf62e5` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let XdkLanguageServerTest's pending analyses publish before finish() returns |
| 82 | `c43c91bf7e` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Drop the sleeps FormattingConfigRoundTripTest takes before reading a stored reply |
| 83 | `8331907146` | P4 | — | L1 | Server tests that check what they claim, not how long they wait | Let two run tests check what they claim instead of how long they wait |
| 84 | `bcac61d569` | P7 | — | L6 | Document notification order (net almost zero) | Drop the change a restart's retired document synchronizer still sends |
| 85 | `d67d3bd7e4` | P12 | — | L10 | Individual playbook case fixes | Wait for LSP4IJ to reopen X259's consumer after the restart |
| 86 | `9c81bd802a` | P12 | — | L10 | Individual playbook case fixes | Send X259's library edit to the old server before the restart |
| 87 | `feb20b5276` | P12 | — | L10 | Individual playbook case fixes | Wait for X257's Invoked request to reach the LSP console |
| 88 | `1445c8cecf` | P12 | — | L10 | Individual playbook case fixes | Reopen a popup under inspection after the IDE was deactivated |
| 89 | `4cb6820ac6` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let the VFS find a library frame's source before LSP4IJ reads the frames |
| 90 | `e9bf44ed62` | P12 | — | L10 | The playbook container and the IDE it drives | Run the IntelliJ playbook in a Linux container on a virtual display |
| 91 | `4ef8742974` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Let two SessionDebuggerTest cases check what their sleeps waited for |
| 92 | `8ba46d269d` | P4 | — | L1 | Keep documents current and the server responsive | Analyze again an open document whose analysis was retired without a replacement |
| 93 | `0aa134df86` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Let the playbooks' diagnostics waits accept only a report for the current text |
| 94 | `3e25e1ef9f` | P12 | — | L10 | The playbook container and the IDE it drives | Fix the container's IntelliJ playbook runs: a user, an init process, JDK 25 and mavenLocal |
| 95 | `1969ec76d2` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Spell XtcTextDocumentService's analyze and analyzeOne the American way |
| 96 | `7a6c404fb4` | P10 | X4 | L9 | Debug Gradle-mode and command-line runs | Debug Gradle-mode runs in VS Code and IntelliJ |
| 97 | `cd9e3db6eb` | P4 | — | L1, L2 | Keep the build's jars whole and fail on compiler warnings | Fail the lang build on compiler warnings, and fix the ten it had |
| 98 | `2dd267a52d` | P9 | X4 | — | Gradle plugin fixes | Don't reuse a module compiled in another copy of the project |
| 99 | `768fab3872` | P7 | — | L6 | — | Keep the language server running while a run or debug session uses it |
| 100 | `48dbd1f1bf` | P12 | X5 | L10 | The playbook container and the IDE it drives | Run the editor playbooks in the container from Gradle, with any engine |
| 101 | `f8844b58ed` | P10 | X4 | L9 | — | Continue, step or pause one fiber while the others stay stopped |
| 102 | `91baa25918` | P5 | — | L3 | — | Link a local's ranges when the caret is just after its name |
| 103 | `e1ccde27ab` | P10 | X4 | — | — | Test that a part of a value's variables from past its end is empty |
| 104 | `0b25542f99` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Debug a command-line run: xtc run runs the program, which connects |
| 105 | `0c0a9691a2` | P10 | X4 | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Move the debugger agent into javatools, with the protocol as records |
| 106 | `6184012f1c` | P10 | — | L9 | — | Keep a debugged task's last output, and X325's queued fiber |
| 107 | `b79c186a5c` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let deduction find a named module in src/main/x beside other sources |
| 108 | `e598612970` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Run and debug a module's xunit tests from the editors, embedded |
| 109 | `b058623066` | P4 | — | L2 | Style and concurrency fixes of the new classes | Compile a closure as a fold, without mutable accumulators |
| 110 | `f910b34173` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Run and debug Ecstasy tests from IntelliJ, in its test console |
| 111 | `6b14581128` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run and debug tests in Gradle and command-line mode, from both editors |
| 112 | `5dd674db72` | P9 | — | L8 | Run and debug a module's xunit tests, reported as xunit's JSON events | Report tests as xunit's own JSON events, which the language server reads once |
| 113 | `0d75d2ae97` | P9 | — | L8 | Supervised execution workers | Lift the lazy-value time limit for the embedded execution tests' worker |
| 114 | `c7fa9aff96` | P10 | X4 | — | Attach the debugger to a program run with --debug, with the agent in javatools | Debug with the agent jar's provider by its name, and javatools' own without one |
| 115 | `6c6e1e1a82` | P2 | X2 | L2 | Expose compilation-owned syntax and resolved semantic facts | Keep the doc comments of properties and methods on their structures |
| 116 | `2d9c0acb51` | P10 | X4 | L9 | Describe debugging in the READMEs | Describe the debugger's attach and R9-R12 features in the READMEs |
| 117 | `8cbe45a3ae` | P9 | X4 | L8 | Describe running and testing in the READMEs | Describe running and debugging tests from both editors in the READMEs |
| 118 | `e63dfedb36` | P12 | — | L10 | Playbook documentation | Give the playbook catalog's size as its 342 cases everywhere |
| 119 | `43abe61716` | P8 | — | L7 | — | List every VS Code command in the extension README, by its own title |
| 120 | `6f8839b71f` | P13 | — | L10 | The lang documentation | Fix lang/README's task paths, versions and feature summary |
| 121 | `b7c5d177e3` | P9 | — | L8 | Describe running and testing in the READMEs | List the editors' test, attach and debug-task sources, and when xtc is needed |
| 122 | `236051b22a` | P12 | — | L10 | Playbook documentation | Point the playbook instructions at the playbook container |
| 123 | `6e3eed1855` | P13 | X5 | L10 | The lang documentation | Use American spelling in the shippable docs |
| 124 | `a79370d53a` | P13 | — | L10 | The lang documentation | Name testing in the embedded build and run index entry |
| 125 | `373be002b2` | P13 | X5 | L10 | The lang documentation | Name the editors' UI as it reads, Ecstasy, where the docs said XTC |
| 126 | `f2027e0ac3` | P13 | — | L10 | The lang documentation | Call the server and the IntelliJ plugin by their names in the lang docs |
| 127 | `85bfaa0998` | P12 | — | L10 | Playbook documentation | List the test and debug-task messages and a test row in the playbook's 18a |
| 128 | `84882c33bc` | P13 | — | L10 | The lang documentation | Say that every adapter needs Java 25 in editor-setup.md |
| 129 | `af14341f95` | P10 | X4 | — | Describe debugging in the READMEs | Name javatools' debugger, agent and provider in its README |
| 130 | `240a3c0892` | P7 | — | L6 | — | Give IntelliJ's missing breakpoint pass count its own LSP4IJ fix |
| 131 | `c8ce4ca806` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member access typed at the end of a line |
| 132 | `6ea64032f0` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name a type by what it is when hovering its name |
| 133 | `8c369c66d1` | P9 | — | L8 | — | Deliver a run's events to dap-server's ProgramEvents, not a copy of it |
| 134 | `6b03dd927d` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete with the language model's keywords and built-in types |
| 135 | `9956c7db87` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Share the formatter's empty-block skeleton and whitespace edits |
| 136 | `f789b18ffe` | P7 | — | L6 | — | Build an embedded run's process handler in one place |
| 137 | `216291190b` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Name the properties that hold behavior or mutable state in camel case |
| 138 | `dadaf68a7e` | P4 | X5 | L1, L2 | Isolate the LSP test workers and split them into tiers | Split the LSP tests into a fast default tier and complete runs |
| 139 | `fac24efb78` | P7 | — | L6 | Code Style page and live templates | Give Ecstasy its Code Style settings page |
| 140 | `1730374c77` | P7 | — | L6 | Code Style page and live templates | Load the Ecstasy live templates, and offer them in Ecstasy files only |
| 141 | `beb55a20c8` | P10 | X4 | — | — | Debug a program that has an interface |
| 142 | `c0ee779c3c` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Lift SessionDebuggerTest's time limit to an hour |
| 143 | `6b7a7431b3` | P4 | X5 | — | Isolate the LSP test workers and split them into tiers | Let the compiler-consumer gate accept the default tier's later cases |
| 144 | `595c47dd2f` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete a member with the cursor right after its dot |
| 145 | `c73f17901f` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer code actions in a module that has a const with a toString |
| 146 | `8cf093e9ff` | P12 | X5 | L10 | The playbook container and the IDE it drives | Queue playbook container runs on one machine lock, each with its own export |
| 147 | `649be2d801` | P12 | — | L10 | The playbook container and the IDE it drives | Start the playbook's IDE from one place, without the plugins it does not use |
| 148 | `4126419fcc` | P12 | X5 | L10 | Detect hangs, not slowness | Let tests wait for a hang only, not for a fast machine |
| 149 | `6768fbb784` | P10 | — | L9 | Debug Gradle-mode and command-line runs | Wait for configurationDone before a debug session starts its program |
| 150 | `796dde1a22` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a tour of the IntelliJ plugin on a card game, checking what it shows |
| 151 | `f32bea2634` | P11 | X2 | L5 | — | Show a library declaration's doc comment in hover, from its source |
| 152 | `d3917775f1` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Give Ecstasy files a PSI of the Ecstasy language |
| 153 | `1c2a11ba2b` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Close a block that Enter opens after a { in an Ecstasy file |
| 154 | `70c25d0b0a` | P7 | — | L6 | — | Remove unused imports with Organize imports, run from IntelliJ's Optimize Imports |
| 155 | `874bc44ea0` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Keep an Ecstasy file's brackets and words as plain text had them |
| 156 | `c43311e438` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Give a constructor's finally block no references lens of its own |
| 157 | `da8b935724` | P5 | — | L3 | Hover, inlay hint and code lens presentation fixes | Name an inferred local's type in its hint as the source names it |
| 158 | `18c008f9ba` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Rerun only the failed Ecstasy tests from the test console |
| 159 | `2032463673` | P10 | — | L9 | A line typed in the Debug console (net small) | Take a line typed in IntelliJ's Debug console to the debug session |
| 160 | `41579b3056` | P2 | X2 | L2 | Expose host compilation outcomes, snapshots, cancellation and cached diagnostics | Report every class's errors from an embedded compile, not only the first's |
| 161 | `b30d67ca6b` | P3 | X3 | L2 | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Complete names and show a call's signature where they are typed at a line's end |
| 162 | `7f97fe3ec1` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Complete a type of another module where a statement starts, with its import |
| 163 | `5d1f963802` | P4 | — | L1, L2 | Style and concurrency fixes of the new classes | Copy a concurrent map's entries without failing when one goes meanwhile |
| 164 | `6e3f944a50` | P4 | — | L1 | — | Name a server's log session by the server alone, and tell a live one by its lock |
| 165 | `90080aca8b` | P7 | — | L6 | Undo of a server's edits | Undo a server's edit of one file without asking, as IntelliJ's quick fixes are |
| 166 | `d513e313fd` | P10 | X4 | — | SessionDebuggerTest made independent of timing | Make SessionDebuggerTest independent of how its threads are scheduled |
| 167 | `0ca1888625` | P6 | — | L4 | Moves: rename adopted properties, widen extraction, move and rename roots | Import a moved type where its new package's name is a property |
| 168 | `817698d2f8` | P6 | — | L4 | Extract statements | Extract statements that use an injected local, passing its value |
| 169 | `cc946d1ce6` | P10 | X4 | — | — | Keep earlier debugger stops out of a clock alarm set for a given time |
| 170 | `44f24a745e` | P9 | X4 | — | Runtime: report a dropped Future callback and a failed service shutdown | Report a Future callback that throws or that a terminated service drops |
| 171 | `2aa62da092` | P6 | — | L4 | Prove code actions with incremental compiles | Prove only the code actions a request asks for, and share a revision's proofs |
| 172 | `85198dae0b` | P4 | — | L1 | — | Keep the execution trace's events out of stderr and server.log |
| 173 | `30fb09d9e1` | P4 | — | L2 | — | Let one source that discovery cannot read cost only itself |
| 174 | `6b1c6d3de2` | P10 | X4 | — | — | Let a debugger condition name an enum value by its enumeration |
| 175 | `0d978d828a` | P12 | — | L10 | Individual playbook case fixes | Give X145 a reference search that waits until it is canceled |
| 176 | `2fd170581a` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Offer Create class for the diagnostic of a statement that constructs it |
| 177 | `afdeafbf48` | P6 | — | L4 | Extract statements | Extract statements from a selection of whole lines |
| 178 | `23de8c34d2` | P9 | X4 | L8 | xtc deduction of a project's modules and its test module | Let xtc test, run and build take a project's test module with its modules |
| 179 | `8568b0fc32` | P9 | — | L8 | Build and run with the embedded compiler, the default build mode | Build a Gradle-imported project embedded in embedded mode |
| 180 | `3e7a85ff6c` | P4 | — | L2 | — | Keep a project's module, source and library order the same in every JVM |
| 181 | `fe0c8e29af` | P7 | — | L6 | Held edits before completion and signature help (net small) | Send LSP4IJ's held edits before it asks for completions or a call's signature |
| 182 | `a01c493962` | P7 | — | L6 | The Ecstasy PSI and typing in an Ecstasy file | Indent the rest of a line that Enter moves into the block it opens |
| 183 | `1fc175f165` | P10 | — | L9 | Conditional breakpoints, hit counts, logpoints | Say on the debug console why a breakpoint's condition cannot be evaluated |
| 184 | `54d8fa6759` | P4 | — | L1, L2 | Keep documents current and the server responsive | Answer requests that a restarted client sends while it opens its files again |
| 185 | `da5d1eb839` | P5 | — | L3 | Completion: keywords, types of other modules with their import, method calls | Insert a method's call when its completion is chosen |
| 186 | `816c3c1df6` | P5 | — | L3 | Formatting: indentation while typing, switches, on-type formatting | Answer on-type formatting without waiting for the change to compile |
| 187 | `306e0cb7e3` | P12 | — | L10 | — | Check that Debug of Current File launches an Ecstasy configuration |
| 188 | `8645d1535a` | P9 | — | L8 | — | Report a stopped run's end without the failure its stop causes |
| 189 | `160369ac85` | P12 | — | L10 | — | Check 7a.9 against the compiler's error budget, which the server keeps |
| 190 | `493427d6d1` | P12 | — | L10 | — | Run the VS Code playbook after the extension suite, not with it |
| 191 | `88b5af8274` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Get a closed file's errors to IntelliJ from the server's workspace report |
| 192 | `d5e1952233` | P6 | — | L4 | Prove code actions with incremental compiles | Prove a code action by compiling only the modules its edit reaches |
| 193 | `b9c935eb5d` | P6 | — | L4 | Prove code actions with incremental compiles | Start a revision's code action compile from the previous one's, and log its cost |
| 194 | `318ca5b8a1` | P12 | X5 | L10 | Detect hangs, not slowness | Find a test's hang by the CPU it stops using, not by an hour on the clock |
| 195 | `f1b8d24438` | P10 | — | L9 | — | Show a stopped run as stopped, without what its stop made it print |
| 196 | `c3f858d9ce` | P2 | X2 | — | A spare key per thread in the hasher map | Give each thread its own spare key for a hasher map's lookups |
| 197 | `3856d6f3e6` | P10 | — | L9 | Physical expression fragments (net small) | Make the debugger's expression fragment physical, which ends a paused session's daemon loop |
| 198 | `6dc9eb14d2` | P9 | X4 | L8 | Runtime: report a dropped Future callback and a failed service shutdown | Complete a service's shutdown however it goes, and report its failure |
| 199 | `29c84446ed` | P9 | X4 | L8 | — | Report a test that throws other than by failing an assertion as aborted, keep its later exceptions, and count skipped tests |
| 200 | `6e0f3f202f` | P12 | — | L10 | — | Count the code actions at the tour's unused import and unused local |
| 201 | `2373bf350d` | P4 | — | L1, L2 | Workspace diagnostic pulls | Answer an idle client's workspace pull of an unchanged graph without a job |
| 202 | `31e85f6842` | P9 | — | L8 | IntelliJ's test console: run, debug, rerun failed tests | Show Rerun Failed Tests on an IntelliJ test run |
| 203 | `f46f2ae33f` | P6 | — | L4 | No code action budget (drop all three) | Wait for LSP4IJ's automatic code action answers only a budget |
| 204 | `a6e77c2797` | P9 | X4 | — | Gradle plugin fixes | Run testXtc whenever a tool reads its test report, never from the cache |
| 205 | `1932284f49` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of the modules that depend on an edited one whose API stayed |
| 206 | `3b7e4d788b` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Run every test module's tests at once in IntelliJ: Run 'All Ecstasy Tests' |
| 207 | `e9b8b3dde4` | P7 | — | L6 | Light test fixes | Keep each light test's stub server and wrappers to that test |
| 208 | `579da273e9` | P4 | — | L1 | Report what the server is still doing | Say which requests the language server is still answering, and which diagnostics it has still to report |
| 209 | `67c607b82d` | P7 | — | L6 | Light test fixes | Tag the light tests' workarounds of two LSP4IJ gaps |
| 210 | `cc571d723a` | P4 | — | L1, L2 | Workspace diagnostic pulls | List a closed test module's tests from the workspace pull's compile |
| 211 | `9ff310cda2` | P6 | — | L4 | Quick fixes: a const with toString, Create class, declared action kinds | Declare the code action kinds the language server's actions have |
| 212 | `880af0f3c0` | P7 | — | L6 | Undo of a server's edits | Ask the server for no edit of a rename that Undo makes, and report a stale refactoring without a dialog |
| 213 | `fb7d659ae4` | P7 | — | L6 | Undo of a server's edits | Test that Undo takes a resolved code action's edit of one file back without asking |
| 214 | `1366d01183` | P6 | — | L4 | Safely delete an unused member | Safely delete a member whose body has an annotation, as an injected local |
| 215 | `6084f33e44` | P10 | X4 | L9 | Restart a stopped frame | Restart a frame whose fiber ran code in its stop |
| 216 | `ddc78304c2` | P9 | — | L8 | — | End the tests of the modules a stopped VS Code test run did not reach |
| 217 | `b4ced017b1` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Compile the VS Code extension's TypeScript into an empty out/ |
| 218 | `a8856e17fa` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Report no diagnostics for a library source's read-only copy |
| 219 | `33095b5afd` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run 'All Ecstasy Tests' before Gradle's Tests in on Ecstasy-only test sources |
| 220 | `9038152238` | P6 | — | L4 | No code action budget (drop all three) | Ask the language server for full code action answers again, not ones within a budget, in IntelliJ |
| 221 | `660f4b6ca8` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Put the embedded build's errors in the Problems window's Project Errors |
| 222 | `e622254f40` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Show a completion item's documentation on Ctrl+Q in IntelliJ, from its resolve |
| 223 | `cfc0517243` | P7 | — | L6 | Undo of a server's edits | Check that Ctrl+Z's Undo action takes a resolved code action's edit back |
| 224 | `06dd3c0487` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Find Run All Ecstasy Tests' existing configuration, not make "(1)" of it |
| 225 | `02f566d112` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Keep Run to Cursor's temporary breakpoint until the program stops, in IntelliJ |
| 226 | `e5f17cd4b3` | P4 | — | L1 | Report what the server is still doing | Answer xtc/busy with the server's open requests and its compiler's jobs |
| 227 | `9355c49946` | P12 | — | L10 | The IntelliJ tour, its recording and video | Record a playbook run's screen as video in the container |
| 228 | `0f5cb32d81` | P12 | — | L10 | The IntelliJ tour, its recording and video | Delete a playbook run's IDE folder once its logs are copied, in the container |
| 229 | `4b38f80016` | P12 | — | L10 | The IntelliJ tour, its recording and video | Check the plugin's features through the IntelliJ tour, and detect its hangs from the IDE's state |
| 230 | `0d221e805b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose the tours' video from their recorded clips, each step from a run where it passed |
| 231 | `9afda7520b` | P10 | — | L9 | VS Code breakpoints and F5 | Let VS Code set breakpoints in Ecstasy files |
| 232 | `accd06aead` | P10 | — | L9 | VS Code breakpoints and F5 | Debug the module of a class's file with F5 |
| 233 | `1232f03511` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Pass a run task's args in Gradle and command-line mode |
| 234 | `62b4ed8962` | P8 | — | L7 | — | Trace the language server's messages when xtc.trace.server says so |
| 235 | `4ca5e508d8` | P5 | — | L3 | — | Fold comments and runs of imports with the compiler adapter |
| 236 | `740e9ac34a` | P9 | — | L8 | Gradle and command-line runs and tests from both editors | Run the editor's module from its Run button and context menu |
| 237 | `f5b46a23cc` | P4 | — | L1 | Workspace diagnostic pulls | Clear a deleted file's diagnostics in the next workspace pull |
| 238 | `a7b6eba728` | P8 | — | L7 | Moves in the Explorer | Say when a move leaves the uses of its sources as they were |
| 239 | `89c90d933a` | P9 | — | L8 | Report the embedded runs the server tracks | Say how many embedded runs the server tracks, and where a test is |
| 240 | `4dc1492a13` | P12 | — | L10 | Diagnostics waits accept only a final report for the current text | Fail the playbooks' diagnostics waits once their reports are final |
| 241 | `4b095e8da9` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook cases X342-X349 for xunit tests, in both editors |
| 242 | `1b9891fcf8` | P12 | — | L10 | The IntelliJ tour, its recording and video | Give a VS Code tour's run its screen recording, and every run only its own, in the container |
| 243 | `35a442d4a0` | P12 | — | L10 | The VS Code tour | Record a tour of the VS Code extension that checks what it shows |
| 244 | `0b527759f4` | P9 | X4 | — | Gradle plugin fixes | Drop XtcRunConfigurationTest's redundant casts to TaskOutputsInternal |
| 245 | `6200692789` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Give a completion item's resolved documentation to Ctrl+Q through public API only, in IntelliJ |
| 246 | `1b2309ca05` | P6 | — | L4 | Prove code actions with incremental compiles | Keep the compiles of modules that compiled when only private declarations move |
| 247 | `46635b9e3a` | P12 | — | L10 | — | Expect the card game's Hand to keep heading when Safe delete removes display |
| 248 | `e415bb0bea` | P4 | — | L1 | Workspace diagnostic pulls | Clear a removed file's report once in workspace pulls that name its result ID |
| 249 | `48385a095c` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Compile a library source's copy on its own even once a compile has named it |
| 250 | `2061a784f2` | P8 | — | L7 | Moves in the Explorer | Ask the server again for a move's edits when a change overtook its answer |
| 251 | `d15fb6ee22` | P12 | — | L10 | The VS Code tour | Make the VS Code tour pass as one whole run, unrecorded |
| 252 | `8d0a78084a` | P12 | — | L10 | IntelliJ tour step fixes | Press the keymap's own Undo key in the IntelliJ tour |
| 253 | `3a1789ae18` | P12 | — | L10 | IntelliJ tour step fixes | Undo in the IntelliJ tour from the editor as the change left it |
| 254 | `0ef798faec` | P7 | — | L6 | Undo of a server's edits | Ask before Undo takes back a rename or move that changed several files |
| 255 | `629f52477b` | P12 | — | L10 | IntelliJ tour step fixes | Answer Undo's question in the IntelliJ tour and playbook as it offers |
| 256 | `5a20d6c7a2` | P12 | — | L10 | IntelliJ tour step fixes | Rename a method of four files in the IntelliJ tour, and show Undo's question |
| 257 | `c78fdbb55b` | P12 | — | L10 | The VS Code tour | Rename cardMatches across four files in the VS Code tour, undone by Ctrl+Z |
| 258 | `1dacbba13f` | P12 | — | L10 | The VS Code tour | Check in the VS Code tour that Ctrl+Z takes a quick fix back whole |
| 259 | `63368314a0` | P12 | — | L10 | The VS Code tour | Show the VS Code tour's Undo of the four-file rename as steps |
| 260 | `18bdf7d90a` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for Go to symbol's popup to close, and never fill a file's editor |
| 261 | `b956e87224` | P4 | — | L1 | — | Keep prepareLibraryPlaybook's JVM to SLF4J's warnings |
| 262 | `3914899a3b` | P12 | — | L10 | The IntelliJ tour, its recording and video | Print the IntelliJ tour's progress in the build's output, step by step |
| 263 | `fc6e889298` | P12 | — | L10 | — | Print both compiler playbooks' progress per case in the build's output |
| 264 | `719593a1ba` | P12 | — | L10 | The IntelliJ tour, its recording and video | Report the written video's real length from the tour composer |
| 265 | `925c1fcc37` | P12 | — | L10 | Detect hangs, not slowness | Fail the debugger probe's waits when nothing is in progress for a minute |
| 266 | `f6dc890295` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove the unused xtc/runnableTargets request |
| 267 | `45dfa00770` | P11 | — | L5 | Navigate the XDK's library sources | Answer hover and Go to Definition in a library copy that the debugger opens |
| 268 | `7126256dec` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Offer Run All Ecstasy Tests beside Gradle's Tests in on mixed test folders |
| 269 | `4d4e4d936d` | P9 | — | L8 | Reuse unchanged modules, rebuild when files change while compiling | Compile a run's embedded build again when its files change on disk as it compiles |
| 270 | `df5673dcdc` | P12 | — | L10 | IntelliJ tour step fixes | Wait in the IntelliJ tour for the new server's didOpen before File Structure |
| 271 | `5219c458e4` | P12 | — | L10 | The VS Code tour | Wait in the VS Code tour for the $xtc problem matcher's error after a failed build |
| 272 | `1478639a6f` | P8 | — | L7 | Extension build: clean out/, dev tool advisories | Upgrade the VS Code extension's npm dev tools past the MSAL, glob and minimatch advisories |
| 273 | `98e148fe91` | P13 | X5 | L10 | Packaging tasks and build defaults | Add IDE plugin packaging and install tasks, and quiet vsce's file tree |
| 274 | `8b6ed673bf` | P11 | — | L5 | Navigate the XDK's library sources | Navigate the XDK's library sources: definitions from their names, frames at their methods |
| 275 | `14e42ccd1d` | P11 | — | L5 | Temporary directories and their locks | Delete the language server's temporary directories once no server needs them |
| 276 | `432b93c38d` | P12 | — | L10 | The IntelliJ tour, its recording and video | Name the tours' video and deck after their editor, tour-intellij and tour-vscode |
| 277 | `b11549955a` | P11 | — | L5 | Temporary directories and their locks | Keep a server directory's lock beside it, where reading the directory cannot release it |
| 278 | `de9a267667` | P11 | — | L5 | Navigate the XDK's library sources | Compile an opened XDK source with its whole library, for navigation in all of it |
| 279 | `5b27f15145` | P12 | X5 | — | — | Keep Python bytecode out of the repository |
| 280 | `eea82770c9` | P11 | — | L5 | Navigate the XDK's library sources | Compile a library source's ecstasy-library content with its library, as its copy is |
| 281 | `3e0bddc9dd` | P12 | — | L10 | Playbook cases added after the catalog | Add playbook case X350 for navigation inside an XDK library's source, in both editors |
| 282 | `044721afa5` | P12 | — | L10 | Individual playbook case fixes | Wait for a fully decorated editor before the UP17 probe's decorated replacement |
| 283 | `117eb6ce1b` | P13 | — | L10 | The lang documentation | Add lang/doc/configuration.md, one reference for the lang tooling's configuration |
| 284 | `48310c186a` | P13 | X5 | — | Packaging tasks and build defaults | Say in AGENTS.md that gradle.properties turns the lang build flags on |
| 285 | `efdaa408f0` | P7 | — | L6 | Light test fixes | Give a document gone from the stub server's workspace report an empty report, as the real server does |
| 286 | `3d037b40c2` | P12 | — | L10 | The IntelliJ tour, its recording and video | Compose recorded tours again, and write the deck to its own file, in the tour composer |
| 287 | `b4779a6f68` | P12 | — | L10 | IntelliJ tour step fixes | Wait for the run widget's Debug of Current File before clicking it, in the IntelliJ tour |
| 288 | `3ce094ef95` | P12 | — | L10 | The VS Code tour | Make .vscode before writing launch.json in the VS Code tour's Debugging, recorded or not |
| 289 | `cde05d7740` | P4 | — | L1 | Workspace diagnostic pulls | Make a test added to an open test module a new report of its own diagnostic pull |
| 290 | `137dc6faae` | P13 | — | L10 | The lang documentation | Correct six statements in the lang docs that the configuration reference found out of date |
| 291 | `9464241317` | P12 | — | L10 | Individual playbook case fixes | Make the UP17 probe's decorated replacement start past the highlighter tree's root |
| 292 | `b3cefcc6a9` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | List a closed file's errors in IntelliJ's Project Errors without a Build, and once after one |
| 293 | `0a04f005b3` | P12 | — | L10 | Playbook cases added after the catalog | Check IntelliJ's Project Errors in X341: a closed member's errors without a Build, once after one |
| 294 | `e2ddb1f89c` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Depend optionally on IntelliJ 2026.3's Problems view plugin, whose classes Project Errors needs |
| 295 | `cb55f2a6bc` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Report the embedded build's errors to the Build window as messages to their file and line |
| 296 | `0f41104d2f` | P12 | — | L10 | Playbook cases added after the catalog | Check in X341 that only the provider lists a Build's errors, and read the Build window's messages by their target |
| 297 | `513782f68f` | P7 | — | L6 | Light test fixes | Keep the stale instrumented classes of an older build off the plugin's unit tests |
| 298 | `13c8ca99a8` | P4 | — | L1, L2 | Report what the server is still doing | Report the server's heap after collections and its compile queue's waits in xtc/languageServiceStatus |
| 299 | `09e084bb12` | P7 | — | L6 | — | Run the language server on ZGC with a 1 GB soft heap limit unless its JVM options choose otherwise |
| 300 | `96899af636` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ of the playbook's disk changes at once, and wait for finished test results in X339 and X340 |
| 301 | `abb1e9a46e` | P4 | — | L1, L2 | Server tests that check what they claim, not how long they wait | Make the retention test's cancellations and the library content test's refresh check independent of timing |
| 302 | `bf48463a6e` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Restart the Ecstasy language server with Ctrl+Alt+X, then R |
| 303 | `1e2390fd99` | P12 | — | L10 | Individual playbook case fixes | Treat the driver's own Structure tree expansion timeout as not yet expanded |
| 304 | `b26c23a702` | P8 | — | L7 | — | Give VS Code's Restart Language Server the same shortcut, Ctrl+Alt+X, then R |
| 305 | `89118d7728` | P13 | — | L10 | The lang documentation | Point the docs' line citations at the lines today's changes moved |
| 306 | `acec5aa6b4` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Move and rename through the playbook's probes only once IntelliJ has finished indexing |
| 307 | `0d4f148d6e` | P7 | — | L6 | Ctrl+Alt+X shortcuts: restart (R) and build mode (B) | Choose the Ecstasy build mode with Ctrl+Alt+X, then B, in both editors |
| 308 | `37c9135e45` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Refresh files IntelliJ has only after a case changes them on disk, not when a case reads them |
| 309 | `645780fc9c` | P12 | X5 | L10 | The playbook container and the IDE it drives | Drive IntelliJ 2026.3 from the playbook, while the plugin still builds against 2026.2.3 |
| 310 | `0799fa6612` | P12 | — | L10 | Detect hangs, not slowness | Let a playbook run shorten its hang bound with -PintellijPlaybookHang |
| 311 | `daf60a5108` | P11 | — | L5 | Discovered modules that share a name | Keep discovered modules that share a name out of the graph, instead of failing every file |
| 312 | `8ea37090c4` | P11 | — | L5 | The XDK's own libraries from source | Analyze the XDK's own libraries from their sources in the XVM repository, instead of the bundled XDK |
| 313 | `7d580a2d44` | P11 | — | L5 | Discovered modules that share a name | Warn of duplicate discovered module names only while the discovered graph is in use |
| 314 | `13d06d234a` | P11 | — | L5 | The XDK's own libraries from source | Replace a bundled library with any workspace module of its name, however the project is configured |
| 315 | `2f584ec288` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Publish previews of the IntelliJ plugin and VS Code extension from a pull request |
| 316 | `3f6b7be77c` | P13 | — | L10 | Publish previews of the editor plugins from a pull request | Name the previews "Ecstasy Language Support (bleeding edge)", and warn of a trailing space in the repository URL |
| 317 | `75873a0d79` | P13 | X5 | — | Packaging tasks and build defaults | Leave the lang build out of the root build by default again |
| 318 | `b13c434a6f` | P10 | X4 | L9 | The debugger finds sources where its host names them | Let the debugger find a module's sources where the language server says |
| 319 | `0a14104960` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the VS Code playbook until the server has heard of a case's changes on disk |
| 320 | `9055993c43` | P12 | — | L10 | Individual playbook case fixes | Ask the Structure view again when LSP4IJ before 0.30.0 keeps an edit's empty symbols |
| 321 | `8a43ce5499` | P12 | — | L10 | Detect hangs, not slowness | Count an IntelliJ playbook case as hung after ten minutes, not an hour |
| 322 | `b14afd4d1e` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Tell IntelliJ at once of the Gradle files the compiler-import playbook cases change |
| 323 | `ff2a02565c` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook's source Move check while IntelliJ has no document of a moved file yet |
| 324 | `5e5675c80f` | P12 | — | L10 | Tell the IDE of disk changes, and wait until the server has them | Wait in the IntelliJ playbook until the server has heard of a change on disk, by its own record |
| 325 | `3d46dea11b` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.0, and format every range IntelliJ asks for |
| 326 | `fb33bc2c54` | P7 | — | L6 | Fold into the commits that added each workaround | Decode related diagnostic reports with LSP4IJ's adapter (UP06) |
| 327 | `b9bd8413a8` | P7 | — | L6 | Fold into the commits that added each workaround | Keep explicit null settings with LSP4IJ's adapter (UP08) |
| 328 | `8747b265b8` | P7 | — | L6 | Fold into the commits that added each workaround | Let snippet completions insert as they are (UP18) |
| 329 | `86ecd91c25` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Offer server save formatting in IntelliJ (UP02) |
| 330 | `94be2a2012` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open bundled library sources as the server's virtual documents (UP25) |
| 331 | `3282c34557` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check inline completion's invocation and selection natively (UP26) |
| 332 | `d444a40688` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Open a document link from Ctrl+Click, and retire the replacement gates (UP52) |
| 333 | `27be81309c` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Run the debugger cases 0.30.0 implements in IntelliJ (UP30, UP33-UP34, UP45-UP48, UP50) |
| 334 | `047d6da067` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check the exception hint, logpoints and hit conditions in IntelliJ (UP31-UP33, UP55) |
| 335 | `6b6ecfd15e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Call LSP4IJ 0.30.0's APIs directly in the playbook's probe (UP34, UP45, UP46) |
| 336 | `dfc70eb4be` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check lazy variables, hexadecimal values and paged variables in IntelliJ (UP35, UP36, UP42) |
| 337 | `f01183901e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check that IntelliJ rebuilds its debugger views on invalidated (UP40) |
| 338 | `b894d76c21` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check what a chosen completion inserts in Evaluate Expression (UP37) |
| 339 | `08794343f1` | P7 | — | L6 | Fold into the commits that added each workaround | Use LSP4IJ's own Parameter Info for Ecstasy (UP10) |
| 340 | `82052a01d8` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ reconnect documents below a moved directory (UP19) |
| 341 | `b84640b640` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ batch the editor refreshes (UP27) |
| 342 | `0e89d23fc8` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ watch the compiler roots outside the project (UP05) |
| 343 | `ba7f464bff` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ resolve and apply lazy code actions (UP11) |
| 344 | `14b2396d74` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep pull results and quick fixes (UP07) |
| 345 | `c4e16524b3` | P7 | — | L6 | Document notification order (net almost zero) | Let LSP4IJ order document notifications and check server edits (UP09, UP04) |
| 346 | `fc17d62b35` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ's process provider own its start and stop (UP01) |
| 347 | `59059155d3` | P5 | — | L3 | — | Send no signature parameters without their metadata |
| 348 | `a3f3824198` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ save the documents a rename changes without an editor (UP21) |
| 349 | `ee7d40b9e7` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check two-range formatting, Go to Symbol and inlay tooltips natively (UP54, UP59, UP60) |
| 350 | `74583e7499` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read a signature without parameters in the playbook |
| 351 | `170c2ab192` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X132, X146 and X254 for LSP4IJ 0.30.0 |
| 352 | `8c0290e358` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Fix the native run's X254 for LSP4IJ 0.30.0 |
| 353 | `dbfcb61716` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X254's library view through the server |
| 354 | `e375ba370a` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Read X5's library target from the server until LSP4IJ connects it |
| 355 | `a0907aa036` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Wait for X131's Ctrl+Click resolve in the console's trace |
| 356 | `81bf2290a8` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Review the internal LSP4IJ APIs for 0.30.0 |
| 357 | `bf8f27c441` | P7 | — | L6 | — | Describe both save formatting owners in IntelliJ (UP02) |
| 358 | `ec5af0c35a` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect X20's signature without parameters in VS Code |
| 359 | `ca24b41755` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep a disposed language server stopped (UP76) |
| 360 | `f4df953e04` | P10 | — | L9 | Physical expression fragments (net small) | Let LSP4IJ make the debugger's expression fragments physical (UP37) |
| 361 | `bdbf2c159c` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ keep the problem mark of a file that opens or closes (UP70) |
| 362 | `479d570a01` | P10 | — | L9 | A line typed in the Debug console (net small) | Let LSP4IJ evaluate a line typed in the Debug console (UP71) |
| 363 | `bad5b2f9e7` | P7 | — | L6 | Held edits before completion and signature help (net small) | Let LSP4IJ send the edits it holds before a request (UP73) |
| 364 | `4f8d11debe` | P7 | X5 | L6 | Build against LSP4IJ 0.30.1 | Build against LSP4IJ 0.30.1, which is 0.30.0's code |
| 365 | `3611a12607` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Expect an expression editor's fragment as an Ecstasy file, as LSP4IJ 0.30.0 makes it |
| 366 | `cfdf0286c1` | P10 | — | L9 | A library frame's source in a folder the VFS has not seen (net almost zero) | Let LSP4IJ find a stack frame's source in a folder the VFS has not seen (UP65) |
| 367 | `5f06796f9b` | P10 | — | L9 | Run to Cursor's breakpoint (net small) | Let LSP4IJ keep Run to Cursor's breakpoint until the program stops (UP44) |
| 368 | `03f642f0f1` | P10 | — | L9 | Attach the debugger to a program run with --debug, with the agent in javatools | Attach in LSP4IJ's attach mode, with the session's own process (UP64) |
| 369 | `a126b6d6c3` | P7 | — | L6 | Completion item documentation on Ctrl+Q (net small) | Let LSP4IJ document a completion item from its resolve (UP78) |
| 370 | `2c50948d4c` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask LSP4IJ whether a file's diagnostics are still to be painted (UP75) |
| 371 | `ad1112c79f` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Restart with X259's change still pending, as LSP4IJ closes it cleanly (UP09) |
| 372 | `bda15073af` | P7 | — | L6 | Fold into the commits that added each workaround | Keep the stub server's stop from throwing as a plain rule, not for LSP4IJ (UP77) |
| 373 | `e644ff80a6` | P7 | — | L6 | Project Errors: a closed file's errors and the embedded build's | Let LSP4IJ pull the workspace's diagnostics for closed documents (UP69) |
| 374 | `98718c75dd` | P6 | — | L4 | No code action budget (drop all three) | Drop the server's code action budget, which no client asks for since LSP4IJ 0.30.0 (UP74) |
| 375 | `4ffcaba78e` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Ask X254's definition again when the restarted server retires its analysis |
| 376 | `02942c8ac3` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Check X350 in IntelliJ through the server's library documents (UP25) |
| 377 | `b88642f6e0` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Keep ClosedRefactoringDocuments for a rename that also moves its directory (UP21, X116) |
| 378 | `a6a079a5f5` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Mark IntelliJ's X350 partial: Go to Definition doesn't navigate inside a library view |
| 379 | `b45d05c3cc` | P13 | X5 | L10 | Packaging tasks and build defaults | Suppress UnstableApiUsage where the build still calls incubating Gradle API unsuppressed |
| 380 | `9a6d2d4271` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Ecstasy tips in IntelliJ's Tip of the Day, the tips of features not yet used first |
| 381 | `94995180eb` | P8 | — | L7 | — | Add a Get Started walkthrough to the VS Code extension |
| 382 | `c71a0704bd` | P12 | — | L10 | Individual playbook case fixes | Wait in the IntelliJ playbook until LSP4IJ's Go to request ends, and close a modal dialog a case leaves open |
| 383 | `ce0ef95839` | P10 | X4 | L9 | The debugger finds sources where its host names them | Find a module's sources only where the debugger's host names them, as modules no longer record where they were compiled |
| 384 | `5b3b418c30` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Add errs' regression tests for compiler diagnostics, embedding, cancellation and duplicate types |
| 385 | `d4d9159d48` | P3 | X3 | — | Expose recovery, cursor fitting and declaration analysis to compiler hosts | Ask testFit's TypeInfo of a conversion as a probe, not with the runtime's silent form |
| 386 | `d796f614a2` | P1 | X1 | — | Regression tests for diagnostics, embedding, cancellation and duplicate types | Check duplicate source types through the command line, and implicitly abstract classes, in manualTests |
| 387 | `56380eac05` | P4 | — | L1, L2 | Integrate compiler-backed language support (split by package, see below) | Remove monikers from the language server, as neither editor uses them |
| 388 | `327de73b5f` | P7 | — | L6 | Tips of the Day and Got It tooltips | Show Got It tooltips in IntelliJ when the server starts with Tree-sitter and when it stops unexpectedly |
| 389 | `f34925546b` | P12 | — | L10 | The VS Code tour | Step X306 only once VS Code shows the stop, and read each step's stack from after its stop |
| 390 | `7153d75cdb` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's first documentation after a restart at the next Ctrl+Q when the hover is late (UP82) |
| 391 | `700cac8211` | P12 | — | L10 | IntelliJ tour step fixes | Edit in the tour's kill step only once the restarted server has the file open (UP83) |
| 392 | `a435dfbef5` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Name LSP4IJ 0.30.1 in the plugin's README and two test comments |
| 393 | `abc6184faf` | P7 | — | L6 | Fold into the commits that added each workaround | Let LSP4IJ 0.30.1 outdate its cached answers, without UP14's semantic-result retirement |
| 394 | `bcebaee40e` | P6 | — | L4 | Rename guards and proofs | Refuse a plain rename that also changes the configured source modules, and say why |
| 395 | `b57b1f0e0f` | P7 | — | L6 | Keep XtcRenameHandler and ClosedRefactoringDocuments, with the reasons | Say why XtcRenameHandler stays, instead of tagging it as an LSP4IJ workaround (UP04) |
| 396 | `26ef1105db` | P10 | — | L9 | The debugger finds sources where its host names them | Name a launched module's source directory from the file the launch names it by, when the source graph lacks it |
| 397 | `a9215634a0` | P7 | — | L6 | Send the server a file's current text when its didOpen was older (UP83) | Send the language server a file's current text when its didOpen was older, on LSP4IJ 0.30.1 (UP83) |
| 398 | `28e0223afe` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Accept the lifecycle listener that OpenedTextRepair implements among the reviewed LSP4IJ internal APIs (UP83) |
| 399 | `c641f5133d` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Record LSP4IJ 0.30.1's IDE error for a stopped server's closed documents as known in the playbook (UP81) |
| 400 | `43dfde02ca` | P7 | — | L6 | Build against LSP4IJ 0.30.1 | Show an Ecstasy hover that takes LSP4IJ 0.30.1 longer than 500 ms in Quick Documentation (UP82) |
| 401 | `f64c680167` | P10 | — | L9 | End a debug run with the exit code LSP4IJ 0.30.1 keeps (UP56) | End a debug run with the exit code LSP4IJ 0.30.1 keeps from the exited event (UP56) |
| 402 | `4456c878aa` | P12 | — | L10 | The playbook on LSP4IJ 0.30.1 | Type with linked editing on in IntelliJ's X133, now that LSP4IJ 0.30.1 has it (UP53) |
| 403 | `ba51925a77` | P12 | — | L10 | IntelliJ tour step fixes | Check that one server comes back when the tour kills the server's process (UP72) |
| 404 | `4d59b37a62` | P12 | — | L10 | IntelliJ tour step fixes | Show the tour's LSP4IJ card as what 0.30.1 brings to the IDE, not what 0.30.0 will |
| 405 | `445227624d` | P10 | — | L9 | The debugger finds sources where its host names them | Find a debugged module's sources in the workspace when no source graph names it |
| 406 | `3a82d3e6e7` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Build, run and debug with Gradle while the workspace's own modules replace bundled libraries |
| 407 | `f2db77f964` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Name each source set's project by its path in the build tree in the IDE's Gradle model (#774) |
| 408 | `d3c2ec5b91` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Tell the client each module's Gradle project from the imported build model (#774) |
| 409 | `e729849350` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in IntelliJ, which reaches a composite's included builds (#774) |
| 410 | `cce3b4f0d7` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Run a module's Gradle tasks in its own project in VS Code, which reaches a composite's included builds (#774) |
| 411 | `3ef0e0e643` | P9 | — | L8 | Gradle tasks in each module's own project (#774) | Expect the XVM repository's Gradle-mode Run, Debug and tests to reach its included builds in manual check 18a.20 (#774) |
| 412 | `d8a27c0b61` | P6 | — | L4 | Rename guards and proofs | Prove a rename in a workspace module that replaces a bundled library as the workspace's sources |
| 413 | `f0d5e201a6` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Offer a replaced library's import candidates from the workspace's compiled library, not the bundle's |
| 414 | `5f31de3524` | P11 | — | L5 | Replaced libraries: their import candidates and their errors | Say on a replaced library with errors that the server's own compiler may be older than the repository's |
| 415 | `60b393f7a3` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Add an editor setting for whether the workspace's own modules replace bundled XDK libraries |
| 416 | `2eb0b4d163` | P6 | — | L4 | Rename guards and proofs | Find a text's line breaks once per comparison in a rename's proof, not once per position |
| 417 | `10970d87cd` | P6 | — | L4 | Rename guards and proofs | Prove a rename where the facts cannot prove every binding, unless one spells a name the rename changes |
| 418 | `5f44add78d` | P6 | — | L4 | Rename guards and proofs | Refuse a rename over ecstasy's sources below a 2 GB heap, and test a rename of the workspace's ecstasy |
| 419 | `e732aef7a5` | P4 | — | L1, L2 | Time and heap logs of the server's work | Log each project query's and each compile's time and heap |
| 420 | `7bcbe3b407` | P4 | — | L1 | Time and heap logs of the server's work | Log what the server did in each minute in which it worked, and over its session |
| 421 | `d2497070e2` | P7 | — | L6 | The language server JVM's memory setting | Choose how the language server's JVM uses memory: the JVM's default or a set maximum, or adaptive |
| 422 | `b9dda07133` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Keep a replaced library to the workspace folder that replaces it, so a window's other projects build embedded |
| 423 | `15bfb50c98` | P9 | — | L8 | Library replacement in Gradle mode, per workspace folder, with the editors' setting | Show the embedded compiler as not available where the project's own modules replace bundled libraries |
| 424 | `4610212d5c` | P13 | — | L10 | The user guide, with screenshots from the tours | Add a user guide for the Ecstasy language tooling, with screenshots from both editor tours |
| 425 | `088a676cdc` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Put every Ecstasy settings page of the IntelliJ plugin under Languages & Frameworks \| Ecstasy |
| 426 | `0ae33f4a07` | P13 | — | L10 | The user guide, with screenshots from the tours | Make the user guide's screenshots from tagged tour steps, rebuilt by guide.py from any tour run |
| 427 | `f073d9fc19` | P7 | — | L6 | Every IntelliJ settings page under one Ecstasy node | Fit IntelliJ's Language Service and Server Runtime pages to the Settings dialog, and show both in the user guide |
| 428 | `5fd5523007` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in IntelliJ, each module's in turn with only its own |
| 429 | `d1641d1822` | P13 | — | L10 | The user guide, with screenshots from the tours | Show IntelliJ and VS Code side by side in the user guide wherever both tours show the feature |
| 430 | `544d88d7c3` | P13 | — | L10 | The user guide, with screenshots from the tours | Move the user guide into lang/doc/user-guide, with its screenshots in images |
| 431 | `523b1d7dff` | P10 | X4 | L9 | Lazy properties and their time limit | Let a debug launch or attach set how long evaluations and lazy values may run |
| 432 | `22a8e6ccbc` | P12 | X5 | L10 | The playbook container and the IDE it drives | Say that gradle.properties leaves the lang build flags off, and pass them in the playbook container's gradle mode |
| 433 | `a66a7fba2c` | P9 | — | L8 | Run All Ecstasy Tests in IntelliJ | Rerun the failed tests of Run 'All Ecstasy Tests' in Gradle mode, each module's in its own project's testXtc |
| 434 | `9b96d6d4b4` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide that embedded tests run with the server's XDK and xunit, not the build's |
| 435 | `512e6a8bec` | P13 | — | L10 | The user guide, with screenshots from the tours | Give testing its own chapter in the user guide: write, find, run, read, rerun, debug, build modes, troubleshooting |
| 436 | `1adc13a4b4` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a rename or safe delete changes a public declaration, that modules outside the workspace are not changed |
| 437 | `f5fc91bb8f` | P6 | — | L4 | Prove code actions with incremental compiles | Start a rename's proof from the revision's compile, and compile after the edit only what it reaches |
| 438 | `e9beeed169` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Let a client's cancellation of a rename, or of xtc/build, reach the adapter's work |
| 439 | `2b5287a637` | P10 | X4 | — | Debugged stacks end at the host's fiber; failed requests say why | End a debugged program's stack at the host's fiber, also when it has moved on from its call |
| 440 | `db2c9d725a` | P10 | — | L9 | Debugged stacks end at the host's fiber; failed requests say why | Answer a failed stack trace, scopes or exception request with its reason, not "Internal error." |
| 441 | `0268310ee3` | P5 | X2 | L3 | Bind more names in the semantic model | Bind a local or parameter used for its formal type, as in t1.DataType, to its declaration |
| 442 | `b6a7635f0c` | P5 | — | L3 | Bind more names in the semantic model | Make the _ placeholder no occurrence in the semantic model |
| 443 | `a526cb43fb` | P5 | X2 | L3 | Bind more names in the semantic model | Bind the names of a method's multiple return values to the values they declare |
| 444 | `0e19edf0ba` | P5 | X2 | L3 | Bind more names in the semantic model | Bind this in a lazy property's calc() and super in a method reference to what they name |
| 445 | `b4d0ef6218` | P6 | — | L4 | Say what a change to a public declaration reaches outside the workspace | Say, when a file move or rename or a plain rename changes a public declaration, that modules outside the workspace are not changed |
| 446 | `0a519c3c89` | P12 | — | L10 | Individual playbook case fixes | Give the VS Code client its own handler of the server's messages back after the playbook's CFG2 |
| 447 | `75f8d0d814` | P12 | — | L10 | IntelliJ tour step fixes | Close the language server's messages as the IntelliJ tour restores the project, so a rename's note stays in its own frames |
| 448 | `c9d943aa62` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Name the constants of an artifact's API by their index in its stripped pool, which needs none of its dependencies |
| 449 | `362d7dcfc4` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Take an artifact's API fingerprint from javatools' FileStructure.writeApiTo, as Gradle's builds do |
| 450 | `e25a440b5e` | P6 | — | L4 | Prove code actions with incremental compiles | After a rename's edit, compile only the modules depending on the edited ones that it can reach |
| 451 | `9e39405f9c` | P6 | — | L4 | A client's cancellation reaches the adapter's work | Ask an adapter's work to stop on every cancellation of a converted result, also once the result is done |
| 452 | `cc674a23f3` | P13 | — | L10 | The user guide, with screenshots from the tours | Say in the user guide what a rename doesn't change or check |
| 453 | `0be2a5ddac` | P5 | — | L3 | Bind more names in the semantic model | Bind a primary constructor's parameter passed in an extends or incorporates clause to the property it declares |
| 454 | `de62643b50` | P5 | X2 | L3 | Bind more names in the semantic model | Keep the initializer facts of a property declared in a loop's body, such as a static local |
| 455 | `4b1463832e` | P5 | — | L3 | Bind more names in the semantic model | Bind a local or parameter used as a type, as in new Pair<String, type>(), to its declaration |
| 456 | `7b3f229727` | P5 | — | L3 | Bind more names in the semantic model | Declare the names of a function type's values in the semantic model, as of its return values in a typedef |
| 457 | `cdaa2f3207` | P5 | — | L3 | Bind more names in the semantic model | Bind a reserved name such as this:class in a property's accessor, as in a method of the class |
| 458 | `0c33fd2e34` | P5 | — | L3 | Bind more names in the semantic model | Bind this.Outer in a virtual child to ecstasy's Outer interface, the class its name spells |
| 459 | `807419f1b7` | P1 | #802 | — | Unused constructor arguments and defaults (open PR #802) | Report constructor arguments and default values that no constructor uses |
| 460 | `8de836047e` | P6 | — | L4 | Rename guards and proofs | Prove XdkRenameUnprovableTest's refusal on a binding that stays unprovable, CompileType.Element |
| 461 | `f992a7a0d2` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Digest the API of each declaration of a module apart (DeclarationDigests) |
| 462 | `36881faadc` | P11 | — | L5 | Compile only what an edit changes: declaration digests and the member-use tracker | Compile again, after an edit, only the modules that use what it changed (member-use tracker) |
| 463 | `6f1818cfe0` | P11 | — | L5 | Library copies: no diagnostics, compiled on their own | Keep diagnostics working when a restart opens another server's library copy again |
| 464 | `ef681e7cff` | P9 | — | L8 | Supervised execution workers | Take the execution worker's events off its standard output, onto a loopback socket of their own |
| 465 | `e9e8be2f8c` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export each project's xtcRun and xtcTest settings in the language server's Gradle model |
| 466 | `b4d7869f07` | P9 | — | L8 | Embedded runs with their Gradle project's xtcRun and xtcTest settings | Run embedded programs with their Gradle project's xtcRun and xtcTest settings, and say so in both editors |
| 467 | `5b23815405` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Drop TypeConstant's import of Silence.PROBE, whose only use went with getJitICType() |
| 468 | `c73fcd8edd` | P1 | X1 | — | Require explicit error listeners, separate reporting from abort policy | Say silent(PROBE) at each newable check of #803's JIT code, and drop the helper that chose a listener for them |
| 469 | `a14af8d820` | P11 | X2 | — | Compile only what an edit changes: declaration digests and the member-use tracker | Link DeclarationDigests' Javadoc to XvmStructure.registerConstants, which javadoc could not find as its own |
| 470 | `6953dfb86b` | P9 | X4 | — | The Gradle model: each source set's project path, xtcRun's and xtcTest's settings | Export the JVM arguments that xtcRun's and xtcTest's JVMs get, now that jvmArgs holds only the build script's |
| 471 | `e3d49dbce2` | P13 | — | L10 | The user guide, with screenshots from the tours | Tighten the user guide: one place for each fact, plainer sentences, and what a newcomer needs |
| 472 | `1ff22d7e8d` | P13 | — | L10 | The user guide, with screenshots from the tours | Split the user guide into a landing page, one page per chapter, and features and shortcuts pages |
| 473 | `cc77837927` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Keep each language server's whole trace in playbook runs, with what each diagnostic pull answered |
| 474 | `450eaa3e97` | P12 | — | L10 | What a failing diagnostics wait saw: whole server traces and what LSP4IJ holds | Say in the tour's failing diagnostics waits what LSP4IJ holds, and in a popup step what could close it |
| 475 | `eadc61d407` | P7 | — | L6 | Marks that LSP4IJ made before it connected a file's document (UP86) | Take back an editor's marks that LSP4IJ made before it connected the file's document (UP86) |
| 476 | `39a446378d` | P12 | — | L10 | IntelliJ tour step fixes | Press Ctrl+F1 in the tour's Warnings step once the edit's code lenses are in place |
| 477 | `d7fdf47007` | P13 | — | L10 | The user guide, with screenshots from the tours | Mark the user guide's keys up as keys, and the names it left plain as code |
| 478 | `4863fade86` | P13 | — | L10 | The user guide, with screenshots from the tours | Add an index of every page and section of the user guide to its landing page |
| 479 | `7102a7234b` | P13 | — | L10 | The user guide, with screenshots from the tours | Give the user guide's long sections subsections, and its index the third level |
| 480 | `244f824635` | P13 | — | L10 | The user guide, with screenshots from the tours | Box the user guide's single screenshots with their captions, as its pairs are |
