module annoTest
    {
    @Inject Console console;

    import ecstasy.reflect.*;

    void run()
        {
        report(new @Session1 @Session2 SessionImpl(1));
        report(new @Session2 @Session1 SessionImpl(2));

        testMixin(Session1, Session2, 3);
        testMixin(Session2, Session1, 4);
        }

    void testMixin(Class clzMixin1, Class clzMixin2, Int id)
        {
        assert clzMixin1.mixesInto(Session);
        assert clzMixin2.mixesInto(Session);

        Class clz = SessionImpl;

        clz = clz.annotate([new Annotation(clzMixin1), new Annotation(clzMixin2)]);

        assert clz.is(Class<Session>);
        assert Struct structure := clz.allocate();
        assert structure.is(SessionImpl:struct);
        structure.id = id;

        report(clz.instantiate(structure));
        }

    void report(Session session)
        {
        console.println($"id={session.id} name={session.getName()}");
        }

    interface Session<Element>
        {
        Int id;
        String getName();
        }

    class SessionImpl<Element>(Int id) implements Session<Element>
        {
        @Override
        String getName()
            {
            return "Impl";
            }
        }

    mixin Session1(String suffix="-1")
            into Session
        {
        @Override
        String getName()
            {
            return super() + suffix;
            }
        }

    mixin Session2
            into Session
        {
        String suffix2 = "-2";

        @Override
        String getName()
            {
            return super() + suffix2;
            }
        }
    }