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
import org.apache.jackrabbit.util.Text;

@Model(
        adaptables=SlingHttpServletRequest.class
)
public class ExperiencePlayer {

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

    protected final static Logger LOGGER = LoggerFactory.getLogger(ExperiencePlayer.class);

    String componentID;
    String experienceID;
    String account;
    String embedType;
    String containerID;
    String containerClass;
    String align;
    String width;
    String height;
    String version;
    boolean hasSize = false;
    ConfigurationService cs = null;
    String inlineCSS;
    String bundleVersion;

    public String getComponentID() {
        return componentID;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public String getExperienceID() {
        return experienceID;
    }

    public String getAccount() {
        return account;
    }

    public String getEmbedType() {
        return embedType;
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

            componentID = Text.md5(currentNode.getPath());

            // log some info about AEM and the component ID
            LOGGER.info("componentID:"+ componentID);
            LOGGER.info("current AEM Version : " + version);

            experienceID = properties.get("experience", "").trim();
            account = properties.get("account", "").trim();
            embedType = properties.get("embedType", "").trim();
            inlineCSS = properties.get("inlineCSS","");
            containerID = properties.get("containerID", "");
            containerClass = properties.get("containerClass", "");

            // Default Values
            align = "center";
            width = "";
            height = "";

            align = properties.get("align", align);

            // we must override BOTH width and height to prevent one being set on Player Page and other set in component.
            if (properties.containsKey("width") || properties.containsKey("height")) {
                width = properties.get("width", width);
                height = properties.get("height", height);
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
