static class StaticChild
    {
    construct()
        {
        @Inject Console console;
        console.println($"in {this:class}");
        }
    }