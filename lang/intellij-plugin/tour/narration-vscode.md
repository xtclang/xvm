# Narration of the VS Code tour

What a voice says over the VS Code extension's tour, step by step, as text: the tour's steps are those of
`lang/vscode-extension/src/test/tour/script.ts`, and the video is the one that `compose.py` makes from a run of it.

Each `##` heading is a section of the tour, named exactly as the tour names it, in the tour's order; the paragraph
right under it is spoken over the section's title card. Each `###` heading under a section is one of its steps, named
exactly as the step's title, and the paragraph under it is spoken over that step. A card that the tour shows as a step
of its own, such as the one that opens Build and run, has its `###` like any other step, after its section's paragraph.
Steps are matched by their section and title, never by number, as the composer renumbers the steps it leaves out. A
line in `<!-- -->` is a note, not spoken. When a step's title changes in the tour, its heading here changes with it.

<!-- The tour's Protocol section, between Settings and The end, has checks only and no steps, so the video does not show
     it and it has no heading here. -->

## Start here

This is a tour of the XTC extension, which brings the Ecstasy programming language to Visual Studio Code. The extension
runs Ecstasy's own compiler beside the editor, as a language server that reads your code as you type. The whole tour
works on one small project. First, an overview of what it covers, and then the project itself.

### Ecstasy in VS Code

The tour comes in two halves. The first reads and edits code: highlighting, hovers, completion, navigation, errors and
their fixes, refactoring and formatting. The second builds, runs, debugs and tests the program, embedded or with Gradle.

### The project

The project is a card game, Crazy Eights. A player plays a card of the same suit or rank as the top of the pile, and an
eight matches anything. Its layout, with a Gradle build and xunit tests, is what Create New Project makes.

### Open the project

The project opens with its files in the Explorer, and the game's Eights class in the editor. The extension starts its
language server, which comes with its own XDK, the Ecstasy development kit, and the status bar says when it is ready.

## Editing code

Editing code comes first: reading what is there, and writing more. The colors, hints, hovers and completion all come
from the language server, which sees the code as the compiler does, rather than matching its text. Then come formatting,
comments, folding, and the views that show a file's shape.

### Semantic highlighting

The compiler colors each name by what it is: a field, a parameter, a local, a type or a static member. A grammar alone
cannot tell these apart, so the colors show at a glance what each name refers to.

### Inlay hints

Where a call's arguments don't say what they are, hints show the names of its parameters. Here, the two arguments of the
deal method are labeled with their parameters, that and numCards.

### Hover documentation

Hovering over a name, or Control K, then Control I, shows its declaration and its doc comment, as the compiler sees
them. Here it is the game's initial hand size constant, with the comment that says how many cards are dealt.

### The XDK's documentation

Hovers work the same for the XDK's own types. HashMap's comes from the sources of the ecstasy module, Ecstasy's core
library, so you can read about a standard type without leaving the editor.

### Parameter hints

Inside a call, Control Shift Space shows the parameters of the method it calls, with the one at the cursor in bold. Here
the cursor is on the second argument of the deal method, so numCards is the one in bold.

### Member completion

After a dot, completion lists the members of the expression's type, as the compiler resolves it, each with its type.
Here, after hand and a dot, the list has the hand's methods, such as addCard and popCard.

### Accept a completion

Typing narrows the list, here to addCard, and Enter takes the item, with its documentation shown beside it. The
completed call compiles.

### Snippets

The extension's snippets complete too. Typing meth and pressing Enter writes a method, and Tab then moves through its
return type, its name and its parameters, for you to fill in.

### Code that needs formatting

In this method, swapCards, a call is indented too far. The formatter indents Ecstasy code by its blocks, and keeps the
spacing within a line as it is written.

### Format the document

Shift Alt F formats the file through the language server's formatter, with the editor's tab size and the extension's
formatting settings. The call in swapCards is now indented by its block, like the code around it.

### Format as you type

Typing an opening brace and Enter indents the new line and closes the block. The server formats each line as it is
typed, so the code stays laid out while you write it.

### Comment out lines

Control slash turns the selected lines into line comments, as the language's configuration says, and pressing it again
turns them back into code.

### Folding

Methods and doc comments fold, where the compiler's folding ranges mark them. Control K, then Control zero folds them
all, leaving a short outline of the class to read at a glance.

### Expand the selection

Shift Alt Right grows the selection by the syntax around it: first a name, then the expression, then the index, then the
statement. Shift Alt Left shrinks it again, step by step.

### Highlight occurrences

With the cursor on a name, every use of it in the file is highlighted. Here it is the local variable card, and its uses
come from the compiler's references to it, rather than from a search of the text.

### The Outline view

