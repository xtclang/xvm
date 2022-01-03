module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        @Unchecked Int i = 3;
        i = i.toUnchecked();

        console.println($"i={i}, type={&i.actualType}, class={&i.actualClass}");
        }
    }
