module TestSimple {
    @Inject Console console;
    void run() {
        String[] tests =
            [
            "",
            " ",
            " abc ",
            " a , b ",
            "a , b,",
            ",a,b,",
            " , a,b , ",
            ];

        private static String str(String s) {
            return s.quoted();
        }

        private static String str(String[] a) {
            return a.toString(render = s -> str(s));
        }

        for (String s : tests) {
            console.print($|{str(s)=}
                           |    {str(s.split(','))=}
                           |    {str(s.split(',', omitEmpty=True))=}
                           |    {str(s.split(',', trim=True))=}
                           |    {str(s.split(',', True, True))=}
                         );
        }
    }
}