The Outline view lists the file's classes, properties and methods, from the compiler's symbols. It follows the cursor as
it moves, so it always shows where you are in the file.

### Code lenses

Above each declaration, a lens counts its references in the project, the tests included. A click on the count lists
them, so you can see what uses a declaration without a search.

## Navigation

Navigation is about finding your way through a project you don't know yet: from a call to the method it calls, from a
name to its type, from a method to everything that uses it. Each answer comes from the compiler, and it reaches into the
XDK's own sources too.

### Go to definition

F12, or Control click, on a call goes to the method it calls. The deck's shuffle method is declared in CardCollection,
which Deck inherits from, and that is where the editor goes.

### Go to type definition

Go to Type Definition goes to the type of what is under the cursor. The variable deck is a Deck, so the editor opens the
Deck class, in its own file.

### Go to implementations

Control F12 on an interface's method lists its implementations in a peek. The choose method of the PlayStrategy
interface has two, in the FirstMatch and SaveEights strategies.

### Find all references

Shift Alt F12 finds every use of a declaration, across all of the project's modules, the test modules included, and
lists them in the References view. Here they are the uses of the player's cardMatches method.

### Call hierarchy

Show Call Hierarchy lists who calls a method, and who calls those callers in turn. The cardMatches method is called by
the player, by both strategies and by a test.

### Type hierarchy

Show Type Hierarchy lists a class's subtypes, or its supertypes. Here, CardCollection has two subtypes, Deck and Hand,
which both extend it.

### Go to a symbol

Control T finds a class, method or property by its name, in the project and in the XDK. Here, typing reshuffle finds the
game's method of that name, and Enter goes to it.

### The XDK's sources

Go to Definition also opens the XDK's own sources, read-only. HashMap is declared in the ecstasy module's maps package,
and its source is there to read, just as the game's own code is.

### A named argument's parameter

From a named argument, Go to Definition opens the parameter that it names. Here a call of the array's freeze method
names its argument inPlace, and the editor opens that parameter in the XDK's source, read-only.

## Diagnostics and fixes

Diagnostics are the compiler's errors and warnings. The extension checks your code as you type, so a mistake shows the
moment you make it, and some come with a fix that writes the missing code for you. This section makes a few mistakes on
purpose, and fixes them.

### Errors as you type

A misspelled method is an error as soon as it is typed. The compiler checks the text in the editor, not the saved file,
so you don't need to save or build to find out.

### The Problems view

Control Shift M opens the Problems view. It lists the errors and warnings of the project, each a click away from its
code, in whichever file it is.

### The error's message

F8 goes to the next problem in the file, and shows the compiler's message under the line, with its error code, so you
can read it in place.

### Errors in other files

Renaming a property by hand, here the card's rank, breaks its uses in other files and in the test modules, none of them
open. The Problems view lists them all, before anything is saved.

### Quick fixes

Control period on an error offers its fixes. The game calls an announce method that doesn't exist yet, so the language
server offers to create it.

### Create a method

The fix writes the new method, with the parameter types that the call implies, here a Player, and leaves its body for
you to fill in. The error is gone.

### Add an import

A type that isn't imported has a fix that adds its import. Here, a test module uses the game's Suit type without
importing it, and the fix adds the import.

### Organize imports

Shift Alt O removes the imports that nothing uses, and sorts the rest. Until then, an unused import is faded, as this
one in the player's tests is.

## Refactoring

Refactoring changes the shape of code without changing what it does. Before any change, the language server recompiles
everything it touches, and checks that each name still refers to what it did. When it can't prove that, it refuses,
rather than leave your code broken.

### Rename a method

F2 renames a method. The box takes the new name, here canFollow for the player's cardMatches, and the server checks it
before anything changes.

### Renamed everywhere

Enter renames the method and every call of it, here in the player, in the FirstMatch and SaveEights strategies, and in
the PlayerTest test module, as one change of four files.

### Undo the rename

One Control Z takes the rename back in all four files at once, without asking, and leaves them unsaved. The call and the
declaration are cardMatches again.

### Undone everywhere

The tests in PlayerTest call cardMatches again too, as do both strategies, and the project compiles just as it did
before.

### Rename a class

Renaming a class renames its file too, as Ecstasy keeps one type per file, and changes every use of it. Here Scoreboard
becomes Tally, and the game now uses a Tally.

### A rename it refuses

F2 on a name that the XDK declares, here Console, is refused before anything changes, and the server gives its reason.

### Rename a file

F2 in the Explorer renames a file, and the class in it along with it. Hand becomes PlayerHand, and every use of it in
the project follows.

### Move a file

Cut and Paste in the Explorer moves a type to another package. Suit moves to the cards package, and every use follows,
by its new package name, or in Deck, where cards already names a property, by an import.

### Refactorings

