module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Exception e = new IllegalState("error");

        // console.println($"text={e.text?}");          // this used to compile
        console.println($"text={e.text? : "none"}"); // this used to assert
        }
    }