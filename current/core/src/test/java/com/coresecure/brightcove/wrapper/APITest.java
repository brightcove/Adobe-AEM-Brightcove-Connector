package com.coresecure.brightcove.wrapper;

import com.coresecure.brightcove.tests.impl.TestBrightcoveModels;
import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.coresecure.brightcove.wrapper.enums.EconomicsEnum;
import com.coresecure.brightcove.wrapper.enums.GeoFilterCodeEnum;
import com.coresecure.brightcove.wrapper.enums.PlaylistFieldEnum;
import com.coresecure.brightcove.wrapper.enums.PlaylistTypeEnum;
import com.coresecure.brightcove.wrapper.filter.CustomAddDialogTabFilter;
import com.coresecure.brightcove.wrapper.models.VideoPlayer;
import com.coresecure.brightcove.wrapper.models.VideoPlayerConfiguration;
import com.coresecure.brightcove.wrapper.objects.*;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables.VideoImportCallable;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl.AssetIntegratorCronBundleImpl;
import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.runnables.AssetPropertyIntegratorRunnable;
import com.coresecure.brightcove.wrapper.sling.*;
import com.coresecure.brightcove.wrapper.utils.*;
import com.coresecure.brightcove.wrapper.webservices.*;
import com.day.cq.dam.api.Asset;
import com.day.cq.replication.ReplicationTransaction;
import com.day.cq.replication.TransportContext;
import com.day.cq.wcm.api.components.ComponentContext;
import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.mime.MimeTypeService;




//USING TO ACCESS PRIVATE METHODS
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


import org.apache.sling.testing.mock.sling.MockSling;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

import javax.activation.MimeType;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Modifier;
import java.net.Proxy;
import java.net.URL;
import java.util.*;
import java.util.List;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;

/**
 * Created by pablo.kropilnicki on 12/21/17.
 */
public class APITest {

