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
package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.enums.EconomicsEnum;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class Video {
    private static final Logger LOGGER = LoggerFactory.getLogger(Video.class);

    public final String name;
    public final String id;
    public final String account_id;
    public final Projection projection;
    public final String reference_id;
    public final String description;
    public final String long_description;
    public final String folderId;
    public final String state;
    public final Collection<String> tags;
    public final Collection<String> labels;
    public final Map<String, Object> custom_fields;
    public final Geo geo;
    public final RelatedLink link;
    public final Schedule schedule;
    public final boolean complete;
    public final EconomicsEnum economics;
    public final JSONArray text_tracks;
    public final JSONArray variants;
    public final Images images;



    public Video(String aName) {
        this( aName, null, null, null, null, null, null, null, false, null);
    }

    public Video(String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink) {
        this(null, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, null, null);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, aEconomics, null);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, aEconomics, aProjection, null);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, aEconomics, aProjection, aText_tracks, null);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks, Images aImages) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, aEconomics, aProjection, aText_tracks, aImages, null, null, null, null);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks, Images aImages, Collection<String> aLabels) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, aEconomics, aProjection, aText_tracks, aImages, null, aLabels, null, null);
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks, Images aImages, Collection<String> aLabels, JSONArray aVariants) {
        this(aId, aName, aReference_id, aDescription, aLong_description, aState, aTags, aGeo, aSchedule, aComplete, aLink, aCustom_fields, aEconomics, aProjection, aText_tracks, aImages, null, aLabels, aVariants, null);
    }

    private Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks, Images aImages, String aAccountId, Collection<String> aLabels, JSONArray aVariants, String aFolderId) {
        id = aId;
        account_id = aAccountId;
        name = aName;
        reference_id = aReference_id;
        description = aDescription;
        long_description = aLong_description;
        state = aState;
        tags = aTags;
        geo = aGeo;
        schedule = aSchedule;
        link = aLink;
        complete = aComplete;
        custom_fields = aCustom_fields;
        economics = aEconomics;
        projection = new Projection(aProjection);
        text_tracks = aText_tracks;
        variants = aVariants;
        images = aImages;
        labels = aLabels;
        folderId = aFolderId;
    }

    private Object getNotNull(JSONObject video, String key) throws JSONException{
        return !video.isNull(key) ? video.get(key) : null;
    }
    public Video(JSONObject video) throws JSONException {

        String localname = null;
        String localid = null;
        String localaccount_id = null;
        Projection localprojection = null;
        String localreference_id = null;
        String localdescription = null;
        String localfolderid = null;
        String locallong_description = null;
        String localstate = null;
        Collection<String> localtags = null;
        Collection<String> localLabels = null;
        Map<String, Object> localcustom_fields = null;
        Geo localgeo = null;
        RelatedLink locallink = null;
        Schedule localschedule = null;
        boolean localcomplete = false;
        EconomicsEnum localeconomics = null;
        JSONArray localtext_tracks = null;
        JSONArray localvariants = null;
        Images localimages = null;
        try {
            localid = (String) getNotNull(video, Constants.ID);
            localaccount_id = (String) getNotNull(video, Constants.ACCOUNT_ID);
            localname = (String) getNotNull(video, Constants.NAME);
            localreference_id = (String) getNotNull(video, Constants.REFERENCE_ID);
            localdescription = (String) getNotNull(video, Constants.DESCRIPTION);
            localfolderid = (String) getNotNull(video, Constants.FOLDER_ID);
            locallong_description = (String) getNotNull(video, Constants.LONG_DESCRIPTION);
            localstate = (String) getNotNull(video, Constants.STATE);
            localprojection = (Projection) getNotNull(video, Constants.PROJECTION);
            localgeo = (Geo) getNotNull(video, Constants.GEO);
            localschedule = (Schedule) getNotNull(video, Constants.SCHEDULE);
            locallink = (RelatedLink) getNotNull(video, Constants.LINK);
            localtext_tracks = (JSONArray) getNotNull(video, Constants.TEXT_TRACKS);
            localvariants = (JSONArray) getNotNull(video, Constants.VARIANTS);
            localcomplete = (Boolean) getNotNull(video, Constants.COMPLETE);
            if (!video.isNull(Constants.TAGS))
            {
                localtags = new ArrayList<String>();
                for (int i = 0; i < video.getJSONArray(Constants.TAGS).length(); i++)
                {
                    localtags.add(video.getJSONArray(Constants.TAGS).getString(i));
                }
            }
            if (!video.isNull(Constants.LABELS))
            {
                localLabels = new ArrayList<String>();
                for (int i = 0; i < video.getJSONArray(Constants.LABELS).length(); i++)
                {
                    localLabels.add(video.getJSONArray(Constants.LABELS).getString(i));
                }
            }
        }
        catch(Exception e)
        {
            LOGGER.error(e.getClass().getName(), e);
        }
        finally
        {
            this.name = localname;
            this.id = localid;
            this.account_id = localaccount_id;
            this.projection = localprojection;
            this.reference_id = localreference_id;
            this.description = localdescription;
            this.folderId = localfolderid;
            this.long_description = locallong_description;
            this.state = localstate;
            this.tags = localtags;
            this.labels = localLabels;
            this.custom_fields = localcustom_fields;
            this.geo = localgeo;
            this.link = locallink;
            this.schedule = localschedule;
            this.complete = localcomplete;
            this.economics = localeconomics;
            this.text_tracks = localtext_tracks;
            this.variants = localvariants;
            this.images = localimages;
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{Constants.ID, Constants.ACCOUNT_ID, Constants.NAME, Constants.REFERENCE_ID, Constants.DESCRIPTION, Constants.LONG_DESCRIPTION, Constants.STATE, Constants.TAGS, Constants.CUSTOM_FIELDS, Constants.GEO, Constants.SCHEDULE, Constants.LINK, Constants.ECONOMICS,Constants.PROJECTION, Constants.TEXT_TRACKS, Constants.LABELS});
        return json;
    }

    public String toString() {
        String result = new String();
        try {
            result = toJSON().toString();
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(),e);
        }
        return result;
    }
}
