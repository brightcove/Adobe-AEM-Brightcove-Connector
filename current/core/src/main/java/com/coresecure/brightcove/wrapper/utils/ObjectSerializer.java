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

    public ObjectSerializer(){/* default implementation ignored */};

    private static String cleanFilterName(String name){
        return (name.startsWith("_")) ? name.substring(1) : name;
    }
    private static void addAsCollection(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Collection value = (Collection) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            JSONArray itemCollection = new JSONArray(value);
            json.put(json_key, itemCollection);
        }
    }
    private static void addAsString(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        String value = (String) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING))  json.put(json_key, value);
    }
    private static void addAsMap(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Map value = (Map) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            JSONObject itemObj = new JSONObject((Map) f.get(obj));
            json.put(json_key, itemObj);
        }
    }
    private static void addAsBoolean(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Boolean value = (Boolean) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) json.put(json_key, value);
    }
    private static void addAsRelatedLink(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        RelatedLink value = (RelatedLink) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.toJSON());
        }
    }
    private static void addAsGeo(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Geo value = (Geo) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.toJSON());
        }
    }
    private static void addAsSchedule(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Schedule value = (Schedule) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.toJSON());
        }
    }
    private static void addAsEconomicsEnum(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        EconomicsEnum value = (EconomicsEnum) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.name());
        }
    }
    private static void addAsText_track(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Text_track value = (Text_track) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.toJSON());
        }
    }
    private static void addAsJSONArray(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        JSONArray value = (JSONArray) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value);
        }
    }
    private static void addAsImages(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        JSONArray value = (JSONArray) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value);
        }
    }
    private static void addAsPoster(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Poster value = (Poster) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.toJSON());
        }
    }
    private static void addAsThumbnail(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Thumbnail value = (Thumbnail) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.toJSON());
        }
    }
    private static void addAsProjection(Field f, Object obj, JSONObject json, String json_key) throws JSONException, IllegalAccessException{
        Projection value = (Projection) f.get(obj);
        if (value.type != null) {
            if (value.type.isEmpty()) {
                json.put(json_key, JSONObject.NULL);
            } else {
                json.put(json_key, value.type);
            }
        }
    }
    private static void addFieldToJson(Field f, Object obj, JSONObject json, String json_key)  throws JSONException, IllegalAccessException {
        if (f.getType().equals(Collection.class)) {
            addAsCollection(f, obj, json, json_key);
        } else if (f.getType().equals(String.class)) {
            addAsString(f, obj, json, json_key);
        } else if (f.getType().equals(Map.class)) {
            addAsMap(f, obj, json, json_key);
        } else if (f.getType().equals(Boolean.class)) {
            addAsBoolean(f, obj, json, json_key);
        } else if (f.getType().equals(RelatedLink.class)) {
            addAsRelatedLink(f, obj, json, json_key);
        } else if (f.getType().equals(Geo.class)) {
            addAsGeo(f, obj, json, json_key);
        } else if (f.getType().equals(Schedule.class)) {
            addAsSchedule(f, obj, json, json_key);
        } else if (f.getType().equals(EconomicsEnum.class)) {
            addAsEconomicsEnum(f, obj, json, json_key);
        } else if (f.getType().equals(Text_track.class)) {
            addAsText_track(f, obj, json, json_key);
        } else if (f.getType().equals(JSONArray.class)) {
            addAsJSONArray(f, obj, json, json_key);
        } else if (f.getType().equals(Images.class)) {
            addAsImages(f, obj, json, json_key);
        }else if (f.getType().equals(Poster.class)) {
            addAsPoster(f, obj, json, json_key);
        }else if (f.getType().equals(Thumbnail.class)) {
            addAsThumbnail(f, obj, json, json_key);
        } else if (f.getType().equals(Projection.class)) {
            addAsProjection(f, obj, json, json_key);
        }
    }
    public static JSONObject toJSON(Object obj, String[] fields) throws JSONException {
        JSONObject json = new JSONObject();
        for (String field_name : fields) {
            try {
                Class<?> c = obj.getClass();

                Field f = c.getDeclaredField(field_name);
                String json_key = cleanFilterName(field_name);
                f.setAccessible(true);
                addFieldToJson(f, obj, json, json_key);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }
}
