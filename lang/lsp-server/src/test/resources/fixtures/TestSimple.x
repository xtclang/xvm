module TestSimple {
    @Inject Console console;

    void run(String[] args = []) {
        assert test1(1, 3) == "default";
        assert test2(1, 3) == "default";

    }

    String test1(Int64 n1, Int64 n2) {
        switch (n1, n2) {
        case 1, 2:
            return "one";
        case 3, 4:
            return "two";
        default:
            return "default";
        }
    }

    String test2(Int64 n1, Int64 n2) {
        return switch (n1, n2) {
            case 1, 2: "one";
            case 3, 4: "two";
            default:   "default";
        };
    }
}
