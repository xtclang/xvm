package org.xvm.lsp.adapter.xdk

import java.io.File

/**
 * The note on the string literals and comments that spell a renamed declaration's old name in the modules a rename
 * changes, which it leaves as they are, as IntelliJ's search in comments and strings reports them: how many, and the
 * first of them by file and line, each where it first spells the name.
 */
internal object XdkRenameSpellings {
    /** The places the note names; it counts the rest. */
    private const val NAMED = 3

    /**
     * The note on the spellings of [name] in [sources], the texts of the modules a rename changes by their paths, outside
     * the rename's [edits] of them; null when there are none.
     */
    fun note(
        name: String,
        sources: Map<String, String>,
        edits: Map<String, List<XdkRename.Edit>>,
    ): String? {
        val spellings =
            sources.toSortedMap().flatMap { (source, text) ->
                val changed = edits[source].orEmpty()
                XdkLexical
                    .spellings(text, name)
                    .filter { spelling -> changed.none { spelling.offset < it.end && it.start < spelling.offset + name.length } }
                    .distinctBy { it.token }
                    .map { Triple(source, text, it) }
            }
        if (spellings.isEmpty()) return null
        val comments = spellings.count { (_, _, spelling) -> spelling.comment }
        val strings = spellings.size - comments
        val counted =
            listOfNotNull(
                count(strings, "string literal").takeIf { strings > 0 },
                count(comments, "comment").takeIf { comments > 0 },
            ).joinToString(" and ")
        val places =
            spellings.take(NAMED).joinToString(", ") { (source, text, spelling) ->
                "${File(source).name}:${XdkRename.position(text, spelling.offset).line + 1}"
            } + if (spellings.size > NAMED) " and ${spellings.size - NAMED} more" else ""
        val (spell, are) = if (spellings.size == 1) "spells" to "is" else "spell" to "are"
        return "Ecstasy: $counted in the modules this rename changes $spell '$name' and $are not changed by it: $places."
    }

    private fun count(
        size: Int,
        noun: String,
    ) = if (size == 1) "1 $noun" else "$size ${noun}s"
}
