# Narration of the IntelliJ tour

What a voice says over the IntelliJ plugin's tour, step by step, as text: the tour's steps are those of
`lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/TourScript.kt`, and the video is the one that
`compose.py` makes from a run of it.

Each `##` heading is a section of the tour, named exactly as the tour names it, in the tour's order; the paragraph
right under it is spoken over the section's title card. Each `###` heading under a section is one of its steps, named
exactly as the step's title, and the paragraph under it is spoken over that step. A card that the tour shows as a step
of its own, such as the one that opens Build and run, has its `###` like any other step, after its section's paragraph.
Steps are matched by their section and title, never by number, as the composer renumbers the steps it leaves out. A
line in `<!-- -->` is a note, not spoken. When a step's title changes in the tour, its heading here changes with it.

## Start here

This is a tour of the XTC plugin, which brings the Ecstasy programming language to IntelliJ IDEA. The plugin runs
Ecstasy's own compiler beside the IDE, as a language server that reads your code as you type. The whole tour works on
one small project. First, an overview of what it covers, and then the project itself.

### Ecstasy in IntelliJ

The tour comes in two halves. The first reads and edits code: highlighting, documentation, completion, navigation,
errors and their fixes, refactoring and formatting. The second builds and runs the program in three ways, then debugs
and tests it.

### The project

The project is a card game, Crazy Eights. A player plays a card of the same suit or rank as the top of the pile, and an
eight matches anything. Its layout, with a Gradle build and xunit tests, is what New Project makes.

### Open the project

The project opens with its sources in the Project view, and the game's Eights class in the editor. The plugin starts its
language server, which comes with its own XDK, the Ecstasy development kit.

## Reading code

Reading code comes first. Before you change anything, you need to see what the code is: what each name means, what a
method expects, and how a file is laid out. What each step shows comes from the language server, which sees the code as
the compiler does, rather than matching its text.

### Semantic highlighting

The compiler colors each name by what it is: a field, a parameter, a local, a type or a static member. A grammar alone
cannot tell these apart, so the colors show at a glance what each name refers to.

### Inlay hints and references

Where a call's arguments don't say what they are, hints show the names of its parameters, here numCards. Above each
declaration, a line counts its references across the project, so you can see what is used, and how much.

### Quick documentation

Control Q, or hovering over a name, shows its signature and its doc comment, as the compiler sees them. Here it is the
game's initial hand size constant, with the comment that says how many cards are dealt.

### The XDK's documentation

Documentation works the same for the XDK's own types. HashMap's comes from the sources of the ecstasy module, Ecstasy's
core library, so you can read about a standard type without leaving the editor.

### Parameter info

Inside a call, Control P shows the parameters of the method it calls, with the one at the cursor in bold. Here the
cursor is on the second argument of the deal method, so numCards is the one in bold.

### Highlight usages

With the cursor on a name, every use of it in the file is highlighted. Here it is the local variable card, and its uses
come from the compiler's references to it, rather than from a search of the text.

### Extend selection

Control W grows the selection by the syntax around it: first a name, then the expression, then the statement, then the
block. Control Shift W shrinks it again, step by step.

### Folding

Methods and doc comments fold, where the compiler's folding ranges mark them. Control Shift minus folds them all,
leaving a short outline of the class to read at a glance.

### File structure

Control F12 lists the file's classes, properties and methods, from the compiler's symbols. Type to filter the list, and
press Enter to go to the one you want.

### The Structure window

The Structure window keeps the same outline open beside the editor. It follows the cursor as it moves, so it always
shows where you are in the file.

## Navigation

Navigation is about finding your way through a project you don't know yet: from a call to the method it calls, from a
name to its type, from a method to everything that uses it. Each answer comes from the compiler, and it reaches into the
XDK's own sources too.

### Go to declaration

Control B, or Control click, on a call goes to the method it calls. The deck's shuffle method is declared in
CardCollection, which Deck inherits from, and that is where the editor goes.

### Go to type declaration

Control Shift B goes to the type of what is under the cursor. The variable deck is a Deck, so the editor opens the Deck
class, in its own file.

### Go to implementation

Control Alt B on an interface's method lists its implementations. The choose method of the PlayStrategy interface has
two, in the FirstMatch and SaveEights strategies.

### Find usages

Alt F7 finds every use of a declaration, across all of the project's modules, the test modules included. Here, the
player's cardMatches method is used by the player, by both strategies, and by the player's tests.

### Call hierarchy

Control Alt H shows who calls a method, and who calls those callers in turn. The cardMatches method is called by the
player and by both strategies, each a branch you can open.

### Type hierarchy

