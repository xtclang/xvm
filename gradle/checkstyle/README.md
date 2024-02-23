# Code Standard

This is the configuration that maintains the code style and quality of the project.

The Checkstyle plugin was selected because:

1) It comes with Gradle and does not require any third party dependencies
2) The code standard can be fully enforced by a checkstyle.xml file in this directory.

Every XDK subproject, currently only Java based ones, but we will likely enable checkstyle for other
languages in the build, including XDK, are given checkstyle tasks for each of their source sets,
typically "checkstyleMain" and "checkstyleTest". These are attached to the check phase of the
Maven lifecycle, and if any source code or config changes have been made since the last build,
Checkstyle will be re-run on the new source.

The XDK code standard conforms to the official Sun / Oracle Java code standard, but since this
has not been updated for several years, and leave some ambiguity for certain constructs,
we have defaulted to the Google code standard for undefined style rules. This should provide the
user with a code standard that looks familiar to most Java developers. We might revisit the code
standard again, or increase the granularity of errors an warnings later.

NOTE: We have tried to make the standard auto enforceable, and believe that for 99% of all source
code, this is the case, given the helper tools described below. In the event that there is a
conflict between automatic in-IDE reformatting, and the successful execution of the checkstyle
tasks, the checkstyle tasks *always take precedence*. The file "checkstyle.xml" is the only
source of truth for the code standard.

## Automatic code formatting during build

We have evaluated the OpenRewrite project and its checkstyle plugin, to verify if a build can
autocorrect any code to conform to the code standard, but currently it seems to be missing some
transforms and logic, for example for brace placement checks. We may introduce formatting as a
separate tasks later, but currently have to prioritize other development efforts. We encourage
any user who wants to tinker with the XTC development environment to contribute.

Currently, automatically enforcing the code standard can be done through .editorconfigs, or
in IntelliJ, by importing the code style and inspections config files in this directory.
With those in place, conforming to the code standard should be as easy as pressing "Ctrl-Alt-L"
(clean up code).

## Work in progress

Checkstyle is currently turned off for the javatools and javatools_utils projects, as they need
to be converted to the new format still.

### XTC Code standard

The code standards only currently supports enforcement and autocorrection for Java 
(and standard format Kotlin, which is a single push button configuration). In the future, 
we will provide an XTC language coding standard as well, but this is work in progress.

## Helper tools

To help the user maintain the code standard, auto-format source code to conform to it, and
get IDE integration so that any new checkstyle violations will immediately produce a warning
during development, the config directory also contains the following files.

(Note that we currently have only implemented solutions for IntelliJ, but will happily accept
code standard enforcement configs and tools for other dev environments)

### xtc-idea-style.xml

This is the code style enforced by the checkstyle.xml file, in a format that IntelliJ can
understand. This makes it possible to auto-reformat code, without having to figure out exactly
why it is non-conforming. Combined with the IDEA Checkstyle plugin and the inspections config
in this directory, it should be the case that zero warnings or errors in the IDE for a file
will only be shown if and only if the file conforms exactly to the code standard.

### xtc-idea-inspections.xml

This is a configuration that adds further logic to IntelliJ, producing warnings, errors and
autocorrect suggestions for code that does not conform to the code standard. It is a good idea
to enable these as well.

### The IDEA Checkstyle Plugin

There is an IntelliJ plugin that can consume the checkstyle.xml and that makes it possible to
continuously apply and update code style based on checkstyle feedback. It can be downloaded
from [here](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea), but is also searchable
and installable from the plugin marketplace in IntelliJ preferences.

### _editorconfig

To use this in your project, copy it to the root of the composite build, and rename it ".editorconfig".
It is an even better idea to symlink it. While most IDEs support some form of ".editorconfig" format,
several of our keys for Java and multiple languages are IntelliJ specific.

At the moment, support for reading ".editorconfig" files and exporting them from existing configuration
in IntelliJ is not 100% stable. Hence, we have supplied IDE specific ways to do incremental conformity
enforcement.

If you are using IntelliJ and want to use the .editorconfig, you have to enable it in the settings.
There will also be an option the IDE displays when you open the .editorconfig file, to import it
into the code style settings. Note, however, that some properties do not seem to be picked up by
IntelliJ, and hence, we recommend that you import the IntelliJ specific code style and inspection
files instead.

## Some notes on the code standard

The code standard was chosen to be familiar to the majority of users, and from established standards.
It needs to be readable and easily enforceable by tools. Basically, it should be possible to work in
whatever code standard you like, and re-enforce the project code standard with a simple
Save As-action in your IDE.

*Note: We have deliberately chosen to avoid any XTC project specific code standard rules, to ensure
that nothing "sticks out", compared to near-universal Java code style.*

The code standard aims to make business logic short, consistent and readable. It also aims to
encourage immutable state by default, over mutable state.

Here are some highlights from the code standard (none of these are XTC specific, and are directly
applied through the existing code standards, but they may be worth mentioning to motivate their
existence). 

* One true brace style (1TBS) is enforced, as per the Oracle and the Google code standards (including mandated spaces around binary operators, no space after unary operators, no space after opening and closing parentheses, and space after keywords.)
* Curly braces are enforced, also around single statements (for consistency and security
  considerations.)
* The Oracle code standard has rules on input groups for imports slightly more detailed than the Google style. At the moment we follow the Oracle style. We might consider relaxing this to the Google style later, which only mandates two group, separated by a blank line: static imports followed by normal imports, both groups in alphabetical order.
* All files need to end with a newline character.
* Declaration order of fields is static before instance, final before non-final, and public to private.
* Import order is split into groups, in alphabetical order, but with "java", followed by "javax" first. Static imports form their own groups, and are placed above the regular imports.
* Fields, parameters and local variables are required to be final (promotes functional style and introducing state is an opt-in, rather than an opt-out)
* Fields and variables are camelCase, starting with a lowercase letter. 
* Classes and Types are camelCase, starting with an uppercase latter.
* Fields and Types can contain letters and numbers only, no underscores or other special characters.
* The only exception to the naming rules is for static final variables, which are all uppercase, with underscores as word separators.
* Instance fields are declared first in a class. Methods follow.
* No Hungarian notation or other prefixing is to be used for variables.
* Wildcard imports are illegal (to avoid namespace pollution and act as an antidote to dependency blobs)
* Standard JavaDoc comments are to be used, but this is not currently enforced.
* ...

### A note on automatic "complexity" checks

There are no cyclomatic complexity checks in the code standard, because it is not an exact science that can be enforced. Having a rule like "too much logic in this statement" tends to breed complexity instead of simplicity. 

What is "too complex" is actually in the eye of the beholder, and for some code, slightly higher complexity than some arbitrary threshold is both more readable than breaking the code up, and also the best way to implement it. 

Determining what is "too complex" is best left to the code review. However, deeply nested ifs and loops *are* automatically rejected. Early returns from control flow are encouraged.

## Future work

We are working on a plugin for complete XTC language support in IntelliJ. This plugin will likely
automatically incorporate and enforce the code standard, but for the moment, we recommend importing
the xml files, to form your XTC code and inspections profile.
