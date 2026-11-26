package org.xvm.lsp.protocol

import com.google.gson.JsonObject

/** `xtc/debugStart` result: the ID of the new debug session. */
data class DebugSessionResponse(
    val sessionId: String,
)

/** `xtc/debugEnd` parameters. */
data class DebugSessionParams(
    val sessionId: String? = null,
)

/** `xtc/debugMessage` parameters: one Debug Adapter Protocol message of a session, in either direction. */
data class DebugMessageParams(
    val sessionId: String,
    val message: JsonObject,
)

/**
 * `xtc/debugTask` parameters: the task that runs a debug session's ([sessionId]) module under the debugger, its
 * Gradle task or the xtc command, as the launch's [buildMode] says, `gradle` or `commandLine`: the module's [module],
 * [method] and [args], as a Gradle run or `xtc run` takes them, with the launcher's `--debug` options [debug], which a
 * Gradle task takes as the Gradle property `xtcDebug`. The program's XDK has the debugger's agent. With [tests], the
 * task runs those tests of the module instead, as `testXtc` or `xtc test` take them, all of them for an empty list,
 * with xunit's `json` report, whose events ([TestEvent]) the client reads from the task's output.
 */
data class DebugTaskParams(
    val sessionId: String,
    val module: String,
    val method: String?,
    val args: List<String>,
    val debug: String,
    val buildMode: String? = null,
    val tests: List<TestSelector>? = null,
)

/** The end of an `xtc/debugTask`: the task's exit code, when it has one. */
data class DebugTaskResult(
    val exitCode: Int? = null,
)
