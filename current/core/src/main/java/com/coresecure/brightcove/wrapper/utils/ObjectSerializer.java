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
package com.coresecure.brightcove.wrapper.utils;

import com.coresecure.brightcove.wrapper.enums.EconomicsEnum;
import com.coresecure.brightcove.wrapper.objects.*;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class ObjectSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectSerializer.class);

    public static JSONObject toJSON(Object obj, String[] fields) throws JSONException {
        JSONObject json = new JSONObject();
        for (String field : fields) {
            try {
                Class<?> c = obj.getClass();

                Field f = c.getDeclaredField(field);
                if (field.startsWith("_")) field = field.substring(1);
                f.setAccessible(true);
                if (f.getType().equals(Collection.class)) {
                    Collection value = (Collection) f.get(obj);
                    if (value != null) {
                        JSONArray itemCollection = new JSONArray(value);
                        json.put(field, itemCollection);
                    }
                } else if (f.getType().equals(String.class)) {
                    String value = (String) f.get(obj);
                    if (value != null)  json.put(field, value);
                } else if (f.getType().equals(Map.class)) {
                    Map value = (Map) f.get(obj);
                    if (value != null) {
                        JSONObject itemObj = new JSONObject((Map) f.get(obj));
                        json.put(field, itemObj);
                    }
                } else if (f.getType().equals(Boolean.class)) {
                    Boolean value = (Boolean) f.get(obj);
                    if (value != null) json.put(field, value);
                } else if (f.getType().equals(RelatedLink.class)) {
                    RelatedLink value = (RelatedLink) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                } else if (f.getType().equals(Geo.class)) {
                    Geo value = (Geo) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                } else if (f.getType().equals(Schedule.class)) {
                    Schedule value = (Schedule) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                } else if (f.getType().equals(EconomicsEnum.class)) {
                    EconomicsEnum value = (EconomicsEnum) f.get(obj);
                    if (value != null) {
                        json.put(field, value.name());
                    }
                } else if (f.getType().equals(Text_track.class)) {
                    Text_track value = (Text_track) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                } else if (f.getType().equals(JSONArray.class)) {
                    JSONArray value = (JSONArray) f.get(obj);
                    if (value != null) {
                        json.put(field, value);
                    }
                } else if (f.getType().equals(Images.class)) {
                    Images value = (Images) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                }else if (f.getType().equals(Poster.class)) {
                    Poster value = (Poster) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                }else if (f.getType().equals(Thumbnail.class)) {
                    Thumbnail value = (Thumbnail) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    }
                } else if (f.getType().equals(Projection.class)) {
                    Projection value = (Projection) f.get(obj);
                    if (value.type != null) {
                        if (value.type.isEmpty()) {
                            json.put(field, JSONObject.NULL);
                        } else {
                            json.put(field, value.type);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Exception",e);
            }
        }
        return json;
    }
}
