/**
 * Test of the BasicResourceProvider.
 */
module TestContainer
    {
    import ecstasy.mgmt.*;
    import ecstasy.reflect.ModuleTemplate;

    @Inject Console console;

    void run(Int depth=0)
        {
        console.print($"Running at depth {depth}");

        @Inject("repository") ModuleRepository repository;

        // run itself
        if (depth < 3)
            {
            ModuleTemplate template = repository.getResolvedModule("TestContainer");
            Container container =
                new Container(template, Lightweight, repository, new SimpleResourceProvider());

            container.invoke("run", Tuple:(depth+1));
            }
        else
            {
            // run TestSimple
            ModuleTemplate   template = repository.getResolvedModule("TestSimple");
            ResourceProvider injector = new BasicResourceProvider();

            Container container = new Container(template, Lightweight, repository, injector);
            container.invoke("run", Tuple:());
            }
        }

    service SimpleResourceProvider
            extends BasicResourceProvider
        {
        @Override
        Supplier getResource(Type type, String name)
            {
            import Container.Linker;

            switch (type, name)
                {
                case (Linker, "linker"):
                    @Inject Linker linker;
                    return linker;

                case (ModuleRepository, "repository"):
                    @Inject ModuleRepository repository;
                    return repository;
                }
            return super(type, name);
            }
        }
    }