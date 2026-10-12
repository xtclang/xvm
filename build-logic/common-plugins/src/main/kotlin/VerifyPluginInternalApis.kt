import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path

/**
 * Checks the verifier's internal-API reports against the exact LSP4IJ integration dependencies.
 * Binary compatibility remains the verifier's responsibility. Unknown APIs, dependency upgrades and
 * incomplete reports fail here, so accepting existing workarounds cannot hide new platform internals.
 */
@DisableCachingByDefault(because = "Verifies external tool reports without producing an artifact")
abstract class VerifyPluginInternalApis : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportsDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val acceptedApis: RegularFileProperty

    @get:Input
    abstract val lsp4ijVersion: Property<String>

    @TaskAction
    fun verify() {
        val count = InternalApiPolicy.verify(
            reportsDirectory.get().asFile.toPath(), acceptedApis.get().asFile.readLines(), lsp4ijVersion.get(),
        )
        logger.lifecycle("Plugin internal API check: {} known LSP4IJ usages; no unreviewed internal APIs", count)
    }
}

/** Matches full API signatures, never package wildcards; unfamiliar report lines fail closed. */
internal object InternalApiPolicy {
    fun verify(reports: Path, policy: List<String>, version: String): Int {
        val entries = policy.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }
        require(entries.firstOrNull() == "lsp4ij=$version") {
            "Review the internal API policy for LSP4IJ $version before changing the dependency version"
        }
        val accepted = entries.drop(1).toSet()
        require(accepted.isNotEmpty() && accepted.all { API.matches(it) }) {
            "Internal API exceptions must name exact LSP4IJ classes or member signatures"
        }
        val verdicts = reports.toFile().walkTopDown().filter { it.name == "verification-verdict.txt" }.toList()
        check(verdicts.isNotEmpty()) { "No Plugin Verifier verdicts found in $reports" }
        return verdicts.sumOf { verdict ->
            val text = verdict.readText()
            val count = COUNT.find(text)?.groupValues?.get(1)?.toInt() ?: 0
            val report = verdict.resolveSibling("internal-api-usages.txt")
            val usages = if (report.isFile) report.readLines().filter(String::isNotBlank) else emptyList()
            check(usages.size == count) { "Incomplete internal API report beside $verdict: expected $count, found ${usages.size}" }
            val dependencies = verdict.resolveSibling("dependencies.txt")
            check(dependencies.isFile && DEPENDENCY.findAll(dependencies.readText()).any { it.groupValues[1] == version }) {
                "Verifier dependency report does not contain the reviewed LSP4IJ $version beside $verdict"
            }
            val unknown = usages.filter { signature(it.substringBefore(" is ")) !in accepted }
            check(unknown.isEmpty()) { "Unreviewed internal APIs in $report:\n${unknown.joinToString("\n")}" }
            usages.size
        }
    }

    /** Debug information changes parameter names between reports; only their types form the API. */
    private fun signature(api: String): String = PARAMETERS.replace(api) { parameters ->
        parameters.groupValues[1].split(',').joinToString(", ", "(", ")") { parameter ->
            PARAMETER_NAME.replace(parameter.trim(), "$1")
        }
    }

    private val API = Regex("Internal (?:class|interface|method|constructor) com\\.redhat\\.devtools\\.lsp4ij\\.[^*]+")
    private val PARAMETERS = Regex("\\(([^()]*)\\)")
    private val PARAMETER_NAME = Regex("^([\\w.$]+(?:\\[\\])*) [\\w$]+$")
    private val COUNT = Regex("(\\d+) usages? of internal API")
    private val DEPENDENCY = Regex("com\\.redhat\\.devtools\\.lsp4ij:([^\\s]+)")
}
