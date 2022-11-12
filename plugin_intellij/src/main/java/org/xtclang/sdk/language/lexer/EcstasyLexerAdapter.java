package org.xtclang.sdk.language.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;

public class EcstasyLexerAdapter extends LexerBase
    {
    private Lexer ecstasyLexer;

    private IElementType myTokenType;
    private CharSequence myText;

    private int myTokenStart;
    private int myTokenEnd;

    private int myBufferEnd;
    private int myState;

    private boolean myFailed;

    public EcstasyLexerAdapter()
        {
        }

    public Lexer getEcstasyLexer()
        {
        return ecstasyLexer;
        }

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState)
        {
        myText = buffer;
        myTokenStart = myTokenEnd = startOffset;
        myBufferEnd = endOffset;
        myTokenType = null;

        Source source = new Source(buffer.toString());
        ErrorListener errorListener = new ErrorList(10);
        Lexer lexer = new Lexer(source, errorListener);

        lexer.setPosition(startOffset);
        // TODO: This should also take the endOffset.
        ecstasyLexer.setPosition(startOffset);
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
        return myText;
        }

    @Override
    public int getBufferEnd()
        {
        return myBufferEnd;
        }

    @Override
    public String toString()
        {
        return "FlexAdapter for " + myFlex.getClass().getName();
        }
    }

