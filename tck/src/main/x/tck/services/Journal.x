service Journal {
    private String[] messages = new String[];

    void add(String msg) = messages.add(msg);

    String[] collect(Boolean clear = True) {
        String[] report = messages.freeze(inPlace=clear);
        if (clear) {
            messages = new String[];
        }
        return report;
    }
    @Override String toString() { return messages.toString(); }
}

