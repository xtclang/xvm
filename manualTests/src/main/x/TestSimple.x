module TestSimple {
    @Inject Console console;

    void run() {
        import ecstasy.mgmt.*;
        import ecstasy.mgmt.Container.InjectionKey;
        import ecstasy.reflect.*;

        @Inject ModuleRepository repository;
        @Inject Container.Linker linker;

        ModuleTemplate template = repository.getResolvedModule("TestSimple");
        InjectionKey[] injects  = linker.collectInjections(template);

        console.print(injects.toString(pre="", post="", sep="\n"));
    }
}