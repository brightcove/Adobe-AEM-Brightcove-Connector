/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2017 Coresecure Inc.

    Authors:    Alessandro Bonfatti
                Yan Kisen
                Pablo Kropilnicki

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    - Additional permission under GNU GPL version 3 section 7
    If you modify this Program, or any covered work, by linking or combining
    it with httpclient 4.1.3, httpcore 4.1.4, httpmine 4.1.3, jsoup 1.7.2,
    squeakysand-commons and squeakysand-osgi (or a modified version of those
    libraries), containing parts covered by the terms of APACHE LICENSE 2.0
    or MIT License, the licensors of this Program grant you additional
    permission to convey the resulting work.

*/

package com.coresecure.brightcove.wrapper.sling;

import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;

@Component(immediate = true,
        label = "Brightcove Service",
        description = "Brightcove Service Configuration",
        name = "com.coresecure.brightcove.wrapper.sling.BrcServiceImpl",
        metatype = true,
        configurationFactory = true,
        policy = ConfigurationPolicy.REQUIRE
)
@Service(value = ConfigurationService.class)
@Properties({
        @Property(name = "accountAlias", label = "Account Alias", description = "Text alias for Account ID", value = ""),
        @Property(name = "key", label = "Account ID", description = "CMS API Account ID", value = ""),
        @Property(name = "client_id", label = "Client ID", description = "CMS API Client ID", value = ""),
        @Property(name = "client_secret", label = "Client Secret", description = "CMS API Client Secret", value = ""),
        @Property(name = "allowed_groups", label = "Allowed Groups", description = "Groups that are allowed to see this account data", value = {"", ""}),
        @Property(name = "playersstore", label = "Players Store Path", description = "Path of the players store locatione", value = "/content/brightcovetools/players"),
        @Property(name = "defVideoPlayerID", label = "Default Video Player ID", description = "Default Video Player ID", value = "default"),
        @Property(name = "defVideoPlayerKey", label="Default Video Player Key", description="Default Video Player Key - DEPRECATED", value=""),
        @Property(name = "defPlaylistPlayerID", label = "Default Playlist Player ID", description = "Default Playlist Player ID", value = "default"),
        @Property(name = "defPlaylistPlayerKey", label = "Default Playlist Player Key", description = "Default Playlist Player Key - DEPRECATED", value = ""),
        @Property(name = "proxy", label = "Proxy server", description = "Proxy server in the form proxy.foo.com:3128", value = {""}),
        @Property(name = "asset_integration_path", label = "Dam Integration Path", description = "Remote Asset Metadata Storage Path", value = "/content/dam/brightcove_assets")
})


public class ConfigurationServiceImpl implements ConfigurationService {
    private ComponentContext componentContext;
    private static Logger loggerVar = LoggerFactory.getLogger(ConfigurationService.class);
    private static final String ALGO = "AES";
    private Dictionary<String, Object> prop;

    private Dictionary<String, Object> getProperties() {
        if (prop == null)
            return new Hashtable<String, Object>();
        return prop;
    }

    @Activate
    void activate(ComponentContext aComponentContext) {
        this.componentContext = aComponentContext;
        this.prop = componentContext.getProperties();
    }

    public String getClientID() {
        return (String) getProperties().get("client_id");
    }

    public String getAssetIntegrationPath() {
        return (String) getProperties().get("asset_integration_path");
    }

    public String getClientSecret() {
        return (String) getProperties().get("client_secret");
    }

    public String getAccountID() {
        return (String) getProperties().get("key");
    }

    public String getPlayersLoc() {
        return (String) getProperties().get("playersstore");
    }

    public String getDefVideoPlayerID() {
        return (String) getProperties().get("defVideoPlayerID");
    }

    public String getDefVideoPlayerKey() {
        return (String) getProperties().get("defVideoPlayerKey");
    }

    public String getDefVideoPlayerDataEmbedded() {
        return "default";
    }

    public String getDefPlaylistPlayerID() {
        return (String) getProperties().get("defPlaylistPlayerID");
    }

    public String getDefPlaylistPlayerKey() {
        return (String) getProperties().get("defPlaylistPlayerKey");
    }

    public String getAccountAlias() {
        return (String) getProperties().get("accountAlias");
    }

    public String[] getAllowedGroups() {
        Object p = getProperties().get("allowed_groups");
        if (p == null) return new String[0];
        if (p instanceof String && ((String) p).trim().length() > 0) {
            return new String[]{((String) p).trim()};
        }

        if (p instanceof String[]) {
            return cleanStringArray((String[]) p);
        }
        return new String[0];
    }

    public List<String> getAllowedGroupsList() {
        return Arrays.asList(getAllowedGroups());
    }


    public String getProxy() {
        return (String) getProperties().get("proxy");
    }

    private String[] cleanStringArray(String[] input) {
        String[] result = input;
        List<String> list = new ArrayList<String>();

        for (String s : input) {
            if (s != null && s.trim().length() > 0) {
                list.add(s.trim());
            }
        }
        result = list.toArray(new String[list.size()]);
        return result;
    }


    private boolean isValidPath(String filePathString){
        Path path = Paths.get(filePathString);
        return Files.exists(path);
    }
}
