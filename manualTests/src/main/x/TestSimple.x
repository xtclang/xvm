module TestSimple {
    @Inject Console console;

    package net import net.xtclang.org;

    import net.Uri;

    void run() {
        // that used to succeed (no failure to parse)
        assert !Uri.parse("http://user@x%20y.com:80/", (e) -> console.print(e));
    }
}