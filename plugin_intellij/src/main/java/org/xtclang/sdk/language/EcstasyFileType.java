package org.xtclang.sdk.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing .*;

public class EcstasyFileType extends LanguageFileType {
    public static final EcstasyFileType INSTANCE = new EcstasyFileType();

    private EcstasyFileType() {
        super(EcstasyLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Ecstasy File";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Ecstasy language file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "x";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return EcstasyIcons.FILE;
    }
}
