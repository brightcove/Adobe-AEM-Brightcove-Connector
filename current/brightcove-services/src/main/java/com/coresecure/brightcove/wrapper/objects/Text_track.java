package com.coresecure.brightcove.wrapper.objects;


import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.Collection;


public class Text_track {

    public String id;
    public String account_id;
    public String src;
    public String srclang;
    public String label;
    public String kind;
    public String mime_type;
    public String asset_id;
    public JSONArray sources;


    private static final Logger LOGGER = LoggerFactory.getLogger(Text_track.class);



    public Text_track(JSONObject aText_track)
    {

        try
        {
            if(!aText_track.isNull("id")) id = aText_track.getString("id");
            if(!aText_track.isNull("account_id"))account_id = aText_track.getString("account_id");
            if(!aText_track.isNull("src"))src = aText_track.getString("src");
            if(!aText_track.isNull("srclang"))srclang = aText_track.getString("srclang");
            if(!aText_track.isNull("label"))label = aText_track.getString("label");
            if(!aText_track.isNull("kind"))kind = aText_track.getString("kind");
            if(!aText_track.isNull("mime_type"))mime_type = aText_track.getString("mime_type");
            if(!aText_track.isNull("asset_id"))asset_id = aText_track.getString("asset_id");
            if(!aText_track.isNull("sources"))sources = aText_track.getJSONArray("sources");

        }
        catch (JSONException e)
        {
            LOGGER.error("JSON TEXT TRACK EXCEPTION " ,e );
        } catch (Exception e )
        {
            LOGGER.error("EXCEPTION TEXT TRACK");
        }

        //sources = aText_track.getString("id");;

    }

    public JSONObject toJSON() throws JSONException
    {

        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"id", "account_id" , "src", "srclang", "label","kind","mime_type","asset_id","sources"});





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
