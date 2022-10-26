module TestSimple
    {
    package net import net.xtclang.org;

    import net.Uri;
    import net.UriTemplate;
    import net.UriTemplate.UriParameters;

    @Inject Console console;

    void run()
        {
        Uri[] uris =
            [
            new Uri("/"),
            new Uri("/tests"),
            new Uri("/tests/"),
            new Uri("/test"),
            new Uri("/test/"),
            new Uri("/test/s"),
            new Uri("/test/abc"),
            new Uri("/test/abc/"),
            new Uri("/test/abc/?k1=v1;k2=v2#red,green,blue"),
            ];

        UriTemplate[] templates =
            [
            new UriTemplate("test"),
            new UriTemplate("tests"),
            new UriTemplate("test/"),
            new UriTemplate("tests/"),
            new UriTemplate("/test"),
            new UriTemplate("/tests"),
            new UriTemplate("/test/"),
            new UriTemplate("/tests/"),
            new UriTemplate("/test/abc"),
            new UriTemplate("/test/abc/"),
            new UriTemplate("/test{/sub}"),
            new UriTemplate("/test{/sub,sub2}{?parms}{#frags}"),
            new UriTemplate("/test{/sub*}{?parms*}{#frags*}"),
            new UriTemplate("/tests{/sub}"),
            new UriTemplate("/tests{/sub,sub2}"),
            new UriTemplate("/tests{/sub*}{?parms*}{#frags*}"),
            ];

        for (UriTemplate template : templates)
            {
            out($"template={template}");
            for (Uri uri : uris)
                {
                if (UriParameters result := template.matches(uri))
                    {
                    out($"  matches: {uri} -> {result}");
                    }
                else
                    {
                    out($"  no match: {uri}");
                    }
                }
            }

        Map<String, UriTemplate.Value> values =
            [
            "count" = ["one", "two", "three"],
            "dom"   = ["example", "com"],
            "dub"   = "me/too",
            "hello" = "Hello World!",
            "half"  = "50%",
            "var"   = "value",
            "who"   = "fred",
            "base"  = "http://example.com/home/",
            "path"  = "/foo/bar",
            "list"  = ["red", "green", "blue"],
            "keys"  = ["semi"=";", "dot"=".", "comma"=","],
            "v"     = "6",
            "x"     = "1024",
            "y"     = "768",
            "empty" = "",
            "nokey" = List:[],
            ];

        String[] examples =
            [
            "{/who}",
            "{/who,who}",
            "{/half,who}",
            "{/who,dub}",
            "{/var}",
            "{/var,empty}",
            "{/var,undef}",
            "{/var,x}/here",
            "{/var:1,var}",
            "{/list}",
            "{/list*}",
            "{/list*,path:4}",
            "{/keys}",
            "{/keys*}",
            ];

        function String(String) render = t -> new UriTemplate(t).format(values);

        out();
        for (String example : examples)
            {
            out($"{example} = {render(example)}");
            }
        }

    static void out(String text="")
        {
        console.println(text);
        }
    }