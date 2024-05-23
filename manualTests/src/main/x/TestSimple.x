module TestSimple {
    package net import net.xtclang.org;

    @Inject Console console;

    void run() {
        String[] nums = ["", "0", "-0", "+0", "_0", "0_", "0.", ".0", "+0_", "1", "123", "0x123",
                         "0o123", "0b123", "1k", "1_1k", "1_1kb", "1_2kib", "0m", "1m",
                         "63k", "64k", "65k", "63ki", "64ki", "65535", "6__5__5__3__5", "65536", "6_5_5_3_6"];

        console.print($|{"string"   .center(12)} \
                       |{"parse"    .center(12)} \
                       |{"parse r2" .center(12)} \
                       |{"parse r8" .center(12)} \
                       |{"parse r10".center(12)} \
                       |{"parse r16".center(12)} \
                       |{"parse r36".center(12)}
                     );
        console.print(('-'.dup(12) + ' ').dup(7));

        Int?[] TestRadixes = [Null, 2, 8, 10, 16, 36];
        for (String num : nums) {
            StringBuffer buf = new StringBuffer();
            buf.append(num.quoted().leftJustify(12));
            for (Int? radix : TestRadixes) {
                if (UInt16 n := UInt16.parse(num, radix)) {
                    buf.add(' ')
                       .append(n.toString().rightJustify(12));
                } else {
                    buf.add(' ').append("err".center(12));
                }
            }
            console.print(buf);
        }

        String[] uris = [
            "",
            "http",
            "http://",
            "http://x",
            "http://xqiz.it",
            "http://cam@xqiz.it",
            "http://xqiz.it/demo",
            "http://xqiz.it/demo/",
            "https://xqiz.it/demo/?#",
            "https://xqiz.it/demo/?x=y#frag",
            "https://test.localhost.xqiz.it/demo/?x=y;a=123#",
        ];

        console.print("\n\nURIs:\n");
        import net.IPAddress;
        import net.Uri;

        @Volatile String? error = Null;
        if ((String?    scheme,
             String?    authority,
             String?    user,
             String?    host,
             IPAddress? ip,
             UInt16?    port,
             String?    path,
             String?    query,
             String?    opaque,
             String?    fragment) := Uri.parse(uri, s -> {error = error? + ", " + s : s;})) {
            console.print($"{uri.quoted()} -> {scheme=}, {authority=}, {user=}, {host=}, {ip=}, {port=}, {path=}, {query=}, {opaque=}, {fragment=}");
        } else {
            console.print($"{uri.quoted()} -> {error}");
        }

        StringBuffer buf = new StringBuffer();
        for (Int i = 0; i < 1000; ++i) {
            buf.ensureCapacity(i);
        }
    }
}
