/**
 * Interactive client for runner.xtclang.org.
 *
 * Start it with `xec runner_client.xtclang.org http://localhost:8080`.
 */
@TerminalApp("Runner CLI", "runner> ", timeout=Duration:30s)
module runner_client.xtclang.org {
    package webcli import webcli.xtclang.org;

    import webcli.*;

    @Command("run", "Run a module")
    String runModule(String moduleName) = post($"/run/{moduleName}");

    @Command("status", "Show task status")
    String status(Int id) = get($"/task/{id}");

    @Command("kill", "Kill a task")
    String kill(Int id) = post($"/task/{id}/kill");
}
