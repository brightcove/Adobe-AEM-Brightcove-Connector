package com.coresecure.brightcove.wrapper.webservices;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.TextUtil;

/**
 * The Brightcove accounts visible to the calling user: every configured account whose
 * {@code allowedGroups} intersects the user's group membership, in configuration order.
 *
 * <p>Single source of truth for both {@code /bin/brightcove/accounts} ({@link BrcAccounts})
 * and the Granite datasource at {@code /bin/accounts/exports}
 * ({@code servlets.AccountsDataSourceServlet}). The datasource used to obtain this list by
 * issuing an internal Sling request to the JSON servlet through
 * {@code org.apache.sling.api.request.builder.Builders}, which only exists from Sling API
 * 2.24 (AEM 6.5 service packs / AEMaaCS) and so blocked the on-prem floor. Calling the
 * shared logic directly removes both the extra request and the dependency.
 * Context: ONPREM-PARITY-PLAN.md §3 Phase 2 step 1.</p>
 */
public final class AccountsList {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountsList.class);

    private AccountsList() {
    }

    /** One selectable account. */
    public static final class Option {
        private final String text;
        private final String value;
        private final int id;

        Option(String text, String value, int id) {
            this.text = text;
            this.value = value;
            this.id = id;
        }

        /** Display label: {@code "<alias> [<accountId>]"}, or the bare account id when no alias is set. */
        public String getText() {
            return text;
        }

        /** The Brightcove account id. */
        public String getValue() {
            return value;
        }

        /** Zero-based position among the accounts visible to this user. */
        public int getId() {
            return id;
        }
    }

    /**
     * Lists the accounts the resolver's user may see.
     *
     * @param resourceResolver the request's resolver (its user is the caller)
     * @return the visible accounts, possibly empty; {@code null} only when the user cannot be
     *         resolved at all (the caller reports 403 in that case)
     * @throws RepositoryException if group membership cannot be read
     */
    public static List<Option> forUser(ResourceResolver resourceResolver) throws RepositoryException {
        Session session = resourceResolver.adaptTo(Session.class);
        UserManager userManager = resourceResolver.adaptTo(UserManager.class);
        if (session == null || userManager == null) {
            return null;
        }
        Authorizable auth = userManager.getAuthorizable(session.getUserID());
        if (auth == null) {
            return null;
        }
        List<String> memberOf = new ArrayList<String>();
        Iterator<Group> groups = auth.memberOf();
        while (groups.hasNext()) {
            memberOf.add(groups.next().getID());
        }

        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
        List<Option> result = new ArrayList<Option>();
        int i = 0;
        for (String account : cg.getAvailableServices()) {
            LOGGER.debug("get account: {}", account);
            ConfigurationService cs = cg.getConfigurationService(account);
            List<String> allowedGroups = new ArrayList<String>(cs.getAllowedGroupsList());
            allowedGroups.retainAll(memberOf);
            if (allowedGroups.isEmpty()) {
                continue;
            }
            String optionText = account;
            String alias = cs.getAccountAlias();
            if (TextUtil.notEmpty(alias)) {
                optionText = String.format("%s [%s]", alias, account);
            }
            result.add(new Option(optionText, account, i++));
        }
        return result;
    }
}
