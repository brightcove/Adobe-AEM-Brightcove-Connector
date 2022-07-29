package com.coresecure.brightcove.wrapper.models;

import com.adobe.granite.license.ProductInfo;
import com.adobe.granite.license.ProductInfoService;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import com.day.cq.wcm.api.Page;
import com.day.cq.dam.api.Asset;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.components.ComponentContext;
import com.day.cq.wcm.api.components.DropTarget;
import com.fasterxml.jackson.annotation.JsonSetter.Value;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Source;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Node;
import java.util.UUID;
import org.apache.jackrabbit.util.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Model(
        adaptables=SlingHttpServletRequest.class
)
public class VideoPlayer {

    @Self
    private SlingHttpServletRequest slingHttpServletRequest;

    @Inject @Source("sling-object")
    private ResourceResolver resourceResolver;

    @OSGiService
    ConfigurationGrabber cg;

    @OSGiService
    ProductInfoService productInfo;

    @ScriptVariable
    private Page currentPage;

    @ScriptVariable
    private PageManager pageManager;


    @ScriptVariable
    private Node currentNode;

    @ScriptVariable
    private ComponentContext componentContext;

    @ScriptVariable
    private Resource resource;

    @ScriptVariable
    private ValueMap properties;

    protected final static Logger LOGGER = LoggerFactory.getLogger(VideoPlayer.class);

    String componentID;
    String videoID;
    String playlistID;
    String account;
    String playerPath;
    String embedType;
    String playerID;
    String playerKey;
    String playerDataEmbed;
    String containerID;
    String containerClass;
    String align;
    String width;
    String height;
    String version;
    boolean hasSize = false;
    boolean ignoreComponentProperties = false;
    ConfigurationService cs = null;
    Resource playerPageResource;
    ValueMap playerProperties;
    String inlineCSS;
    String bundleVersion;

