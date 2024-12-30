module TestSimple {

    package cli     import cli.xtclang.org;
    package webauth import webauth.xtclang.org;

    @Inject Console console;

    void run() {
        test(1);
    }
}