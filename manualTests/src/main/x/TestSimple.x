module TestSimple
    {
    @Inject Console console;

    Class  SimpleClass    = TestSimple;    // "Simple" is a Class     // used to fail to compile
    Type   SimpleType     = TestSimple;    // "Simple" is a Type
    Module SimpleInstance = TestSimple;    // "Simple" is a singleton

    void run()
        {
        report(SimpleClass);
        report(SimpleType);
        report(SimpleInstance);
        }

    void report(Object o)
        {
        console.println($"type ={&o.actualType}");
        console.println($"value={o}");
        }
    }