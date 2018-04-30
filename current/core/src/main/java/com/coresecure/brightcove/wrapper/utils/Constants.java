package com.coresecure.brightcove.wrapper.utils;

import com.day.cq.dam.api.DamConstants;
import com.day.cq.wcm.api.NameConstants;

/**
 * Created by pablo.kropilnicki on 12/18/17.
 */
public class Constants {

    public Constants(){/* default implementation ignored */}
    public static final String EMPTY_URLPARAMS = "";
    public static final String EMPTY_Q_PARAM = "";
    public static final String EMPTY_SORT_PARAM = "";

    public static final String AUTHENTICATION_HEADER = "Authorization";
    public static final String ACCOUNTS_API_PATH = "/accounts/";
    public static final String VIDEOS_API_PATH = "/videos/";
    public static final String WHITESPACE_FIX = "%20%2B";
    public static final String REFERENCE_SEARCH_FIELD_TAG = "ref:";
    public static final String INGEST_REQUEST = "/ingest-requests";

    //JSON KEY CONSTANTS
    public static final String ID = "id";
    public static final String ACCOUNT_ID = "account_id";
    public static final String NAME = "name";
    public static final String REFERENCE_ID = "reference_id";
    public static final String DESCRIPTION = "description";
    public static final String LONG_DESCRIPTION = "long_description";
    public static final String STATE = "state";
    public static final String PROJECTION = "projection";

    public static final String TAGS = "tags";
    public static final String GEO = "geo";
    public static final String SCHEDULE = "schedule";
    public static final String LINK = "link";
    public static final String COMPLETE = "complete";

    public static final String TEXT_TRACKS = "text_tracks";
    public static final String TRACK_LANG = "track_lang";
    public static final String TRACK_KIND = "track_kind";
    public static final String TRACK_LABEL = "track_label";
    public static final String TRACK_MIME_TYPE = "track_mime_type";
    public static final String TRACK_DEFAULT = "track_default";
    public static final String TRACK_SOURCE = "track_source";
    public static final String TRACK_FILEPATH = "track_filepath";



    public static final String IMAGES = "images";
    public static final String THUMBNAIL = "thumbnail";
    public static final String POSTER = "poster";
    public static final String SRC = "src";
    public static final String URL = "url";

    public static final String CREATED_AT = "created_at";
    public static final String STARTS_AT = "starts_at";
    public static final String ENDS_AT = "ends_at";
    public static final String CUE_POINTS = "cue_points";
    public static final String UPDATED_AT = "updated_at";

    public static final String BRC_LINK_URL = "brc_link_url";
    public static final String BRC_LINK_TEXT = "brc_link_text";

    public static final String BRC_LASTSYNC = "brc_lastsync";
    public static final String BRC_ID = "brc_id";
    public static final String BRC_COMPLETE = "brc_complete";
    public static final String BRC_ECONOMICS = "brc_economics";
    public static final String BRC_PROJECTION = "brc_projection";
    public static final String BRC_ACCOUNTID = "brc_account_id";
    public static final String BRC_CUSTOM_FIELDS = "brc_custom_fields";
    public static final String BRC_REFERENCE_ID = "brc_reference_id";
    public static final String BRC_DESCRIPTION = "brc_description";
    public static final String BRC_LONG_DESCRIPTION = "brc_long_description";
    public static final String BRC_DURATION = "brc_duration";
    public static final String BRC_STATE = "brc_state";


    public static final String EQUIRECTANGULAR = "equirectangular";



    public static final String CUSTOM_FIELDS = "custom_fields";
    public static final String DURATION = "duration";
    public static final String ECONOMICS = "economics";

    public static final String DIGITAL_MASTER_ID = "digital_master_id";
    public static final String FOLDER_ID = "folder_id";

    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    public static final String ALIGN = "align";

    public static final String COUNTRIES = "countries";
    public static final String EXCLUDE_COUNTRIES = "exclude_countries";
    public static final String RESTRICTED = "restricted";


    public static final String ACCESS_TOKEN = "access_token";
    public static final String API_REQUEST_URL = "api_request_url";
    public static final String SIGNED_URL = "signed_url";
    public static final String OBJECT_KEY = "object_key";
    public static final String BUCKET = "bucket";


    public static final String BRC_POSTER_PNG = "brc_poster.png";
    public static final String BRC_THUMBNAIL_PNG = "brc_thumbnail.png";

    public static final String SENT = "sent";
    public static final String MASTER = "master";
    public static final String RESPONSE = "response";

    public static final String NULLSTRING = "null";
    public static final String SRCLANG = "srclang";


    public static final String LABEL = "label";
    public static final String KIND = "kind";
    public static final String MIME_TYPE = "mime_type";
    public static final String ASSET_ID = "asset_id";
    public static final String SOURCES = "sources";
    public static final String UNDERSCORE_DEFAULT = "_default";
    public static final String DEFAULT = "default";

    public static final String BINARY = "binary";
    public static final String ITEMS = "items";
    public static final String TOTALS = "totals";
    public static final String QUERY = "query";
    public static final String SORT = "sort";
    public static final String VALUE = "value";
    public static final String TEXT = "text";
    public static final String START = "start";
    public static final String LIMIT = "limit";
    public static final String PLST = "playlist";
    public static final String PLST_NAME = "plst.name";
    public static final String PLST_REFERENCE_ID = "plst.referenceId";
    public static final String PLST_SHORT_DESC = "plst.shortDescription";


    public static final String ORIGINAL_FILENAME = "original_filename";
    public static final String THUMBNAIL_URL = "thumbnailURL";
    public static final String DEFAULT_THUMBNAIL_LOCATION = "/etc/designs/cs/brightcove/shared/img/noThumbnail.jpg";
    public static final String THUMBNAIL_SOURCE = "thumbnail_source";
    public static final String POSTER_SOURCE = "poster_source";



    public static final String DELIMETER_STRING_DOUBLE = "\\.\\.";
    public static final String ERROR = "error";
    public static final String VIDEOID = "videoid";
    public static final String RESULT_LOG_TMPL = "Result: {}";
    public static final String RESULT_LOG_NEW_VIDEO_TMPL = "New video id: {} ";
    public static final String ERROR_LOG_TMPL = "Error! {}";
    public static final String REP_ERROR_LOG_TMPL = "Replication failed: {} ";
    public static final String REP_FAILED_ERROR = "Replication failed: ";
    public static final String REP_ACTION_TYPE_TMPL = "Replication action type {} ";
    public static final String REP_ACTIVATION_SUCCESS_TMPL = "BC: ACTIVATION SUCCESSFUL >> {} ";
    public static final String REP_ACTIVATION_FAILED_TMPL = "BC: ACTIVATION FAILED >> {} ";

    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_LENGTH_HEADER = "Content-Length";
    public static final String CONTENT_LANGUAGE_HEADER = "Content-Language";
    public static final String CONTENT_LANGUAGE_LOCALITY = "en-US";


    public final static String DEFAULT_OAUTH_URL = "https://oauth.brightcove.com/v4";
    public final static String DEFAULT_PLAYERS_API_URL = "https://players.api.brightcove.com/v2";
    public final static String DEFAULT_API_URL = "https://cms.api.brightcove.com/v1";
    public final static String DEFAULT_DI_API_URL = "https://ingest.api.brightcove.com/v1";



    public static final String ASSET_METADATA_PATH = NameConstants.NN_CONTENT+"/"+DamConstants.ACTIVITY_TYPE_METADATA;

    public static final String FAVORITE = "favorite";

    public static final String VIDEO_IDS = "video_ids";

    public static final String PLAYLIST_TYPE = "type";


}
