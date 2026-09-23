module Watching {
    void run(String[] args) {
        @Inject Directory rootDir;
        @Inject Console console;

        // Deliberately leave the registration open so that the host exercises session shutdown.
        rootDir.watch(new ecstasy.fs.FileWatcher() {}.makeImmutable());
        if (args[0] == "cancel") {
            @Inject Timer timer;
            @Future Tuple pending;
            timer.schedule(Duration:1H, () -> { pending = (); });
            console.print("watching");
            return pending;
        }

        console.print("watching");
        if (args[0] == "failure") {
            throw new IllegalState("expected watcher failure");
        }
    }
}
