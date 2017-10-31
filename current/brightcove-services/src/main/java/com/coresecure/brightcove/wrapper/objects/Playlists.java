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

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;


/**
 * <p>Not a real Media API object - a wrapper object to represent a list of Playlist objects.</p>
 *
 * @author Sander Gates <three.4.clavins.kitchen @at@ gmail.com>
 *
 */
public class Playlists extends ArrayList<Playlist> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Playlists.class);

    private static final long serialVersionUID = 232810143858103556L;

    private Integer totalCount = 0;

    public Playlists(JSONObject jsonObj) throws JSONException {
        JSONArray jsonItems = jsonObj.getJSONArray("items");
        for(int itemIdx=0;itemIdx<jsonItems.length();itemIdx++){
            JSONObject jsonItem = (JSONObject)jsonItems.get(itemIdx);
            Playlist playlist = new Playlist(jsonItem);
            add(playlist);
        }

        try{
            totalCount = jsonObj.getInt("total_count");
        }
        catch(JSONException e){
            LOGGER.error("JsonException",e);
            totalCount = -1;
        }
    }

    public Integer getTotalCount(){
        return this.totalCount;
    }
}