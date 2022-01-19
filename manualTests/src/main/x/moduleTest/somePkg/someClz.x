const someClz<SomeType>
    {
    construct()
        {
        @Inject Console console;
        console.println($"in {this:class}");
        }
    finally
        {
        new VirtualChild();
        new StaticChild();
        }
    }