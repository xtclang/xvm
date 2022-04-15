class VirtualChild
    {
    construct()
        {
        @Inject Console console;
        console.println($"in {this:class} with SomeType={SomeType}");
        }
    }