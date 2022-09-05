module TestSimple
    {
    @Inject Console console;

    package net import net.xtclang.org;
    import net.URI;

    package web import web.xtclang.org;
    import web.routing.UriTemplate;

    void run()
        {
        URI uri = new URI("h");
        UriTemplate template = new UriTemplate("h");
        assert:debug;
        assert template.matches(uri);
        }
    }