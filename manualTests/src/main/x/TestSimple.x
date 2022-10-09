module TestSimple
    {
    import ecstasy.mgmt.*;
    import ecstasy.reflect.ModuleTemplate;

    @Inject Console console;

    void run(Int depth=0)
        {
        console.println($"Running at depth {depth}");

        if (depth < 3)
            {
            @Inject("repository") ModuleRepository repository;

            ModuleTemplate template = repository.getResolvedModule("TestSimple");
            Container container =
                new Container(template, Lightweight, repository, new SimpleResourceProvider());

            container.invoke("run", Tuple:(depth+1));
            }
        }

    service SimpleResourceProvider
            extends BasicResourceProvider
        {
        @Override
        Supplier getResource(Type type, String name)
            {
            switch (type, name)
                {
                case (ModuleRepository, "repository"):
                    @Inject ModuleRepository repository;
                    return repository;
                }
            return super(type, name);
            }
        }
    }