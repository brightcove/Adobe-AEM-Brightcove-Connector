package com.coresecure.brightcove.wrapper.sling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a Brightcove account configuration written for the on-prem 6.0.x connector and exposes
 * it as a {@link ConfigurationService}, so an in-place upgrade from 6.0.x keeps its accounts.
 *
 * <p>The 6.0.x connector declared its factory component as
 * {@code com.coresecure.brightcove.wrapper.sling.BrcServiceImpl} with snake_case property keys
 * ({@code key}, {@code client_id}, {@code client_secret}, {@code allowed_groups},
 * {@code asset_integration_path}, …). The current {@link ConfigurationServiceImpl} uses the class
 * name as its PID and camelCase keys, and OSGi silently ignores a configuration under an unknown
 * PID. Measured on 2026-09-17: with only the current component present, an upgraded 6.0.12
 * instance answered {@code /bin/brightcove/accounts} with {@code []} and every account was gone
 * (ONPREM-PARITY-PLAN.md §1.3 and §3 Phase 4).</p>
 *
 * <p>This component binds the legacy PID and maps each legacy key onto the interface. It logs
 * one WARN per account on activation so the migration is visible; when a current-format
 * configuration exists for the same account id, {@link ConfigurationGrabberImpl} prefers that
 * one. ⚠️ Do not add features here: new settings go on {@link ConfigurationServiceImpl} only, and
 * a legacy account that needs them must be migrated.</p>
 */
@Component(service = ConfigurationService.class, immediate = true,
        configurationPid = LegacyConfigurationServiceImpl.LEGACY_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = LegacyConfigurationServiceImpl.LegacyConfig.class, factory = true)
public class LegacyConfigurationServiceImpl implements ConfigurationService {

    /** The 6.0.x factory PID. Never rename: it is the whole point of this class. */
    public static final String LEGACY_PID = "com.coresecure.brightcove.wrapper.sling.BrcServiceImpl";

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyConfigurationServiceImpl.class);

    /**
     * Attribute ids must equal the 6.0.x property names. ⚠️ OSGi component-property-type naming
     * (DS spec 112.8.2.1): a SINGLE underscore in a method name becomes a DOT in the property
     * name, so {@code client_id()} would read {@code client.id} and silently get the default.
     * A DOUBLE underscore becomes one literal underscore. Measured 2026-09-17: with single
     * underscores the account id ({@code key}) resolved but every other value was empty, so the
     * group filter dropped the account and {@code /bin/brightcove/accounts} returned {@code []}.
     * {@code LegacyConfigurationServiceImplTest} pins the generated attribute ids.
     */
    @ObjectClassDefinition(name = "Brightcove Service (legacy 6.0.x configuration)",
            description = "Read-only compatibility view of a configuration written for the on-prem 6.0.x connector. "
                    + "Migrate it to \"Brightcove Service\" (ConfigurationServiceImpl); new settings are not available here.")
    public @interface LegacyConfig {
        @AttributeDefinition(name = "Account Alias")
        String accountAlias() default "";

        @AttributeDefinition(name = "Account ID (legacy key)")
        String key() default "";

        @AttributeDefinition(name = "Client ID (legacy client_id)")
        String client__id() default "";

        @AttributeDefinition(name = "Client Secret (legacy client_secret)")
        String client__secret() default "";

        @AttributeDefinition(name = "Allowed Groups (legacy allowed_groups)")
        String[] allowed__groups() default {};

        @AttributeDefinition(name = "Players Store Path (legacy playersstore)")
        String playersstore() default "/content/brightcovetools/players";

        @AttributeDefinition(name = "Default Video Player ID")
        String defVideoPlayerID() default "default";

        @AttributeDefinition(name = "Default Video Player Key")
        String defVideoPlayerKey() default "";

        @AttributeDefinition(name = "Default Playlist Player ID")
        String defPlaylistPlayerID() default "default";

        @AttributeDefinition(name = "Default Playlist Player Key")
        String defPlaylistPlayerKey() default "";

        @AttributeDefinition(name = "Proxy server (legacy proxy)")
        String proxy() default "";

        @AttributeDefinition(name = "DAM Integration Path (legacy asset_integration_path)")
        String asset__integration__path() default "/content/dam/brightcove_assets";

        @AttributeDefinition(name = "Default Ingest Profile (legacy ingest_profile)")
        String ingest__profile() default "";
    }

    private LegacyConfig config;

    @Activate
    @Modified
    void activate(final LegacyConfig config) {
        this.config = config;
        LOGGER.warn("Brightcove account '{}' ({}) is configured under the legacy factory PID {} (snake_case keys). "
                + "It works, but new settings are unavailable until it is recreated under "
                + "com.coresecure.brightcove.wrapper.sling.ConfigurationServiceImpl with camelCase keys. "
                + "See docs/onprem-upgrade-6.0-to-7.md.",
                config.accountAlias(), config.key(), LEGACY_PID);
    }

    @Override
    public boolean isLegacy() {
        return true;
    }

    public String getClientID() {
        return config.client__id();
    }

    public String getAssetIntegrationPath() {
        return config.asset__integration__path();
    }

    public String getClientSecret() {
        return config.client__secret();
    }

    public String getAccountID() {
        return config.key();
    }

    public String getPlayersLoc() {
        return config.playersstore();
    }

    public String getDefVideoPlayerID() {
        return config.defVideoPlayerID();
    }

    public String getDefVideoPlayerKey() {
        return config.defVideoPlayerKey();
    }

    public String getDefVideoPlayerDataEmbedded() {
        return "default";
    }

    public String getDefPlaylistPlayerID() {
        return config.defPlaylistPlayerID();
    }

    public String getDefPlaylistPlayerKey() {
        return config.defPlaylistPlayerKey();
    }

    public String getAccountAlias() {
        return config.accountAlias();
    }

    public String[] getAllowedGroups() {
        String[] groups = config.allowed__groups();
        if (groups == null) {
            return new String[0];
        }
        List<String> clean = new ArrayList<String>();
        for (String g : groups) {
            if (g != null && g.trim().length() > 0) {
                clean.add(g.trim());
            }
        }
        return clean.toArray(new String[clean.size()]);
    }

    public List<String> getAllowedGroupsList() {
        return Arrays.asList(getAllowedGroups());
    }

    public String getProxy() {
        return config.proxy();
    }

    public String getIngestProfile() {
        return config.ingest__profile();
    }

    /** 6.0.x had no tag-include setting. */
    public String getTagInclude() {
        return "";
    }
}
