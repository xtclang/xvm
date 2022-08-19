/**
 * Represents explicit cookie consent information.
 *
 * @param necessary        explicitly allow "Strictly Necessary" cookies
 * @param functionality    explicitly allow "Functionality"/"Preferences" cookies
 * @param performance      explicitly allow "Performance"/"Statistics" cookies
 * @param marketing        explicitly allow "Marketing"/"Targeting" cookies
 * @param socialMedia      explicitly allow "SocialMedia" (Third Party) cookies
 * @param allowFirstParty  explicitly allow all "First Party" cookies, regardless of other settings
 * @param blockThirdParty  explicitly disallow all "Third Party" cookies, regardless of other
 *                         settings
 */
const CookieConsent(
        Boolean necessary       = False,
        Boolean functionality   = False,
        Boolean performance     = False,
        Boolean marketing       = False,
        Boolean socialMedia     = False,
        Boolean allowFirstParty = False,
        Boolean blockThirdParty = False,
        )
    {
    /**
     * Copy this `CookieConsent`, changing the specified items.
     *
     * @param necessary        (optional) explicitly allow "Strictly Necessary" cookies
     * @param functionality    (optional) explicitly allow "Functionality"/"Preferences" cookies
     * @param performance      (optional) explicitly allow "Performance"/"Statistics" cookies
     * @param marketing        (optional) explicitly allow "Marketing"/"Targeting" cookies
     * @param socialMedia      (optional) explicitly allow "SocialMedia" (Third Party) cookies
     * @param allowFirstParty  (optional) explicitly allow all "First Party" cookies, regardless of
     *                         other settings
     * @param blockThirdParty  (optional) explicitly disallow all "Third Party" cookies, regardless
     *                         of other settings
     *
     * @return a new CookieConsent
     */
    CookieConsent with(Boolean? necessary       = Null,
                       Boolean? functionality   = Null,
                       Boolean? performance     = Null,
                       Boolean? marketing       = Null,
                       Boolean? socialMedia     = Null,
                       Boolean? allowFirstParty = Null,
                       Boolean? blockThirdParty = Null,
                      )
        {
        return new CookieConsent(necessary       = necessary       ?: this.necessary,
                                 functionality   = functionality   ?: this.functionality,
                                 performance     = performance     ?: this.performance,
                                 marketing       = marketing       ?: this.marketing,
                                 socialMedia     = socialMedia     ?: this.socialMedia,
                                 allowFirstParty = allowFirstParty ?: this.allowFirstParty,
                                 blockThirdParty = blockThirdParty ?: this.blockThirdParty,
                                );
        }


    // ----- pre-built Consents --------------------------------------------------------------------

    /**
     * The default consent (no consent).
     */
    static CookieConsent None = new CookieConsent();

    /**
     * The "all" consent for first party cookies (but no consent for third party cookies).
     */
    static CookieConsent All_1st = new CookieConsent(necessary       = True,
                                                     functionality   = True,
                                                     performance     = True,
                                                     marketing       = True,
                                                     socialMedia     = True,
                                                     allowFirstParty = True,
                                                     blockThirdParty = True,
                                                    );

    /**
     * The "all" consent (including third party cookies).
     */
    static CookieConsent All = new CookieConsent(necessary       = True,
                                                 functionality   = True,
                                                 performance     = True,
                                                 marketing       = True,
                                                 socialMedia     = True,
                                                 allowFirstParty = True,
                                                 blockThirdParty = False,
                                                );


    // ----- category-based access -----------------------------------------------------------------

    /**
     * * Necessary     - explicitly allow "Strictly Necessary" cookies
     * * Functionality - explicitly allow "Functionality"/"Preferences" cookies
     * * Performance   - explicitly allow "Performance"/"Statistics" cookies
     * * Marketing     - explicitly allow "Marketing"/"Targeting" cookies
     * * SocialMedia   - explicitly allow "SocialMedia" (Third Party) cookies
     */
    enum Category(Char abbreviation)
        {
        Necessary('N'), Functionality('F'), Performance('P'), Marketing('M'), SocialMedia('S');

        /**
         * Obtain a `Category` from its one-character abbreviation.
         *
         * @param abbreviation  the one-character abbreviation of the `Category`
         *
         * @return the specified `Category`
         */
        conditional Category from(Char abbreviation)
            {
            switch(abbreviation)
                {
                case 'N': return True, Necessary;
                case 'F': return True, Functionality;
                case 'P': return True, Performance;
                case 'M': return True, Marketing;
                case 'S': return True, SocialMedia;
                default: return False;
                }
            }
        }

    /**
     * Query the CookieConsent to see if a particular `Category` is explicitly consented to.
     *
     * @param category    the category of consent
     * @param thirdParty  pass False if the cookie being checked for consent is a "first party"
     *                    cookie (same web site owner, even if the domain or path may differ), or
     *                    True if the cookie being checked for consent is a "third party" (different
     *                    web site owner) cookie
     *
     * @return `True` iff a cookie with the specified `Category` and specified third party status is
     *         consented to
     */
    Boolean allows(Category category, Boolean thirdParty=False)
        {
        if (!thirdParty && allowFirstParty)
            {
            return True;
            }

        if (thirdParty && blockThirdParty)
            {
            return False;
            }

        return switch(category)
            {
            case Necessary    : necessary;
            case Functionality: functionality;
            case Performance  : performance;
            case Marketing    : marketing;
            case SocialMedia  : socialMedia;
            };
        }

    /**
     * Add one or more categories of explicitly allowed cookies to the `CookieConsent`, producing a
     * new `CookieConsent`.
     *
     * @param category  one or more categories to add
     *
     * @return a new CookieConsent
     */
    CookieConsent allow(Category|Category[] category)
        {
        return category.is(Category[])
                ? this.with(
                    necessary     = necessary     || category.contains(Necessary),
                    functionality = functionality || category.contains(Functionality),
                    performance   = performance   || category.contains(Performance),
                    marketing     = marketing     || category.contains(Marketing),
                    socialMedia   = socialMedia   || category.contains(SocialMedia),
                    )
                : this.with(
                    necessary     = necessary     || category == Necessary,
                    functionality = functionality || category == Functionality,
                    performance   = performance   || category == Performance,
                    marketing     = marketing     || category == Marketing,
                    socialMedia   = socialMedia   || category == SocialMedia,
                    );
        }

    /**
     * Remove one or more categories of explicitly allowed cookies from the `CookieConsent`,
     * producing a new `CookieConsent`.
     *
     * @param category  one or more categories to remove consent for
     *
     * @return a new CookieConsent
     */
    CookieConsent block(Category|Category[] category)
        {
        return category.is(Category[])
                ? this.with(
                    necessary     = necessary     && !category.contains(Necessary),
                    functionality = functionality && !category.contains(Functionality),
                    performance   = performance   && !category.contains(Performance),
                    marketing     = marketing     && !category.contains(Marketing),
                    socialMedia   = socialMedia   && !category.contains(SocialMedia),
                    )
                : this.with(
                    necessary     = necessary     && category != Necessary,
                    functionality = functionality && category != Functionality,
                    performance   = performance   && category != Performance,
                    marketing     = marketing     && category != Marketing,
                    socialMedia   = socialMedia   && category != SocialMedia,
                    );
        }


    // ----- compact persistent form ---------------------------------------------------------------

    /**
     * Parse the format of the [toString] method to create a `CookieConsent` instance that would
     * create the same result from a call to its `toString` method.
     *
     * @param text  the result from a previous call to [CookieConsent.toString()](toString).
     *
     * @return True iff the text was successfully parsed
     * @return (conditional) the CookieConsent that matches the parsed information
     */
    static conditional CookieConsent fromString(String text)
        {
        import ecstasy.collections.CaseInsensitive;

        if (text == "" || CaseInsensitive.areEqual(text, "None"))
            {
            return True, None;
            }

        Boolean necessary       = False;
        Boolean functionality   = False;
        Boolean performance     = False;
        Boolean marketing       = False;
        Boolean socialMedia     = False;
        Boolean allowFirstParty = False;
        Boolean blockThirdParty = False;

        for (String part : text.split(','))
            {
            switch (part.trim())
                {
                case "N"      : necessary       = True; break;
                case "F"      : functionality   = True; break;
                case "P"      : performance     = True; break;
                case "M"      : marketing       = True; break;
                case "S"      : socialMedia     = True; break;
                case "All_1st": allowFirstParty = True; break;
                case "No_3rd" : blockThirdParty = True; break;

                default:
                    return False;
                }
            }

        return True, new CookieConsent(necessary, functionality, performance, marketing, socialMedia,
                allowFirstParty, blockThirdParty);
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer();

        for (Category category : Category.values)
            {
            if (allows(category))
                {
                if (buf.size > 0)
                    {
                    buf.add(',');
                    }
                buf.add(category.abbreviation);
                }
            }

        if (allowFirstParty)
            {
            if (buf.size > 0)
                {
                buf.add(',');
                }
            "All_1st".appendTo(buf);
            }

        if (blockThirdParty)
            {
            if (buf.size > 0)
                {
                buf.add(',');
                }
            "No_3rd".appendTo(buf);
            }

        return buf.size == 0 ? "None" : buf.toString();
        }
    }
