package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.enums.GeoFilterCodeEnum;
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.util.Collection;

public class RelatedLink {
    public String text;
    public String url;
    public Collection<GeoFilterCodeEnum> countries;

    public RelatedLink(String aText, String aUrl) {
        text = aText;
        url = aUrl;
    }

    public RelatedLink(JSONObject aLink) throws JSONException {
        text = aLink.getString("text");
        url = aLink.getString("url");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"text", "url"});
        return json;
    }

    public String toString() {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            return null;
        }
    }

}
