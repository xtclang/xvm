module TestSimple
    {
    package net import net.xtclang.org;
    import net.*;

    @Inject Console console;
    @Inject Network network;
    @Inject Network insecureNetwork;
    @Inject Network secureNetwork;

    @Inject Timer timer;

    void run()
        {
        NameService ns = network.nameService;
        Lookup svc = new Lookup(ns);

        String[] names = ["yahoo.com", "google.com", "oracle.com", "what.the.sh.it"];
        for (String name : names)
            {
            svc.report^(name);
            }
        }

    @Concurrent
    service Lookup(NameService ns)
        {
        void report(String name)
            {
            @Future Tuple<Boolean, IPAddress[]> resolve = ns.resolve^(name);

            &resolve.whenComplete((r, x) ->
                {
                console.println($"{timer.elapsed.milliseconds}: {name}={r}");
                if (r?[0])
                    {
                    @Future Tuple<Boolean, String> lookup = ns.reverseLookup^(r[1][0]);
                    &lookup.whenComplete((r2, x2) ->
                        {
                        if (r2?[0])
                            {
                            console.println($"{timer.elapsed.milliseconds}: {name}={r2[1]}");
                            }
                        });
                    }
                });
            }
        }
    }