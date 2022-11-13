package org.xtclang.sdk.language;


import com.intellij.lang.Language;


public class EcstasyLanguage extends Language
    {

    public static final EcstasyLanguage INSTANCE = new EcstasyLanguage();

    private EcstasyLanguage()
        {
        super("Ecstasy");
        }
    }
