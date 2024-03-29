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

import com.coresecure.brightcove.wrapper.enums.GeoFilterCodeEnum;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

public class Folder {
    public final String id;
    public final String name;
    public final String path;
    private static final Logger LOGGER = LoggerFactory.getLogger(Folder.class);

    public Folder(String aId, String aName, String aPath) {
        id = aId;
        name = aName;
        path = aPath;
    }

    public Folder(JSONObject aFolder) throws JSONException {
        id = aFolder.getString(Constants.ID);
        name = aFolder.getString(Constants.FOLDER_ID);
        path = "";
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{Constants.ID, Constants.NAME});
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
