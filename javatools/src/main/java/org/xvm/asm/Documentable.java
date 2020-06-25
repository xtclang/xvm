package org.xvm.asm;



/**
 * Represents XVM structures that can contain their own documentation.
 */
public interface Documentable
    {
    /**
     * Obtain the documentation for the item.
     *
     * @return the documentation, or null for no documentation
     */
    String getDocumentation();

    /**
     * Specify the documentation for the item.
     *
     * @param sDoc  the documentation, or null to indicate no documentation
     */
    void setDocumentation(String sDoc);
    }
