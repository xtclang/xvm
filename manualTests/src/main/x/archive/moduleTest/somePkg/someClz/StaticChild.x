static class StaticChild
    {
    construct()
        {
        @Inject Console console;
        console.print($"in {this:class}");
        }
    }