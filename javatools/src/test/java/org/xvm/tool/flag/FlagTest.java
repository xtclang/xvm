package org.xvm.tool.flag;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@SuppressWarnings("ConstantValue")
public class FlagTest
    {
    @Test
    public void shouldCreateFlag()
        {
        String        name         = "foo";
        char          shortcut     = 'f';
        String        usage        = "testing...";
        BooleanValue  value        = new BooleanValue();
        BooleanValue  dfltValue    = new BooleanValue();
        BooleanValue  noOptValue   = new BooleanValue();
        boolean       hidden       = false;
        boolean       passThru     = false;
        String        passThruName = null;

        Flag<Boolean> flag    = new Flag<>(name, shortcut, usage, value, dfltValue, noOptValue,
                hidden, passThru, passThruName);

        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(sameInstance(value)));
        assertThat(flag.getDefaultValue(), is(sameInstance(dfltValue)));
        assertThat(flag.getNoArgDefault(), is(sameInstance(noOptValue)));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(passThruName));
        }

    @Test
    public void shouldCreateFlagWithNoShorthand()
        {
        String        name         = "foo";
        char          shortcut     = Flag.NO_SHORTHAND;
        String        sUsage       = "testing...";
        BooleanValue  value        = new BooleanValue();
        BooleanValue  dfltValue    = new BooleanValue();
        BooleanValue  noOptValue   = new BooleanValue();
        boolean       hidden       = false;
        boolean       passThru     = false;
        String        passThruName = null;

        Flag<Boolean> flag       = new Flag<>(name, shortcut, sUsage, value, dfltValue, noOptValue, 
                hidden, passThru, passThruName);

        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(false));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(sUsage));
        assertThat(flag.getValue(), is(sameInstance(value)));
        assertThat(flag.getDefaultValue(), is(sameInstance(dfltValue)));
        assertThat(flag.getNoArgDefault(), is(sameInstance(noOptValue)));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(passThruName));
        }

    @Test
    public void shouldCreateHiddenFlag()
        {
        String        name         = "foo";
        char          shortcut     = 'f';
        String        sUsage       = "testing...";
        BooleanValue  value        = new BooleanValue();
        BooleanValue  dfltValue    = new BooleanValue();
        BooleanValue  noOptValue   = new BooleanValue();
        boolean       hidden       = true;
        boolean       passThru     = false;
        String        passThruName = null;

        Flag<Boolean> flag       = new Flag<>(name, shortcut, sUsage, value, dfltValue, 
                noOptValue, hidden, passThru, passThruName);

        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(sUsage));
        assertThat(flag.getValue(), is(sameInstance(value)));
        assertThat(flag.getDefaultValue(), is(sameInstance(dfltValue)));
        assertThat(flag.getNoArgDefault(), is(sameInstance(noOptValue)));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(passThruName));
        }

    @Test
    public void shouldPassThruCreateFlag()
        {
        String        name         = "foo";
        char          shortcut     = 'f';
        String        usage        = "testing...";
        BooleanValue  value        = new BooleanValue();
        BooleanValue  dfltValue    = new BooleanValue();
        BooleanValue  noOptValue   = new BooleanValue();
        boolean       hidden       = false;
        boolean       passThru     = true;

        Flag<Boolean> flag    = new Flag<>(name, shortcut, usage, value, dfltValue, noOptValue,
                hidden, passThru, null);

        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(sameInstance(value)));
        assertThat(flag.getDefaultValue(), is(sameInstance(dfltValue)));
        assertThat(flag.getNoArgDefault(), is(sameInstance(noOptValue)));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(name));
        }

    @Test
    public void shouldPassThruCreateFlagWithPassThruName()
        {
        String        name         = "foo";
        char          shortcut     = 'f';
        String        usage        = "testing...";
        BooleanValue  value        = new BooleanValue();
        BooleanValue  dfltValue    = new BooleanValue();
        BooleanValue  noOptValue   = new BooleanValue();
        boolean       hidden       = false;
        boolean       passThru     = true;
        String        passThruName = "bar";

        Flag<Boolean> flag    = new Flag<>(name, shortcut, usage, value, dfltValue, noOptValue,
                hidden, passThru, passThruName);

        assertThat(flag.getName(), is(name));
        assertThat(flag.hasShorthand(), is(true));
        assertThat(flag.getShorthand(), is(shortcut));
        assertThat(flag.getUsage(), is(usage));
        assertThat(flag.getValue(), is(sameInstance(value)));
        assertThat(flag.getDefaultValue(), is(sameInstance(dfltValue)));
        assertThat(flag.getNoArgDefault(), is(sameInstance(noOptValue)));
        assertThat(flag.isHidden(), is(hidden));
        assertThat(flag.isPassThru(), is(passThru));
        assertThat(flag.getPassThruName(), is(passThruName));
        }
    }
