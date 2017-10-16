package com.coresecure.brightcove.wrapper.webservices;


//*Imports*//
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.mime.MimeTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.servlet.ServletException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Authors: Pablo Kropilnicki and Alessandro Bonfatti
 * Coresecure Inc.
 * All rights reserved.
 */



@Service
@Component
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"html"}),
        @Property(name = "sling.servlet.paths", value = {"/bin/brightcove/dataload"})
})
public class AssetPropertyIntegrator extends SlingAllMethodsServlet {
    private static final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";


    private static final Logger LOGGER = LoggerFactory.getLogger(AssetPropertyIntegrator.class);

    private static final String[] fields = {"name", "created_at"  , "duration", "complete", "id", "account_id" ,"description" , "link", "tags","long_description", "reference_id", "economics", "updated_at" , "schedule", "state", "geo" , "custom_fields","text_tracks" , "images" ,"projection"};

    @Reference
    MimeTypeService mType;

    //MAPPING DECISIONS
    /*
    *
    * Name                          -> original_filename
    * Date Published                -> created_at
    * Duration                      -> duration (in seconds)
    * videoID                       -> id
    * Short Description             -> description
    * Long Description              -> long_description
    * Link to Related Item          -> link.url
    * Text for Related Item         -> link.text
    * tags                          -> tags
    * Reference ID                  -> reference_id
    * Economics                     -> economics
    *
    * */
//    public String name;
//    public String id;
//    public String account_id;
//    public String reference_id;
//    public String description;
//    public String long_description;
//    public String state;
//    public Collection<String> tags;
//    public Map<String, String> custom_fields;
//    public Geo geo;
//    public RelatedLink link;
//    public Schedule schedule;
//    public boolean complete;
//    public EconomicsEnum economics;

