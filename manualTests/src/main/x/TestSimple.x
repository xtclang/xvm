module TestSimple
    {
    @Inject Console console;

    void run()
        {
        show(v:1);
        show(v:1.0);
        show(v:1.1);
        show(v:1.1beta);
        show(v:1.1.beta);
        show(v:1.1-beta);
        show(v:1.1beta-2);
        show(v:1.1-beta-2);
        show(v:1.1.beta-2);
        show(v:1.1.rc-3+b17);
        show(v:beta);
        show(v:beta2);
//        show(v:1.1.rc-3+b17€);
//        show(v:1.o);

        String s = v:1.2.3.4.alpha-5.toString();
        console.println($"s={s}");
        }

    void show(Version v)
        {
        console.println($"version={v}");
        }
    }
