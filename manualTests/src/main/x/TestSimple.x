module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;
    import ecstasy.collections.*;
    import collections.*;

    package host  import host.xtclang.org;

    void run()
        {
        @Inject Directory homeDir;

        Directory d = host.ensureHome(homeDir, "test");
        console.println(d);

        assert Module m := host.isModuleImport();
        console.println(m);
        }
    }
