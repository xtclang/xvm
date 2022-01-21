# Directory: ./manualTests/ #

This directory contains various tests. Some are current; others (unfortunately) not.

In general, before creating a pull request (or for committers, before pushing), you
should do the following actions in a shell window, from the `./xvm/` directory:
                           
First, run the "suite" of manual tests:

    gradle runAll > new.out

Next, compare the results with the previous results from the suite:

    diff old.out new.out

There will be some minor diffs, such as timestamps and elapsed time measures; those
are fine. You're looking for any significant diffs. (Welcome to unit testing, 1960s
style.) Finally, if you're happy with the results, then save them off:

    mv new.out old.out

Once xUnit (WIP) is done, and as we boot-strap more and more of the prototype code
into the Ecstasy codebase, this directory will probably be retired (or archived).
Unit and  functional tests will be (for the most part) embedded in the various
libraries that the tests are designed for (see the xUnit `@Test` annotation).
