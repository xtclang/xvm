module TestReflection.xqiz.it
    {
    @Inject Ecstasy.io.Console console;

    void run()
        {
        testInstanceOf();
        testMaskReveal();
        }

    void testInstanceOf()
        {
        import Ecstasy.collections.HashMap;

        console.println("\n** testInstanceOf");

        Object o = new HashMap<Int, String>();
        assert &o.instanceOf(Map<Int, String>);
        assert !&o.instanceOf(Map<String, String>);
        }

    void testMaskReveal()
        {
        import Ecstasy.fs.Directory;

        console.println("\n** testMaskReveal");

        @Inject Directory tmpDir;

        // the implementation of tmpDir is a "const OSDirectory", which is definitely Stringable,
        // but they must not be able to use that fact when in a different container;
        // since the tests for now run "in-container", the revealAs() would work

        console.println($"tmpDir=" + tmpDir.toString());

        assert !tmpDir.is(Stringable);

        try
            {
            Stringable str = tmpDir.as(Stringable);
            assert;
            }
        catch (Exception e)
            {
            console.println($"e={e.text}");
            }

        assert Stringable str := &tmpDir.revealAs<Stringable>();
        console.println($"str.length={str.estimateStringLength()}");

        const Point(Int x, Int y);

        Point p = new Point(1, 1);
        str = &p.maskAs<Stringable>();

        try
            {
            p = str.as(Point);
            assert;
            }
        catch (Exception e)
            {
            console.println($"e={e.text}");
            }

        assert p := &str.revealAs<Point>();
        console.println($"p={p}");
        }
    }