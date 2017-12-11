package com.coresecure.brightcove.wrapper.models;

import com.adobe.granite.license.ProductInfo;
import com.adobe.granite.license.ProductInfoService;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.day.cq.wcm.api.Page;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.day.cq.wcm.api.components.EditContext;


import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Node;
import org.apache.jackrabbit.util.Text;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;

@Model(
        adaptables=SlingHttpServletRequest.class
)
public class VideoPlayerConfiguration
{

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
    private EditContext editContext;

    @ScriptVariable
    private Resource resource;

    @ScriptVariable
    private ValueMap properties;

    protected final static Logger LOGGER = LoggerFactory.getLogger(VideoPlayer.class);

    @Self
    VideoPlayer videoPlayer;

    //    Had to add
    String dialogPath = "";
    String description = "";
    String segmentPath = "";
    String title = "";

    public String getTitle()
    {
        return title;
    }

    public ComponentContext getComponentContext()
    {
        return componentContext;
    }

    public String getDropTargetPrefix()
    {
        return DropTarget.CSS_CLASS_PREFIX;
    }

    public String getInlineCSS()
    {
        return properties.get("inlineCSS", String.class);
    }

    public String getDialogPath() {return dialogPath;}

    public VideoPlayer getVideoPlayer() {
        return videoPlayer;
    }
    public String getVideoWidth() {
        return videoPlayer.hasSize ? videoPlayer.getWidth() : "480px";
    }
    public String getVideoHeight() {
        return videoPlayer.hasSize ? videoPlayer.getHeight() : "270px";
    }
    @PostConstruct
    protected void init()
    {
    //init
        try
        {
            this.segmentPath = Text.getRelativeParent(resource.getPath(), 1);
            this.title = properties.get("jcr:title", Text.getName(segmentPath));
            this.description = properties.get("jcr:description", "");
            this.dialogPath = "";

            ValueMap playerProperties = currentPage.getProperties();

            if (editContext != null && editContext.getComponent() != null)
            {
                dialogPath = editContext.getComponent().getDialogPath();
            }
        }
        catch (Exception e)
        {
            LOGGER.error("exception error ", e);
        }
    //end init
    }

}
