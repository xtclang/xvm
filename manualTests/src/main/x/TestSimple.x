module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    @Inject Timer timer;

    void run()
        {
        P1<Int> p1 = new P1();
        P2<Int> p2 = new P2();
        P3<Int> p3 = new P3();

        profile((i) -> p1.instantiate(i, ""), 10000);
        profile((i) -> p2.instantiate(i, ""), 10000);
        profile((i) -> p3.instantiate(i, ""), 10000);

        profile((i) -> p1.instantiate(i, ""), 3000000, "p1");
        profile((i) -> p2.instantiate(i, ""), 3000000, "p2");
        profile((i) -> p3.instantiate(i, ""), 3000000, "p3");
        }

    void profile(function void (Int) run, Int iterations, String? title = Null)
        {
        timer.reset();
        for (Int i = 0; i < iterations; i++)
            {
            run(i);
            }
        Duration time = timer.elapsed;
        if (title != Null)
            {
            console.println($|{title}: Elapsed {time.milliseconds} ms; \
                             |latency {(time / iterations).nanoseconds} ns
                             );
            }
        }

    class P1<Key, Value>
        {
        Child instantiate(Key key, Value value)
            {
            return new Child(key, value);
            }

        static class Child(Object key, Object value)
            {
            }
        }

    class P2<Key, Value>
        {
        Child instantiate(Key key, Value value)
            {
            return new Child(key, value);
            }

        class Child(Key key, Value value)
            {
            }
        }

    class P3<Key, Value>
        {
        Child instantiate(Key key, Value value)
            {
            return new Child<Key, Value>(key, value);
            }

        static class Child<Key, Value>(Key key, Value value)
            {
            }
        }
    }
