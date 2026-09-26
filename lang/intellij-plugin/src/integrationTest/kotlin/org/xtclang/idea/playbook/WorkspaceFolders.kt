package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.singleProject
import java.nio.file.Path

/** Use the installed client's transport for the same workspace notification as the VS Code driver. */
fun Driver.changeWorkspaceFolders(
    removed: Path,
    added: Path,
) {
    fun folder(path: Path) = new(RemoteWorkspaceFolder::class, path.toUri().toString().removeSuffix("/"), path.fileName.toString())
    val event = new(RemoteWorkspaceFoldersEvent::class, listOf(folder(added)), listOf(folder(removed)))
    val params = new(RemoteWorkspaceFoldersParams::class, event)
    service<LanguageClients>(singleProject())
        .getStartedServers()
        .single()
        .getLanguageServer()
        .getWorkspaceService()
        .didChangeWorkspaceFolders(params)
}

@Remote("org.eclipse.lsp4j.services.LanguageServer", plugin = "com.redhat.devtools.lsp4ij")
interface RemoteLanguageServer {
    fun getWorkspaceService(): RemoteWorkspaceService
}

@Remote("org.eclipse.lsp4j.services.WorkspaceService", plugin = "com.redhat.devtools.lsp4ij")
interface RemoteWorkspaceService {
    fun didChangeWorkspaceFolders(params: RemoteWorkspaceFoldersParams)
}

@Remote("org.eclipse.lsp4j.WorkspaceFolder", plugin = "com.redhat.devtools.lsp4ij")
interface RemoteWorkspaceFolder

@Remote("org.eclipse.lsp4j.WorkspaceFoldersChangeEvent", plugin = "com.redhat.devtools.lsp4ij")
interface RemoteWorkspaceFoldersEvent

@Remote("org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams", plugin = "com.redhat.devtools.lsp4ij")
interface RemoteWorkspaceFoldersParams
