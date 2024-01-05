package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.xtclang.plugin.XtcCompilerExtension;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.xtclang.plugin.ProjectDelegate.provideString;
import static org.xtclang.plugin.ProjectDelegate.stringProvider;

public class DefaultXtcCompilerExtension extends DefaultXtcTaskExtension implements XtcCompilerExtension {
    private final Property<Boolean> disableWarnings;
    private final Property<Boolean> isStrict;
    private final Property<Boolean> hasQualifiedOutputName;
    private final Property<Boolean> hasVersionedOutputName;
    private final Property<Boolean> shouldForceRebuild;
    private final Map<Object, Object> renameOutputMap;
    private final MapProperty<Object, Object> renameOutput;

    @Inject
    public DefaultXtcCompilerExtension(final Project project) {
        super(project);
        this.disableWarnings = objects.property(Boolean.class).value(false);
        this.isStrict = objects.property(Boolean.class).value(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).value(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).value(false);
        this.shouldForceRebuild = objects.property(Boolean.class).value(false);
        this.renameOutputMap = new HashMap<>();
        this.renameOutput = objects.mapProperty(Object.class, Object.class).value(renameOutputMap);
    }

    @Override
    public void moduleFilename(final Object from, final Object to) {
        renameOutputMap.put(stringProvider(project, from), stringProvider(project, to));
    }

    @Override
    public Property<Boolean> getNoWarn() {
        return disableWarnings;
    }

    @Override
    public Property<Boolean> getStrict() {
        return isStrict;
    }

    @Override
    public Property<Boolean> getQualifiedOutputName() {
        return hasQualifiedOutputName;
    }

    @Override
    public Property<Boolean> getVersionedOutputName() {
        return hasVersionedOutputName;
    }

    @Override
    public Property<Boolean> getForceRebuild() {
        return shouldForceRebuild;
    }

    @Override
    public MapProperty<Object, Object> getModuleFilenames() {
        return renameOutput;
    }

    public String resolveModuleFilename(final String from) {
        for (Map.Entry<Object, Object> entry : renameOutput.get().entrySet()) {
            if (provideString(entry.getKey()).equals(from)) {
                return provideString(entry.getValue());
            }
        }
        return from;
    }
}
