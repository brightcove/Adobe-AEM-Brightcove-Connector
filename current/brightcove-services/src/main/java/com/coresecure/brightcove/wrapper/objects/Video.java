package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.enums.EconomicsEnum;
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class Video {
    public String name;
    public String id;
    public String account_id;
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


    public Video(JSONObject video) throws JSONException {
        id = video.getString("id");
        account_id = video.getString("account_id");
        name = video.getString("name");
        reference_id = video.getString("reference_id");
        description = video.getString("description");
        long_description = video.getString("long_description");
        state = video.getString("state");
        tags = new ArrayList<String>();
        for (int i = 0; i < video.getJSONArray("tags").length(); i++) {
            tags.add(video.getJSONArray("tags").getString(i));
        }
        if (!video.isNull("geo")) geo = new Geo(video.getJSONObject("geo"));
        if (!video.isNull("schedule")) schedule = new Schedule(video.getJSONObject("schedule"));
        if (!video.isNull("link")) link = new RelatedLink(video.getJSONObject("link"));

        complete = video.getBoolean("complete");
    }

    public Video(String aName) {
        name = aName;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"id", "account_id", "name", "reference_id", "description", "long_description", "state", "tags", "custom_fields", "geo", "schedule", "link", "economics"});
        return json;
    }

    public String toString() {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            return null;
        }
    }
}
