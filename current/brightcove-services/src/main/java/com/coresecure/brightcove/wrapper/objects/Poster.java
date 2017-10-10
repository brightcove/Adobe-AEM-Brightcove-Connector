package com.coresecure.brightcove.wrapper.objects;


import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Poster {

    public String url;

    private static final Logger LOGGER = LoggerFactory.getLogger(Poster.class);

    public Poster(String aSrc)
    {

        url = aSrc;

        //sources = aText_track.getString("id");;

    }
    public Poster(JSONObject aPoster) throws JSONException
    {

        url = aPoster.getString("src");

        //sources = aText_track.getString("id");;

    }


    public JSONObject toJSON() throws JSONException
    {

        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"url"});
        return json;
    }

    public String toString()
    {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            return null;
        }
    }

}