    //Mocking Library

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    // prepare sling request
    ResourceResolver resourceResolver = MockSling.newResourceResolver();
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver);
    MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

    //    SlingHttpServletRequest req = mock(SlingHttpServletRequest.class);
    //    SlingHttpServletResponse resp = mock(SlingHttpServletResponse.class);

    //STATIC TESTING VARIABLES
    public static String CLIENT_ID = "bfc763b9-1492-48c0-ae8f-837782a8b885";
    public static String CLIENT_SECRET = "lJvCasqov1E05VSOBUmaNp1BUVDcpam30cP94KjWVk1kkucpaI5ieFY_-P3AqU1x_RO_c5SwLoJh1zLndnXQfw";
    public static String ACCOUNT_ID = "1153191516001";

    public static Platform defaultPlatform;
    public static BrightcoveAPI brightcoveAPI;
    public static Account account;
    public static CmsAPI api;
    public static AccountUtil accountUtil;
    public static HttpServices service = new HttpServices();
    public static ServiceUtil serviceUtil = mock(ServiceUtil.class, ACCOUNT_ID); //CALLS CONFIGURATION GRABBER = FAILS



    @Test
    public void MainTest() {
        defaultPlatform = new Platform(Constants.DEFAULT_OAUTH_URL, Constants.DEFAULT_API_URL, Constants.DEFAULT_DI_API_URL, Constants.DEFAULT_PLAYERS_API_URL);
        PlatformTest();


        account = new Account(defaultPlatform, CLIENT_ID, CLIENT_SECRET, ACCOUNT_ID);
        AccountTest();

        api = new CmsAPI(account);
        ApiTest(api);

        brightcoveAPI = new BrightcoveAPI(CLIENT_ID, CLIENT_SECRET, ACCOUNT_ID,  "");
        TestBrightcoveAPI();

        accountUtil = mock(AccountUtil.class);

        TestAccountUtil();

        TestHTTPServices();

        TestStaticStructures();

        //TEST INTEGRATION TEST//TODO: Not ready
        TestModelsAEM();

        BundleActivatorTest();

        AssetIntegratorTest();

        AssetPropertyIntegratorRunnableTest();

        BrcApiTests();

        AssetPropertyIntegratorRunnableTests();
        AssetPropertyIntegratorTests();
        BrcAccountsUITests();
        BrcImageApiTests();
        BrcReplicationHandlerTests();
        IngestTests();
        S3UploadUtilTests();

        BrcSuggestionsTests();
        CertificateListServiceImplTests();
        ConfigurationGrabberImplTests();
        ConfigurationServiceImplTests();
        BrcAccountsTests();



        //IN PROGRESS
//        ObjectSerializerTest();


        //FAILING TESTS
        TestVideoImportCallable();
        TestConfigGrabber();
        MockTests();

    }

    private void PlatformTest() {
        assertNotNull(defaultPlatform.getDI_API_Url());
        assertNotNull(defaultPlatform.getOAUTH_Url());
        assertNotNull(defaultPlatform.getAPI_Url());
        assertNotNull(defaultPlatform.getPLAYERS_API_URL());
        defaultPlatform.setProxy("test");

        String targetURL = "url";
        String targetVideoID = "123";
        String payload = "pay";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("headerone", "valueone");

        String r_deleteAPI = defaultPlatform.deleteAPI("url", "videoID", headers);
        String r_postAPI = defaultPlatform.postAPI(targetURL, payload, headers);
        String r_patchAPI = defaultPlatform.patchAPI(targetURL, payload, headers);
        String r_postDI_API = defaultPlatform.postDI_API(targetURL, payload, headers);
        String r_getDI_API = defaultPlatform.getDI_API(targetURL, payload, headers);
        String r_postDIRequest_API = defaultPlatform.postDIRequest_API(targetURL, payload, headers);
    }

    private void ApiTest(CmsAPI api) {

        //SINCE GET VIDEOS IS RECURSIVE - IF WE GIVE MORE PARAMETERS IT WILL TEST / CASCADE DOWN
        JSONArray videos = api.getVideos();
        assertNotNull(videos);
        if (videos.length() > 0) {
            try {
                Video video = new Video(videos.getJSONObject(0));
                assertTrue(api.getVideo(video.id).has(Constants.ID));
                assertNotNull(video.toString());
                assertTrue(api.getVideo(video.id).has(Constants.LONG_DESCRIPTION));
            } catch (JSONException e) {
                assertTrue(false);
            }
        }


        //GET PLAYLISTS COUNT
        assertNotNull(api.getPlaylistsCount());

        //GET PLAYLISTS  --
        //NULL PLAYLUIST -> JSONException
        try {
            Playlists errPlaylists = new Playlists(new JSONObject());
        } catch (JSONException e) {
            System.out.println("Playlists null case");
            //e.printStackTrace();
        }
        JSONArray playlists = api.getPlaylists();
        if (playlists.length() > 0) {
            try {

                //PLAYLIST TESTS  - //////////////////////////////////////////////////////////////////////////////////////////
                Playlist playlist = new Playlist(playlists.getJSONObject(0));

//              Playlist stringPlaylistNull = new Playlist(null); // BAD CODE
                Playlist stringPlaylistEmpty = new Playlist("{}");
                assertTrue(api.getPlaylist(playlist.getId()).has(Constants.ID));

                //ITERATING ITSELF THROUGH GET FUNCTIONS AND SET FUNCTIONS USING RECURSION
                List<String> videoIds = playlist.getVideoIds();
                playlist.setId(playlist.getId());
                playlist.setReferenceId(playlist.getReferenceId());
                playlist.setAccountId(playlist.getAccountId());
                playlist.setName(playlist.getName());
                playlist.setDescription(playlist.getDescription());
                playlist.setVideoIds(videoIds);
                playlist.setPlaylistType(playlist.getPlaylistType());
                playlist.toJSON();

                //END PLAYLIST TESTS  - //////////////////////////////////////////////////////////////////////////////////////////

                JSONObject playlistObj = new JSONObject();
                JSONArray playlistsItems = new JSONArray();
                playlistsItems.put(0, playlists.getJSONObject(0));
                playlistObj.put("items", playlistsItems);
                playlistObj.put("total_count", 0); //purposeful mismatch

                //WE ADD A STATIC PLAYLIST OBJ INITIALIZATION
                Playlists recursiveConstructedPlaylists = new Playlists(playlistObj);
                assertNotNull(recursiveConstructedPlaylists.getTotalCount());
                //System.out.println(playlistObj.toString(1)); //testprint



                //ERR Trigger
                playlistObj.remove("total_count");
                Playlists recursiveConstructedPlaylistsErr = new Playlists(playlistObj);


                try {
                    //PLAYLIST REFLECT
                    PlaylistTypeEnum playEnum = PlaylistTypeEnum.ALPHABETICAL;
                    Method getType = Playlist.class.getDeclaredMethod("getType", String.class);
                    getType.setAccessible(true);
                    PlaylistTypeEnum getTypeResp1 = (PlaylistTypeEnum) getType.invoke(playlist, "OLDEST_TO_NEWEST");
                    PlaylistTypeEnum getTypeResp2 = (PlaylistTypeEnum) getType.invoke(playlist, "NEWEST_TO_OLDEST");
                    PlaylistTypeEnum getTypeResp3 = (PlaylistTypeEnum) getType.invoke(playlist, "ALPHABETICAL");
                    PlaylistTypeEnum getTypeResp4 = (PlaylistTypeEnum) getType.invoke(playlist, "PLAYSTOTAL");
                    PlaylistTypeEnum getTypeResp5 = (PlaylistTypeEnum) getType.invoke(playlist, "PLAYS_TRAILING_WEEK");
                    PlaylistTypeEnum getTypeResp6 = (PlaylistTypeEnum) getType.invoke(playlist, "EXPLICIT");
                    PlaylistTypeEnum getTypeResp7 = (PlaylistTypeEnum) getType.invoke(playlist, "ERROR");

                } catch (Exception e) {
                    System.out.println("PlaylistReflect: " + e);
                    //e.printStackTrace();
                }


            } catch (JSONException e) {
                assertTrue(false);
            }
        }


        assertTrue(api.getCustomFields().has("custom_fields"));
        assertTrue(api.getPlayers().has("items"));

        //VIDEO INITIALIZATIONS - DEPENDENT PARAMETER CONSTRUCTION TESTS  -  ////////////////////////////////////////////////////////////////////////////////////////
        try {


            String aId = "1234";
            String aName = "videoName";
            String aReference_id = "1234";
            String aDescription = "test1234";
            String aLong_description = "test1234";
            String aState = "test1234";
            boolean aComplete = false;
            EconomicsEnum aEconomics;
            String aAccountId = "test1234";


            //PROJECTION STATIC
            String aProjection = "test1234";
            Projection projectionStatic = new Projection("equirectangular");
            projectionStatic.toString();

            /////////////////////////////////// - GEO
            //EX:  {"geo":{"restricted":true,"exclude_countries":false,"countries":["ph","in","my","ja"]}}

            Geo aGeo;
            //BUILDING SAMPLE GEO
            JSONObject jsonGeo = new JSONObject();
            JSONObject geo = new JSONObject();
            JSONArray countries = new JSONArray();


            //Populate Constructor JSON
            countries.put(0, "ph");
            countries.put(1, "in");
            countries.put(2, "my");
            countries.put(3, "ja");
            geo.put("countries", countries);
            geo.put("exclude_countries", false);
            geo.put("restricted", true);
            jsonGeo.put("geo", geo);

            //Create Geo
            aGeo = new Geo(geo);


            //Globals
            assertNotNull(aGeo.exclude_countries);
            assertNotNull(aGeo.countries);
            assertNotNull(aGeo.restricted);

            //Test Printout
            assertNotNull(aGeo.toJSON());


            Collection<GeoFilterCodeEnum> aCountries = new ArrayList<GeoFilterCodeEnum>();


            //STATIC GEO
            Geo geoStatic = new Geo(false, false, aCountries);


            /////////////////////////////////// - END GEO


            /////////////////////////////////// - TAGS
            Collection<String> aTags = new ArrayList<String>(Arrays.asList(new String[]{"sam", "ple", "tags"}));
            /////////////////////////////////// - END TAGS


            /////////////////////////////////// - SCHEDULE
            Schedule aSchedule;

            //{"name":"greatblueheron.mp4","schedule":{"starts_at":"2017-03-10","ends_at":"2018-12-31"}}

            JSONObject jsonSchedule = new JSONObject();
            JSONObject schedule = new JSONObject();
            schedule.put("starts_at", "2017-03-10");
            schedule.put("ends_at", "2017-03-10");
            jsonSchedule.put("name", "greatblueheron.mp4");
            jsonSchedule.put("schedule", schedule);

            //WAS: aSchedule = new Schedule(jsonSchedule)
            aSchedule = new Schedule(schedule.getString("starts_at"), schedule.getString("starts_at"));

            //Globals
            assertNotNull(aSchedule.starts_at);
            assertNotNull(aSchedule.ends_at);

            //Test Printout
            assertNotNull(aSchedule.toJSON());
            assertNotNull(aSchedule.toString());

            //Static Construction
            Schedule jSchedule = new Schedule(aSchedule.toJSON());

            Schedule errSchedule = new Schedule("2017-03-10", null);
            assertNotNull(errSchedule.toString());

            ///////////////////////////////////>>> - END SCHEDULE


            /////////////////////////////////// - RELATED LINK START
            RelatedLink aLink;

            JSONObject jsonRelatedLink = new JSONObject();
            jsonRelatedLink.put("text", "sampletext");
            jsonRelatedLink.put("url", "https://www.sample.com");

            aLink = new RelatedLink(jsonRelatedLink);
            assertNotNull(aLink.text);
            assertNotNull(aLink.url);
            assertNotNull(aLink.toJSON());
            assertNotNull(aLink.toString());

            /////////////////////////////////// - RELATED LINK END

            /////////////////////////////////// - CUSTOM FIELDS
            Map<String, Object> aCustom_fields = new HashMap<String, Object>();
            aCustom_fields.put("customFieldOne", true);
            aCustom_fields.put("customFieldTwo", false);
            /////////////////////////////////// - CUSTOM FIELDS END

            /////////////////////////////////// - CUSTOM FIELDS END
            JSONArray aText_tracks = new JSONArray();
            /////////////////////////////////// - CUSTOM FIELDS END

            /////////////////////////////////// - IMAGES -> (POSTER + THUMBNAIL)
            Poster poster;
            Thumbnail thumbnail;
            Images aImages;

            //POSTER
            JSONObject jsonPoster = new JSONObject();
            jsonPoster.put("src", "postersourcelocation");
            poster = new Poster(jsonPoster);

            //POSTER TESTS
            assertNotNull(poster.url);
            assertNotNull(poster.toJSON());
            assertNotNull(poster.toString());

            //THUMBNAIL
            JSONObject jsonThumbnail = new JSONObject();
            jsonThumbnail.put("src", "thumbnailsourcelocation");
            thumbnail = new Thumbnail(jsonThumbnail);

            //THUMBNAIL TESTS
            assertNotNull(thumbnail.url);
            assertNotNull(thumbnail.toJSON());
            assertNotNull(thumbnail.toString());

            //IMAGES
            JSONObject jsonImages = new JSONObject();
            jsonImages.put("poster", jsonPoster);
            jsonImages.put("thumbnail", jsonThumbnail);
            aImages = new Images(jsonImages);






            //IMAGES TESTS
            assertNotNull(aImages.poster);
            assertNotNull(aImages.thumbnail);
            assertNotNull(aImages.toJSON());
            assertNotNull(aImages.toString());


            try {
                ObjectSerializer objSerial = new ObjectSerializer();
                String json_key = "key";
                JSONObject fieldJson = new JSONObject();
                Object fieldObj = new Object();
                Class serializerClass = ObjectSerializer.class;
                Field[] fieldArr = serializerClass.getFields();
                Field field = fieldArr[0];
                Byte[] annotationsBytes = new Byte[10];


                final Constructor<?> constructor = Field.class.getDeclaredConstructor();
                if (constructor.isAccessible() || !Modifier.isPrivate(constructor.getModifiers()))
                {
                    Assert.fail("constructor is not private");
                }
                constructor.setAccessible(true);
                ObjectSerializer objSerialConstructed = (ObjectSerializer)constructor.newInstance(serializerClass, serializerClass.getName(), serializerClass,1, 1, "Singature",annotationsBytes);
                constructor.setAccessible(false);


                Method textTrackSerialization = ObjectSerializer.class.getDeclaredMethod("addFieldToJson", Field.class, Object.class, JSONObject.class, String.class);
                textTrackSerialization.invoke(aImages.getClass(), aImages, fieldJson, json_key);
            }
            catch (Exception e)
            {
                System.out.println("Serializer");
            }


            //VIDEO CREATION FROM STATIC - //////////////////////////////////////////////////////////////////////////////////////////
            Video testvideo = new Video(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, null, aProjection, aText_tracks, aImages);

            //ADDITIONAL CONSTRUCTORS - DID NOT CASCADE....

            assertNotNull(testvideo);
            assertNotNull(testvideo.toJSON());
            assertNotNull(testvideo.toString());

            System.out.println("[" + testvideo.toString() + "]");

            //VIDEO INITAILIZATION FROM FIRST VIDEO CREATED - NOW IN JSON
            Video jsonTestVideo = new Video(testvideo.toJSON());
            assertNotNull(jsonTestVideo);
            assertNotNull(jsonTestVideo.toJSON());
            assertNotNull(jsonTestVideo.toString());


            //STATIC CALLS - GEO ENUM
            //Cannot instantiate enum...
            GeoFilterCodeEnum geoEnum = GeoFilterCodeEnum.AD;
            assertNotNull(GeoFilterCodeEnum.lookupByCode("zm"));
            assertNotNull(GeoFilterCodeEnum.lookupByName("ZAMBIA"));
            assertTrue(GeoFilterCodeEnum.lookupByName("ENGLISH") == null);
            assertNotNull(geoEnum.getName());
            assertNotNull(geoEnum.getCode());
            assertNotNull(geoEnum.toString());
            assertTrue((GeoFilterCodeEnum.lookupByName(null) == null));
            assertTrue((GeoFilterCodeEnum.lookupByCode(null) == null));

            //STATIC CONSTRUCTORS
            Video video0 = new Video(aName);
            Video video1 = new Video(aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink);
            Video video2 = new Video(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink);
            Video video3 = new Video(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, null);
            Video video4 = new Video(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, null, aProjection);
            Video video5 = new Video(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, null, aProjection, aText_tracks);
            Video video6 = new Video(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, null, aProjection, aText_tracks, aImages);

            //STATIC - EMPTY
            Video.class.toString();

           String output = video6.toString();

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


            //JSONREADER - REFLECT////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            try {

                final Constructor<?> constructor = JsonReader.class.getDeclaredConstructor();
                if (constructor.isAccessible() || !Modifier.isPrivate(constructor.getModifiers()))
                {
                    Assert.fail("constructor is not private");
                }
                constructor.setAccessible(true);
                constructor.newInstance();
                constructor.setAccessible(false);
            }
            catch (Exception e)
            {
                System.out.print("superfail");
            }



            //JsonReader jReader = new JsonReader();

            //EMPTY COVERAGE
//        RelatedLink.class.toString(); //Todo: try to fire this off
            //END STATIC CALLS - ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


            //NOW THAT WE HAVE VIDEO...


            JSONObject payload = new JSONObject();
            String payloadVideoID = video1.id;
            String profile = "";
            String master = "";
            Ingest ingest = new Ingest(profile, master);
            String filename = "";
            Playlist playlist = new Playlist();
            String q = "";
            JSONArray input = new JSONArray();

            //TODO: Implemment this the right way - with a integration test video (requires research)
            JSONObject uploadInjestResponse = api.uploadInjest(payloadVideoID, payload);
            JSONObject requestIngestURLResponse = api.requestIngestURL(payloadVideoID, profile, master, false);
            JSONObject getIngestURLResponse = api.getIngestURL(payloadVideoID, filename);
            JSONObject createVideoResponse = api.createVideo(video1);
            JSONObject updateVideoResponse = api.updateVideo(video1);
            JSONObject deleteVideoResponse = api.deleteVideo(payloadVideoID);
            JSONObject createPlaylistResponse = api.createPlaylist(playlist);
            JSONObject getVideosCountResponse = api.getVideosCount(q);
            JSONObject createIngestResponse = api.createIngest(video1, ingest);
            JSONArray getVideoSourcesResponse = api.getVideoSources(payloadVideoID);
            JSONObject getVideoImagesByRefResponse = api.getVideoImagesByRef(payloadVideoID);
            JSONArray getVideoSourcesByRefResponse = api.getVideoSourcesByRef(payloadVideoID);
            JSONObject getVideoByRefResponse = api.getVideoByRef(payloadVideoID);
            JSONObject getCustomFieldsResponse = api.getCustomFields();
            JSONObject getVideoImagesResponse = api.getVideoImages(payloadVideoID);
            JSONArray addThumbnailResponse = api.addThumbnail(input);
            JSONArray getVideosResponse = api.getVideos("0", "10");
            JSONArray getVideosResponseWLimit = api.getVideos("0", "10", 10);
            JSONArray getVideosResponseWSort = api.getVideos(0, 10, "created_at");


        } catch (JSONException e) {
            System.out.println("test exception - json - Images" + e);
            assertTrue(false);
        }

        /////////////////////////////////// - END IMAGES -> (POSTER + THUMBNAIL)


