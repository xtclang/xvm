module TestSimple {
    @Inject Console console;

    package net import net.xtclang.org;

    import net.Uri;

    void run() {
        if (String error := checkInvalidName("a@b")) {
            console.print(error);
        }
    }

    conditional String checkInvalidName(String hostName) {
        @Volatile String? error = Null;
        if (String? user := Uri.parseAuthority(hostName, (e) -> {error = e;})) {
            if (user != Null) {
                error = "User section is not allowed";
            }
        }
        return error == Null ? False : (True, error); // this used to fail to compile
    }
}