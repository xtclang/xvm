package org.xtclang.idea.playbook

import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo

/** Reuse a matching native installation before asking Starter to download an installer archive. Starter 263's install blocks. */
internal class CachedIdeInstaller(
    private val fallback: IdeInstaller,
) : IdeInstaller {
    override fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> = CachedIdes.installed(ideInfo) ?: fallback.install(ideInfo)
}
