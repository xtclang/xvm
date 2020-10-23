module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Final @Lazy @Final Int i;     // duplicate Final
        @Lazy @Inject Console console; // Inject is not compatible with other Ref annotations
        }
    }
