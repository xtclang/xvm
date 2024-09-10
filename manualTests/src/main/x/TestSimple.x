module TestSimple {
    @Inject Console console;

    package web import web.xtclang.org;

    import web.*;

    void run(String[] args=["http://localhost"]) {
        HttpClient client = new HttpClient();

        String uri = args[0] + "/hello";

        ResponseIn response1 = client.get^(uri);
        &response1.whenComplete((r, e) -> console.print($"GET: {e==Null ? r : e}"));
        assert !&response1.assigned; // this used to fail

        ResponseIn response2 = client.delete^(uri);
        &response2.whenComplete((r, e) -> console.print($"DELETE: {e==Null ? r : e}"));
        assert !&response2.assigned;  // this used to fail
    }
}
