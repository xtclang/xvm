/**
 * Leave an idle child owning a file channel after all application handles to the child are gone.
 * The embedding host verifies that releasing the parent closes the channel and releases retention.
 */
module NestedResources {
    import ecstasy.mgmt.Container;
    import ecstasy.mgmt.ModuleRepository;
    import ecstasy.mgmt.PassThroughResourceProvider;

    void run() {
        openNested();
        @Inject Console console;
        console.print("ready");
    }

    void openNested() {
        @Inject("repository") ModuleRepository repository;
        val template = repository.getResolvedModule("NestedResources");
        val child = new Container(template, Lightweight, repository, new PassThroughResourceProvider());
        child.invoke("open", ());
    }

    void open() {
        @Inject Directory rootDir;
        Holder.channel = rootDir.fileFor("nested.dat").ensure().open(write=[]);
    }

    static service Holder {
        ecstasy.fs.FileChannel? channel;
    }
}
