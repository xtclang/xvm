import java.util.Properties
import java.util.concurrent.TimeUnit

// A real Gradle producer with deterministic publication/exit gates. The XTC plugin's evaluated
// model is tested by TestKit; this fixture isolates the two IDEs' import and cancellation lifecycle.
abstract class ExportPlaybookModel : DefaultTask() {
    @get:Internal
    abstract val controlDirectory: DirectoryProperty

    @get:Internal
    abstract val report: RegularFileProperty

    @TaskAction
    fun export() {
        val control = controlDirectory.get().asFile
        val request = Properties().apply { control.resolve("request.properties").reader().use(::load) }
        val operation = request.getProperty("operation")
        val target = report.get().asFile
        target.parentFile.mkdirs()
        when (request.getProperty("output")) {
            "valid" -> control.resolve("model.json").copyTo(target, overwrite = true)
            "invalid" -> target.writeText("{")
            "missing" -> target.delete()
            else -> error("Unknown fixture output")
        }
        control.resolve("invocations.txt").appendText("$operation:$name\n")
        val released = control.resolve("$operation.release")
        control.resolve("$operation.pid").writeText(ProcessHandle.current().pid().toString())
        control.resolve("$operation.started").writeText(name)
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (!released.exists()) {
                check(System.nanoTime() < deadline) { "Playbook did not release import $operation" }
                Thread.sleep(50)
            }
            check(request.getProperty("outcome") == "success") { "Deliberate playbook import failure" }
        } finally {
            control.resolve("$operation.finished").writeText(name)
        }
    }
}

listOf("exportXtcLspModel", "prepareXtcLspModel").forEach { taskName ->
    tasks.register<ExportPlaybookModel>(taskName) {
        controlDirectory.set(layout.projectDirectory.dir(".compiler-import-playbook"))
        report.set(layout.projectDirectory.file(".gradle/xtc/lsp-model.json"))
        doNotTrackState("Each invocation is controlled by the editor playbook")
    }
}
