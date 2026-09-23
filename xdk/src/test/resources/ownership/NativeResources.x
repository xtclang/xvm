module NativeResources {
    package net import net.xtclang.org;
    package web import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    void run(String[] args) {
        @Inject Console console;
        switch (args[0]) {
        case "file":
            @Inject Directory rootDir;
            File file = rootDir.fileFor("owned.dat").ensure();
            Holder.channel = file.open(write=[]);
            break;
        case "watch":
            @Inject Directory rootDir;
            Holder.cancel = rootDir.watch(new ecstasy.fs.FileWatcher() {}.makeImmutable());
            break;
        case "socket":
            @Inject net.Network network;
            assert net.Socket socket := network.connect((new net.IPAddress("127.0.0.1"), new UInt16(args[2])));
            Holder.socket = socket;
            socket.out.writeByte(1);
            break;
        case "http":
            @Inject web.Client.Connector connector;
            (Int status, _, _, _) = connector.sendRequest("GET", args[2], [], [], []);
            assert status == 200;
            break;
        case "server":
            @Inject xenia.HttpServer server;
            server.bind(new web.http.HostInfo("127.0.0.1", httpPort=0, httpsPort=0));
            if (args[1] == "close") {
                server.close();
                server.close();
            }
            break;
        case "paused":
            @Inject Timer timer;
            timer.start();
            timer.schedule(Duration:1H, () -> {});
            timer.stop();
            break;
        case "callbacks":
            @Inject Clock clock;
            @Inject Timer timer;
            for (Int i : 0..<10) {
                function void () cancelClock = clock.schedule(Duration:1H, () -> {});
                function void () cancelTimer = timer.schedule(Duration:1H, () -> {});
                cancelClock();
                cancelClock();
                cancelTimer();
                cancelTimer();
            }
            break;
        }
        if (args[1] == "failure") {
            console.print("ready");
            throw new IllegalState("expected resource-owner failure");
        }
        if (args[1] == "cancel") {
            @Inject Timer timer;
            @Future Tuple pending;
            timer.schedule(Duration:1H, () -> {pending = ();});
            console.print("ready");
            return pending;
        }
        console.print("ready");
        if (args[1] == "read") {
            assert net.Socket socket ?= Holder.socket;
            socket.in.readByte();
        }
    }

    static service Holder {
        ecstasy.fs.FileChannel? channel;
        net.Socket? socket;
        function void ()? cancel;
    }
}
