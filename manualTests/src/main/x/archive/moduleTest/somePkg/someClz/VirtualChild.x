class VirtualChild
    {
    construct()
        {
        @Inject Console console;
        console.print($"in {this:class} with SomeType={SomeType}");
        }
    }