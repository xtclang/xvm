module TestSimple {
    @Inject Console console;

    package convert import convert.xtclang.org;
    package crypto import crypto.xtclang.org;

    import crypto.*;

    void run() {
        File store = ./test_store.p12;

        try {
            @Inject(opts=new KeyStore.Info(store.contents, "")) KeyStore keystore;
            console.print(keystore.keyNames);
        } catch (Exception ignore) {} // this used to log an RT message regardless

        try {
            @Inject(opts=new KeyStore.Info(store.contents, "")) KeyStore keystore;
            console.print(keystore.keyNames);
        } catch (Exception e) {
            console.print($"{e=}");
        }
    }
}