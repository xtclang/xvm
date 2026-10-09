package org.xtclang.idea.playbook

import com.google.gson.JsonParser
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Reuse a matching native installation before asking Starter to download an installer archive. */
internal class CachedIdeInstaller(
    private val fallback: IdeInstaller,
) : IdeInstaller {
    override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
        val builds = GlobalPaths.instance.getLocalCacheDirectoryFor("builds")
        val cached =
            Files.list(builds).use { paths ->
                paths
                    .filter { Files.isDirectory(it, NOFOLLOW_LINKS) }
                    .filter { matches(it, ideInfo) }
                    .sorted()
                    .findFirst()
            }
        if (cached.isEmpty) return fallback.install(ideInfo)
        val ide = di.direct.instance<IdeDistributionFactory>().installIDE(cached.get(), ideInfo.executableFileName)
        println("Reusing native IntelliJ installation: ${ide.installationPath} (${ide.build})")
        return ide.build to ide
    }

    private fun matches(
        directory: Path,
        info: IdeInfo,
    ): Boolean =
        Files
            .find(directory, 4, { path, attributes ->
                attributes.isRegularFile && path.fileName.toString() == "product-info.json"
            })
            .use { files ->
                files.anyMatch { file ->
                    // Missing or invalid product metadata cannot establish a matching installation.
                    runCatching {
                        val product = JsonParser.parseString(Files.readString(file)).asJsonObject
                        product["productCode"].asString == info.productCode &&
                            product["version"].asString == info.version &&
                            directory.fileName.toString() == "${info.productCode}-${product["buildNumber"].asString}"
                    }.getOrDefault(false)
                }
            }
}