Control Shift R on a selection offers the server's refactorings, such as extracting it to a local variable or to a
private method.

### Extracted to a local

The returned expression, the player whose turn is next, is now a local variable, a Player, the type that the method
returns, and the return statement reads it.

### Extract a method

Statements extract to a private method too. The three lines that print the hand sizes become one call of a new method,
which holds them.

## The extension's commands

The extension adds commands of its own to the Command Palette, each starting with the word Ecstasy, and some have keys
too. They show what the language server is doing and how it is set up, and change what it builds. The last of them makes
a new project.

### The server's log

Control Alt X, then L shows the language server's log, and pressing it again hides it. The log says what the server
started with, and what it has done since.

### The effective configuration

The Show Effective Language Service Configuration command reports the settings the server runs with, and what it found.
It is the first place to look when something doesn't work as you expect.

### Compiler paths

The Show Effective Compiler Paths command lists the modules the compiler builds, as it found them in the project's
layout or its Gradle build.

### Configure compiler paths

The Configure Compiler Paths command edits what the compiler builds. Its Quick Pick offers explicit modules, consumers
from other folders, or the Gradle model.

### Libraries and sources

The Configure Libraries and Sources command sets the compiled modules that the project builds against, and where their
sources are.

### Switch the language adapter

Control Alt X, then A, or a click on Ecstasy in the status bar, switches the server's adapter: the compiler's, or
Tree-sitter's, which knows only syntax, as a fallback for a project the compiler has trouble with.

### Export the server's logs

The Export Server Logs command saves the server's logs to a ZIP file, to attach to a report when something goes wrong.

### Create a project

The Create New Project command asks for a name, a kind and a folder, then runs xtc init in the terminal. The result is a
Gradle project, ready to build.

## Build and run

Now the tour turns from editing to running. This section builds the card game, runs it, and plays it in a terminal. The
same commands, keys and lenses work whichever way the folder builds, and the section ends by running the game with
Gradle, then listing the extension's tasks.

### Build and run

Ecstasy projects build and run two ways in VS Code, chosen per folder. Embedded uses the language server's own compiler
and execution worker, with no Gradle, for the fastest round trip. Gradle runs the XTC Gradle plugin's tasks, with the
build's own settings and XDK.

### The build mode

In a folder with a Gradle build, the status bar says how it builds. A click chooses the embedded compiler, Gradle, or
automatic, which builds with Gradle when there is a Gradle build.

### Build

Control F9 builds every module with the language server's compiler, without Gradle, and shows the build in the terminal.
There is nothing to sync or install first.

### A failed build

When a build fails, its errors are listed with their files and lines, each a click away from its code. Here they show in
the build's terminal.

### Run from the editor

The Run button of a module's editor runs it in the embedded execution worker, beside the language server, in a terminal
of its own. Here it starts a game of Crazy Eights.

### Program input

The program reads what you type in its terminal. Each Enter plays a turn, and the terminal shows what each player did.

### Run with Gradle

In Gradle mode, the same Run runs the XTC Gradle plugin's runXtc task, with the build's own settings and its XDK, in the
terminal. The game plays the same way.

### Tasks

Run Task lists the extension's tasks. In Gradle mode, the build's Build, Test and Clean tasks are there, beside the
tasks that the folder's own task configuration defines.

## Debugging

Now the debugger. VS Code's own debugger connects, through the extension's debug adapter, to a debug session in the
language server, which builds and runs the program just as Run does. This section stops the card game in many ways,
steps through it, and looks at its state while it is paused.

### Debugging

F5 debugs the module, embedded or with Gradle. The debugger stops at line, conditional, hit count, function, data and
exception breakpoints, and at logpoints. It steps over, into and out, and shows fibers, frames, variables and watches.

### A conditional breakpoint

A breakpoint can have a condition. This one, in the constructor of Deck, stops only when the deck adds the Queen of
Spades, and runs past every other card.

### Debug

F5 debugs the module of the active editor, with no configuration needed. The program stops where the condition first
holds, and the Variables view shows the card, the Queen of Spades.

### Frames and the call stack

When it continues, the program stops at a line breakpoint while it deals the cards. The Call Stack view shows deal,
called from initializeGame, in the constructor of Eights.

### Step into

F11 steps into the call on the current line. Here that is popCard, the method that takes the top card off the deck.

### Step over

F10 runs the current line and stops at the next one. The Variables view shows what the line changed, so you can follow a
value as it changes.

### Step out

Shift F11 runs to the end of popCard and stops back in deal, the method that called it, with the card that popCard
returned.

### Set a value

Set Value changes a variable of the paused frame. Here numCards becomes six, so this player is dealt one card more, and
the program goes on with the new value.

### Run to the cursor

