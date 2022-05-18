module TestSimple
    {
    package net import net.xtclang.org;
    import net.*;

    @Inject Console console;
    @Inject Network network;
    @Inject Network insecureNetwork;
    @Inject Network secureNetwork;

    void run()
        {
        console.println("Hello world!");
        console.println($"network={&network.actualType}, secureNetwork={&secureNetwork.actualType}, insecureNetwork={&insecureNetwork.actualType}");
        assert !network.isSecure();

        NameService ns = network.nameService;
        console.println($"nameService={&ns.actualType}");

        IPAddress[]? addrs = Null;
        addrs := ns.resolve("google.com");
        console.println($"google.com={addrs}");

        String name = "?";
        name := ns.reverseLookup(new IPAddress("127.0.0.1"));
        console.println($"127.0.0.1={name}");
        }
    }