Control H shows a class's supertypes and subtypes, in the Hierarchy window. Here, CardCollection has two subtypes, Deck
and Hand, which both extend it.

### Go to symbol

Control Alt Shift N finds a class, method or property by its name, in the project and in the XDK. Here, typing reshuffle
finds the game's method of that name.

### The XDK's sources

Go to declaration also opens the XDK's own sources, read-only. HashMap is declared in the ecstasy module's collections
package, and its source is there to read, just as the game's own code is.

### A named argument's parameter

From a named argument, Go to declaration opens the parameter that it names. Here a call of the array's freeze method
names its argument inPlace, and the editor opens that parameter in the XDK's source, read-only.

## Writing code

Writing code is next. Completion offers what the compiler knows is there, templates write the common declarations for
you, and the formatter lays the code out as your code style says. Each of them helps while you are still learning the
language.

### Member completion

After a dot, completion lists the members of the expression's type, as the compiler resolves it, each with its type.
Here, after hand and a dot, the list has the hand's methods, such as addCard and popCard.

### Name completion

As a name is typed, completion offers the types, variables and keywords in scope. Here, typing Score offers the game's
own Scoreboard.

### Live templates

Control J lists Ecstasy's live templates, which write common declarations for you: a module, a class, an interface, a
service, a mixin, an enum, a const, a package or a method.

### Expand a template

Typing an abbreviation, here meth, and pressing Tab expands its template into a method. Tab then moves through its
placeholders, the return type, the name and the parameters, for you to fill in.

### Enter and braces

Typing an opening brace and Enter wraps the statement that follows in a new block, indented as the code style says, and
closes the block. Here, an if statement now guards the return.

### Comment out lines

Control slash turns the selected lines into line comments, and pressing it again turns them back into code. Control
Shift slash wraps a selection in a block comment instead.

### Code that needs formatting

In this method, swapCards, one line is indented too far. How Ecstasy code is laid out is up to the code style's
settings, and the next step applies them.

### Reformat code

Control Alt L reformats the file, or just the selection, through the language server's formatter, with the IDE's code
style. The line in swapCards is now indented by its block, like the code around it.

### Code style

The Ecstasy page under Settings, Editor, Code Style holds the indents, the continuation indents and the other settings
that the formatter follows. Change them here, and the next reformat uses them.

## Diagnostics

Diagnostics are the compiler's errors and warnings. The plugin checks your code as you type, so a mistake shows the
moment you make it, and some come with a fix that writes the missing code for you. This section makes a few mistakes on
purpose, and fixes them.

### Errors as you type

A misspelled method is an error as soon as it is typed. The compiler checks the text in the editor, not the saved file,
so you don't need to save or build to find out.

### The error's message

Control F1 shows the compiler's message for the problem at the cursor, with its error code. The marks in the scrollbar
show every problem in the file, wherever it is.

### Warnings

Warnings are marked too. The variable card is always a Card, so this new assertion can never fail, and the compiler
warns about it.

### The Problems window

The Problems window lists the errors and warnings of the open file, and of the whole project, each a click away from its
code. Here it shows the warning about the assertion.

### Quick fixes

Alt Enter on an error offers its fixes. The game calls an announce method that doesn't exist yet, so the language server
offers to create it.

### Create a method

The fix writes the new method, with the parameter types that the call implies, here a Player, and leaves its body for
you to fill in. The error is gone.

### Add an import

A type that isn't imported has a fix that adds its import. Here, a test module uses the game's Suit type without
importing it, and the fix adds the import.

### Generate a doc comment

Alt Enter on a declaration also offers to write its doc comment. For the nextPlayer method, it writes the outline of the
comment, with its parameters and its return value, ready for you to fill in.

### Optimize imports

Control Alt O removes the imports that nothing uses, and sorts the rest. Until then, an unused import is grayed out, as
this one in the player's tests is.

## Refactoring

Refactoring changes the shape of code without changing what it does. Before any change, the language server recompiles
everything it touches, and checks that each name still refers to what it did. When it can't prove that, it refuses,
rather than leave your code broken.

### Rename

Shift F6 renames a method. Here the player's cardMatches becomes canFollow, and the dialog checks the new name before
anything changes.

### Renamed everywhere

The rename changes the declaration and every call of it: in the player, in both strategies and in the player's tests,
all as one change of the project.

### Undo asks first

Control Z takes the whole rename back in one step. Because the change spans several files, IntelliJ asks first, as it
does for any change of the project.

### Undone everywhere

OK takes the rename back in every file at once. Every call is cardMatches again, and the project compiles just as it did
before.

### Rename a class

