module TestSimple {

    @Inject static Console console;
    void run(String[] args = []) {
        console.print(filter("aABbcCdDe", Char.asciiUppercase));
    }

    String filter(String s, function Boolean(Char) match) {
        StringBuffer buffer = new StringBuffer(s.size);
        for (Char ch : s.chars) {
            if (match(ch)) {    // this used to assert at run time
                buffer.add(ch);
            }
        }
        return buffer.toString();
    }
}