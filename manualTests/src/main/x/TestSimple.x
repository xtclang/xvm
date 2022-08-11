module TestSimple
    {
    @Inject Console console;

    package net import net.xtclang.org;
    package web import web.xtclang.org;

    import net.URI;
    import web.routing.UriTemplate;

    void run()
        {
        val uri      = new URI("/sock/41685/17");
        val template = new UriTemplate("/sock/{id}/{size}");
        console.println($"template={template}, uri={uri}");
        if (val result := template.matches(uri))
            {
            console.println($"match: {result}");
            }
        else
            {
            console.println($"no match");
            }
        }
    }