Renaming a class renames its file too, as Ecstasy keeps one type per file, and changes every use of it. Here Scoreboard
becomes Tally, and the game now uses a Tally.

### A rename it refuses

A rename that would change what the code means is refused. Here the local for the drawn card would become name, and a
message would then print the card in place of the player's name, so the server says no.

### Extract and inline

Alt Enter on a whole returned expression, or on a typed local's initializer, offers to extract it to a local variable or
to a private method. The same key offers to inline code, where that applies.

### Extracted to a local

The expression is now a local variable, a Boolean, the type that the method returns, and the return statement reads it.
The file compiles as before.

### Extract a method

Statements extract to a private method too. The three lines that print the hand sizes become one call of a new method,
which holds them.

### Safe delete

A private member that nothing uses, the hand's heading method, can be deleted safely. The server checks for uses first,
and offers the deletion only when there are none.

## Project and files

This section is about starting a project of your own. IntelliJ's New Project wizard has an Ecstasy entry, which makes
the same kind of project as the tour's card game, with a Gradle build that uses the XTC Gradle plugin. It doesn't need
an XDK installed.

### New Project

File, New, Project, then Ecstasy, makes a new project as the xtc init command does. Choose its type, such as an
application or a library, and whether it has several modules. The project comes with the XTC Gradle plugin.

## Build and run

Now the tour turns from editing to running. This section builds the card game, runs it, and plays it from the IDE's own
windows. The same run configuration and the same buttons work whichever way the project builds, and the section ends by
trying each of those ways.

### Build and run

Ecstasy projects build and run three ways. Embedded uses the language server's own compiler, with no Gradle, for the
fastest round trip. Gradle runs the XTC Gradle plugin's tasks, with the build's own settings. And the command line runs
xtc, as a terminal would.

### The build mode

The Build and Run page, under Settings, Languages and Frameworks, Ecstasy, chooses how the project builds. The embedded
compiler is the default, even when there is a Gradle build beside it.

### Build Project

Control F9 builds every module with the language server's compiler, without Gradle, and reports to the Build window.
There is nothing to sync or install first.

### A failed build

When a build fails, its errors are listed with their files and lines, each a click away from its code. Here, a mistake
in the game's code is reported in the Build window.

### The Run icon

A module with a run method has a Run icon in the gutter beside its declaration. A click runs it, and makes a run
configuration for the module, to use again later.

### Run

The game runs in the embedded execution worker, beside the language server, and the Run window is its console. Here it
starts a game of Crazy Eights.

### Console input

The program reads what you type in the Run window. Each Enter plays a turn, and the Run window shows what each player
did.

### The run configuration

Run, Edit Configurations shows the run configuration. It names the module, its arguments, here two players' names, and
what it runs with: the project's build mode, or embedded, Gradle or xtc, for this configuration alone.

### Choose the build mode

Control Alt X, then B, or Tools, Select Ecstasy Build Mode, chooses how Build, Rebuild and Run work: embedded, Gradle,
or automatic. Here the project switches to Gradle.

### Build with Gradle

In Gradle mode, Control F9 builds with the project's own Gradle build, which runs the XTC plugin's compileXtc task among
its tasks.

### Run with Gradle

In Gradle mode, the same run configuration runs the XTC Gradle plugin's runXtc task, with the build's own settings and
its XDK. The game plays the same way.

### The Gradle window

The Gradle window lists the XTC plugin's tasks, among them compileXtc, runXtc and testXtc, to run as you would run any
Gradle task.

### Run with xtc

Set to the command line, the configuration runs the XDK's xtc run command, as a terminal would, and its output shows in
the same Run window.

## Debugging

Now the debugger. The plugin connects IntelliJ's own debugger to a debug session in the language server, which builds
and runs the program just as Run does. This section stops the card game in several ways, steps through it, and looks at
its state while it is paused.

### Debugging

The debugger stops at line, conditional and exception breakpoints, and can attach to a program run outside the IDE. It
steps over, into and out, even into the XDK's code. And it shows frames, fibers, variables and watches, and evaluates
expressions.

### A conditional breakpoint

A breakpoint can have a condition. This one, in the constructor of Deck, stops only when the deck adds the Queen of
Spades, and runs past every other card.

### Debug

Shift F9, or the Debug button in the toolbar, debugs the run configuration. The program stops where the condition first
holds, and the Variables view shows the card, the Queen of Spades.

### Frames and variables

When it resumes, the program stops at a line breakpoint while it deals the cards. The frames show deal, called from
initializeGame, in the constructor of Eights, and the variables show each frame's values.

### Step into

F7 steps into the call on the current line. Here that is popCard, the method that takes the top card off the deck.

### Step over

