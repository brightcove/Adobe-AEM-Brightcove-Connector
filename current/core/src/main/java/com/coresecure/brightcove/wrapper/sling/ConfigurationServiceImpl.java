/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2018 Coresecure Inc.

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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component(service = ConfigurationService.class, immediate = true)
@Designate(ocd = ConfigurationServiceImpl.Config.class, factory = true)
public class ConfigurationServiceImpl implements ConfigurationService {

    @ObjectClassDefinition(name = "Brightcove Service", description = "Brightcove Service Configuration")
	public static @interface Config {

        @AttributeDefinition(name = "Account Alias", description = "Text alias for the account ID.")
		String accountAlias() default "";

        @AttributeDefinition(name = "Account ID", description = "CMS API account ID.")
		String accountId() default "";

        @AttributeDefinition(name = "Client ID", description = "CMS API client ID.")
		String clientId() default "";

        @AttributeDefinition(name = "Client Secret", description = "CMS API client secret.")
		String clientSecret() default "";

		@AttributeDefinition(name = "Allowed Groups", description = "Groups that are allowed to see this account data.")
		String[] allowedGroups() default {""};

		@AttributeDefinition(name = "Player Store Path", description = "Path of the players store location.")
		String playerStorePath() default "/content/brightcovetools/players";

		@AttributeDefinition(name = "Default Video Player ID", description = "Default Video Player ID")
		String defaultVideoPlayerId() default "default";

		@AttributeDefinition(name = "Default Video Player Key", description = "Default Video Player Key")
		String defaultVideoPlayerKey() default "";

        @AttributeDefinition(name = "Default Playlist Player ID", description = "Default Playlist Player ID")
		String defaultPlaylistPlayerId() default "default";

		@AttributeDefinition(name = "Default Playlist Player Key", description = "Default Playlist Player Key")
		String defaultPlaylistPlayerKey() default "";

		@AttributeDefinition(name = "Proxy Server", description = "Proxy server in the form proxy.foo.com:3128")
		String proxyServer() default "";

		@AttributeDefinition(name = "DAM Integration Path", description = "Remote Asset Metadata Storage Path")
		String damIntegrationPath() default "/content/dam/brightcove_assets";

		@AttributeDefinition(name = "Default Ingest Profile", description = "Default ingestion profile to use for videos.")
		String defaultIngestProfile() default "";
	}

    private static Logger loggerVar = LoggerFactory.getLogger(ConfigurationService.class);
    private static final String ALGO = "AES";
    private Config config;

    @Activate
    @Modified
    void activate(final Config config) {
        loggerVar.info("activate");
        this.config = config;
    }

    @Deactivate
    void deactivate(final Config config) {
        loggerVar.info("deactivate");
    }


    public String getClientID() {
        return this.config.clientId();
    }

    public String getAssetIntegrationPath() {
        return this.config.damIntegrationPath();
    }

    public String getClientSecret() {
        return this.config.clientSecret();
    }

    public String getAccountID() {
        return this.config.accountId();
    }

    public String getPlayersLoc() {
        return this.config.playerStorePath();
    }

    public String getDefVideoPlayerID() {
        return this.config.defaultVideoPlayerId();
    }

    public String getDefVideoPlayerKey() {
        return this.config.defaultVideoPlayerKey();
    }

    public String getDefVideoPlayerDataEmbedded() {
        return "default";
    }

    public String getDefPlaylistPlayerID() {
        return this.config.defaultPlaylistPlayerId();
    }

    public String getDefPlaylistPlayerKey() {
        return this.config.defaultPlaylistPlayerKey();
    }

    public String getAccountAlias() {
        return this.config.accountAlias();
    }

    public String[] getAllowedGroups() {
        Object p = this.config.allowedGroups();
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
        String proxy = this.config.proxyServer();
        loggerVar.debug("getProxy() " + proxy);
        return proxy;
    }

    public String getIngestProfile() {
        return this.config.defaultIngestProfile();
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


    private boolean isValidPath(String filePathString) {
        Path path = Paths.get(filePathString);
        return Files.exists(path);
    }
}