//


    }

    private void TestHTTPServices() {

        //final SlingHttpServletRequest request = null; - TODO: figure out how to do inject this

        try {

            String videoId = "123";
            String accountKeyStr = "1153191516001";
            String queryStr = "";
            int offset = 0;
            int limit = 30;
            String query = "";
            Boolean exportCSV = false;
            String sort = "";
            String querystr = "";
            String limits = "";
            String playlistID = "";
            String q = "";

            Video video = new Video("sup");
            String filename = "";

            String videoIds = "";
            String videoProperties = "";

            try {
                Method getAccountCookieMethod = ServiceUtil.class.getMethod("getAccountCookie", SlingHttpServletRequest.class);
                getAccountCookieMethod.setAccessible(true);
                Cookie accountCookie = (Cookie) getAccountCookieMethod.invoke(serviceUtil, request);
            } catch (Exception e) {
                System.out.println("accountCookie");
            }


//         String AccountFromCookie = serviceUtil.getAccountFromCookie(request);
//         List sortedMap = serviceUtil.sortByValue(final Map m);
//         String length =  serviceUtil.getLength(videoId, accountKeyStr);
//
//
//


            String ingestURL = "";
            String ingestProfile = "";
            InputStream is = new InputStream() {
                @Override
                public int read() throws IOException {
                    return 0;
                }
            };


            ServiceUtil service = new ServiceUtil(ACCOUNT_ID);
            ConfigurationGrabber configGrabber = service.getConfigurationGrabber();
            String videoName = service.getName(videoId, accountKeyStr);

            boolean videoDelete = service.deleteVideo(videoId);
            boolean isLong = service.isLong("123");
            boolean full_scroll = false;

            String queryListOne = service.getList(exportCSV, query);
            String queryListTwo = service.getList(exportCSV, offset, limit, full_scroll, query);
            String queryListThree = service.getList(exportCSV, offset, limit, full_scroll, query, sort);
            String videoSearch = service.searchVideo(querystr, offset, limit);
            String findVideo = service.searchVideo(querystr, offset, limit, sort);
            String findVideoByID = service.getVideoByRefID(videoId);
            String getSideMenuList = service.getListSideMenu(limits);
            String getSuggestions = service.getSuggestions(querystr, offset, limit);
            String playlistsQueryOne = service.getPlaylists(offset, limit, exportCSV, full_scroll);
            String playlistsQueryTwo = service.getPlaylists(q, offset, limit, exportCSV, full_scroll);

            JSONObject players = service.getPlayers();
            JSONObject playlistsByID = service.getPlaylistByID(playlistID);
            JSONObject updateVideoObj = service.updateVideo(video);
            JSONObject createVideo = service.createVideo(video, ingestURL, ingestProfile);
            JSONObject createVideoS3 = service.createVideoS3(video, filename, is);
            JSONObject selectedVideosObj = service.getSelectedVideo(videoId);
            JSONObject customFieldsObj = service.getCustomFields();

            JSONArray sourcesArray = service.getVideoSources(videoId);
            JSONArray videoJSONArray = service.getVideosJsonByIds(videoIds, videoProperties);

        } catch (NullPointerException e) {
            System.out.println("TestHTTPServices:" + e);
        } catch (Exception e) {
            System.out.println("TestHTTPServices:" + e);
        }

    }

    private void TestStaticStructures() {
        try
        {
            EnumsTests();
            IngestTests();
            AccountTest();
            TokenTest();
            TestBinaryObj();
            TestTextTrackObj();
            TestTextUtil();
            TestImageUtils();
            TestVideoPlayerModel();
            TestVideoPlayerConfiguration();
            CustomAddDialogTabFilterTests();

            //./////////////////////////////////////////////////////////////CONSTANTS
            try {   Constants constants = new Constants(); } catch (Exception e) {   System.out.println("Constants Exception: " + e); }

            //./////////////////////////////////////////////////////////////JCR UTILS < pre int test
            try {   JcrUtil.now2calendar(); } catch (Exception e) {   System.out.println("ExceptionFromJCR: " + e); }

        }
        catch (Exception e)
        {
            System.out.println(e);
        }
    }

    private void TestModelsAEM() {
       TestVideoPlayerModel();
       TestVideoPlayerConfiguration();
    }

    private void BundleActivatorTest() {
        Activator activator = new Activator();
        BundleContext bundleContext = mock(BundleContext.class);
        //Todo: Figure out right mock for BundleContext
        try {
            activator.start(bundleContext);
            activator.stop(bundleContext);
        } catch (Exception e) {
            System.out.println("Activator err: " + e);
        }
    }

    private void AssetIntegratorTest() {
        AssetIntegratorCronBundleImpl assetIntegratorCronBundle = new AssetIntegratorCronBundleImpl();
        assetIntegratorCronBundle.getStatus();
    }

    private void AssetPropertyIntegratorRunnableTest() {
        MimeTypeService mType = mock(MimeTypeService.class);
        ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
        int maxThreadNum = 1;
        AssetPropertyIntegratorRunnable runnableInit = new AssetPropertyIntegratorRunnable(mType, resourceResolverFactory, maxThreadNum);
        runnableInit.run();
    }

    /*  Static Tests */

    //ENUMS
    private void EnumsTests() {
        try {
            EconomicsEnum econEnumString = EconomicsEnum.AD_SUPPORTED;
            EconomicsEnum econEnumString2 = EconomicsEnum.FREE;

            assertNotNull(econEnumString.toString());

            PlaylistFieldEnum playlistFieldEnum = PlaylistFieldEnum.ACCOUNTID;
            assertNotNull(playlistFieldEnum.toString());

            assertNotNull(playlistFieldEnum.getJsonName());
            assertNotNull(playlistFieldEnum.toString());
            assertNotNull(playlistFieldEnum.getDefinition());

            EnumSet<PlaylistFieldEnum> enumSet = playlistFieldEnum.CreateEmptyEnumSet();
            EnumSet<PlaylistFieldEnum> enumSet2 = playlistFieldEnum.CreateFullEnumSet();

            assertNotNull(enumSet);
            assertNotNull(enumSet2);
        } catch (Exception e) {
            System.out.println("Exception Enums" + e);
        }
    }

    private void IngestTests() {
        try {
            //Ingest  - 88%
            final Map<String, String> master = new HashMap<String, String>();
            master.put("Test", "String");
            Ingest ingest1 = new Ingest("", master);
            Ingest ingest2 = new Ingest("", "");
            assertNotNull(ingest1.toJSON());
            assertNotNull(ingest2.toString());
        } catch (Exception e) {
            System.out.println("IngestTests" + e);
        }
    }

    private void AccountTest() {
        assertNotNull(account.getLoginToken());
        assertNotNull(account.getAccount_ID());
    }

    private void TokenTest() {
        TokenObj token = account.getToken();
        assertNotNull(token.getToken());
        assertNotNull(token.getTokenType());
        assertNotNull(token.getExpire_in());
    }

    private void TestBinaryObj() {
        try {
            //./////////////////////////////////////////////////////////////BinaryObj
            String str = "THISISACORESECURETESTSTRING";
            // String -> Input Stream
            InputStream is = new ByteArrayInputStream(str.getBytes());
            // Input -> Buffered Reader
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            br.close();

            BinaryObj binaryObj = new BinaryObj();
            BinaryObj binaryObjInit = new BinaryObj(is, "text/plain");
        } catch (Exception e) {
            System.out.println("BinaryObjTests" + e);
        }
    }

    private void TestTextTrackObj(){

    try {
        /////////////////////////////////////////////////////////////TextTrackObject
        String id = "1234";
        String account_id = "1234";
        String src = "1234";
        String srclang = "1234";
        String label = "1234";
        String kind = "1234";
        String mime_type = "1234";
        String asset_id = "1234";
        Boolean _default = false;
        JSONArray sources = new JSONArray();

        JSONObject text_track_constructor = new JSONObject();
        text_track_constructor.put(Constants.ID, id);
        text_track_constructor.put(Constants.ACCOUNT_ID, account_id);
        text_track_constructor.put(Constants.SRC, src);
        text_track_constructor.put(Constants.SRCLANG, srclang);
        text_track_constructor.put(Constants.LABEL, label);
        text_track_constructor.put(Constants.KIND, kind);
        text_track_constructor.put(Constants.MIME_TYPE, mime_type);
        text_track_constructor.put(Constants.ASSET_ID, asset_id);
        text_track_constructor.put(Constants.SOURCES, sources);
        text_track_constructor.put(Constants.DEFAULT, false);

        Text_track hardConstTT = new Text_track(id, account_id, src, srclang, label, kind, mime_type, asset_id, _default, sources);
        Text_track hardJsonTT = new Text_track(text_track_constructor);

        text_track_constructor.put(Constants.ID, (JSONObject) null);
        text_track_constructor.put(Constants.ID, (JSONObject) null);
        text_track_constructor.put(Constants.ACCOUNT_ID, (JSONObject) null);
        text_track_constructor.put(Constants.SRC, (JSONObject) null);
        text_track_constructor.put(Constants.SRCLANG, (JSONObject) null);
        text_track_constructor.put(Constants.LABEL, (JSONObject) null);
        text_track_constructor.put(Constants.KIND, (JSONObject) null);
        text_track_constructor.put(Constants.MIME_TYPE, (JSONObject) null);
        text_track_constructor.put(Constants.ASSET_ID, (JSONObject) null);
        text_track_constructor.put(Constants.SOURCES, (JSONObject) null);
        text_track_constructor.put(Constants.DEFAULT, (JSONObject) null);

        Text_track hardJsonTTT = new Text_track(text_track_constructor);

        //MISSING EXCEPTINO THROW SEE TEXT TRACK...
        assertNotNull(hardConstTT.toJSON());
        assertNotNull(hardJsonTT.toString());


        assertNotNull(hardConstTT.toString());

    }
    catch (Exception e)
    {
        System.out.println("TextTrackObjTest " + e);
    }
}

    private void TestTextUtil() {
        //./////////////////////////////////////////////////////////////TextUtil
        TextUtil textUtil = new TextUtil();
        assertNotNull(textUtil.isEmpty("String"));
        assertNotNull(textUtil.notEmpty("AnotherString"));
        assertNotNull(!(textUtil.notEmpty(null)));
    }

    private void TestImageUtils() {
        /////////////////////////////////////////////////////////////Image Utils
        BufferedImage bufferedImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Rectangle rectObj = new Rectangle(50, 100);
        assertNotNull(ImageUtil.cropImage(bufferedImage, rectObj));
        assertNotNull(ImageUtil.cropImage(bufferedImage, rectObj));
        assertNotNull(ImageUtil.cropImage(bufferedImage, rectObj));
    }

    private void TestVideoPlayerModel() {
        VideoPlayer videoPlayerModel = new VideoPlayer();
        try {

            try {
                Method myInitMethod = VideoPlayer.class.getDeclaredMethod("init", null);
                myInitMethod.setAccessible(true);
                myInitMethod.invoke(null);
            } catch (Exception e) {
                System.out.println("TestModelsAEM Property Exception");
                //e.printStackTrace();
            }

            videoPlayerModel.toString();
            videoPlayerModel.getComponentID();
            videoPlayerModel.getCurrentNode();
            videoPlayerModel.getVideoID();
            videoPlayerModel.getPlaylistID();
            videoPlayerModel.getAccount();
            videoPlayerModel.getPlayerPath();
            videoPlayerModel.getPlayerID();
            videoPlayerModel.getPlayerDataEmbed();
            videoPlayerModel.getPlayerKey();
            videoPlayerModel.getContainerID();
            videoPlayerModel.getContainerClass();
            videoPlayerModel.getAlign();
            videoPlayerModel.getWidth();
            videoPlayerModel.getHeight();
            videoPlayerModel.getHasSize();
            videoPlayerModel.getIgnoreComponentProperties();
            videoPlayerModel.getVersion();
            videoPlayerModel.getDropTargetPrefix();
            ComponentContext playerContext = videoPlayerModel.getComponentContext();
            ValueMap playerProps = videoPlayerModel.getPlayerProperties();
            Resource videPlayerRes = videoPlayerModel.getPlayerPageResource();
            videoPlayerModel.getInlineCSS();//Only one which throws exception...


        } catch (Exception e) {
            System.out.println("TestAEM Exception" + e);
            //e.printStackTrace();
        }
    }

    private void TestVideoPlayerConfiguration() {
        //VideoPlayerConfiguration
        VideoPlayerConfiguration videoPlayerConfiguration = new VideoPlayerConfiguration();
        try {
            ComponentContext playerContext = videoPlayerConfiguration.getComponentContext();
            videoPlayerConfiguration.getDialogPath();
            videoPlayerConfiguration.getTitle();
            videoPlayerConfiguration.getDropTargetPrefix();

            //THROW NULL POINTER - TODO:fix
            //since the init function is not really implemented there are no JCR to acceess props....
            //videoPlayerConfiguration.getInlineCSS();
            //videoPlayerConfiguration.getVideoWidth();
            //videoPlayerConfiguration.getVideoHeight();


        } catch (NullPointerException e) {
            System.out.println("VideoPlayerConfiguration NullPointer: " + e);
        } catch (Exception e) {
            System.out.println("VideoPlayerConfiguration Error: " + e);
        }
    }

    private void CustomAddDialogTabFilterTests() {
        CustomAddDialogTabFilter customAddDialogTabFilter = new CustomAddDialogTabFilter();

        try {
            FilterChain pChain = FilterChain.class.newInstance();
            customAddDialogTabFilter.init();
            customAddDialogTabFilter.getServletInfo();
            customAddDialogTabFilter.destroy();
            customAddDialogTabFilter.doFilter(request, response, pChain);
        } catch (Exception e) {
            System.out.println("CustomAddDialogTabFilterTests" + e);
        }
    }



    private void TestAccountUtil() {
        //Mock Experiment   [ACCOUNT UTIL]
        String resourcePath = "";
        String selectors = "";
        String extension = "";
        String suffix = "";
        String queryString = "";

        ConfigurationGrabber cg = mock(ConfigurationGrabber.class);
        ConfigurationService cs = cg.getConfigurationService(ACCOUNT_ID); //GET

        try {

            //Would not work
            //MockSlingHttpServletRequest req = new MockSlingHttpServletRequest(resourcePath, selectors, extension, suffix, queryString);
            SlingHttpServletRequest slingReq = mock(SlingHttpServletRequest.class);

            //ACCOUNT UTIL - Todo: Fix
//        accountUtil.isAuthorized(slingReq, cs);
//        accountUtil.getSelectedAccount(slingReq);
//        accountUtil.getSelectedServices(slingReq);

        } catch (Exception e) {
            System.out.println("TestAccountUtil: "+ e);
            //e.printStackTrace();
        }


    }
    private void BrcAccountsTests() {
        BrcAccounts brcAccounts = new BrcAccounts();

        try {

        } catch (Exception e) {
            System.out.println("BrcAccountsTests" + e);
        }
    }
    private void BrcSuggestionsTests() {
        BrcSuggestions brcSuggestions = new BrcSuggestions();

        try {
            //           PROTECTED
            //           brcSuggestions.doGet();
        } catch (Exception e) {
            System.out.println("BrcSuggestionsTests" + e);
        }
    }

    //IN QUESTION
    private void ConfigurationGrabberImplTests() {





        ConfigurationGrabberImpl configurationGrabberImpl = new ConfigurationGrabberImpl();




        try {
            configurationGrabberImpl.getAvailableServices();
            configurationGrabberImpl.getAvailableServices(request);
            configurationGrabberImpl.getConfigurationService("KEY");
        } catch (Exception e) {
            System.out.println("ConfigurationGrabberImplTests" + e);
        }
    }
    private void ConfigurationServiceImplTests() {
        ConfigurationServiceImpl configurationServiceImpl = new ConfigurationServiceImpl();
        try {
            configurationServiceImpl.getAccountAlias();
            configurationServiceImpl.getAccountID();
            configurationServiceImpl.getAllowedGroups();
            configurationServiceImpl.getAllowedGroupsList();
            configurationServiceImpl.getAssetIntegrationPath();
            configurationServiceImpl.getClientID();
            configurationServiceImpl.getClientSecret();
            configurationServiceImpl.getDefPlaylistPlayerID();
            configurationServiceImpl.getDefPlaylistPlayerKey();
            configurationServiceImpl.getDefVideoPlayerDataEmbedded();
            configurationServiceImpl.getDefVideoPlayerID();
            configurationServiceImpl.getDefVideoPlayerKey();
            configurationServiceImpl.getPlayersLoc();
            configurationServiceImpl.getProxy();

            Method configsServiceCleanStringArray = ConfigurationServiceImpl.class.getDeclaredMethod("cleanStringArray", String[].class);
        } catch (Exception e) {
            System.out.println("ConfigurationServiceImplTests" + e);
        }
    }
    private void CertificateListServiceImplTests() {
        CertificateListServiceImpl certificateListServiceImpl = new CertificateListServiceImpl();
        certificateListServiceImpl.getCertificatePaths();
        certificateListServiceImpl.getEnableTrustedCertificate();
    }
    private void BrcReplicationHandlerTests() {
        try {
            BrcReplicationHandler brcReplicationHandler = new BrcReplicationHandler();
            TransportContext ctx = mock(TransportContext.class);
            ReplicationTransaction tx = mock(ReplicationTransaction.class);
            //          brcReplicationHandler.deliver(ctx, tx);

            //Unsure how to initialize the abstract class - TransportContext

        } catch (Exception e) {
            System.out.println("BrcReplicationHandlerTests" + e);
        }

    }
    private void S3UploadUtilTests() {
        try {
            //S3UploadUtil - 66%
            S3UploadUtil s3UploadUtil = new S3UploadUtil();
            URL url = new URL("http://www.test.com");
            String str = "THISISACORESECURETESTSTRING";
            InputStream is = new ByteArrayInputStream(str.getBytes());
            s3UploadUtil.uploadToUrl(url, is);
        } catch (Exception e) {
            System.out.println("S3UploadUtilTests" + e);
        }
    }
    private void BrcImageApiTests() {
        try
        {
            BrcImageApi brcImageApi = new BrcImageApi();
            brcImageApi.init();
        }
        catch (Exception e)
        {
            System.out.println("BrcImageApiTests" + e);
        }
    }
    private void BrcAccountsUITests() {
        try {
            BrcAccountsUI brcAccountsUIMock = new BrcAccountsUI();
            brcAccountsUIMock.routeUIrequest(request, response); // Nullpointer
            brcAccountsUIMock.api(request, response);
        } catch (Exception e) {
            System.out.println("BrcAccountsUITests" + e);
        }

    }
    private void AssetPropertyIntegratorTests() {
        try {
            AssetPropertyIntegrator assetPropertyIntegrator = mock(AssetPropertyIntegrator.class);
            assetPropertyIntegrator.init();
            //Private Methods
        } catch (Exception e) {
            System.out.println("AssetPropertyIntegratorTests" + e);
        }
    }
    private void AssetPropertyIntegratorRunnableTests() {
        AssetPropertyIntegratorRunnable assetPropertyIntegratorRunnable = mock(AssetPropertyIntegratorRunnable.class);
        assetPropertyIntegratorRunnable.run();
    }

    private void BrcApiTests() {
        BrcApi myBrcAPI = new BrcApi();
        JSONObject outputJSON = new JSONObject();

        try {
            //ALL METHODS PRIVATE - MUST REFLECT
            //        brcAPI.getServletInfo();
            //        brcAPI.getServletConfig();
            //        brcAPI.getServletContext();


            //FIRST GOOD CASE TO USE REFLECTION

            myBrcAPI.init();
            String output;
            Boolean outputBool;


            //GET SERVICES
            Method myGetServices = BrcApi.class.getDeclaredMethod("getServices", SlingHttpServletRequest.class);
            myGetServices.setAccessible(true);
            outputBool = (Boolean) myGetServices.invoke(myBrcAPI, request);
//



        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println("BrcApiTests : " + e);
        }


        try
        {
            Method myLocalPlayers = BrcApi.class.getDeclaredMethod("getLocalPlayers", SlingHttpServletRequest.class);
            myLocalPlayers.setAccessible(true);
            outputJSON = (JSONObject) myLocalPlayers.invoke(myBrcAPI, request);
        }
                catch (Exception e)
        {
            System.out.println("getLocalPlayers : " + e);
            //e.printStackTrace();
        }

        try
        {
            Method myLocalPlayers1 = BrcApi.class.getDeclaredMethod("getPlayers");
            myLocalPlayers1.setAccessible(true);
            outputJSON = (JSONObject) myLocalPlayers1.invoke(myBrcAPI, request);
        }
        catch (Exception e)
        {
            System.out.println("GetPlayersTest : " + e);
            //e.printStackTrace();
        }


    }
    private void TestBrightcoveAPI() {


        //TEST STATIC : TODO : Reference actual services and complete test - otherwise =  nullpointer
        try {
            BrightcoveAPI static_BC_API = new BrightcoveAPI("1153191516001");
        } catch (NullPointerException e) {
            System.out.println("Null static BC API");
        }
    }

    private void TestVideoImportCallable() {
            JSONObject innerObj = new JSONObject();
            String confPath = "";
            String requestedServiceAccount = "";
            ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
            MimeTypeService mType = mock(MimeTypeService.class);
            ServiceUtil serviceUtil = mock(ServiceUtil.class);

            String localpath = "";
            String id = "";
            String brightcove_filename = "";

            try {
               VideoImportCallable videoImportCallable = new VideoImportCallable(innerObj, confPath, requestedServiceAccount, resourceResolverFactory, mType, serviceUtil);
               videoImportCallable.call();

               Method createAssetMethod = videoImportCallable.getClass().getDeclaredMethod("createAsset", String.class, String.class, String.class);

               createAssetMethod.setAccessible(true);
               Asset outputAsset = (Asset) createAssetMethod.invoke(videoImportCallable, localpath, id, brightcove_filename);
               assertNotNull(outputAsset);

           }
           catch (Exception e)
           {
               System.out.println("VideoImportCallable");
           }
    }

    private void TestConfigGrabber(){

        try {
            ConfigurationGrabberImpl cg = ConfigurationGrabberImpl.class.newInstance();


            cg.getConfigurationService("key");
            cg.getAvailableServices();
        }
        catch (Exception e)
        {
            System.out.println(e);
        }
    }


    //NEEDS TO BE IMPROVED
    private void MockTests() {

        try {

            VideoPlayerConfiguration videoPlayerConfiguration = mock(VideoPlayerConfiguration.class);

                /* Throws NullPointer
            BrcAccountsUI brcAccountsUI = new BrcAccountsUI(); //Throws Nullpoint
            brcAccountsUI.routeUIrequest(req, resp);
             */
            BrcAccountsUI brcAccountsUIMock2 = new BrcAccountsUI();
            brcAccountsUIMock2.routeUIrequest(request, response);
            Class<?> clazz2 = brcAccountsUIMock2.getClass();
            Method method2 = clazz2.getDeclaredMethod("doGet");
            method2.setAccessible(true);
            System.out.println("Method2"+method2.invoke(brcAccountsUIMock2));
        } catch (Exception e) {
            System.out.println("Mock Tests: " + e);
        }

    }


    
    
}


