package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Ingest {
    public String profile;
    public Map<String, String> master;

    public Ingest(String aProfile, Map<String, String> aMaster) {
        profile = aProfile;
        master = aMaster;

    }

    public Ingest(String aProfile, String aUrl) {
        master = new HashMap<String, String>();
        master.put("url", aUrl);
        profile = aProfile;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"profile", "master"});
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
