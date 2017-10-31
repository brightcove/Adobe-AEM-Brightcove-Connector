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
    public Boolean _default;
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
            if(!aText_track.isNull("default"))_default = aText_track.getBoolean("default");

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

        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"id", "account_id" , "src", "srclang", "label","kind","mime_type","asset_id","sources", "_default"});





        return json;
    }

    public String toString()
    {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            LOGGER.error("JsonException",e);
            return null;
        }
    }

}
