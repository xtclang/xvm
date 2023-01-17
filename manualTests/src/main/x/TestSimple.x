module TestSimple
    {
    @Inject Console console;

    package crypto import crypto.xtclang.org;

    void run(String[] args = ["password"])
        {
        import crypto.*;
        import crypto.KeyStore.Info;

        @Inject Directory curDir;
        File store = curDir.fileFor("data/hello/https.p12");

        @Inject(opts=new Info(store.contents, args[0])) KeyStore keystore;

        for (Certificate cert : keystore.certificates)
            {
            console.println($"certificate={cert}");
            if (CryptoKey key := cert.containsKey())
                {
                console.println($"key={key}");
                }
            }
        }
    }