package org.xtclang.sdk.language.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.xtclang.sdk.language.EcstasyFileType;
import org.xtclang.sdk.language.EcstasyLanguage;

public class EcstasyFile extends PsiFileBase
    {

    public EcstasyFile(@NotNull FileViewProvider viewProvider)
        {
        super(viewProvider, EcstasyLanguage.INSTANCE);
        }

    @NotNull
    @Override
    public FileType getFileType()
        {
        return EcstasyFileType.INSTANCE;
        }

    @Override
    public String toString()
        {
        return "Ecstasy File";
        }
    }
