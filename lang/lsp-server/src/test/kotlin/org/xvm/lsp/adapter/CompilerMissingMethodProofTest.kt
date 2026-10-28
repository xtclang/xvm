package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkMissingMethods.Dispatch
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.memberActionFacts
import org.xvm.lsp.adapter.xdk.missingMethodDestinations
import org.xvm.lsp.adapter.xdk.projectRenameFacts
import org.xvm.lsp.adapter.xdk.renameFacts
import org.xvm.lsp.adapter.xdk.toDependency

class CompilerMissingMethodProofTest {
    @Test
    fun `imports shift the inserted method but cannot weaken its source signature proof`() {
        CompilerTestSupport.configure()
        val librarySource = "/proof/Library.x"
        val libraryText = "module Library { class Other {} }"
        val text =
            """
            module Missing {
                package lib import Library;
                package shared import Types;
                Object read(lib.Other peer, shared.Value value) {
                    val local = value;
                    return peer.missing(local);
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val types = embedding.compileModule(Source("module Types { class Value {} }", "/proof/Types.x"), null, errors)
        assertThat(types.succeeded()).isTrue()
        val typeDependency = types.toDependency()
        val library = embedding.compileModule(Source(libraryText, librarySource), null, errors)
        assertThat(library.succeeded()).isTrue()
        val dependencies = XdkDependencies(listOf(library.toDependency(), typeDependency))
        val destinations = library.missingMethodDestinations(setOf("Types"))
        val open = dependencies.open()
        val failed = embedding.compileModule(Source(text, SOURCE), open.repository, ErrorList())
        assertThat(failed.succeeded()).isFalse()
        val inputs = failed.renameFacts(open, includeMissingMethods = true, missingDestinations = destinations).missingMethodInputs
        val fresh = dependencies.open()
        val declarations = embedding.analyzeDeclarations(Source(text, SOURCE), fresh.repository, errors).orElseThrow()
        val candidate = declarations.memberActionFacts(fresh, errors, inputs, destinations).missingMethods.single()
        val edits = requireNotNull(candidate.edit(libraryText))
        assertThat(edits.imports).hasSize(1)

        fun proves(member: XdkRename.Edit): Boolean {
            val plan = XdkRename.Plan(mapOf(SOURCE to text, librarySource to libraryText), mapOf(librarySource to (edits.imports + member)))
            val nextErrors = ErrorList()
            val typeInputs = XdkDependencies(listOf(typeDependency)).open()
            val nextLibrary =
                embedding.compileModule(
                    Source(plan.proposed.getValue(librarySource), librarySource),
                    typeInputs.repository,
                    nextErrors,
                )
            assertThat(nextLibrary.succeeded()).describedAs(nextErrors.errors.toString()).isTrue()
            val next = XdkDependencies(listOf(nextLibrary.toDependency(), typeDependency)).open()
            val caller = embedding.compileModule(Source(text, SOURCE), next.repository, nextErrors)
            assertThat(caller.succeeded()).describedAs(nextErrors.errors.toString()).isTrue()
            val after =
                CompilerRenameFacts.merge(
                    mapOf(
                        "library" to nextLibrary.projectRenameFacts(typeInputs, nextErrors),
                        "caller" to caller.projectRenameFacts(next, nextErrors),
                    ),
                )
            return candidate.bindsNewMethod(after, plan, member)
        }
        assertThat(proves(edits.member)).isTrue()
        assertThat(proves(edits.member.copy(text = edits.member.text.replace("types.Value arg1", "Object arg1")))).isFalse()
        assertThat(proves(edits.member.copy(text = edits.member.text.replace("public Object", "public String")))).isFalse()
    }

    @Test
    fun `cross module proof reads the actual destination signature and rejects compatible substitutions`() {
        CompilerTestSupport.configure()
        val librarySource = "/proof/Library.x"
        val libraryText = "module Library { class Other {} }"
        val text =
            """
            module Missing {
                package lib import Library;
                Object read(lib.Other peer, String value) {
                    return peer.missing(value);
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val library = embedding.compileModule(Source(libraryText, librarySource), null, errors)
        assertThat(library.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val dependencies = XdkDependencies(listOf(library.toDependency()))
        val destinations = library.missingMethodDestinations()
        val open = dependencies.open()
        val failed = embedding.compileModule(Source(text, SOURCE), open.repository, ErrorList())
        assertThat(failed.succeeded()).isFalse()
        val inputs = failed.renameFacts(open, includeMissingMethods = true, missingDestinations = destinations).missingMethodInputs
        val fresh = dependencies.open()
        val declarations = embedding.analyzeDeclarations(Source(text, SOURCE), fresh.repository, errors).orElseThrow()
        val headers = declarations.memberActionFacts(fresh, errors, inputs, destinations)
        assertThat(errors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        val edit = requireNotNull(candidate.edit(libraryText)).member

        fun proves(proposed: XdkRename.Edit): Boolean {
            val plan = XdkRename.Plan(mapOf(SOURCE to text, librarySource to libraryText), mapOf(librarySource to listOf(proposed)))
            val nextErrors = ErrorList()
            val nextLibrary = embedding.compileModule(Source(plan.proposed.getValue(librarySource), librarySource), null, nextErrors)
            assertThat(nextLibrary.succeeded()).describedAs(nextErrors.errors.toString()).isTrue()
            val next = XdkDependencies(listOf(nextLibrary.toDependency())).open()
            val caller = embedding.compileModule(Source(text, SOURCE), next.repository, nextErrors)
            assertThat(caller.succeeded()).describedAs(nextErrors.errors.toString()).isTrue()
            val after =
                CompilerRenameFacts.merge(
                    mapOf(
                        "library" to nextLibrary.projectRenameFacts(XdkDependencies(emptyList()).open(), nextErrors),
                        "caller" to caller.projectRenameFacts(next, nextErrors),
                    ),
                )
            return candidate.bindsNewMethod(after, plan, proposed)
        }
        assertThat(proves(edit)).isTrue()
        assertThat(proves(edit.copy(text = edit.text.replace("String arg1", "Object arg1")))).isFalse()
        assertThat(proves(edit.copy(text = edit.text.replace("public Object", "public String")))).isFalse()
    }

    @Test
    fun `cross owner local and initializer proof rejects compatible signature changes and argument rebinding`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                class Other {}
                Object read(Other peer, String value) {
                    var first = value;
                    var other = "other";
                    Object result = peer.missing(first);
                    return result;
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val failed = embedding.compileModule(Source(text, SOURCE), null, ErrorList())
        assertThat(failed.succeeded()).isFalse()
        val inputs = failed.renameFacts(XdkDependencies(emptyList()).open(), includeMissingMethods = true).missingMethodInputs
        val errors = ErrorList()
        val declarations = embedding.analyzeDeclarations(Source(text, SOURCE), null, errors).orElseThrow()
        val headers = declarations.memberActionFacts(XdkDependencies(emptyList()).open(), errors, inputs)
        assertThat(errors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        assertThat(candidate.declaration).isEqualTo("public Object missing(String arg1)")
        assertThat(candidate.arguments).hasSize(1)
        val edit = requireNotNull(candidate.edit(text)).member

        fun proves(
            proposed: XdkRename.Edit,
            additional: List<XdkRename.Edit> = emptyList(),
        ): Boolean {
            val plan = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to (additional + proposed)))
            val compiledErrors = ErrorList()
            val compiled = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, compiledErrors)
            assertThat(compiled.succeeded()).describedAs(compiledErrors.errors.toString()).isTrue()
            val after = compiled.projectRenameFacts(XdkDependencies(emptyList()).open(), compiledErrors)
            return candidate.bindsNewMethod(after, plan, proposed)
        }
        assertThat(proves(edit)).isTrue()
        // Both changes still compile, but change the API requested by the original local types.
        assertThat(proves(edit.copy(text = edit.text.replace("String arg1", "Object arg1")))).isFalse()
        assertThat(proves(edit.copy(text = edit.text.replace("public Object", "public String")))).isFalse()
        val argument = text.indexOf("first);")
        val redirect = XdkRename.Edit(argument, argument + "first".length, "other")
        assertThat(proves(edit, listOf(redirect))).isFalse()
    }

    @Test
    fun `cross owner repair must retain signature identity even when another return type compiles`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                class Other {}
                Object read(Other peer) {
                    return peer.missing();
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val failed = embedding.compileModule(Source(text, SOURCE), null, ErrorList())
        assertThat(failed.succeeded()).isFalse()
        val errors = ErrorList()
        val declarations = embedding.analyzeDeclarations(Source(text, SOURCE), null, errors).orElseThrow()
        val inputs = failed.renameFacts(XdkDependencies(emptyList()).open(), includeMissingMethods = true).missingMethodInputs
        val headers = declarations.memberActionFacts(XdkDependencies(emptyList()).open(), errors, inputs)
        assertThat(errors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        assertThat(candidate.declaration).isEqualTo("public Object missing()")
        val edit = requireNotNull(candidate.edit(text)).member

        fun proves(edit: XdkRename.Edit): Boolean {
            val plan = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit)))
            val compiledErrors = ErrorList()
            val compiled = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, compiledErrors)
            assertThat(compiled.succeeded()).describedAs(compiledErrors.errors.toString()).isTrue()
            val after = compiled.projectRenameFacts(XdkDependencies(emptyList()).open(), compiledErrors)
            return candidate.bindsNewMethod(after, plan, edit)
        }
        assertThat(proves(edit)).isTrue()
        // A String still fits the caller's Object result, but it changes the requested API.
        assertThat(proves(edit.copy(text = edit.text.replace("public Object", "public String")))).isFalse()
    }

    @Test
    fun `class qualifier proof requires static dispatch and the inserted owner declaration`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                class Other { static Int missing(Int value) = value; }
                class Box {
                    Int read(Int value) {
                        return Box.missing(value);
                    }
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val failed = embedding.compileModule(Source(text, SOURCE), null, errors)
        assertThat(failed.succeeded()).isFalse()
        val inputs = failed.renameFacts(XdkDependencies(emptyList()).open(), includeMissingMethods = true).missingMethodInputs
        assertThat(inputs.receivers.values.map { it.dispatch }).containsExactly(Dispatch.STATIC)
        val declarationErrors = ErrorList()
        val analysis = embedding.analyzeDeclarations(Source(text, SOURCE), null, declarationErrors).orElseThrow()
        val headers = analysis.memberActionFacts(XdkDependencies(emptyList()).open(), declarationErrors, inputs)
        assertThat(declarationErrors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        assertThat(candidate.declaration).isEqualTo("private static Int64 missing(Int64 arg1)")
        val edit = requireNotNull(candidate.edit(text)).member

        fun compile(plan: XdkRename.Plan): CompilerRenameFacts {
            val repairedErrors = ErrorList()
            val repaired = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, repairedErrors)
            assertThat(repaired.succeeded()).describedAs(repairedErrors.errors.toString()).isTrue()
            return repaired.projectRenameFacts(XdkDependencies(emptyList()).open(), repairedErrors)
        }
        val plan = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit)))
        val after = compile(plan)
        assertThat(candidate.bindsNewMethod(after, plan, edit)).isTrue()
        assertThat(candidate.copy(dispatch = Dispatch.INSTANCE).bindsNewMethod(after, plan, edit)).isFalse()
        val qualifier = text.indexOf("Box.missing")
        val redirect = XdkRename.Edit(qualifier, qualifier + "Box".length, "Other")
        val wrong = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(redirect, edit)))
        assertThat(candidate.bindsNewMethod(compile(wrong), wrong, edit)).isFalse()
    }

    @Test
    fun `repair must preserve a same owner receiver even when another receiver compiles`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                class Box {
                    Int read(Box peer, Int value) {
                        return peer.missing(value);
                    }
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val failed = embedding.compileModule(Source(text, SOURCE), null, errors)
        assertThat(failed.succeeded()).isFalse()
        val partial = failed.renameFacts(XdkDependencies(emptyList()).open())
        val inputs = failed.renameFacts(XdkDependencies(emptyList()).open(), includeMissingMethods = true).missingMethodInputs
        assertThat(inputs.receivers).hasSize(1)
        val declarationErrors = ErrorList()
        val analysis = embedding.analyzeDeclarations(Source(text, SOURCE), null, declarationErrors).orElseThrow()
        val headers = analysis.memberActionFacts(XdkDependencies(emptyList()).open(), declarationErrors, inputs)
        assertThat(declarationErrors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        assertThat(candidate.declaration).isEqualTo("private Int64 missing(Int64 arg1)")
        val edit = requireNotNull(candidate.edit(text)).member
        val before = CompilerRenameFacts.merge(mapOf("headers" to headers, "partial" to partial))

        fun proves(plan: XdkRename.Plan): Boolean {
            val repairedErrors = ErrorList()
            val repaired = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, repairedErrors)
            assertThat(repaired.succeeded()).describedAs(repairedErrors.errors.toString()).isTrue()
            val after = repaired.projectRenameFacts(XdkDependencies(emptyList()).open(), repairedErrors)
            assertThat(candidate.bindsNewMethod(after, plan, edit)).isTrue()
            return XdkRename.preservesKnownBindings(before, after, plan)
        }
        assertThat(proves(XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit))))).isTrue()
        // Both receivers select the new method, but switching instances changes program behavior.
        val receiver = text.indexOf("peer.missing")
        val redirect = XdkRename.Edit(receiver, receiver + "peer".length, "this")
        assertThat(proves(XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(redirect, edit))))).isFalse()
    }

    @Test
    fun `inferred local evidence remains detached and repair must bind the exact argument declaration`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                Int read(Int value) {
                    var first = value;
                    var other = value + 1;
                    Int result = missing(first);
                    return result;
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val failed = embedding.compileModule(Source(text, SOURCE), null, errors)
        assertThat(failed.succeeded()).isFalse()
        val inputs = failed.renameFacts(XdkDependencies(emptyList()).open(), includeMissingMethods = true).missingMethodInputs
        assertThat(inputs.localTypes.values.map { it.source }).containsExactlyInAnyOrder("Int64", "Int64", "Int64")
        val declarationErrors = ErrorList()
        val analysis = embedding.analyzeDeclarations(Source(text, SOURCE), null, declarationErrors).orElseThrow()
        val headers = analysis.memberActionFacts(XdkDependencies(emptyList()).open(), declarationErrors, inputs)
        assertThat(declarationErrors.errors).isEmpty()
        val candidate = headers.missingMethods.single()
        assertThat(candidate.declaration).isEqualTo("private Int64 missing(Int64 arg1)")
        assertThat(candidate.arguments).hasSize(1)
        val edit = requireNotNull(candidate.edit(text)).member

        fun proves(plan: XdkRename.Plan): Boolean {
            val repairedErrors = ErrorList()
            val repaired = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, repairedErrors)
            assertThat(repaired.succeeded()).describedAs(repairedErrors.errors.toString()).isTrue()
            return candidate.bindsNewMethod(repaired.projectRenameFacts(XdkDependencies(emptyList()).open(), repairedErrors), plan, edit)
        }
        assertThat(proves(XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit))))).isTrue()
        // This compiles with the same signature, but the argument now denotes a different local.
        val argument = text.indexOf("first);")
        val redirect = XdkRename.Edit(argument, argument + "first".length, "other")
        assertThat(proves(XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(redirect, edit))))).isFalse()
    }

    @Test
    fun `fresh parameter signature repairs a failed body without changing known bindings`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Missing {
                Int keep(Int value) = value;
                Int keep(String value) = value.size;
                Int read(Int value) {
                    Int previous = keep(value);
                    return missing(value);
                }
            }
            """.trimIndent()
        val embedding = EmbeddingSupport.instance()
        val errors = ErrorList()
        val failed = embedding.compileModule(Source(text, SOURCE), null, errors)
        assertThat(failed.succeeded()).isFalse()
        val partial = failed.renameFacts(XdkDependencies(emptyList()).open())
        val declarationErrors = ErrorList()
        val analysis =
            embedding
                .analyzeDeclarations(Source(text, SOURCE), null, declarationErrors)
                .orElseThrow()

        val headers = analysis.memberActionFacts(XdkDependencies(emptyList()).open(), declarationErrors)
        assertThat(declarationErrors.errors).isEmpty()
        assertThat(headers.missingMethods.map { it.declaration }).containsExactly("private Int64 missing(Int64 arg1)")
        val candidate = headers.missingMethods.single()
        val edit = requireNotNull(candidate.edit(text)).member
        val plan = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(edit)))
        val compiledErrors = ErrorList()
        val compiled = embedding.compileModule(Source(plan.proposed.getValue(SOURCE), SOURCE), null, compiledErrors)
        assertThat(compiled.succeeded()).describedAs(compiledErrors.errors.toString()).isTrue()
        val after = compiled.projectRenameFacts(XdkDependencies(emptyList()).open(), compiledErrors)
        assertThat(candidate.bindsNewMethod(after, plan, edit)).isTrue()
        val before = CompilerRenameFacts.merge(mapOf("headers" to headers, "partial" to partial))
        assertThat(XdkRename.preservesKnownBindings(before, after, plan)).isTrue()

        // The graph still compiles if an existing call switches overloads, but that is not a
        // repair of the missing method. The old compiler-selected call must remain unchanged.
        val call = text.indexOf("keep(value)")
        val redirect = XdkRename.Edit(call, call + "keep(value)".length, "keep(\"text\")")
        val wrong = XdkRename.Plan(mapOf(SOURCE to text), mapOf(SOURCE to listOf(redirect, edit)))
        val redirectedErrors = ErrorList()
        val redirected = embedding.compileModule(Source(wrong.proposed.getValue(SOURCE), SOURCE), null, redirectedErrors)
        assertThat(redirected.succeeded()).describedAs(redirectedErrors.errors.toString()).isTrue()
        val redirectedFacts = redirected.projectRenameFacts(XdkDependencies(emptyList()).open(), redirectedErrors)
        assertThat(XdkRename.preservesKnownBindings(before, redirectedFacts, wrong)).isFalse()
    }

    private companion object {
        const val SOURCE = "Missing.x"
    }
}
