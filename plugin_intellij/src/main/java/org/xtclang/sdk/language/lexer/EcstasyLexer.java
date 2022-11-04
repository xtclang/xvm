package org.xtclang.sdk.language.lexer;

import com.intellij.lexer.LexerPosition;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;

public class EcstasyLexer extends com.intellij.lexer.LexerBase
    {
    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState)
        {
        Source source = new Source(buffer.toString());
        ErrorListener errorListener = new ErrorList(10);
        Lexer lexer = new Lexer(source, errorListener);
        }

    @Override
    public int getState()
        {
        return 0;
        }

    @Override
    public @Nullable IElementType getTokenType()
        {
        return null;
        }

    @Override
    public int getTokenStart()
        {
        return 0;
        }

    @Override
    public int getTokenEnd()
        {
        return 0;
        }

    @Override
    public void advance()
        {

        }

    @Override
    public @NotNull CharSequence getBufferSequence()
        {
        return null;
        }

    @Override
    public int getBufferEnd()
        {
        return 0;
        }
    }
