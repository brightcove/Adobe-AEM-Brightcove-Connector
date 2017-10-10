package com.coresecure.brightcove.wrapper.objects;


import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.Collection;


public class Images {

    public Thumbnail thumbnail;
    public Poster poster;

    private static final Logger LOGGER = LoggerFactory.getLogger(Images.class);


    public Images() {

    }
    public Images(JSONObject aImagesObj) throws JSONException
    {

            thumbnail = new Thumbnail(aImagesObj.getJSONObject("thumbnail"));
            poster = new Poster(aImagesObj.getJSONObject("poster"));



        //sources = aText_track.getString("id");;

    }

    public Images(Poster aPoster, Thumbnail aThumbnail)
    {

        thumbnail = aThumbnail;
        poster = aPoster;
    }

    public JSONObject toJSON() throws JSONException
    {

        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"poster","thumbnail"});
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
