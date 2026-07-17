module TestSimple {

    @Inject Console console;

    package net import net.xtclang.org;

    import net.Network;
    import net.NameService.Record;

    void run() {
        @Inject Network secureNetwork;

        Record[] records = secureNetwork.nameService.records("welcome.xqizit.cloud");

        console.print(records.toString(pre="", post="", sep="\n"));

        if (String data := secureNetwork.nameService.getData("xqizit.cloud", "welcome", "CNAME")) {
            console.print($"CNAME welcome {data}");
        }
    }
}
