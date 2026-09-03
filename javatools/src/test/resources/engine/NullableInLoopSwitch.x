module NullableInLoopSwitch {
    void run() {}
    String eatString() {
        StringBuffer? buf = Null;
        while (True) {
            Char ch = 'x';
            switch (ch) {
            default:
                if (buf == Null) { buf = new StringBuffer(); }
                buf.add(ch);
                break;
            }
        }
    }
}
