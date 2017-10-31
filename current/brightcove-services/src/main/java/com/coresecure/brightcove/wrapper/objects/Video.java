/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2017 Coresecure Inc.

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
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Video {
    private static final Logger LOGGER = LoggerFactory.getLogger(Video.class);

    public String name;
    public String id;
    public String account_id;
    public Projection projection;
    public String reference_id;
    public String description;
    public String long_description;
    public String state;
    public Collection<String> tags;
    public Map<String, Object> custom_fields;
    public Geo geo;
    public RelatedLink link;
    public Schedule schedule;
    public boolean complete;
    public EconomicsEnum economics;
    public JSONArray text_tracks;
    public Images images;

    public Video(String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink) {
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
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink) {
        id = aId;
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
    }


    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics) {
        id = aId;
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
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection) {
        id = aId;
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
    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks) {
        id = aId;
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

    }

    public Video(String aId, String aName, String aReference_id, String aDescription, String aLong_description, String aState, Collection<String> aTags, Geo aGeo, Schedule aSchedule, boolean aComplete, RelatedLink aLink, Map<String, Object> aCustom_fields, EconomicsEnum aEconomics, String aProjection, JSONArray aText_tracks, Images aImages) {
        id = aId;
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
        images = aImages;

    }





    public Video(JSONObject video) throws JSONException {

        if (!video.isNull("id")) id = video.getString("id");
        if (!video.isNull("account_id")) account_id = video.getString("account_id");
        if (!video.isNull("name")) name = video.getString("name");
        if (!video.isNull("reference_id")) reference_id = video.getString("reference_id");
        if (!video.isNull("description")) description = video.getString("description");
        if (!video.isNull("long_description")) long_description = video.getString("long_description");
        if (!video.isNull("state")) state = video.getString("state");
        if (!video.isNull("projection")) projection = new Projection(video.getString("projection"));
        if (!video.isNull("tags")) {
            tags = new ArrayList<String>();
            for (int i = 0; i < video.getJSONArray("tags").length(); i++) {
                tags.add(video.getJSONArray("tags").getString(i));
            }
        }
        if (!video.isNull("geo")) geo = new Geo(video.getJSONObject("geo"));
        if (!video.isNull("schedule")) schedule = new Schedule(video.getJSONObject("schedule"));
        if (!video.isNull("link")) link = new RelatedLink(video.getJSONObject("link"));

        if(!video.isNull("text_tracks")) text_tracks = video.getJSONArray("text_tracks");





        complete = video.getBoolean("complete");
    }

    public Video(String aName) {
        name = aName;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"id", "account_id", "name", "reference_id", "description", "long_description", "state", "tags", "custom_fields", "geo", "schedule", "link", "economics","projection", "text_tracks"});
        return json;
    }

    public String toString() {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            LOGGER.error("JsonException",e);
            return null;
        }
    }
}
