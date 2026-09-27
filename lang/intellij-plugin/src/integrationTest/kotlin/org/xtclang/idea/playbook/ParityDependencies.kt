package org.xtclang.idea.playbook

import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.dependencyCases() {
    case("X47") { data ->
        val consumer = dependencies()
        val library = open(data.string("file"))
        replace(library, common["dependency"].asJsonObject.string("brokenLibrary"))
        diagnostics(library) { items -> items.any { it.code?.startsWith(data.string("compilerCodePrefix")) == true } }
        blocked()
        check(targets(consumer, "definition", consumer.at(data.string("anchor"), data.int("offset"))).isEmpty())
        replace(library, fixture(library.file))
        clean(library)
        linked(consumer)
    }
    case("X48") { data ->
        val consumer = dependencies()
        val library = open(data.string("file"))
        replace(library, common["dependency"].asJsonObject.string("stringLibrary"))
        errors(consumer)
        discard(library)
        linked(consumer)
        check(open(library.file).text == fixture(library.file))
    }
    case("X49") { data ->
        val consumer = dependencies()
        val file = data.string("file")
        delete(file)
        published(file) { items -> items.any { it.string("code") == data.string("diagnosticCode") } }
        blocked()
        write(file)
        linked(consumer)
        published(file) { it.isEmpty() }
    }
    case("X50") { data ->
        val consumer = dependencies()
        val file = data.string("file")
        virtual(file, data.string("text"), data.int("version")) {
            published(file) { it.isNotEmpty() }
            blocked()
        }
        linked(consumer)
        published(file) { it.isEmpty() }
        write(file, data.string("text"))
        published(file) { it.isNotEmpty() }
        blocked()
        delete(file)
        linked(consumer)
        published(file) { it.isEmpty() }
    }
    case("X51") { data ->
        val consumer = dependencies()
        val unrelated = open(data.string("unrelatedFile"))
        val library = open(data.string("libraryFile"))
        val pending =
            (0 until 10).map { index ->
                replace(
                    library,
                    if (index % 2 ==
                        1
                    ) {
                        fixture(library.file)
                    } else {
                        common["dependency"].asJsonObject.string("brokenLibrary")
                    },
                    settle = false,
                )
                protocol.request("textDocument/definition", consumer.params(consumer.at(data.string("anchor"), data.int("offset"))))
            }
        pending.forEach { awaitRetired("textDocument/definition", it) }
        settle(library)
        linked(consumer)
        clean(unrelated)
        check(query("textDocument/documentSymbol", unrelated).rows().isNotEmpty())
    }
    case("X52") { data ->
        write(data.string("libraryFile"))
        write(data.string("bridgeFile"), data.string("bridgeText"))
        write(
            data.string("consumerFile"),
            fixture(data.string("consumerFile")).replace(data.string("replaceFrom"), data.string("bridgeImport")),
        )
        configure(data["sourceModules"])
        val consumer = open(data.string("consumerFile"))
        clean(consumer)
        val library = open(data.string("libraryFile"))
        replace(library, common["dependency"].asJsonObject.string("stringLibrary"))
        published(data.string("bridgeFile")) { it.isNotEmpty() }
        blocked()
        replace(library, common["dependency"].asJsonObject.string("brokenLibrary"))
        blocked()
        replace(library, fixture(library.file))
        clean(consumer)
        val bridge = open(data.string("bridgeFile"))
        replace(bridge, data.string("brokenBridge"))
        blocked()
        replace(bridge, data.string("bridgeText"))
        clean(consumer)
        check(targets(consumer, "definition", consumer.at(data.string("anchor"), data.int("offset"))).single().string("uri") == bridge.uri)
    }
    case("CFG2") { data ->
        val consumer = dependencies()
        val before = trace.notifications("window/showMessage").size
        configure(data["sourceModules"])
        with(driver) {
            waitFor("client receives rejection of cyclic graph", 45.seconds) {
                trace.notifications("window/showMessage").drop(before).any { data.pattern("pattern").containsMatchIn(it.string("message")) }
            }
        }
        linked(consumer)
    }
    case("CFG3") {
        val consumer = dependencies()
        val version = version(consumer)
        protocol.notify("workspace/didChangeConfiguration", mapOf("settings" to null))
        linked(consumer)
        check(version(consumer) == version)
    }
}

private fun ParityWorkspace.blocked() {
    val dependency = common["dependency"].asJsonObject
    published(dependency.string("consumer")) { items -> items.any { it.string("code") == dependency.string("blockedCode") } }
}
