package jit.org.xtclang.ecstasy.io;

import org.xvm.javajit.intrinsic.Ctx;
import org.xvm.javajit.intrinsic.xObj;
import org.xvm.javajit.intrinsic.xStr;

/**
 * AUTOGEN: interface Console
 */
public interface Console {
    /**
     * void print(Object object = "", Boolean suppressNewline = False);
     */
    void print(Ctx $ctx, xObj object, boolean suppressNewline);

    default void print(Ctx $ctx, xObj object,
                       boolean suppressNewline$default, boolean suppressNewline) {
        print($ctx, object, suppressNewline$default ? false : suppressNewline);
    }

    /**
     * String readLine(String prompt = "", Boolean suppressEcho = False);
     */
    xStr readLine(Ctx $ctx, xStr prompt, boolean suppressEcho);

    default xStr readLine(Ctx $ctx, boolean prompt$default, xStr prompt,
                          boolean suppressEcho$default, boolean suppressEcho) {
        return readLine($ctx, prompt$default ? xStr.EmptyString : prompt,
                        suppressEcho$default ? false : suppressEcho);
    }
}

