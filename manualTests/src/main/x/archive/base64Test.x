module base64Test
    {
    @Inject Console console;

    package web import web.xtclang.org;
    import web.codecs.Base64Format;

    void run()
        {
            {
            Base64Format fmt = new Base64Format();
            console.print($"format={fmt.name}");
            Byte[] orig  = [0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF];
            console.print($"orig={orig}");
            String text  = fmt.encode(orig);
            console.print($"text={text}");
            Byte[] bytes = fmt.decode(text);
            console.print($"bytes={bytes}");
            }

        @Inject Random rnd;
        for (Int i : 0..10000)
            {
            Int    len  = rnd.int(50);
            Byte[] orig = new Byte[len];
            rnd.fill(orig);
            Boolean fill = rnd.boolean();
            Int? linelen = rnd.boolean() ? rnd.int(50)+1 : Null;

            Base64Format fmt = new Base64Format(fill, linelen);

            String text;
            try
                {
                text = fmt.encode(orig);
                }
            catch (Exception e)
                {
                console.print($"failed: encode({orig}, {fill}, {linelen})");
                throw e;
                }

            Byte[] bytes;
            try
                {
                bytes = fmt.decode(text);
                }
            catch (Exception e)
                {
                console.print($"failed: decode({text})");
                console.print($"(original: {orig})");
                throw e;
                }

            assert bytes == orig as $"mismatch: orig={orig}, text={text}, bytes={bytes}";
            }
        }
    }