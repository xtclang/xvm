service Journal {
    private String[] messages = new String[];

    private void foo(String msg) = messages.add(msg);
    
    void add(String msg) = foo(msg);

    String[] collect(Boolean clear = True) {
        String[] report = messages.freeze(inPlace=clear);
        if (clear) {
            messages = new String[];
        }
        return report;
    }
    @Override String toString() { return messages.toString(); }
}

