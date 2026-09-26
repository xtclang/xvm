package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Test data shared with the VS Code driver; editor actions remain native to each client. */
class SharedScenarios(
    val sourceHash: String,
    val common: Common,
    val scenarios: Map<String, Scenario>,
) {
    data class Common(
        val sourceModules: List<SourceModule>,
        val fixtures: List<Fixture>,
        val editing: Editing,
    )

    data class Fixture(
        val file: String,
        val pattern: String,
    )

    data class Editing(
        val file: String,
        val run: String,
        val inspect: String,
    )

    data class Coverage(
        val coverage: String,
        val limitations: List<String>,
    )

    data class Scenario(
        val title: String,
        val manual: List<String>,
        val values: JsonObject,
        val intellij: Coverage,
    ) {
        fun text(key: String): String = requireNotNull(values[key]) { "Missing scenario value $key" }.asString

        fun strings(key: String): List<String> = requireNotNull(values[key]).asJsonArray.map { it.asString }

        fun rows(key: String): List<JsonObject> = requireNotNull(values[key]).asJsonArray.map { it.asJsonObject }
    }

    private inline fun <reified T> core(id: String): T {
        val json = scenarios.getValue(id).values.deepCopy()
        json.addProperty("id", id)
        return Gson().fromJson(json, T::class.java)
    }

    val diagnostics: Diagnostics = core("X2")
    val definitions: Definitions = core("X4")
    val completion: Completion = core("X7")
    val dependencyNavigation: Navigation = core("X45")
    val dependencyEdit: DependencyEdit = core("X46")
    val configuration: Configuration = core("CFG1")
    val warning: Warning = core("7a.8")

    data class SourceModule(
        val name: String,
        val uri: String,
        val dependencies: List<String>,
    )

    data class Edit(
        val from: String,
        val to: String,
    ) {
        fun apply(text: String): String {
            val start = unique(text, from)
            return text.replaceRange(start, start + from.length, to)
        }
    }

    data class Location(
        val cursor: String,
        val targetFile: String,
        val target: String,
    )

    data class ExpectedError(
        val edit: Edit,
        val severity: String,
        val codePrefix: String,
        val messageContains: String,
        val rangeStart: String,
        val rangeEnd: String,
    )

    data class Diagnostics(
        val id: String,
        val file: String,
        val errors: List<ExpectedError>,
        val cleanAppend: String,
    )

    data class Definitions(
        val id: String,
        val file: String,
        val locations: List<Location>,
    )

    data class Completion(
        val id: String,
        val file: String,
        val edit: Edit,
        val cursor: String,
        val label: String,
        val accepted: Edit,
    )

    data class Navigation(
        val id: String,
        val file: String,
        val location: Location,
    )

    data class DependencyEdit(
        val id: String,
        val file: String,
        val consumer: String,
        val edit: Edit,
    )

    data class Configuration(
        val id: String,
        val consumer: String,
    )

    data class Warning(
        val id: String,
        val file: String,
        val severity: String,
        val code: String,
        val messageContains: String,
        val count: Int,
        val declaration: String,
        val edit: Edit,
    )

    val ids: List<String> get() = scenarios.keys.toList()
    val files: List<String> get() = common.fixtures.map { it.file }
    val implementedIds: Set<String> get() = scenarios.filterValues { it.intellij.coverage != "not-implemented" }.keys

    val graph: String
        get() = Gson().toJson(mapOf("xtc" to mapOf("compiler" to mapOf("sourceModules" to common.sourceModules))))

    fun validate(fixtures: Map<String, String>) {
        diagnostics.errors.forEach { expected ->
            val text = expected.edit.apply(fixtures.getValue(diagnostics.file))
            require(offset(text, expected.rangeStart) <= offset(text, expected.rangeEnd))
        }
        definitions.locations.forEach { location ->
            offset(fixtures.getValue(definitions.file), location.cursor)
            offset(fixtures.getValue(location.targetFile), location.target)
        }
        val incomplete = completion.edit.apply(fixtures.getValue(completion.file))
        offset(incomplete, completion.cursor)
        completion.accepted.apply(incomplete)
        offset(fixtures.getValue(dependencyNavigation.file), dependencyNavigation.location.cursor)
        offset(fixtures.getValue(dependencyNavigation.location.targetFile), dependencyNavigation.location.target)
        dependencyEdit.edit.apply(fixtures.getValue(dependencyEdit.file))
        offset(fixtures.getValue(warning.file), warning.declaration)
        warning.edit.apply(fixtures.getValue(warning.file))
        files.forEach { fixtures.getValue(it) }
    }

    companion object {
        fun read(path: Path): SharedScenarios {
            val contents = Files.readString(path)
            val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents.toByteArray(Charsets.UTF_8)))
            val json = JsonParser.parseString(contents).asJsonObject
            require(json["schemaVersion"].asInt == 2) { "Unsupported shared playbook schema" }
            val gson = Gson()
            val scenarios =
                json.getAsJsonObject("cases").entrySet().associate { (id, value) ->
                    id to gson.fromJson(value, Scenario::class.java)
                }
            val expected = (1..96).map { "X$it" } + listOf("CFG1", "CFG2", "CFG3", "7a.8", "7a.9")
            require(scenarios.keys.toList() == expected) { "The catalog must describe the complete playbook in order" }
            scenarios.forEach { (id, scenario) ->
                require(scenario.intellij.coverage in setOf("full", "partial", "not-implemented")) { "Missing IntelliJ coverage for $id" }
                require(
                    scenario.intellij.coverage == "full" || scenario.intellij.limitations.isNotEmpty(),
                ) { "Explain the IntelliJ gap for $id" }
            }
            return SharedScenarios(hash, gson.fromJson(json["common"], Common::class.java), scenarios)
        }

        fun text(
            template: String,
            vararg values: String,
        ): String {
            val slots = Regex("\\$\\{(\\d+)\\}")
            val indices = slots.findAll(template).map { it.groupValues[1].toInt() }.toSet()
            require(indices == values.indices.toSet()) { "Template substitutions do not match: $template" }
            return slots.replace(template) { values[it.groupValues[1].toInt()] }
        }

        fun offset(
            text: String,
            marked: String,
        ): Int {
            val marker = unique(marked, "§")
            return unique(text, marked.replace("§", "")) + marker
        }

        private fun unique(
            text: String,
            anchor: String,
        ): Int {
            require(anchor.isNotEmpty()) { "Empty shared playbook anchor" }
            val start = text.indexOf(anchor)
            require(start >= 0 && text.indexOf(anchor, start + 1) < 0) { "Expected one shared playbook anchor: $anchor" }
            return start
        }
    }
}