F8 runs the current line and stops at the next one. The Variables view shows what the line changed, so you can follow a
value as it changes.

### Step out

Shift F8 runs to the end of popCard and stops back in deal, the method that called it, with the card that popCard
returned.

### Run to cursor

Alt F9 runs to the line with the cursor, here in dealAll, as a breakpoint there would, without your having to set one.

### Smart step into

On a line with several calls, Shift F7 asks which one to step into. Here the line calls both deal and size, and you pick
the one you want.

### Evaluate expression

Alt F8 evaluates an expression in the paused frame. The selection, a call of size, gives the number of cards left in the
deck, forty-two.

### Watches

An expression added to the watches is evaluated again at every stop, beside the frame's variables. Here it is the deck's
label, which stays in view as you step.

### The program's console

When it resumes, the game waits for Enter. The Console of the Debug window shows the program's output, and a line typed
there is the program's input.

### An exception

An exception that nothing catches stops the program where it is thrown. Here, popCard has a bug that reads one card past
the end, and the debugger stops on that line and shows the exception's message.

### Debug with Gradle

In Gradle mode, Debug runs the runXtc task under the debugger. The program connects to the debug session, and stops at
the same breakpoints, in the same Debug window.

### A condition that cannot be evaluated

A breakpoint whose condition cannot be evaluated, here because it names something that doesn't exist, stops anyway, to
be safe, and the console says why it stopped.

### Breakpoints while you edit

A breakpoint follows its line as you type a line above it. The running program was built before the edit, and it still
stops at the code where the breakpoint was set.

## Tests

Ecstasy's test framework is xunit. A test is a method with the Test annotation, and a project usually keeps its tests in
test modules of their own. The plugin finds them as you write them, and runs them in IntelliJ's own test runner, in each
build mode.

### Tests

From the gutter or a run configuration, you can run a module's tests, a single test or several, and rerun the failed
ones. The results show what passed, failed or was skipped, and any test runs under the debugger too.

### Test icons

The gutter has a test icon beside every test module and every method with the Test annotation. The icons come from the
language server's view of the tests.

### Run or debug a test

A click on a test icon offers to run or debug what it marks. Here it is the icon of the DeckTest module, which runs
every test in it.

### Test results

The tests run embedded, and IntelliJ's test runner shows each one's result, how long it took, and what it printed. All
eight of the deck's tests pass.

### A failing test

A failing test is red, with its assertion's message and the line that failed, a click away. Here one of the deck's tests
was made to fail on purpose.

### Debug a test

Debug, on a test's icon, runs the test under the debugger. It stops at the test's breakpoint, here just before the deck
is shuffled, and everything the debugger does works there.

### Tests with Gradle

In Gradle mode, the same icons run the XTC plugin's testXtc task, and xunit's results come back to the same test runner,
so the results read the same either way.

## Settings and the server

The last section looks at the plugin's settings, and at the language server itself. Every page of the plugin is under
Settings, Languages and Frameworks, Ecstasy. These pages, and the server's own window, are also the places to look when
something doesn't work as you expect.

### The language server

The Language Service page, under Settings, Languages and Frameworks, Ecstasy, can turn each of the language service's
features off, per project, such as the inlay hints. It also chooses the language adapter.

### The compiler

The Compiler page shows the modules the compiler found to build, the test modules among them, and how it found them.
Here they come from importing the project's Gradle build.

### The server's runtime

The Server Runtime and Logs page sets how the server runs: its Java options, its memory, and how long its logs are kept,
for when something goes wrong.

### Language servers

LSP4IJ's Language Servers window shows the state of the Ecstasy language server, and traces each message that the IDE
and the server send each other, here a request for documentation.

## With LSP4IJ 0.30.1

The plugin talks to its servers through LSP4IJ, Red Hat's client for language servers and debug adapters. Version 0.30.1
of LSP4IJ brings more of the Ecstasy server's features to the IDE, in the debugger and in the editor. The tour doesn't
show them step by step, so this card lists them.

### With LSP4IJ 0.30.1

For breakpoints, there are function and data breakpoints, hit counts, logpoints and exception filters. The debugger adds
pause, restarting a frame, inline values, hexadecimal display and paged values. And linked editing changes a local's
occurrences together as you type one.

## The end

That is the end of the tour. In one small project, you have seen the plugin read, write, check and refactor Ecstasy
code, then build it, run it, debug it and test it, all through one language server, the same one that VS Code uses.

### That's the tour

To try it yourself, install the XTC plugin from the Marketplace, and open an Ecstasy project, or make a new one with New
Project. The Ecstasy pages in Settings hold the language service, its compiler and server, and the build mode.
