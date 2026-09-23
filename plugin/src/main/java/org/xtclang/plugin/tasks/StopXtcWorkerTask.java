package org.xtclang.plugin.tasks;

import java.time.Duration;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import org.xtclang.plugin.runtime.persistent.WorkerClient;

/** Stop this checkout's idle XTC workers explicitly; active build leases are never stolen. */
@DisableCachingByDefault(because = "Stops live local worker processes")
public abstract class StopXtcWorkerTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getWorkerDirectory();

    @Internal
    public abstract Property<String> getShutdownTimeout();

    @TaskAction
    public void stopWorkers() throws Exception {
        final int count = WorkerClient.stopAll(getWorkerDirectory().get().getAsFile().toPath(),
            Duration.parse(getShutdownTimeout().get()));
        getLogger().lifecycle("Stopped {} idle XTC worker(s)", count);
    }
}
