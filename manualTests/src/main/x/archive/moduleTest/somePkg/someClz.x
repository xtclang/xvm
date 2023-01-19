const someClz<SomeType>
    {
    construct()
        {
        @Inject Console console;
        console.print($"in {this:class}");
        }
    finally
        {
        new VirtualChild();
        new StaticChild();
        }
    }