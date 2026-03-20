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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


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
    public final ArrayNode sources;


    private static final Logger LOGGER = LoggerFactory.getLogger(Text_track.class);


    public Text_track(String id, String account_id, String src, String srclang, String label, String kind, String mime_type, String asset_id, Boolean _default, ArrayNode sources)
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



    public Text_track(ObjectNode aText_track)
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
        ArrayNode localsources = null;
        try
        {
            if(aText_track.has(Constants.ID) && !aText_track.get(Constants.ID).isNull()) localid = aText_track.get(Constants.ID).asText();
            if(aText_track.has(Constants.ACCOUNT_ID) && !aText_track.get(Constants.ACCOUNT_ID).isNull()) localaccount_id = aText_track.get(Constants.ACCOUNT_ID).asText();
            if(aText_track.has(Constants.SRC) && !aText_track.get(Constants.SRC).isNull()) localsrc = aText_track.get(Constants.SRC).asText();
            if(aText_track.has(Constants.SRCLANG) && !aText_track.get(Constants.SRCLANG).isNull()) localsrclang = aText_track.get(Constants.SRCLANG).asText();
            if(aText_track.has(Constants.LABEL) && !aText_track.get(Constants.LABEL).isNull()) locallabel = aText_track.get(Constants.LABEL).asText();
            if(aText_track.has(Constants.KIND) && !aText_track.get(Constants.KIND).isNull()) localkind = aText_track.get(Constants.KIND).asText();
            if(aText_track.has(Constants.MIME_TYPE) && !aText_track.get(Constants.MIME_TYPE).isNull()) localmime_type = aText_track.get(Constants.MIME_TYPE).asText();
            if(aText_track.has(Constants.ASSET_ID) && !aText_track.get(Constants.ASSET_ID).isNull()) localasset_id = aText_track.get(Constants.ASSET_ID).asText();
            if(aText_track.has(Constants.SOURCES) && !aText_track.get(Constants.SOURCES).isNull()) localsources = (ArrayNode) aText_track.get(Constants.SOURCES);
            if(aText_track.has(Constants.DEFAULT) && !aText_track.get(Constants.DEFAULT).isNull()) local_default = aText_track.get(Constants.DEFAULT).asBoolean();
        }
        catch (Exception e)
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

    public ObjectNode toJSON() throws IOException
    {
        ObjectNode json = ObjectSerializer.toJSON(this, new String[]{Constants.ID, Constants.ACCOUNT_ID , Constants.SRC, Constants.SRCLANG, Constants.LABEL,Constants.KIND,Constants.MIME_TYPE,Constants.ASSET_ID,Constants.SOURCES, Constants.UNDERSCORE_DEFAULT});
        return json;
    }

    public String toString() {
        String result = new String();
        try {
            result = toJSON().toString();
        } catch (IOException e) {
            LOGGER.error(e.getClass().getName(),e);
        }
        return result;
    }
}
