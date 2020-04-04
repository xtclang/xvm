module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run(  )
        {
        if (String word := foo(False))
            {
            console.println($"True, {word}");
            }
        else
            {
            console.println($"False");
            }
        assert (Int i, String s) := bar();
        console.println($"{i} {s}");
        }

    conditional String foo(Boolean flag)
        {
        return flag, flag ? "hello" : "goodbye";
        }

    conditional (Int, String) bar()
        {
        return True, 1, "hello";
        }
    }