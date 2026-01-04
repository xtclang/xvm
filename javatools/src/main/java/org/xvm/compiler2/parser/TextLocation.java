package org.xvm.compiler2.parser;

/**
 * An immutable location in source text, with line and column information.
 */
public record TextLocation(
        SourceText source,
        int offset,
        int line,
        int column
) {
    /**
     * @return the file name
     */
    public String getFileName() {
        return source.getFileName();
    }

    /**
     * @return the 1-based line number (for display)
     */
    public int getDisplayLine() {
        return line + 1;
    }

    /**
     * @return the 1-based column number (for display)
     */
    public int getDisplayColumn() {
        return column + 1;
    }

    /**
     * @return a formatted location string like "file.x:10:5"
     */
    public String toDisplayString() {
        return source.getFileName() + ":" + getDisplayLine() + ":" + getDisplayColumn();
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
