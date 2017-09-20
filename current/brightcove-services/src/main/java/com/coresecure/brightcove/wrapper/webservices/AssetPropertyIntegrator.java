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
import javax.servlet.ServletException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
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


    private static final Logger LOGGER = LoggerFactory.getLogger(AssetPropertyIntegrator.class);

    private static final String[] fields = {"name", "created_at"  , "duration", "complete", "id", "account_id" ,"description" , "link", "tags","long_description", "reference_id", "economics", "updated_at" , "schedule", "state", "geo" , "custom_fields","text_tracks" , "images"};

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

        try
        {
            //MAIN TRY - CONFIGURATION GRAB SERVICE
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            String selectedaccount = AccountUtil.getSelectedAccount(req);
            LOGGER.trace(selectedaccount);
            Set<String> services = new TreeSet<String>();
            if (selectedaccount != null && !selectedaccount.isEmpty()) {
                ConfigurationService cs = cg.getConfigurationService(selectedaccount);
                if (cs != null) {
                    services.add(selectedaccount);
                } else {
                    services = cg.getAvailableServices(req);
                }
            } else {
                services = cg.getAvailableServices(req);
            }
            for(String requestedAccount: services) {
                ConfigurationService cs = cg.getConfigurationService(requestedAccount);

                if (cg != null && requestedAccount != null && cs != null) {
                    Session session = req.getResourceResolver().adaptTo(Session.class);
                    String confPath = cs.getAssetIntegrationPath();
                    String basePath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount).concat("/");

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


                    //LOGGER.trace(requestedAccount);
                    //LOGGER.trace(cs.getAssetIntegrationPath());

                    //LOGGER.info(fields.toString());
                    //LOGGER.trace("FIELDS INCLUDES DESCRIPTION?: " + fields.toString().contains("description"));
                    if (is_authorized) {
                        //FUNCTION CALL FOR THE JSON


                        //get total  % a split = n, loop for n - TODO: PAGINATION? I THINK IS ALREADY HANDLED BY DEFAULT BRC










                        try {
                            int startOffset = 0;
                            JSONObject jsonObject = new JSONObject(serviceUtil.searchVideo("", startOffset, 0));

                            JSONArray itemsArr = (JSONArray) jsonObject.getJSONArray("items");

                                Boolean videoExists;
                                //FOR EACH VIDEO IN THE ITEMS ARRAY
                                for (int i = 0; i < itemsArr.length(); i++) {
                                    JSONObject innerObj = itemsArr.getJSONObject(i);
                                    //EACH VIDEO

                                    String src = innerObj.has("thumbnailURL") && !innerObj.get("thumbnailURL").equals(null) ? innerObj.getString("thumbnailURL") : "";
                                    //GET THUMBNAIL

                                    //TODO: clean order - this makes up the name
                                    Boolean active = false;
                                    if (innerObj.has("state") && !innerObj.get("state").equals(null)) {
                                        active = "ACTIVE".equals(innerObj.getString("state")) ? true : false;
                                    }
                                    //Boolean condition_one = innerObj.has("name") && !innerObj.get("name").equals(null);
                                    Boolean condition_two = innerObj.has("id") && !innerObj.get("id").equals(null);

                                    //TODO - Fix redundant condition three
                                    if (active && condition_two) {
                                        String original_filename = innerObj.getString("name");

                                        String brightcove_filename = innerObj.getString("id")+ ".mp4";//original_filename + "__" + innerObj.getString("id")+ ".mp4";
                                        LOGGER.trace("###NAME###>>" + brightcove_filename);
                                        LOGGER.trace("###ACTIVE###>>" + active);


                                        Asset newAsset = null;
                                        InputStream binary;
                                        InputStream is;

                                        //TODO:PRINT STATEMENT - DEBUGGER
                                        //LOGGER.trace(innerObj.toString(1));

                                        if (src != null && !src.equals("")) {
                                            String localpath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount + "/").concat(brightcove_filename);
                                            String oldpath = (confPath.endsWith("/") ? confPath : confPath.concat("/")).concat(requestedAccount + "/").concat(original_filename);

                                            LOGGER.trace("CONFPATH : " + confPath);
                                            LOGGER.trace(">>PATH: " + localpath);
                                            LOGGER.trace(">>ORIGINAL: " + oldpath);
                                            //LOGGER.trace("####REQUEST GETS: " + get_response);
                                            newAsset = resourceResolver.getResource(oldpath) != null ? resourceResolver.getResource(oldpath).adaptTo(Asset.class) : resourceResolver.getResource(localpath) != null ? resourceResolver.getResource(localpath).adaptTo(Asset.class) : null;
                                            if (newAsset == null) {
                                                //IF NEW ASSET IS NULL MEANS THAT - Resource At 'localpath'


                                                String mime_type = "";
                                                //String response_j = "";


                                                if (src.startsWith("/") && !src.startsWith("//")) //HAS LOCAL THUMB PATH
                                                {
                                                    //IF LOCAL IMAGE LOAD UNSUCCESSFUL - LOAD DEFAULT
                                                    LOGGER.trace("\t\t#####>#>#>#>#>>#># INTERNAL");
                                                    LOGGER.trace("->>local/: " + src);
                                                    LOGGER.trace("[INTERNAL] IS: " + localpath);


                                                    Resource thumbRes = resourceResolver.resolve(req, src);
                                                    mime_type = mType.getMimeType(thumbRes.getName());
                                                    LOGGER.trace("MIIIIIIIIIIMEEEE******\t\t" + mime_type + " newRES: " + src);


                                                    try {
                                                        //READ THUMBNAIL FROM LOCAL ADDRESS
                                                        binary = JcrUtils.readFile(thumbRes.adaptTo(Node.class));

                                                        //THROWS REPO EXCEPTION OF NOT FOUND
                                                        //IF IT FAILS TO READ FILE FROM LOCAL, THEN IT MUST CREATE THE DEFAULT THUMBNAIL
                                                    } catch (RepositoryException e) {
                                                        LOGGER.error("Local thumbnail could not be read", e);
                                                        break;
                                                    }
                                                } else {
                                                    LOGGER.trace("->>external/: " + src);
                                                    LOGGER.trace("[EXTERNAL] LOCALPATH IS: " + localpath);
                                                    String urlParameters = "";
                                                    Map<String, String> nullmap = new HashMap<String, String>();
                                                    LOGGER.trace("[IMAGE-PULL] : " + src + " " + urlParameters + " " + nullmap);
                                                    JSONObject get_response = HttpServices.executeFullGet(src, urlParameters, nullmap);
                                                    binary = new ByteArrayInputStream((byte[]) get_response.get("binary"));
                                                    mime_type = get_response.getString("mime_type"); //<=-==== DOES NOT EXIST?!?!? MIME TYPE?
                                                    //response_j = get_response.getString("response");
                                                    LOGGER.trace("MIIIIIIIIIIMEEEE\t\t" + mime_type);

                                                    if (binary == null) //IF REMOTE IMAGE LOAD UNSUCCESSFUL - LOAD DEFAULT
                                                    {
                                                        LOGGER.error("External thumbnail could not be read");
                                                        break;
                                                    }
                                                }
                                                AssetManager assetManager = resourceResolver.adaptTo(AssetManager.class);

                                                //TODO:Check if asset exists

                                                newAsset = assetManager.createAsset(localpath, binary, mime_type, true);
                                                resourceResolver.commit();


                                                //LOGGER.trace(mime_type);
                                                //LOGGER.trace(response_j);
                                                updateAsset(newAsset, innerObj, resourceResolver, requestedAccount);

                                            } else {
                                                //ASSET HAS BEEN INITIALIZED LOCALLY
                                                //date check


                                                try {
                                                    Date local_mod_date = new Date(newAsset.getLastModified());
                                                    //LOGGER.trace("UPDATED AT::::::::: " + innerObj.get(x));


                                                    //LOGGER.trace("PRE-PARSE>>>>>>>" + innerObj.getString("updated_at") );
                                                    final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
                                                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);
                                                    Date remote_date = sdf.parse(innerObj.getString("updated_at"));

                                                    if (local_mod_date.compareTo(remote_date) < 0) {
                                                        LOGGER.trace("OLDERS-DATE>>>>>" + local_mod_date);
                                                        LOGGER.trace("PARSED-DATE>>>>>" + remote_date);
                                                        LOGGER.trace(local_mod_date + " < " + remote_date);
                                                        LOGGER.trace("MODIFICATION DETECTED");
                                                        updateAsset(newAsset, innerObj, resourceResolver, requestedAccount);


                                                    } else {
                                                        LOGGER.trace("No Changes to be Made = Asset is equivalent");
                                                    }


                                                    //                                            String formatString = "2014-08-12T17:58:50.916Z";
                                                    //                                            LOGGER.trace("DATE>>>>>"+formatString);
                                                    //                                            String[] formatStrings = { "yyyy-MM-dd\'T\'HH:mm:ss.\'SSSXXX\'"};
                                                    //
                                                    //                                            //SimpleDateFormat x = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                                                    //
                                                    //
                                                    //                                            for (String x : formatStrings) {
                                                    //                                                try {
                                                    //                                                    Date date = new SimpleDateFormat(formatString).parse(formatString);
                                                    //                                                    LOGGER.trace("\t\tDATE>>>>>"+date.toString());
                                                    //
                                                    //                                                }
                                                    //                                                catch (ParseException e)
                                                    //                                                {
                                                    //                                                    LOGGER.error("ex");
                                                    //                                                    break;
                                                    //                                                }
                                                    //                                            }


                                                    //                                            if (local_mod_date.compareTo(remote_mod_date) < 0) {
                                                    //                                                LOGGER.trace("<MATCHING DATES>>\tLOCAL/OLD " + local_mod_date + " REMOTE/NEW>>" + remote_mod_date);
                                                    //                                                //updateAsset(newAsset, innerObj, resourceResolver);
                                                    //
                                                    //                                            } else {
                                                    //                                                LOGGER.trace("<UPDATES>>\t " + local_mod_date + " < " + remote_mod_date);
                                                    //                                            }

                                                } catch (Exception p) {
                                                    LOGGER.error("Parsing exception", p);
                                                    break;
                                                }

                                            }
                                        } else {
                                            LOGGER.error("SOURCE WAS EMPTY");
                                            LOGGER.error("INVALID REMOTE ASSET SOURCE: {}", src);
                                            LOGGER.error("SRC:" + src);
                                        }
                                        //FOR LOOP


                                        LOGGER.trace(">>>>>>>>>>>>>>>>>>>>>");
                                    } else {
                                        LOGGER.trace("Video does not have correct initialization (missing core properties) - skipping: " + innerObj.toString(1));
                                    }
                                    //MAIN VIDEO LOOP
                                }

                                LOGGER.debug("### " + itemsArr.toString(1));


                        } catch (JSONException j) {
                            LOGGER.error("JSON EXCEPTION", j);
                        } catch (IllegalArgumentException i) {
                            LOGGER.error("IllegalArgumentException", i);
                        } catch (RepositoryException r) {
                            LOGGER.error(" javax.jcr.RepositoryException : INVALID TAG CHARS?", r);
                        } catch (RuntimeException t) {
                            LOGGER.error(" javax.jcr.RepositoryException : INVALID TAG CHARS?", t);
                        }

                        //HERE WE MAY CATCH THE TAGGING PROBLEM AND ALLOW LOOP TO KEEP TRAVERSING THROUGH ASSETS







                    } else {
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

            if (newAsset != null) {

                LOGGER.trace(innerObj.toString(1));

                LOGGER.trace("updatedASSET: " + newAsset.getPath());
                //ONLY IF CREATE SUCCESSFUL
                Resource assetRes = newAsset.adaptTo(Resource.class);
                Resource metadataRes = assetRes.getChild("jcr:content/metadata");
                ModifiableValueMap map = metadataRes.adaptTo(ModifiableValueMap.class);


                long current_time_millisec = new java.util.Date().getTime();
                map.put("brc_lastsync", current_time_millisec);



                TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
                map.put("brc_account_id", requestedAccount);
                List<String> tags = new ArrayList<String>();
                map.put("cq:tags", tags.toArray());

                for (String x : fields) {
                    LOGGER.trace("[X] {} " + innerObj.getString(x), x);

                    if (innerObj.has(x) && !innerObj.get(x).equals(null)) {
                        String key = x;

                        //                    if (x.equals("images"))
                        //                    {
                        //                    //TODO:? WHY HERE? TO CREATE?
                        //
                        //                    }
                        if (x.equals("tags")) {
                            key = "cq:".concat(x);
                        } else if ("name".equals(x)){
                            key = "dc:title";
                        } else {
                            key = "brc_".concat(x);
                        }
                        // SECOND PRINT STATEMNT - LOGGER.trace("" + x + " -> " + "[" + innerObj.get(x) + "] is null? -> " + innerObj.get(x).equals(null));

                        Object obj = innerObj.get(x);


                        //IF JSON ARRAY
                        if (obj instanceof JSONArray) {
                            JSONArray objArray = (JSONArray) obj;

//                            if (x.equals("text_tracks")) {
//                                //GET TEXT TRACK NODE IF IT EXISTS
//
//                                x = "brc_".concat(x);
//                                Node tracks_node; //GET TRACKS NODE TO ADD SUBNODES
//
//                                tracks_node = metadataRes.getChild(x) == null ? metadataRes.adaptTo(Node.class).addNode(x) : metadataRes.adaptTo(Node.class).getNode(x);
//                                if (tracks_node != null) {
//                                    Node text_track_node; //FOR EACH TRACK
//                                    JSONObject text_track_obj;
//                                    NodeIterator tracks = tracks_node.getNodes();
//
//                                    for (int w = 0; w < objArray.length(); w++) {
//                                        //CHECK IF TRACK IS IN THE TEXT TRACKS GROUP
//                                        text_track_obj = objArray.getJSONObject(w);
//
//                                        LOGGER.trace("***********TEXT_TRACK***********");
//                                        LOGGER.trace(text_track_obj.toString(1));
//                                        String track_id = text_track_obj.has("id") ? text_track_obj.getString("id") : Integer.toString(w);
//
//                                        text_track_node = !tracks_node.hasNode(track_id) ? tracks_node.addNode(track_id) : tracks_node.getNode(track_id);
//
//                                        Iterator keys = text_track_obj.keys();
//                                        Node text_track_sources;
//                                        Node text_track_source;
//                                        while (keys.hasNext()) {
//                                            Object k = keys.next();
//
//                                            if (k.equals("sources")) {
//                                                JSONArray sources = text_track_obj.getJSONArray("sources");
//                                                text_track_source = !text_track_node.hasNode("sources") ? text_track_node.addNode("sources") : text_track_node.getNode("sources");
//                                                String[] values = new String[sources.length()];
//                                                for (int src_cnt = 0; src_cnt < sources.length(); src_cnt++) {
//                                                    JSONObject source_obj = sources.getJSONObject(src_cnt);
//                                                    text_track_source.setProperty("src_".concat(Integer.toString(src_cnt)), source_obj.getString("src"));
//                                                }
//                                                LOGGER.trace("**SOURCES**" + sources.toString(1));
//
//
//                                                //                                        if(k instanceof JSONArray && k.equals("sources"))
//                                                //                                        {
//                                                //
//                                                //                                            //INITIALIZES SOURCES SUBNODES
//                                                //                                            text_track_sources = !text_track_node.hasNode("sources") ? text_track_node.addNode("sources") : text_track_node.getNode("sources");
//                                                //
//                                                //                                            String value = text_track_obj.getString(k.toString());
//                                                //                                            text_track_node.setProperty(k.toString(), value);
//                                                //
//                                                ////                                            for(int src_cnt = 0 ; src_cnt < ((JSONArray) k).length(); src_cnt++)
//                                                ////                                            {
//                                                ////                                                JSONObject cur_src = ((JSONArray) k).getJSONObject(src_cnt);
//                                                ////                                                text_track_source = !text_track_sources.hasNode(Integer.toString(src_cnt)) ? text_track_sources.addNode(Integer.toString(src_cnt)) : text_track_sources.getNode(Integer.toString(src_cnt));
//                                                ////                                                text_track_source.setProperty("src", cur_src.getString("src"));
//                                                ////                                                LOGGER.trace("**SOURCE**: " + cur_src.getString("src"));
//                                                ////                                            }
//                                            } else if (!k.equals("sources")) {
//                                                //SETS ALL OTHER PROPERTIES
//                                                String value = text_track_obj.getString(k.toString());
//                                                text_track_node.setProperty(k.toString(), value);
//                                            }
//
//                                        }
//
//                                    }
//
//                                }
//                                resourceResolver.commit();
//                                LOGGER.trace("***********/TEXT_TRACK***********");
//                            }
                            if (x.equals("tags")) {
                                for (int cnt = 0; cnt < objArray.length(); cnt++) {
                                    String tagValue = objArray.getString(cnt);
                                    try {
                                        if (tagManager.canCreateTag(tagValue)) {

                                            Tag tag = tagManager.createTag(tagValue.replaceAll(": ",":").trim(), tagValue, "");


                                            //TODO: We handled this trim as above, to





                                            //Tag tag = tagManager.createTagByTitle(tagValue, Locale.US);
                                            resourceResolver.commit();
                                            LOGGER.trace("t> " + tag.toString());
                                            //tagManager.setTags(assetRes, new Tag[]{tag}, true);
                                        } else {
                                            //Tag[] tags = tagManager.findTagsByTitle(tagValue, Locale.US);
                                            //tagManager.setTags(assetRes, tags, true);
                                            LOGGER.trace("TAG CANT BE CREATED or REPLACED");
                                        }
                                        tags.add(tagValue.trim());
                                    } catch (InvalidTagFormatException e) {
                                        LOGGER.error("Invalid Tag Format", e);
                                    }
                                }
                                resourceResolver.commit();
                                map.put(key, tags.toArray());
                            } else {
                                map.put(key, objArray.join("#@#").split("#@#"));
                            }
                        } else if (obj instanceof JSONObject) {
                            JSONObject objObject = (JSONObject) obj;


                            if (x.equals("images"))
                            {

                                LOGGER.trace("**********IMAGES*******");
                                LOGGER.trace(objObject.toString(1));
                                if (objObject.has("poster"))
                                {
                                    JSONObject images_poster_obj = objObject.getJSONObject("poster");
                                    String src = images_poster_obj.getString("src");
                                    //DO GET FOR RENDITION -> TO ASSET "brc_poster"
                                    URL srcURL = new URL(src);
                                    InputStream ris = srcURL.openStream();
                                    //Map<String,Object> rendition_map = new HashMap<String,Object>();
                                    newAsset.addRendition("brc_poster.png",ris,"image/jpeg");
                                }

                                if (objObject.has("thumbnail"))
                                {
                                    JSONObject images_poster_obj = objObject.getJSONObject("thumbnail");
                                    String src = images_poster_obj.getString("src");
                                    //DO GET FOR RENDITION -> TO ASSET "brc_thumbnail"

                                    InputStream ris = new URL(src).openStream();
                                    //Map<String,Object> rendition_map = new HashMap<String,Object>();
                                    newAsset.addRendition("brc_thumbnail.png",ris,"image/jpeg");

                                    ris = new URL(src).openStream();//<= FIXES DISMISSED InputStream*
                                    newAsset.addRendition("original",ris,"image/jpeg");


                                }

                                LOGGER.trace("**********///IMAGES*******");
                            } else {


                                //GENERIC SUBMODULE CASES

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
                        } else {
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
                                        }catch (Exception e)
                                        {
                                            LOGGER.error("*!*!*! Duration Check Error:!", e);
                                        }
                                    //END DURATION CHECK AND SET

                            map.put(key, obj); //MAIN SET OF THE KEYS->VALUES FOR THIS VIDEO OBJECT
                        }
                    }
                }
                resourceResolver.commit();
            } else {
                LOGGER.error("Asset creation failed");
            }
        }
        catch (JSONException e)
        {
            LOGGER.error("JSON EXCEPTION", e);
        } catch (NullPointerException e) {
            LOGGER.error("NULL POINTER", e);
        } catch (MalformedURLException e)
        {
            LOGGER.error("FILE NOT FOUND", e);
        } catch (IOException e)
        {
            LOGGER.error("FILE NOT FOUND", e);
        }

    }
}
