module test4.examples.org {

    import ecstasy.io.IOException;

    @Inject Console console;

    void run() {
        testSwitch1(0);
        testSwitch1(4);
        testSwitch1(171);

        console.print(testSwitch2(0));
        console.print(testSwitch2(4));
        console.print(testSwitch2(171));

        console.print(testSwitch3(33));
        console.print(testSwitch3(-Int32.MaxValue+1));

        console.print(testSwitch4(Green));
    }

    void testSwitch1(Int i) {
        switch (i) {
        case 0:
            console.print("1-a");
            break;
        case 1..6:
            console.print("1-b");
            break;
        case 7:
            console.print("1-c");
            break;
        case 14,15:
            console.print("1-d");
            break;
        default:
            console.print("1-other");
            break;
        }
    }

    String testSwitch2(Int i) {
        return switch (i) {
            case 0:    "2-a";
            case 1..6: "2-b";
            case 7:    "2-c";
            case 14,15:"2-d";
            default:   "2-other";
        };
    }

    String testSwitch3(Int i) {
        return switch (i) {
            case 0..2:   "3-a";
            case 30..70: "3-b";
            case 1555:   "3-c";
            case Int32.MaxValue..Int64.MaxValue: "3-d";
            default:     "3-other";
        };
    }

    String testSwitch4(Color color) {
        return switch (color) {
            case Red:   "Rojo";
            case Green: "Verde";
            case Blue:  "Azul";
        };
    }

    enum Color {Red, Green, Blue}
}