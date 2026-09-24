package org.xvm.compiler.ast;

import org.xvm.asm.ErrorListener;

/**
 * The context and error listener in force while a statement is being validated, captured so that a
 * variable created lazily during that validation can still be registered against them.
 *
 * <p>A statement holds one of these only while it is validating, and null otherwise. That null is the
 * statement's state - "I am not validating" - and not an absent listener: the two used to be
 * separate fields set and cleared together, so "am I validating" and "what do I report to" could
 * disagree, and the listener half had to be treated as nullable everywhere it was read.
 *
 * @param ctx   the context to register lazily created variables against
 * @param errs  the listener to report against while validating
 */
record ValidationScope(Context ctx, ErrorListener errs) {
}
