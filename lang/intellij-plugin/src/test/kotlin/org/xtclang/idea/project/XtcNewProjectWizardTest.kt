package org.xtclang.idea.project

import org.xvm.tool.XtcProjectCreator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the XTC New Project Wizard.
 */
class XtcNewProjectWizardTest {

    @Test
    fun wizardProperties() {
        val wizard = XtcNewProjectWizard()
        assertEquals("XTC", wizard.id)
        assertEquals("XTC", wizard.name)
        assertTrue(wizard.isEnabled())
        assertNotNull(wizard.icon)
    }

    @Test
    fun projectTypeNames() {
        // getName() returns lowercase names used for display/CLI
        assertEquals("application", XtcProjectCreator.ProjectType.APPLICATION.getName())
        assertEquals("library", XtcProjectCreator.ProjectType.LIBRARY.getName())
        assertEquals("service", XtcProjectCreator.ProjectType.SERVICE.getName())
    }

    @Test
    fun projectTypeFromString() {
        assertEquals(XtcProjectCreator.ProjectType.APPLICATION, XtcProjectCreator.ProjectType.fromString("app"))
        assertEquals(XtcProjectCreator.ProjectType.LIBRARY, XtcProjectCreator.ProjectType.fromString("lib"))
        assertEquals(XtcProjectCreator.ProjectType.SERVICE, XtcProjectCreator.ProjectType.fromString("svc"))
        assertEquals(XtcProjectCreator.ProjectType.APPLICATION, XtcProjectCreator.ProjectType.fromString(null))
    }
}
