module TestSimple
    {
    @Inject Console console;

    package crypto import crypto.xtclang.org;

    import crypto.*;
    import crypto.KeyStore.Info;

    void run(String[] args = ["password"])
        {
        @Inject Directory curDir;
        File store = curDir.fileFor("data/hello/https.p12");

        @Inject(opts=new Info(store.contents, args[0])) KeyStore keystore;

        assert CryptoKey key := keystore.getKey("hello");
        console.print($"key={key}");

        @Inject Algorithms algorithms;
        console.print(algorithms.byName.values);
        }
    }