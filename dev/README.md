A sample application script that can be reset to various source file anywhere on your hard drive, and then 
compiled and debugged.

## Local process mode:

Requires "fork = false" in the xtcCompile and/or xtcRun configurations. This will make the plugin
launch xec or xtc in the current process.  

0) Make sure your program won't hit any interaction, such as assert:debug, because Gradle has swallowed stdin 
completely. Working on an "interactive = true". However, the strength behind this config is that you can put
breakpoints everywhere in the IDE. 

1) Put a breakpoint anywhere in the Java launcher implementation in IntelliJ (e.g. the Runner or the Compiler, maybe
its process method). Also put a breakpoint somewhere else in the build, for example in a script, or in the Plugin
code proper. I recommend XtcCompileTask::compile and XtcRunTask::run, which are executed when the project configuration
has been processed, locked down, resolved and we are in the execution phase. 

2) To debug the compiler, expand the Gradle view and navigate to the build tasks. ("xvm -> xvm (root) -> dev -> Tasks -> build -> build")
Right click the build task, hit debug, and watch your breakpoints, both in and out of the build get hit when the compile job runs.

3) To debug the runner, expand the Gradle view and navigate to the build tasks. ("xvm -> xvm (root) -> dev -> Tasks -> application -> runXtc") 
Do as you did in 2.

## Fork mode:

(The default. Make sure that "fork = false" for both the xtcCompile and xtcRun configuration, or not defined).

0) Add an assert:break somewhere 

Do as in 2) and 3) above.  Note that your breakpoints inside the launchers won't trigger, because it runs in a separate process.
(Of course you can debug this with socket as per usual: https://stackoverflow.com/questions/37702073/gradle-remote-debugging-process), which
I suggest that you try later. 

When the assert hits, your console window in IDE will show you the debugger as usual. Do some XTC debugging. When satisfied,
look for the "quit" command and notice that it doesn't exist. Since we are in one single threaded execution, there is not much
more fun to be had here, and no way to complete the build after this. I assume that Gene and Cam will fix.

## Lifecycle

The XTC plugin follows the standard JVM language style augmented life cycle. Source is expected to go into:
"src/main/x", resources into "src/main/resorces" (or test instead of main, currently not in use, for test source).

Gradle/Maven life cycle: https://docs.gradle.org/current/userguide/build_lifecycle.html]
Gradle/Maven JVM language life cycle: https://docs.gradle.org/current/userguide/building_java_projects.html

To see how tasks are interdependent, use the "taskTree" source, and follow its instructions. 

The source sets are compiled by compileXtc/compileXtc<SourceSetName> tasks. 
Programs get run with the runXtc/runXtc<SourceSetName> tasks.

The runner tasks depend on their compile tasks and other resource processing. 

If you want to run every module in the source set, use the xtcRunAll tasks.

