@TerminalApp("Simple command tool test")
module TestSimple {
    package cli import cli.xtclang.org;

    import cli.*;

    // ----- stateless API -------------------------------------------------------------------------

    @Command("time", "Show current time")
    Time showTime() {
        @Inject Clock clock;
        return clock.now;
    }

    @Command("dirs", "Show home current and temp directories")
    (Directory, Directory, Directory) showDirs() {
        @Inject Directory curDir;
        @Inject Directory homeDir;
        @Inject Directory tmpDir;
        return curDir, homeDir, tmpDir;
    }

    // ----- stateful API --------------------------------------------------------------------------

    service Stateful {
        Int count;

        @Command("inc", "Increment the count")
        Int addCount(@Desc("increment value") Int increment = 1) {
            count += increment;
            return count;
        }
    }
}