    @Override
    protected void doPost(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException {

        //LOGGER.trace("POST");
    }

    @Override
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException
    {
        //INITIALIZING THE RESOURCE RESOLVER
        ResourceResolver resourceResolver = req.getResourceResolver();

        LOGGER.trace("BRIGHTCOVE ASSET INTEGRATION - SYNCHRONIZING DATABASE");
        try
        {
            //MAIN TRY - CONFIGURATION GRAB SERVICE
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();    //GETCONFIG SERVICE
            String selectedaccount = AccountUtil.getSelectedAccount(req);       //GET CURRENT ACCOUNT
            Set<String> services = new TreeSet<String>();                            //BUILD SERVICES

            LOGGER.trace(selectedaccount);
            //IF THERE EXISTS A VALID ACCOUNT VALUE - GET CONFIGURATION OF THAT ACCOUNT
            if (selectedaccount != null && !selectedaccount.isEmpty())
            {
                ConfigurationService cs = cg.getConfigurationService(selectedaccount); //GET CONFIG FOR SELECTED ACCOUNT
                if (cs != null)
                {
                    services.add(selectedaccount);          //INITIALIZE SERVICES FOR SPECIFIED ACCOUNT
                }
                else
                {
                    services = cg.getAvailableServices(req); //ELSE GET AVAILABLE SERVICES
                }
            }
            else
            {
                services = cg.getAvailableServices(req);    //ELSE GET AVAILABLE SERVICES
            }


            for(String requestedAccount: services)
            {
                //GET CURRENT CONFIGURATION
                ConfigurationService cs = cg.getConfigurationService(requestedAccount);

                if (cg != null && requestedAccount != null && cs != null)
                {
                    //IF ACCOUNT IS VALID - INITIATE SYNC CONFIGURATION
                    Session session = req.getResourceResolver().adaptTo(Session.class); //GET CURRENT SESSION
                    String confPath = cs.getAssetIntegrationPath();                     //GET PRECONFIGURED SYNC DAM TARGET PATH
                    String basePath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount).concat("/"); //CREATE BASE PATH
                    //CREATE AND NAME BRIGHTCOVE ASSET FOLDERS PER ACCOUNT
                    Node accountFolder = JcrUtil.createPath(basePath, "sling:OrderedFolder", session);
                    accountFolder.setProperty("jcr:title", cs.getAccountAlias());
                    session.save();
                    //IF NEITHER CONFIGURATION - NOR BRIGHTCOVE SERVICE IS NULL...
                    List<String> allowedGroups = cs.getAllowedGroupsList();
                    ServiceUtil serviceUtil = new ServiceUtil(requestedAccount);

                    //AUTHORIZATION CHECK
                    boolean is_authorized = false;
                    UserManager userManager = req.getResourceResolver().adaptTo(UserManager.class);
                    try {
                        Authorizable auth = userManager.getAuthorizable(session.getUserID());

                        if (auth != null) {
                            Iterator<Group> groups = auth.memberOf();
                            while (groups.hasNext() && !is_authorized) {
                                Group group = groups.next();
                                if (allowedGroups.contains(group.getID())) is_authorized = true; //<-Authorization
                            }
                        }
                    } catch (RepositoryException re) {
                        LOGGER.error("executeRequest", re);
                    }



                    //IF USER IS AUTHORIZED AND ACCOUNT IS IN AUTHENTICATED GROUP
                    if (is_authorized)
                    {

                        //get total  % a split = n, loop for n - TODO: PAGINATION? I THINK IS ALREADY HANDLED BY DEFAULT BRC


                            //GET VIDEOS
                            int startOffset = 0;
                            JSONObject jsonObject = new JSONObject(serviceUtil.searchVideo("", startOffset, 0)); //QUERY<------
                            JSONArray itemsArr = (JSONArray) jsonObject.getJSONArray("items");


                            int success = 0;
                            int failure = 0;
                            int equal = 0;

                            LOGGER.trace("<<< " +itemsArr.length() + " INCOMING VIDEOS");

                                //FOR EACH VIDEO IN THE ITEMS ARRAY
                                for (int i = 0; i < itemsArr.length(); i++)
                                {
                                    try {
                                        //FOR EACH VIDEO COMING BACK FROM THE QUERY
                                        JSONObject innerObj = itemsArr.getJSONObject(i);



                                        //CHECK IF VIDEO'S STATE IS SET TO ACTIVE - CONDITION ONE
                                        Boolean active = false;
                                        if (innerObj.has("state") && !innerObj.get("state").equals(null)) {
                                            active = "ACTIVE".equals(innerObj.getString("state")) ? true : false;
                                        }

                                        //CONDITIONN TWO - MUST HAVE AN ID
                                        Boolean hasID = innerObj.has("id") && !innerObj.get("id").equals(null);
                                        String ID = innerObj.has("id") ? innerObj.getString("id") : null;


                                        LOGGER.trace(">>>>START>>>>>{"+ID +"}>> "+ (active && hasID) +">>>>>");

                                        //TODO: CHECK IF VIDEO COMING INTO DAM (1) IS ACTIVE (2) HAS AN ID + SRC IMAGE??
                                        if (active && hasID)
                                        {
                                            String name = innerObj.getString("name");
                                            String brightcove_filename = ID + ".mp4"; //BRIGHTCOVE FILE NAME IS ID + . MP4 <-
                                            String original_filename = innerObj.getString("original_filename") !=  null ? innerObj.getString("original_filename").replaceAll("%20", " ") : null ;

    //                                        LOGGER.trace("###BC_FILENAME###>>" + brightcove_filename);
    //                                        LOGGER.trace("###NAME###>>" + name);
    //                                        LOGGER.trace("###ACTIVE###>>" + active);
    //                                        LOGGER.trace("###ORIGINAL_NAME###>>" + original_filename);

                                            LOGGER.trace("SYNCING VIDEO>>["+name+"\tSTATE:ACTIVE\tTO BE:" + original_filename+"]");


                                            //INITIALIZING ASSET SEARCH // INITIALIZATION
                                            Asset newAsset = null;
                                            InputStream binary;
                                            InputStream is;

                                            //TODO:PRINT STATEMENT - DEBUGGER
                                            //LOGGER.trace(innerObj.toString(1));


                                        //USNIG THE CONFIGURATION - BUILD THE DIRECTORY TO SEARCH FOR THE LOCAL ASSETS OR BUILD INTO
                                        String localpath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount + "/").concat(brightcove_filename);
                                        String oldpath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount + "/").concat(original_filename);

                                        //LOGGER.trace("CONFPATH : " + confPath);
                                        LOGGER.trace("SEARCHING FOR LOCAL ASSET");
                                        LOGGER.trace(">>ORIGINAL: " + oldpath);
                                        LOGGER.trace(">>PATH: " + localpath);


                                        //TRY TO GET THIS ASSET IN THE CONFIGURED BC NODE PATH - IF IT IS NULL - IT MUST BE CREATED
                                        newAsset = resourceResolver.getResource(oldpath) != null ? resourceResolver.getResource(oldpath).adaptTo(Asset.class) : resourceResolver.getResource(localpath) != null ? resourceResolver.getResource(localpath).adaptTo(Asset.class) : null;

                                        if (newAsset == null)
                                        {
                                            //IF NEW ASSET IS NULL MEANS THAT - IT MUST BE CREATED


                                            String mime_type = "";
                                            //String response_j = "";


                                            //GET THUMBNAIL - SOURCE CHECK? - CONDITION THREE? WHy?
                                            String thumbnail_src = innerObj.has("thumbnailURL") && !innerObj.get("thumbnailURL").equals(null) ? innerObj.getString("thumbnailURL") : "";


                                            if (thumbnail_src.startsWith("/") && !thumbnail_src.startsWith("//")) //HAS LOCAL THUMB PATH
                                            {
                                                //IF THE THUMBNAIL SOURCE IST /CONTENT/DAM/ IT IS LOCAL - IF LOCAL >>

                                                LOGGER.trace("->>Pulling local image as this video's thumbnail image binary");
                                                LOGGER.trace("->>Thumbnail Source is/: " + thumbnail_src);
                                                LOGGER.trace("->>Looking for local thumbnail source at [INTERNAL]: " + localpath);


                                                Resource thumbRes = resourceResolver.resolve(req, thumbnail_src); //RESOLVE TO IMAGE
                                                mime_type = mType.getMimeType(thumbRes.getName());                //MATCH TO THUMBNAIL SOURCE MIME TYPE

                                                LOGGER.trace("MIME TYPE COMING IN:\t" + mime_type + " NEW THUMBNAIL RESOURCE: " + thumbnail_src);


                                                try
                                                {
                                                    //READ THUMBNAIL FROM LOCAL ADDRESS
                                                    binary = JcrUtils.readFile(thumbRes.adaptTo(Node.class));

                                                    //THROWS REPO EXCEPTION OF NOT FOUND
                                                    //IF IT FAILS TO READ FILE FROM LOCAL, THEN IT MUST CREATE THE DEFAULT THUMBNAIL
                                                }
                                                catch (RepositoryException e)
                                                {
                                                    LOGGER.error("Local thumbnail image source could not be read", e);
                                                    LOGGER.error("FAILURE TO LOAD THUMBNAIL SOURCE FOR VIDEO " + newAsset.getPath());
                                                    break;
                                                }

                                            }
                                            else
                                            {
                                                LOGGER.trace("->>Pulling external image as this video's thumbnail image binary - Must do a GET");
                                                LOGGER.trace("->>Thumbnail Source is/: " + thumbnail_src + " DESTINATION >> " + localpath);

                                                String urlParameters = "";
                                                Map<String, String> nullmap = new HashMap<String, String>();
                                                LOGGER.trace("->>[PULLING THUMBNAIL] : " + thumbnail_src + " " + urlParameters + " " + nullmap);

                                                JSONObject get_response = HttpServices.executeFullGet(thumbnail_src, urlParameters, nullmap);
                                                if (get_response != null && get_response.has("binary")) {
                                                    binary = new ByteArrayInputStream((byte[]) get_response.get("binary"));
                                                    mime_type = get_response.getString("mime_type"); //< SET MIME TYPE

                                                    if (binary == null) //IF REMOTE IMAGE LOAD UNSUCCESSFUL - LOAD DEFAULT
                                                    {
                                                        LOGGER.error("External thumbnail could not be read");
                                                        LOGGER.error("FAILURE TO LOAD THUMBNAIL SOURCE FOR VIDEO " + newAsset.getPath());
                                                    }
                                                }
                                                else
                                                {
                                                    Resource thumbRes = resourceResolver.resolve(req, "/etc/designs/cs/brightcove/shared/img/noThumbnail.jpg"); //RESOLVE TO IMAGE
                                                    mime_type = mType.getMimeType(thumbRes.getName());                //MATCH TO THUMBNAIL SOURCE MIME TYPE

                                                    LOGGER.trace("MIME TYPE COMING IN:\t" + mime_type + " NEW THUMBNAIL RESOURCE: " + thumbnail_src);


                                                    try
                                                    {
                                                        //READ THUMBNAIL FROM LOCAL ADDRESS
                                                        binary = JcrUtils.readFile(thumbRes.adaptTo(Node.class));

                                                        //THROWS REPO EXCEPTION OF NOT FOUND
                                                        //IF IT FAILS TO READ FILE FROM LOCAL, THEN IT MUST CREATE THE DEFAULT THUMBNAIL
                                                    }
                                                    catch (RepositoryException e)
                                                    {
                                                        LOGGER.error("Local thumbnail image source could not be read", e);
                                                        LOGGER.error("FAILURE TO LOAD THUMBNAIL SOURCE FOR VIDEO " + newAsset.getPath());
                                                        break;
                                                    }
                                                }
                                            }




                                            //CALL ASSET MANAGER
                                            AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);

                                            //CREATE ASSET - NEEDS BINARY OF THUMBNAIL IN ORDER TO SET IT FOR THE NEW ASSET
                                            newAsset = assetManager.createAsset(localpath, binary, mime_type, true);

                                            //SAVE CHANGES
                                            resourceResolver.commit();


                                            //TODO: THESE LOGS SHOW GET RESPONSE
                                            //LOGGER.trace(mime_type);
                                            //LOGGER.trace(response_j);

                                            //AFTER ASSET HAS BEEN CREATED --> UPDATE THE ASSET WITH THE INNER OBJ WE ARE STiLL PROCESSING
                                            updateAsset(newAsset, innerObj, resourceResolver, requestedAccount);

                                            //END CASE - ASSET NOT FOUND - MUST BE CREATED
                                            success++;
                                        }
                                        else
                                        {

                                            //START CASE - ASSET HAS BEEN FOUND LOCALLY - CAN BE UPDATED
                                            LOGGER.trace("ASSET FOUND - UPDATING");

                                            //DATE COMPARISON TO MAKE SURE IT MUST BE UPDATED
                                            try {


                                                Date local_mod_date = new Date(newAsset.getLastModified());
                                                //LOGGER.trace("UPDATED AT::::::::: " + innerObj.get(x));


                                                //LOGGER.trace("PRE-PARSE>>>>>>>" + innerObj.getString("updated_at") );
                                                SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);
                                                Date remote_date = sdf.parse(innerObj.getString("updated_at"));

                                                //LOCAL COMPARISON DATE TO SEE IF IT NEEDS TO UPDATE
                                                if (local_mod_date.compareTo(remote_date) < 0)
                                                {
                                                    LOGGER.trace("OLDERS-DATE>>>>>" + local_mod_date);
                                                    LOGGER.trace("PARSED-DATE>>>>>" + remote_date);
                                                    LOGGER.trace(local_mod_date + " < " + remote_date);
                                                    LOGGER.trace("MODIFICATION DETECTED");
                                                    updateAsset(newAsset, innerObj, resourceResolver, requestedAccount);
                                                    success++;


                                                }
                                                else
                                                {
                                                    LOGGER.trace("No Changes to be Made = Asset is equivalent");
                                                    equal++;
                                                }

                                            }
                                            catch (Exception p)
                                            {
                                                LOGGER.error("Parsing exception", p);
                                                failure++;
                                                break;
                                            }

                                        }

                                        }
                                        else
                                        {
                                            LOGGER.warn("VIDEO INITIALIZATION FAILED - NOT ACTIVE / NO ID - skipping: " + innerObj.toString(1));
                                            failure++;
                                        }


                                        LOGGER.trace(">>>>>>>>>{"+ID +"}>>>>>END>>>>");

                                    //MAIN VIDEO ARRAY TRAVERSAL LOOP
                                    } catch (JSONException j) {
                                        LOGGER.error("JSON EXCEPTION", j);
                                        failure++;
                                    } catch (IllegalArgumentException u) {
                                        LOGGER.error("IllegalArgumentException", u);
                                        failure++;
                                    } catch (RepositoryException r) {
                                        LOGGER.error(" javax.jcr.RepositoryException : INVALID TAG CHARS?", r);
                                        failure++;
                                    } catch (RuntimeException t) {
                                        LOGGER.error(" javax.jcr.RuntimeException : INVALID TAG CHARS?", t);
                                        failure++;
                                    }
                                }

                            LOGGER.trace(">>>>FINISHED BRIGHTCOVE SYNC PAYLOAD TRAVERSAL>>>>");
                            LOGGER.warn(">>>> SYNC DATA: nochange: " + equal + " success: " + success + " skipped or failed: " + failure+" >>>>");


                        //END (AUTHORIZED) BLOCK
                    }
                    else
                    {
                        LOGGER.error("Brightcove Sync Failed - Invalid User Authentication (Check configuration)");
                        resp.sendError(403);
                    }

                } else {
                    throw new Exception("[Invalid or missing Brightcove configuration]");
                }
            }


            resourceResolver.close();
            //END MAIN TRY
        }
        catch (Exception e)
        {
            LOGGER.error("ERROR" , e);
        }

    }

    private void updateAsset(Asset newAsset, JSONObject innerObj, ResourceResolver resourceResolver, String requestedAccount) throws JSONException, RepositoryException, PersistenceException {

        try {

            if (newAsset != null)
            {
                //LOGGER.trace(innerObj.toString(1));
                LOGGER.trace("UPDATING ASSET>>: " + newAsset.getPath());

                Resource assetRes = newAsset.adaptTo(Resource.class);                        //INITIALIZE THE ASSET RESOURCE
                ModifiableValueMap assetmap = assetRes.getChild("jcr:content").adaptTo(ModifiableValueMap.class);

                Resource metadataRes = assetRes.getChild("jcr:content/metadata");            //INITIALIZE THE ASSET BC METADATA MAP RESOURCE
                ModifiableValueMap map = metadataRes.adaptTo(ModifiableValueMap.class);

                //SET FIRST PIECE OF METADATA
                map.put("brc_account_id", requestedAccount);

                //HANDLE TAG S
                TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
                List<String> tags = new ArrayList<String>();


                map.put("cq:tags", tags.toArray());

                for (String x : fields)
                {

                    if(innerObj.has(x))
                    {
                        LOGGER.trace("[X] {} " + innerObj.getString(x), x);
                    }

                    //ADAPT NAME OF METADATA COMPING IN -> AEM PROPERTIES TO BE STORED
                    if (innerObj.has(x))
                    {
                        String key = x;
                        if (x.equals("tags"))
                        {
                            key = "cq:".concat(x); //TAGS -> CQ TAGS
                        }
                        else if ("name".equals(x))
                        {
                            key = "dc:title";      //NAME -> ASSET TITLE
                        }
                        else
                        {
                            key = "brc_".concat(x); //ALL ELSE -> BRC_KEYNAME
                        }
                        // SECOND PRINT STATEMNT - LOGGER.trace("" + x + " -> " + "[" + innerObj.get(x) + "] is null? -> " + innerObj.get(x).equals(null));

                        Object obj = innerObj.get(x);

                        //IF THE CURRENT METADATA IS AN ARRAY
                        if (obj instanceof JSONArray)
                        {
                            JSONArray objArray = (JSONArray) obj;
                            if (x.equals("tags")) {


                                //LOGGER.trace("TAG ARRAY "+objArray.toString(1));

                                for (int cnt = 0; cnt < objArray.length(); cnt++)
                                {
                                    String tagValue = objArray.getString(cnt);

                                    String tagKey = tagValue.replaceAll(": ",":").trim();


                                    try {
                                        if (tagManager.canCreateTag(tagKey)) {

                                            Tag tag = tagManager.createTag(tagKey, tagValue, "");

                                            //Tag tag = tagManager.createTagByTitle(tagValue, Locale.US);
                                            resourceResolver.commit();
                                            LOGGER.trace("tag created > " + tagValue);
                                            //tagManager.setTags(assetRes, new Tag[]{tag}, true);
                                        } else {
                                            //Tag[] tags = tagManager.findTagsByTitle(tagValue, Locale.US);
                                            //tagManager.setTags(assetRes, tags, true);
                                            LOGGER.error("tag create failed [exists] > added >  ", tagValue);

                                        }
                                        tags.add(tagKey);
                                    } catch (InvalidTagFormatException e) {
                                        LOGGER.error("Invalid Tag Format", e);
                                    }
                                }
                                resourceResolver.commit();
                                map.put(key, tags.toArray());
                            } else {
                                map.put(key, objArray.join("#@#").split("#@#"));
                            }
                        }
                        else if (obj instanceof JSONObject)
                        {

                            //ELSE IF IT IS AN OBJECT
                            JSONObject objObject = (JSONObject) obj;

                            //CASE IMAGES
                            if (x.equals("images"))
                            {
                                if (objObject != null) {

                                    try {

                                        //LOGGER.trace(objObject.toString());
                                        if (objObject.has("poster")) {
                                            JSONObject images_poster_obj = objObject.getJSONObject("poster");
                                            String src = images_poster_obj.getString("src");
                                            //DO GET FOR RENDITION -> TO ASSET "brc_poster"
                                            URL srcURL = new URL(src);
                                            InputStream ris = srcURL.openStream();
                                            //Map<String,Object> rendition_map = new HashMap<String,Object>();
                                            newAsset.addRendition("brc_poster.png", ris, "image/jpeg");
                                        }

                                        if (objObject.has("thumbnail")) {
                                            JSONObject images_poster_obj = objObject.getJSONObject("thumbnail");
                                            String src = images_poster_obj.getString("src");
                                            //DO GET FOR RENDITION -> TO ASSET "brc_thumbnail"

                                            InputStream ris = new URL(src).openStream();
                                            //Map<String,Object> rendition_map = new HashMap<String,Object>();
                                            newAsset.addRendition("brc_thumbnail.png", ris, "image/jpeg");

                                            ris = new URL(src).openStream();//<= FIXES DISMISSED InputStream*
                                            newAsset.addRendition("original", ris, "image/jpeg");
                                        }
                                    }
                                    catch (Exception e)
                                    {
                                        LOGGER.error("Failure to initialize remote source for "+ newAsset.getPath(), e);
                                    }


                                } else {
                                    newAsset.removeRendition("brc_poster.png");
                                    newAsset.removeRendition("brc_thumbnail.png");
                                }
                            } //CASE SCHEDULE
                            else if (x.equals("schedule"))
                            {
                                if (objObject != null) {


                                    LOGGER.trace("PRE-PARSE>>>>>>>");
                                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);


                                    String starts_at = objObject.getString("starts_at");
                                    if (starts_at != null && !starts_at.equals("null")) {
                                        assetmap.put("onTime", starts_at);
                                    } else {
                                        if (assetmap.containsKey("onTime")) assetmap.remove("onTime");
                                    }
                                    String ends_at = objObject.getString("ends_at");
                                    if (ends_at != null && !ends_at.equals("null")) {
                                        assetmap.put("offTime", ends_at);
                                    } else {
                                        if (assetmap.containsKey("offTime")) assetmap.remove("offTime");
                                    }
                                } else {
                                    LOGGER.trace("PRE-REMOVE>>>>>>>");
                                    if (assetmap.containsKey("onTime")) assetmap.remove("onTime");
                                    if (assetmap.containsKey("offTime")) assetmap.remove("offTime");
                                }
                            } //ELSE - LINK
                            else if (x.equals("link"))
                            {

                                //"link":{"text":"Sample related link","url":"www.brightcove.com"},"
                                if (objObject != null)
                                {
                                    String link_url = objObject.getString("url");
                                    if (link_url != null && !link_url.equals("null")) {
                                        map.put("brc_link_url", link_url);
                                    } else {
                                        if (map.containsKey("brc_link_url")) map.remove("brc_link_url");
                                    }
                                    String link_text = objObject.getString("text");
                                    if (link_text != null && !link_text.equals("null")) {
                                        map.put("brc_link_text", link_text);
                                    } else {
                                        if (map.containsKey("brc_link_text")) map.remove("brc_link_text");
                                    }
                                }
                                else
                                {
                                    if (map.containsKey("brc_link_text")) map.remove("brc_link_text");
                                    if (map.containsKey("brc_link_url")) map.remove("brc_link_url");
                                }
                            }
                            else
                            {

                                //TODO: CHECK - SUBMODULE NECESSARY ? This is else JSON Object Case


                                Node subNode;
                                Resource subResource;
                                if (metadataRes.getChild(key) == null) {
                                    subNode = metadataRes.adaptTo(Node.class).addNode(key);
                                } else {
                                    subNode = metadataRes.adaptTo(Node.class).getNode(key);
                                }

                                if (subNode != null) {
                                    subResource = metadataRes.getChild(key);
                                    ModifiableValueMap submap = subResource.adaptTo(ModifiableValueMap.class);
                                    Iterator<String> itrObj = objObject.keys();
                                    while (itrObj.hasNext()) {
                                        String selectorKey = itrObj.next();
                                        submap.put(selectorKey, objObject.get(selectorKey));
                                    }
                                }

                                //map.put(key, objObject.get(key));

                            }
                        }
                        else //NOT ARRAY NOR OBJECT
                        {



                            //DURATION SETTING AND CHECK
                            try {
                                //Check format of brc_duration
                                if (key.equals("brc_duration") && obj!=null)
                                {
                                    //LOGGER.trace("*!*!*! current key : " + key.toString() + " value: " + obj.toString());
                                    //conditional conversion
                                    int input = Integer.parseInt(obj.toString());
                                    input = input / 1000 ;
                                    obj = String.format("%02d:%02d:%02d", input/3600,(input % 3600) / 60,(input % 3600) % 60);
                                    //LOGGER.trace("*!*!*! is now :" + obj.toString());
                                }
                            }
                            catch (Exception e)
                            {
                                LOGGER.error("*!*!*! Duration Check Error:!", e);
                            }
                            //END DURATION CHECK AND SET



                            //THIS HANDLES REST OF NULL SET KEYS WHICH MAP TO PROPERTY VALUES
                            if (obj != null && !obj.equals(null) && !obj.equals("null"))
                            {
                                map.put(key, obj); //MAIN SET OF THE KEYS->VALUES FOR THIS VIDEO OBJECT
                            }
                            else
                            {

                                     //TODO: Improvie this check, this is the handle for null object / string
                                    //WE TAKE THESE NULL VALUES AS ACTUAL VALUES AND EXECUTE
                                    if (x.equals("images"))
                                    {
                                        //NULL ON IMAGES MEANS NULL SOURCES OF IMAGES
                                        newAsset.removeRendition("brc_poster.png");
                                        newAsset.removeRendition("brc_thumbnail.png");
                                    }
                                    else if (x.equals("schedule"))
                                    {
                                        //NULL OBJECT OF SCHEDULE = EMPTY SCHEDULE METADATA
                                        if (assetmap.containsKey("onTime")) assetmap.remove("onTime");
                                        if (assetmap.containsKey("offTime")) assetmap.remove("offTime");
                                    }
                                    else if (x.equals("link"))
                                    {
                                        //NULL LINK OBJECT MEANS EMPTY URL + TEXT METADATA
                                        if (map.containsKey("brc_link_text")) map.remove("brc_link_text");
                                        if (map.containsKey("brc_link_url")) map.remove("brc_link_url");
                                    }
                                    else
                                    {
                                        //IF ANY OTHER ARE NULL AND WERE ACTIVE - ARE NOW EQUIVALENT
                                        if (map.containsKey(key)) map.remove(key);
                                    }
                                }
                        }
                    }
                    else
                    {
                        LOGGER.trace("##HAS KEY BUT OBJECT IT LEADS TO IS NULL!");
                        LOGGER.trace("## HAS OBJECT WITH KEY : " + x +" ? "+innerObj.has(x) + " isnull? : "+ innerObj.get(x).equals(null));
                    }
                }



                //AFTER SETTING ALL THE METADATA - SET THE LAST UPDATE TIME
                long current_time_millisec = new java.util.Date().getTime();
                map.put("brc_lastsync", current_time_millisec);
                resourceResolver.commit();


                LOGGER.trace(">>UPDATED METADATA FOR VIDEO : [" + map.get("brc_id")+ "]");
            }
            else
            {
                LOGGER.error("BC ASSET UPDATE FAILED - ASSET IS NULL ! ERROR");
            }

            //MAIN TRY
        }
        catch (JSONException e)
        {
            LOGGER.error("JSON EXCEPTION", e);
        } catch (NullPointerException e) {
            LOGGER.error("NULL POINTER", e);
        }  catch (IOException e)
        {
            LOGGER.error("FILE NOT FOUND", e);
        }

    }


}
