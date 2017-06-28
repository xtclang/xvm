package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xClock
        extends ClassTemplate
    {
    public static xClock INSTANCE;

    public xClock(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        //    @ro DateTime epoch;
        //    @ro TimeZone timezone;
        //    @ro Interval precision;
        //    @ro Boolean monotonic;
        //    @ro Boolean realtime;
        //
        //    @ro Time time;
        //
        //    Timer createTimer();
        //
        //    Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp);
        }
    }