Run to Cursor runs to the line with the cursor, here in dealAll, as a breakpoint there would, without your having to set
one.

### Step into a target

On a line with several calls, Step Into Target asks which one to step into. Here the line calls both deal and size, and
you pick the one you want.

### Watches

A watch, here the numCards parameter of deal, is evaluated again at every stop. Watches and hovers read the program's
values, and run none of its code.

### The Debug Console

Control Shift Y opens the Debug Console. An expression typed there runs in the paused frame, and completion offers the
frame's names as you type.

### Restart a frame

Restart Frame, on a frame chosen in the Call Stack view, runs that frame again from its first line. Here the dealAll
method starts over.

### A logpoint

A logpoint prints a message instead of stopping. This one writes who plays, and the cards left in their hand, to the
Debug Console, at every turn.

### Pause

When it continues, the game waits for Enter, and a line typed in the Debug Console is its input. Paused while it waits,
the program stops as soon as it next runs, once it has that line.

### A function breakpoint

A function breakpoint stops in a method by its name, wherever it is declared. Here, recordWin is typed in the
Breakpoints view, to stop the program as the game ends.

### Fibers

Played to its end, the game stops in recordWin. In Ecstasy, a service runs on fibers of its own, so the Scoreboard
service shows in the Call Stack view beside the game's fiber.

### Library frames

Stepping into the map's put method goes into the XDK. Its frames and its sources show just like the program's own, and
the sources open read-only.

### A data breakpoint

Break on Value Change, on a field in the Variables view, stops where that field changes. Here it watches the second
player's strategy, to find where Bob is given his.

### An exception

Here is a bug: popCard reads past the end of its cards. The debugger stops where the uncaught exception is thrown, and
shows its message and its stack.

### Launch configurations

Add Configuration, in the launch configuration file, offers the extension's snippets: a module to launch, or a program
to attach to. A configuration can give arguments, or stop at the first line.

### Stop on entry

With stop on entry set, a launch configuration stops the program at its first line, before it does anything, so you can
step from the very start.

### Breakpoints while you edit

A breakpoint follows its line as you type a line above it. The running program was built before the edit, and it still
stops at the code where the breakpoint was set.

### Attach to a program

The Copy Option to Debug a Program command copies the debug option for xtc run. A program run with it waits for the
Attach configuration, and then stops at its breakpoints.

### Debug with Gradle

In Gradle mode, F5 runs the runXtc task under the debugger. The program connects to the debug session, and stops at the
same breakpoints.

## Tests

Ecstasy's test framework is xunit. A test is a method with the Test annotation, and a project usually keeps its tests in
test modules of their own. The extension finds them as you write them, and runs them in VS Code's Testing view, in each
build mode.

### Tests

From the Testing view or the gutter, you can run a single test, a module's tests, or all of them. The results show what
passed, failed or was skipped, and any test runs under the debugger too.

### The Testing view

The Testing view lists the test modules and their tests, from the language server's analysis. From there, you can run,
debug and filter them.

### Test icons

The gutter has a run icon beside every test module and every method with the Test annotation. A click runs it, and a
right click debugs it.

### Run a test

Run Test at Cursor runs the test that the cursor is in, embedded. The gutter and the Testing view both show that it
passed.

### Run a module's tests

With the cursor on the module's own line, the same command runs every test of the DeckTest module, and each result shows
in the Testing view.

### Run all the tests

Run All Tests runs the tests of every test module in the project, and the Test Results view shows what each of them
printed.

### A failing test

A failing test is red, and its assertion's message shows at the line that failed, so you can see why without leaving the
editor.

### A new test

A test written while the project is open is in the Testing view as soon as the language server has read it, with its
gutter icon, and it runs. There is no restart.

### Debug a test

Debug Test at Cursor runs the test under the debugger. It stops at the test's breakpoint, here just before the deck is
shuffled, and everything the debugger does works there.

### Tests with Gradle

In Gradle mode, the same Testing view runs the XTC plugin's testXtc task, and xunit's results come back to it, so the
results read the same either way.

## Settings

The last section is the extension's settings. A project needs none of them: without them, the compiler finds the
project's modules itself, and uses the XDK it comes with. They are there when you want to change how the project builds,
how code is formatted, or how the server runs.

### The extension's settings

The Open Language Service Settings command lists the extension's settings: the build mode, formatting, hints and lenses,
and the runtime of the language server.

## The end

That is the end of the tour. In one small project, you have seen the extension read, write, check and refactor Ecstasy
code, then build it, run it, debug it and test it, all through one language server, the same one that IntelliJ uses.

### That's the tour

To try it yourself, install the XTC extension from the Marketplace, and open an Ecstasy project, or make a new one with
Create New Project. Select Build Mode chooses embedded or Gradle, and Open Language Service Settings has the rest.
