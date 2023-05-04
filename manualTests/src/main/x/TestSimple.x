module TestSimple
    {
    @Inject Console console;

    import ecstasy.mgmt.ResourceProvider;

    package auth import webauth.xtclang.org inject(auth.Configuration startingCfg) using Injector;

    void run()
        {
        }

    static service Injector implements ResourceProvider
        {
        @Override
        ResourceProvider.Supplier getResource(Type type, String name)
            {
            import ecstasy.annotations.InjectedRef;

            switch (type, name)
                {
                case (auth.Configuration, _):
                    return (InjectedRef.Options opts) ->
                        {
                        return new auth.Configuration(["admin"="addaya"], configured=False);
                        };

                default:
                    return (InjectedRef.Options address) ->
                        throw new Exception($|Invalid resource: type="{type}", name="{name}"
                                           );
                }
            }
        }
    }