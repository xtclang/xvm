package org.xvm.lsp.run

import java.util.concurrent.CountDownLatch

/**
 * A stand-in for the language server process in parent death tests: it starts a real execution worker
 * running a module, prints the worker's process ID once the program prints, and waits to be killed.
 * Arguments: the directory holding the module, the module name, and the run's root directory.
 */
object RunWorkerHost {
    @JvmStatic
    fun main(args: Array<String>) {
        val (modulePath, module, rootDir) = args
        val workers = RunWorkerProcess()
        val printed = CountDownLatch(1)
        workers.start(
            RunWorkerProcess.Request(listOf(modulePath), module, "run", emptyList(), rootDir),
            object : RunWorkerProcess.Listener {
                override fun output(text: String) = printed.countDown()

                override fun exited(
                    code: Long?,
                    error: String?,
                ) {}
            },
        )
        printed.await()
        println("worker-pid=${workers.workerPids().single()}")
        System.out.flush()
        Thread.sleep(Long.MAX_VALUE)
    }
}
