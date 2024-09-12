package org.xvm.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class XtcCommandTest
    {
    @Test
    public void shouldExecuteBuildCommand()
        {
        AbstractCommand buildCommand = mockLauncher("build");
        XtcCommand      launcher     = new XtcCommandStub()
            {
            @Override
            protected AbstractCommand createBuildCommand()
                {
                return buildCommand;
                }
            };

        launcher.run("build", List.of("one", "two"));
        verify(buildCommand).run(List.of("one", "two"));
        }

    @Test
    public void shouldExecuteRunCommand()
        {
        AbstractCommand runCommand = mockLauncher("run");
        XtcCommand      launcher   = new XtcCommandStub()
            {
            @Override
            protected AbstractCommand createRunCommand()
                {
                return runCommand;
                }
            };

        launcher.run("run", List.of("one", "two"));
        verify(runCommand).run(List.of("one", "two"));
        }

    @Test
    public void shouldExecuteTestCommand()
        {
        AbstractCommand testCommand = mockLauncher("test");
        XtcCommand      launcher    = new XtcCommandStub()
            {
            @Override
            protected AbstractCommand createTestCommand()
                {
                return testCommand;
                }
            };

        launcher.run("test", List.of("one", "two"));
        verify(testCommand).run(List.of("one", "two"));
        }

    @Test
    public void shouldExecuteInfoCommand()
        {
        AbstractCommand infoCommand = mockLauncher("info");
        XtcCommand      launcher    = new XtcCommandStub()
            {
            @Override
            protected AbstractCommand createInfoCommand()
                {
                return infoCommand;
                }
            };

        launcher.run("info", List.of("one", "two"));
        verify(infoCommand).run(List.of("one", "two"));
        }

    @Test
    public void shouldDisplayHelpForInvalidCommandName()
        {
        XtcCommand launcher = spy(new XtcCommandStub());
        launcher.run("foo", List.of("one", "two"));
        verify(launcher).displayHelp();
        }

    
    @SuppressWarnings("unchecked")
    protected static AbstractCommand mockLauncher(String name)
        {
        AbstractCommand launcher = mock(AbstractCommand.class);
        doReturn(name).when(launcher).name();
        doReturn(launcher).when(launcher).findSubCommand(any(Queue.class));
        return launcher;
        }

    static class XtcCommandStub
            extends XtcCommand
        {
        @Override
        protected AbstractCommand createBuildCommand()
            {
            return mockLauncher("build");
            }

        @Override
        protected AbstractCommand createRunCommand()
            {
            return mockLauncher("run");
            }

        @Override
        protected AbstractCommand createTestCommand()
            {
            return mockLauncher("test");
            }

        @Override
        protected AbstractCommand createInfoCommand()
            {
            return mockLauncher("info");
            }
        }
    }
