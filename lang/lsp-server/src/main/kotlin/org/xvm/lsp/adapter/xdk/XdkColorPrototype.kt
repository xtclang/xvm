package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.Component.Format
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.ByteConstant
import org.xvm.compiler.InvocationBinding
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.lsp.adapter.ColorPresentation
import org.xvm.lsp.adapter.ColorValue
import org.xvm.lsp.adapter.DocumentColor
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit
import org.xvm.util.PackedInteger
import kotlin.math.roundToInt
import java.util.List.copyOf as immutableList

/**
 * Opt-in L77 experiment for ColorPrototype.Rgba, an ordinary test-fixture const, not an XDK API.
 * Only the resolved shorthand constructor with four UInt8 channels is recognized. No user code
 * is executed. Copy source spans on the compiler worker; queries retain no AST/pool/constant.
 */
internal object XdkColorPrototype {
    private const val MODULE = "ColorPrototype"
    private const val TYPE = "Rgba"
    private const val MAX_CHANNEL = 255
    private val names = listOf("red", "green", "blue", "alpha")
    private val comments = setOf(Token.Id.EOL_COMMENT, Token.Id.ENC_COMMENT)
    private val newlines = Regex("\r\n|\r|\n")

    /** Source.toString(start, end) normalizes CRLF. Edits must retain the original text. */
    private class SourceText(
        val text: String,
    ) {
        private val starts = listOf(0) + newlines.findAll(text).map { it.range.last + 1 }.toList()

        fun offset(at: Long): Int = starts[Source.calculateLine(at)] + Source.calculateOffset(at)

        fun slice(
            start: Long,
            end: Long,
        ): String = text.substring(offset(start), offset(end))
    }

    private data class Construction(
        val source: Source,
        val start: Long,
        val end: Long,
        val binding: InvocationBinding,
    )

    internal data class Channel(
        val parameter: Int,
        val start: Int,
        val end: Int,
        val value: Int,
    )

    internal class Site(
        val sourceName: String?,
        val range: Range,
        private val text: String,
        channels: List<Channel>,
        private val trailingComma: Boolean,
    ) {
        private val channels = immutableList(channels)
        val color =
            channels.associate { it.parameter to it.value }.let { values ->
                ColorValue(
                    values.getValue(0).toDouble() / MAX_CHANNEL,
                    values.getValue(1).toDouble() / MAX_CHANNEL,
                    values.getValue(2).toDouble() / MAX_CHANNEL,
                    (values[3] ?: MAX_CHANNEL).toDouble() / MAX_CHANNEL,
                )
            }

        fun presentation(color: ColorValue): ColorPresentation {
            val values = listOf(color.red, color.green, color.blue, color.alpha).map { (it * MAX_CHANNEL).roundToInt() }
            val alpha =
                if (channels.none { it.parameter == 3 } && values[3] != MAX_CHANNEL) {
                    text.dropLast(1) + (if (trailingComma) " " else ", ") + "alpha = ${values[3]})"
                } else {
                    text
                }
            val replacement =
                channels.sortedByDescending { it.start }.fold(alpha) { result, channel ->
                    result.replaceRange(channel.start, channel.end, values[channel.parameter].toString())
                }
            return ColorPresentation("RGBA(${values.joinToString(", ")})", TextEdit(range, replacement))
        }
    }

    fun capture(compilation: EmbeddingSupport.Compilation): Map<String?, List<Site>> {
        if (!compilation.succeeded()) return emptyMap()
        return ConstantPool.withPool(compilation.pool()).use {
            buildList {
                compilation.constructorBindings().forEach { (node, binding) ->
                    node.source?.let { add(Construction(it, node.startPosition, node.endPosition, binding)) }
                }
                compilation.initializerBindings().forEach { (owner, initializer) ->
                    initializer.calls().filter { it.construction() }.forEach { call ->
                        owner.source?.let { add(Construction(it, call.span().startPosition(), call.span().endPosition(), call.binding())) }
                    }
                }
            }.groupBy { it.source }
                .flatMap { (source, calls) ->
                    val raw = SourceText(source.toRawString())
                    calls.mapNotNull { site(source.fileName, raw, it.start, it.end, it.binding) }
                }.distinctBy { it.sourceName to it.range }
                .groupBy { it.sourceName }
        }
    }

    private fun site(
        sourceName: String?,
        source: SourceText,
        start: Long,
        end: Long,
        binding: InvocationBinding,
    ): Site? {
        val identity = binding.method()
        val owner = identity.parentConstant.parentConstant
        if (owner.moduleConstant.name != MODULE || owner.pathString != TYPE || owner.component?.format != Format.CONST) return null
        val method = identity.component as? MethodStructure ?: return null
        if (!method.isShorthandConstructor || !method.isConstructor || method.typeParamCount != 0) return null
        val parameters = method.params.toList()
        if (parameters.map { it.name } != names || parameters.any { it.type != method.constantPool.typeUInt8() }) return null
        // A changed fixture/default is not silently interpreted as the prototype contract.
        if ((parameters.last().defaultValue as? ByteConstant)?.value != MAX_CHANNEL) return null
        val text = source.slice(start, end)
        if (!text.endsWith(')')) return null
        val channels =
            binding.arguments().map { argument ->
                val raw = source.slice(argument.startPosition(), argument.endPosition())
                val errors = ErrorList()
                val tokens = Lexer(Source(raw), errors).asSequence().filter { it.id !in comments }.toList()
                if (errors.hasSeriousErrors()) return null
                val literal =
                    if (argument.named()) {
                        if (tokens.size != 3 || tokens[0].id != Token.Id.IDENTIFIER || tokens[1].id != Token.Id.ASN) return null
                        tokens[2]
                    } else {
                        tokens.singleOrNull() ?: return null
                    }
                if (literal.id != Token.Id.LIT_INT) return null
                val number = literal.value as? PackedInteger ?: return null
                // The successful UInt8 compilation already enforces this; keep the projection bounded.
                val integer = number.bigInteger
                val value = integer.toInt()
                if (integer != value.toBigInteger() || value !in 0..MAX_CHANNEL) return null
                val prefix = source.offset(argument.startPosition()) - source.offset(start)
                val argumentSource = SourceText(raw)
                Channel(
                    argument.parameterIndex(),
                    prefix + argumentSource.offset(literal.startPosition),
                    prefix + argumentSource.offset(literal.endPosition),
                    value,
                )
            }
        val indices = channels.map { it.parameter }
        if (indices.toSet().size != indices.size || indices.any { it !in 0..3 } || !(0..2).all { it in indices }) return null
        val tail =
            Lexer(Source(text.substring(channels.maxOf { it.end }, text.lastIndex)), ErrorList())
                .asSequence()
                .filter { it.id !in comments }
                .toList()
        if (tail.isNotEmpty() && tail.map { it.id } != listOf(Token.Id.COMMA)) return null
        return Site(sourceName, Range(position(start), position(end)), text, channels, tail.isNotEmpty())
    }

    private fun position(at: Long) = Position(Source.calculateLine(at), Source.calculateOffset(at))

    fun colors(sites: List<Site>): List<DocumentColor> = sites.map { DocumentColor(it.range, it.color) }
}
