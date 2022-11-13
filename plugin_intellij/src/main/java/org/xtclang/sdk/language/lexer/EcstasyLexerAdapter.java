package org.xtclang.sdk.language.lexer;


import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xtclang.sdk.language.EcstasyLanguage;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;


public class EcstasyLexerAdapter extends LexerBase
    {
    private Lexer ecstasyLexer;

    private CharSequence myText;
    private Source source;

    private int myTokenStart;
    private int myTokenEnd;


    private Token currentToken;

    private int myBufferEnd;

    public Lexer getEcstasyLexer()
        {
        return ecstasyLexer;
        }

    @Override
    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState)
        {
        myText = buffer;
        myBufferEnd = endOffset;
        myTokenStart = myTokenEnd = startOffset;

        source = new Source(buffer.toString());
        ErrorListener errorListener = new ErrorList(10);
        ecstasyLexer = new Lexer(source, errorListener);

        ecstasyLexer.setPosition(startOffset);
        // TODO: This should also take the endOffset.
        ecstasyLexer.setPosition(startOffset);
        currentToken = null;
        }

    @Override
    public int getState()
        {
        locateToken();
        return 0; // TODO: Need help with this
        }

    @Override
    public @Nullable IElementType getTokenType()
        {
        locateToken();
        if (currentToken == null)
            {
            return null;
            }
        else
            {
            return new IElementType(currentToken.getId().name(), EcstasyLanguage.INSTANCE);
            }
        }

    @Override
    public int getTokenStart()
        {
        locateToken();
        return myTokenStart;
        }

    @Override
    public int getTokenEnd()
        {
        locateToken();
        return myTokenEnd;
        }

    @Override
    public void advance()
        {
        locateToken();
        currentToken = null;
        }

    private void locateToken()
        {
        if (currentToken != null)
            {
            return;
            }

        if (!ecstasyLexer.hasNext())
            {
            currentToken = null;
            }
        else
            {
            myTokenStart = myTokenEnd;
            currentToken = ecstasyLexer.next();
            myTokenEnd = source.getMOffset();
            }
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
        return "FlexAdapter for " + ecstasyLexer.getClass().getName();
        }
    }

