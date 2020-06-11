module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println(Id.Allow);
        }

    enum Category {Normal, ContextSensitive, Special, Artificial}

    /**
     * Ecstasy source code is composed of these lexical elements.
     */
    enum Id(String? text, Category category=Normal)
        {
        Any   ("_"     ),
        Allow ("allow" , ContextSensitive)
        }
    }