module TestSimple {
    @Inject Console console;

    void run() {
        function void ()[] prints = [];

        {
            Int i;
            for (i = 0; i < 3; i++) {
                prints += () -> console.print($"{i=}");
            }
        }

        {
            @Volatile Int i;
            for (i = 0; i < 3; i++) {
                prints += () -> console.print($"{i=}");
            }
        }

        for (val print : prints) {
			print();
		}
    }
}