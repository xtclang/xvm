module TestSimple
    {
    @Inject Console console;

    void run(  )
        {
        import ecstasy.reflect.Annotation;

        Type t = @Unchecked Int;
        assert Annotation anno := t.annotated();
        assert Type t0 := t.modifying();

        console.println(anno);
        console.println(t);
        console.println(t0);

        Type t2 = t0.annotate([anno]);
        console.println(t2);
        }
    }