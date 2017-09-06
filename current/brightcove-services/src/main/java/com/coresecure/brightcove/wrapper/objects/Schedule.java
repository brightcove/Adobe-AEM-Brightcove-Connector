package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

public class Schedule {
    public String starts_at;
    public String ends_at;

    public Schedule(String aStarts_at, String aEnds_at) {
        starts_at = aStarts_at;
        ends_at = aEnds_at;
    }

    public Schedule(JSONObject aSchedule) throws JSONException {
        starts_at = aSchedule.getString("starts_at");
        ends_at = aSchedule.getString("ends_at");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"starts_at", "ends_at"});
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