    public String getComponentID() {
        return componentID;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public String getVideoID() {
        return videoID;
    }

    public String getPlaylistID() {
        return playlistID;
    }

    public String getAccount() {
        return account;
    }

    public String getPlayerPath() {
        return playerPath;
    }

    public String getEmbedType() {
        return embedType;
    }

    public String getPlayerID() {
        return playerID;
    }

    public String getPlayerDataEmbed() {
        return playerDataEmbed;
    }

    public String getPlayerKey() {
        return playerKey;
    }

    public String getContainerID() {
        return containerID;
    }

    public String getContainerClass() {
        return containerClass;
    }

    public String getAlign() {
        return align;
    }

    public String getWidth() {
        return hasSize? width+"px":null;
    }

    public String getHeight() {
        return hasSize?height+"px":null;
    }

    public boolean getHasSize() {
        return hasSize;
    }

    public boolean getIgnoreComponentProperties() {
        return ignoreComponentProperties;
    }

    public Resource getPlayerPageResource() {
        return playerPageResource;
    }

    public ValueMap getPlayerProperties() {
        return playerProperties;
    }

    public String getVersion() {
        return version;
    }

    public ComponentContext getComponentContext() {
        return componentContext;
    }

    public String getDropTargetPrefix() {
        return DropTarget.CSS_CLASS_PREFIX;
    }

    public String getBundleVersion() {
        return bundleVersion;
    }
    public String getInlineCSS() {
        return inlineCSS;
    }

    @PostConstruct
    protected void init(){
        LOGGER.info(this.getClass().getName());
        Version bundle_version = FrameworkUtil.getBundle(getClass()).getVersion();
        bundleVersion = String.format("%s.%s.%s", bundle_version.getMajor(),  bundle_version.getMinor(), bundle_version.getMicro());
        try {
            LOGGER.info(resource.getName() + "INIT");

            ProductInfo[] productInfos = productInfo.getInfos();
            if (productInfos.length > 0) {
                version = productInfos[0].getShortVersion();
            }


            LOGGER.info("current AEM Version : " + version);

            //cg = ServiceUtil.getConfigurationGrabber();
            componentID = Text.md5(currentNode.getPath());
            LOGGER.info("componentID:"+ componentID);

            // if a fileList parameter exists, use it to overwrite the videoID
            String videoPlayerDropPath = properties.get("videoPlayerDropPath", "");
            if (videoPlayerDropPath.length() > 0) {
                // a file was dropped here - get the resource and prop
                LOGGER.info("found dropped video path : " + videoPlayerDropPath);
                Resource videoResource = resourceResolver.resolve(videoPlayerDropPath);
                Resource metadataRes = videoResource.getChild(Constants.ASSET_METADATA_PATH);
                ValueMap map = metadataRes.adaptTo(ValueMap.class);
                String droppedVideoId = map.get(Constants.BRC_ID, String.class);
                //videoID = droppedVideoId;
                LOGGER.info("found asset video id : " + droppedVideoId);
                LOGGER.info("current video id : " + currentNode.getProperty("videoPlayer").getString());
                currentNode.setProperty("videoPlayer", droppedVideoId);

                // now delete the property for next time to be clean
                currentNode.setProperty("videoPlayerDropPath", "");

                currentNode.getSession().save();
            }

            videoID = properties.get("videoPlayer", "").trim();

            // check to see if the video ID is actually in the format "name [ID]"
            Pattern p = Pattern.compile("\\[(.*?)\\]");
            Matcher m = p.matcher(videoID);

            if (m.find()) {
                videoID = m.group(1);
            }

            playlistID = properties.get("videoPlayerPL", "").trim();

            account = properties.get("account", "").trim();
            playerPath = properties.get("playerPath", "").trim();
            embedType = properties.get("embedType", "").trim();
            playerID = "";
            playerKey = "";
            playerDataEmbed = "";
            inlineCSS = properties.get("inlineCSS","");

            containerID = properties.get("containerID", "");
            containerClass = properties.get("containerClass", "");

            // Default Values

            align = "center";
            width = "";
            height = "";


            //fallback to default
            try {
                if (TextUtil.notEmpty(account)) {
                    cs = cg.getConfigurationService(account);

                        playerID = cs.getDefVideoPlayerID();
                        playerDataEmbed = cs.getDefVideoPlayerDataEmbedded();
                        playerKey = cs.getDefVideoPlayerKey();

                }
            } catch (NullPointerException e) {
                LOGGER.error(e.getClass().getName(),e);
            }

            playerID = properties.get("playerID", playerID).trim();
            playerKey = properties.get("playerKey", playerKey).trim();

            playerDataEmbed = playerDataEmbed.isEmpty() ? "default" : playerDataEmbed;


            // Load Player Configuration
            Page playerPage = pageManager.getPage(playerPath);
            if (playerPage != null) {

                playerProperties = playerPage.getProperties();

                playerID = playerProperties.get("playerID", playerID);
                playerKey = playerProperties.get("playerKey", playerKey);
                playerDataEmbed = playerProperties.get("data_embedded", playerDataEmbed);


                align = playerProperties.get("align", align);
                width = playerProperties.get(Constants.WIDTH, width);
                height = playerProperties.get(Constants.HEIGHT, height);

                //append the class to the container wrap
                containerClass += " " + playerProperties.get("containerClass", "");

                ignoreComponentProperties = playerProperties.get("ignoreComponentProperties", ignoreComponentProperties);
            }

            // Override with local component properties IF enabled

            if (!ignoreComponentProperties) {

                align = properties.get("align", align);

                //we must override BOTH width and height to prevent one being set on Player Page and other set in component.
                if (properties.containsKey("width") || properties.containsKey("height")) {
                    width = properties.get("width", width);
                    height = properties.get("height", height);
                }
            }

            // Adjust size accordingly
            if (TextUtil.notEmpty(width) || TextUtil.notEmpty(height)) {
                hasSize = true;
                if (TextUtil.isEmpty(width)) {
                    width = String.valueOf((480 * Integer.parseInt(height, 10)) / 270);
                } else if (TextUtil.isEmpty(height)) {
                    height = String.valueOf((270 * Integer.parseInt(width, 10)) / 480);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception", e);
        }

    }


}
