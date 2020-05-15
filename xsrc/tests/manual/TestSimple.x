module TestSimple
    {
    @Inject Console console;

    void run(  )
        {
        import ecstasy.reflect.Annotation;

        // TODO GG Class c = @Unchecked Int;

        Type t = @Unchecked Int:protected;
        assert Annotation anno := t.annotated();
        assert Type t0 := t.modifying();

        console.println(anno);
        console.println(t);
        console.println(t0);

        Type t2 = t0.annotate(anno);
        console.println(t2);

        assert t == t2;

        assert Class c := t.fromClass();
        (Class c0, Annotation[] annos) = c.deannotate();

        console.println(annos);
        console.println(c);
        console.println(c0);

        Class c2 = c0.annotate(annos);
        console.println(c2);

        assert c == c2;
        }
    }