import com.sun.source.tree.BinaryTree
import com.sun.source.tree.LiteralTree
import com.sun.source.tree.Tree
import com.sun.source.util.JavacTask
import com.sun.source.util.TreeScanner
import com.sun.source.util.Trees
import java.net.URI
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

/**
 * Inserts missing spaces around Java binary operators using syntax trees, without type resolution.
 * Parsing distinguishes subtraction from unary minus and shifts/comparisons from generic brackets.
 * Only missing spaces are inserted: existing columns, line breaks and literal contents survive.
 */
object JavaBinaryOperatorSpacing {
    fun format(source: String): String {
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Java formatting requires a JDK" }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val input = object : SimpleJavaFileObject(URI.create("string:///SpotlessInput.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
        }
        compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8).use { files ->
            val options = listOf("-proc:none", "--enable-preview", "--source", Runtime.version().feature().toString())
            val task = compiler.getTask(null, files, diagnostics, options, null, listOf(input)) as JavacTask
            val units = task.parse().toList()
            // A formatter must not guess around syntax the parser could not understand.
            if (diagnostics.diagnostics.any { it.kind == Diagnostic.Kind.ERROR }) return source
            val positions = Trees.instance(task).sourcePositions
            val insertions = sortedSetOf<Int>(reverseOrder())
            fun spaceOperator(offset: Int, length: Int) {
                val after = offset + length
                if (offset > 0 && !source[offset - 1].isWhitespace()) insertions.add(offset)
                if (after < source.length && !source[after].isWhitespace()) insertions.add(after)
            }
            for (unit in units) {
                object : TreeScanner<Unit, Unit>() {
                    override fun visitBinary(node: BinaryTree, unused: Unit?): Unit? {
                        val start = positions.getEndPosition(unit, node.leftOperand).toInt()
                        val end = positions.getStartPosition(unit, node.rightOperand).toInt()
                        if (start >= 0 && end in start..source.length) {
                            val gap = JavaWhitespace.maskNonCode(source.substring(start, end))
                            val operator = gap?.trim()
                            if (gap != null && operator != null && operator in OPERATORS) {
                                spaceOperator(start + gap.indexOf(operator), operator.length)
                            }
                        }
                        return super.visitBinary(node, unused)
                    }

                    override fun visitLiteral(node: LiteralTree, unused: Unit?): Unit? {
                        // javac may fold adjacent string literals while parsing. Their original
                        // range still includes concatenation operators; masked text hides literal
                        // and comment contents so only the real plus signs remain visible.
                        if (node.kind == Tree.Kind.STRING_LITERAL) {
                            val start = positions.getStartPosition(unit, node).toInt()
                            val end = positions.getEndPosition(unit, node).toInt()
                            if (start >= 0 && end in start..source.length) {
                                val literal = JavaWhitespace.maskNonCode(source.substring(start, end))
                                literal?.forEachIndexed { offset, char ->
                                    if (char == '+') spaceOperator(start + offset, 1)
                                }
                            }
                        }
                        return super.visitLiteral(node, unused)
                    }
                }.scan(unit, Unit)
            }
            return StringBuilder(source).apply { insertions.forEach { insert(it, ' ') } }.toString()
        }
    }

    private val OPERATORS = setOf("*", "/", "%", "+", "-", "<<", ">>", ">>>", "<", ">", "<=", ">=", "==", "!=", "&", "^", "|", "&&", "||")
}
