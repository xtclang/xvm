package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.FileStructure
import org.xvm.asm.constants.TypeInfo
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.NewExpression
import java.util.Properties

/** Final compositions must be correct even when earlier fit/composition probes were silent. */
class TypeInfoFinalCompositionTest {
    @Test
    fun `fresh serialized array XML and XODB compositions retain substituted members and supers`() {
        val repository = BuildRepository()
        val index = Properties().apply { resource("modules.properties").use { load(it) } }
        for (name in index.getProperty("modules").split(',')) {
            resource(name).use { repository.storeModule(FileStructure(it).module) }
        }
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport.instance().compileModule(
                Source("module LoadedAudit { package xml import xml.xtclang.org; package db import jsondb.xtclang.org; }"),
                repository,
                errors,
            )
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(errors.errors).isEmpty()

        fun classes(moduleName: String): List<ClassStructure> {
            val module = compilation.file().getModule(compilation.file().moduleIds().single { it.name == moduleName })
            return components(if (module.isFingerprint) module.fingerprintOrigin else module).filterIsInstance<ClassStructure>()
        }

        val arrays = classes("ecstasy.xtclang.org").filter { it.identityConstant.pathString.contains(".elementAt(Int).Object:") }
        assertThat(arrays).hasSize(7)
        val expected = mapOf("asBooleanArray" to "Boolean", "asNibbleArray" to "Nibble", "asByteArray" to "Byte", "asBitArray" to "Bit")
        arrays.forEach { type ->
            inspect(type) { info ->
                val pool = type.constantPool
                val assigned =
                    info.properties.entries
                        .single { it.key.pathString.endsWith(".element.assigned") }
                        .value
                assertThat(assigned.type).isEqualTo(pool.typeBoolean())
                val referent =
                    info.properties.entries
                        .single { it.key.pathString.endsWith(".element.assigned.Referent") }
                        .value.type
                assertThat(referent.getParamType(0)).isEqualTo(pool.typeBoolean())
                val getter =
                    info.methods.entries
                        .single { it.key.pathString.endsWith(".element.get()") }
                        .value
                val setter =
                    info.methods.entries
                        .single { it.key.pathString.contains(".element.set(") }
                        .value
                assertThat(setter.signature.params.toList()).containsExactlyElementsOf(getter.signature.returns.toList())
                val element =
                    getter.signature.returns
                        .single()
                        .valueString
                val name = type.identityConstant.pathString
                assertThat(element).endsWith(expected.entries.firstOrNull { name.contains(it.key) }?.value ?: "NumType")
                val assignedGetter =
                    info.methods.entries
                        .single { it.key.pathString.endsWith(".element.assigned.get()") }
                        .value
                assertThat(assignedGetter.chain.toList()).anySatisfy {
                    assertThat(it.identity.pathString).isEqualTo("NakedRef.get()")
                    assertThat(it.signature.returns.toList()).containsExactly(pool.typeBoolean())
                }
            }
        }

        val cursor = classes("xml.xtclang.org").single { it.identityConstant.pathString.endsWith("ContentList.cursor(Int).Cursor:1") }
        inspect(cursor) { info ->
            val value =
                info.properties.entries
                    .single { it.key.name == "value" }
                    .value.type
            assertThat(value.valueString).isEqualTo("xml:Content")
            val insert = info.methods.values.single { it.signature.name == "insert" }
            assertThat(insert.signature.params.toList()).containsExactly(value)
            assertThat(insert.chain.toList()).anySatisfy {
                assertThat(it.identity.pathString).contains("List.Cursor.insert")
                assertThat(it.signature.params.toList()).containsExactly(value)
            }
        }

        val maps = classes("jsondb.xtclang.org").filter { it.identityConstant.pathString.contains(".dbChildren.calc().Map:") }
        assertThat(maps).hasSize(7)
        maps.forEach { type ->
            inspect(type) { info ->
                if (type.identityConstant.pathString.endsWith(".calc().Map:1")) {
                    val get =
                        info.methods.entries
                            .single { it.key.pathString.endsWith(".Map:1.get(String)") }
                            .value
                    assertThat(get.signature.params.toList()).containsExactly(type.constantPool.typeString())
                    assertThat(get.signature.returns.map { it.valueString }).containsExactly("Boolean", "oodb:DBObject")
                    assertThat(get.chain.toList()).anySatisfy {
                        assertThat(it.identity.pathString).contains("Map.get(")
                        assertThat(it.signature.returns.toList()).containsExactlyElementsOf(get.signature.returns.toList())
                    }
                }
            }
        }
    }

    @Test
    fun `fresh anonymous property has Boolean assigned metadata and invalid override reports to host`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val source =
            """
            module ArrayAudit {
                Var<Byte> elementAt(Int index) {
                    return new Object() {
                        Byte element {
                            @Override Boolean assigned.get() = index < 4;
                            @Override Byte get() { assert:bounds assigned; return 1; }
                            @Override void set(Byte value) {}
                        }
                    }.&element;
                }
            }
            """.trimIndent()
        val compilation = EmbeddingSupport.instance().compileModule(Source(source), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        ConstantPool.withPool(compilation.pool()).use {
            val created = nodes(compilation.parsed()).filterIsInstance<NewExpression>().single()
            val info = created.type.ensureTypeInfo(errors)
            assertThat(
                info.properties.entries
                    .single { it.key.pathString.endsWith(".element.assigned") }
                    .value.type,
            ).isEqualTo(compilation.pool().typeBoolean())
        }
        assertThat(errors.errors).isEmpty()

        val invalidErrors = ErrorList()
        val invalid = source.replace("@Override void set(Byte value) {}", "@Override void absent() {}")
        val rejected = EmbeddingSupport.instance().compileModule(Source(invalid), null, invalidErrors)
        assertThat(rejected.succeeded()).isFalse()
        assertThat(invalidErrors.errors).anyMatch { it.code.startsWith("VERIFY-") && it.message.contains("absent") }
        assertThat(invalidErrors.errors).noneMatch { it.code == "EMB-5" }
    }

    private fun inspect(
        type: ClassStructure,
        check: (TypeInfo) -> Unit,
    ) {
        ConstantPool.withPool(type.constantPool).use {
            val errors = ErrorList()
            val info = type.formalType.ensureTypeInfo(errors)
            assertThat(errors.errors).describedAs(type.identityConstant.pathString).isEmpty()
            check(info)
        }
    }

    private fun components(root: Component): List<Component> = listOf(root) + root.children().flatMap(::components)

    private fun nodes(root: AstNode): List<AstNode> =
        buildList {
            add(root)
            root.children().forEachRemaining { addAll(nodes(it)) }
        }

    private fun resource(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/typeinfo-test/$name")) {
            "Missing compiled test module: $name"
        }
}
