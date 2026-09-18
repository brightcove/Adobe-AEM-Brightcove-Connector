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
package com.coresecure.brightcove.wrapper.utils;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a DAM asset's Brightcove folder in step with the DAM folder it lives in.
 *
 * Context: current/docs/core-folder-sync.md (the three publish paths, why the
 * account-root guard exists, and what the retry is for).
 *
 * <p>There are three code paths that push an asset to Brightcove and each has to do
 * this: {@code BrightcovePublishListener} (the DAM publish event),
 * {@code BrightcoveSyncAssetWorkflowStep} (the workflow step), and
 * {@code BrcReplicationHandler} (the classic replication agent at
 * {@code /etc/replication/agents.author/brightcove}). The logic lived in two
 * copy-pasted private methods and was missing entirely from the third, which is
 * ONPREM-PARITY-PLAN.md §3 Phase 3 item 2: on-prem fixed the replication path in
 * {@code 1911f57} and the fix never reached this line.</p>
 */
public final class FolderSyncUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FolderSyncUtil.class);

    public static final String BRC_FOLDER_ID = "brc_folder_id";

    /**
     * Retry delay for the workflow step. It runs on a workflow thread, so it can
     * afford to wait for a concurrent publish to finish creating the folder.
     */
    public static final long WORKFLOW_RETRY_DELAY_SECONDS = 15L;

    /**
     * No delay. ⚠️ Both the publish listener and the replication transport handler run
     * on shared infrastructure threads (OSGi EventAdmin delivery and the replication
     * agent queue), and must not block them.
     */
    public static final long NO_RETRY_DELAY = 0L;

    private FolderSyncUtil() {
    }

    /**
     * Make the video's Brightcove folder match the DAM folder the asset sits in,
     * creating the Brightcove folder if the DAM folder has not been synced yet.
     *
     * <p>Never throws: a folder-sync failure must not fail the publish that just
     * succeeded.</p>
     *
     * @param serviceUtil        account-scoped Brightcove client
     * @param videoId            the Brightcove video id the asset was just pushed to
     * @param assetNode          the asset node, whose PARENT is the folder that matters
     * @param retryDelaySeconds  seconds to wait before the one retry, or
     *                           {@link #NO_RETRY_DELAY} to retry immediately
     */
    public static void syncFolder(ServiceUtil serviceUtil, String videoId, Node assetNode,
                                  long retryDelaySeconds) {
        try {
            Node parentNode = assetNode.getParent();
            LOG.trace("CHECKING PARENT FOR BRC_FOLDER_ID: {}", parentNode.getPath());

            // Already-synced subfolder: just move the video. No account-root check is
            // needed on this branch, because an account root never carries
            // brc_folder_id, and moving into a folder that already exists cannot
            // create a spurious one.
            if (parentNode.hasProperty(BRC_FOLDER_ID)) {
                moveToKnownFolder(serviceUtil, parentNode, videoId);
                return;
            }

            // From here on the helper would CREATE a Brightcove folder, which is the
            // branch the account-root guard exists for. ⚠️ Three states, not two: a
            // configuration lookup that cannot answer is not the same as "this is not
            // the account root", and treating it as the latter is what creates a
            // spurious folder named after the account id.
            Boolean accountRoot = isAccountRoot(parentNode);
            if (accountRoot == null) {
                LOG.warn("Cannot tell whether {} is an account root (configuration "
                        + "unavailable); skipping Brightcove folder creation rather than "
                        + "risking a folder named after the account", parentNode.getPath());
                return;
            }
            if (accountRoot) {
                return;
            }

            String folderId = serviceUtil.createFolder(parentNode.getName());
            if (isUsable(folderId)) {
                recordFolderIdAndMove(serviceUtil, parentNode, videoId, folderId);
                return;
            }

            LOG.error("*************************** No folder created ***************************");
            if (retryDelaySeconds > NO_RETRY_DELAY) {
                TimeUnit.SECONDS.sleep(retryDelaySeconds);
            }
            // Re-read the parent from the persistent store in case a concurrent publish
            // created the folder. ⚠️ keepChanges=true: in Oak refresh() is session-wide, so
            // refresh(false) would discard pending metadata (e.g. BRC_ID) set before this
            // call, leaving the BGS-1705 marker commit to persist brc_lastsync with no
            // brc_id, which routes the next publish to updateVideo with a null id.
            parentNode.refresh(true);

            if (parentNode.hasProperty(BRC_FOLDER_ID)) {
                moveToKnownFolder(serviceUtil, parentNode, videoId);
                return;
            }
            folderId = serviceUtil.createFolder(parentNode.getName());
            if (isUsable(folderId)) {
                recordFolderIdAndMove(serviceUtil, parentNode, videoId, folderId);
            } else {
                LOG.error("*************************** No folder created attempt 2 ***************************");
            }
        } catch (Exception e) {
            LOG.error("Error syncing folder", e);
        }
    }

    /**
     * Whether the asset sits directly in an account root folder
     * (e.g. {@code <damIntegrationPath>/<accountId>}).
     *
     * ⚠️ Without this the caller would create a spurious Brightcove folder named after
     * the account id and move every root-level video into it: the account root carries
     * no {@code brc_folder_id}, so it is otherwise indistinguishable from an unsynced
     * subfolder.
     *
     * @return TRUE / FALSE, or null when the account configuration could not be read at
     *         all and the question therefore has no answer. {@code getConfigurationGrabber()}
     *         goes through {@code FrameworkUtil.getBundle()}, which yields nothing outside
     *         an OSGi container, so this is reachable and must not collapse into FALSE.
     */
    static Boolean isAccountRoot(Node parentNode) throws Exception {
        ConfigurationGrabber cg;
        try {
            cg = ServiceUtil.getConfigurationGrabber();
        } catch (Exception | LinkageError e) {
            LOG.debug("Configuration grabber unavailable", e);
            return null;
        }
        if (cg == null) {
            return null;
        }
        for (String accountId : cg.getAvailableServices()) {
            ConfigurationService cs = cg.getConfigurationService(accountId);
            if (cs == null) {
                continue;
            }
            String integrationPath = cs.getAssetIntegrationPath();
            if (integrationPath == null) {
                continue;
            }
            String normalized = integrationPath.endsWith("/")
                    ? integrationPath.substring(0, integrationPath.length() - 1)
                    : integrationPath;
            if (parentNode.getPath().equals(normalized + "/" + accountId)) {
                LOG.info("Asset is at account root level ({}), skipping Brightcove folder sync",
                        parentNode.getPath());
                return true;
            }
        }
        return false;
    }

    private static boolean isUsable(String folderId) {
        return folderId != null && !folderId.isEmpty();
    }

    private static void moveToKnownFolder(ServiceUtil serviceUtil, Node parentNode, String videoId)
            throws Exception {
        String brcFolderId = parentNode.getProperty(BRC_FOLDER_ID).getString();
        LOG.trace("SUBFOLDER FOUND - SETTING THE FOLDER ID to '{}'", brcFolderId);
        serviceUtil.moveVideoToFolder(brcFolderId, videoId);
    }

    private static void recordFolderIdAndMove(ServiceUtil serviceUtil, Node parentNode,
                                              String videoId, String folderId) throws Exception {
        parentNode.setProperty(BRC_FOLDER_ID, folderId);
        parentNode.getSession().save();
        LOG.trace("SUBFOLDER FOUND - SETTING THE FOLDER ID to '{}'", folderId);
        serviceUtil.moveVideoToFolder(folderId, videoId);
    }

    /**
     * The account folder an asset belongs to, walking up past a synced subfolder.
     *
     * <p>Assets used to live directly under {@code <integrationPath>/<accountId>}, so the
     * account id was just the parent's name. With subfolder sync the parent can be a
     * Brightcove subfolder instead, and reading its name yields a folder id where an
     * account id is expected: the account lookup then misses and the publish is skipped
     * with "Account not existing". A synced subfolder is identified by carrying
     * {@code brc_folder_id}.</p>
     *
     * @param parentNode the asset's parent node
     * @return the account folder name, or null when it cannot be determined
     */
    public static String resolveAccountId(Node parentNode) {
        if (parentNode == null) {
            return null;
        }
        try {
            if (parentNode.hasProperty(BRC_FOLDER_ID)) {
                return parentNode.getParent().getName();
            }
            return parentNode.getName();
        } catch (Exception e) {
            LOG.error("Could not resolve the account folder for the asset's parent", e);
            return null;
        }
    }
}
