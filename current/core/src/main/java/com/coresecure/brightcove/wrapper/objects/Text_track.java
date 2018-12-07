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


import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Text_track {

    public final String id;
    public final String account_id;
    public final String src;
    public final String srclang;
    public final String label;
    public final String kind;
    public final String mime_type;
    public final String asset_id;
    public final Boolean _default;
    public final JSONArray sources;


    private static final Logger LOGGER = LoggerFactory.getLogger(Text_track.class);


    public Text_track(String id, String account_id, String src, String srclang, String label, String kind, String mime_type, String asset_id, Boolean _default, JSONArray sources)
    {
        this.id = id;
        this.account_id = account_id;
        this.src = src;
        this.srclang = srclang;
        this.label = label;
        this.kind = kind;
        this.mime_type = mime_type;
        this.asset_id = asset_id;
        this._default = _default;
        this.sources = sources;
    }



    public Text_track(JSONObject aText_track)
    {
        String localid = null;
        String localaccount_id = null;
        String localsrc = null;
        String localsrclang = null;
        String locallabel = null;
        String localkind = null;
        String localmime_type = null;
        String localasset_id = null;
        Boolean local_default = null;
        JSONArray localsources = null;
        try
        {
            if(!aText_track.isNull(Constants.ID)) localid = aText_track.getString(Constants.ID);
            if(!aText_track.isNull(Constants.ACCOUNT_ID))localaccount_id = aText_track.getString(Constants.ACCOUNT_ID);
            if(!aText_track.isNull(Constants.SRC))localsrc = aText_track.getString(Constants.SRC);
            if(!aText_track.isNull(Constants.SRCLANG))localsrclang = aText_track.getString(Constants.SRCLANG);
            if(!aText_track.isNull(Constants.LABEL))locallabel = aText_track.getString(Constants.LABEL);
            if(!aText_track.isNull(Constants.KIND))localkind = aText_track.getString(Constants.KIND);
            if(!aText_track.isNull(Constants.MIME_TYPE))localmime_type = aText_track.getString(Constants.MIME_TYPE);
            if(!aText_track.isNull(Constants.ASSET_ID))localasset_id = aText_track.getString(Constants.ASSET_ID);
            if(!aText_track.isNull(Constants.SOURCES))localsources = aText_track.getJSONArray(Constants.SOURCES);
            if(!aText_track.isNull(Constants.DEFAULT))local_default = aText_track.getBoolean(Constants.DEFAULT);
        }
        catch (JSONException e)
        {
            LOGGER.error(e.getClass().getName(),e );
        } catch (Exception e )
        {
            LOGGER.error(e.getClass().getName(), e);
        } finally {
            this.id = localid;
            this.account_id = localaccount_id;
            this.src = localsrc;
            this.srclang = localsrclang;
            this.label = locallabel;
            this.kind = localkind;
            this.mime_type = localmime_type;
            this.asset_id = localasset_id;
            this._default = local_default;
            this.sources = localsources;
        }
    }

    public JSONObject toJSON() throws JSONException
    {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{Constants.ID, Constants.ACCOUNT_ID , Constants.SRC, Constants.SRCLANG, Constants.LABEL,Constants.KIND,Constants.MIME_TYPE,Constants.ASSET_ID,Constants.SOURCES, Constants.UNDERSCORE_DEFAULT});
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
