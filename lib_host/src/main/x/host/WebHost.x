/**
 * AppHost for a Web module.
 */
class WebHost(String moduleName, Directory homeDir, String appRealm)
        extends platform.AppHost(moduleName, homeDir, appRealm)
    {
    @Override
    Boolean isWeb.get()
        {
        return True;
        }
    }