package org.xvm.xrun;
/**
XTC IllegalState exception is thrown by assert and by close.
Java assert throws AssertionFailedException.
Java close  throws IOException.
I don't see/expect any catches of assert exceptions, except for demo purposes, and possibly a few times in some testing harness.
I DO see exactly 2 catches of IllegalState (in the JSONDB Catalog.x).
I might expect more from users, used to catching close fails.

Since the exception throw by XTC assert and close are the same thing, and can be caught by the same thing, I need to force the matching Java exceptions to map to some same thing, so they can be caught by whatever I map XTC's IllegalState exception to.
This means I merge Java's IOException and Java's AssertionFailedException. 
That means I need to catch every Java close call or every Java assert call (or both), and remap the exception.
Right now, every XTC assert is mapped to a Java assert; and this means I might wrap (in Java) for every XTC assert... OR
when I call the Java mapping for every XTC close, I'll have to catch Java's obvious IOException and map it.

There's a lot less wrapping of exceptions going on, if we can unbundle XTC assert's IllegalState exception from XTC close's IllegalState.

So I am Once Again, asking for a language change: make the XTC assert throw e.g. AssertionFailed instead of IllegalState.
*/                                   
public class IllegalStateX extends RuntimeException {
}
