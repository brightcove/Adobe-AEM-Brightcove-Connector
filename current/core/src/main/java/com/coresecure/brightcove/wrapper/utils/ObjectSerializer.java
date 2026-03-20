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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private static void addAsCollection(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        Collection value = (Collection) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            ArrayNode itemCollection = JsonNodeFactory.instance.arrayNode();
            for (Object item : value) {
                itemCollection.add(item.toString());
            }
            json.set(json_key, itemCollection);
        }
    }
    private static void addAsString(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        String value = (String) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING))  json.put(json_key, value);
    }
    private static void addAsMap(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        Map value = (Map) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            ObjectNode itemObj = JsonNodeFactory.instance.objectNode();
            for (Object k : value.keySet()) {
                Object v = value.get(k);
                itemObj.put(k.toString(), v != null ? v.toString() : "");
            }
            json.set(json_key, itemObj);
        }
    }
    private static void addAsBoolean(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        Boolean value = (Boolean) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) json.put(json_key, value);
    }
    private static void addAsRelatedLink(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
        RelatedLink value = (RelatedLink) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value.toJSON());
        }
    }
    private static void addAsGeo(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
        Geo value = (Geo) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value.toJSON());
        }
    }
    private static void addAsSchedule(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
        Schedule value = (Schedule) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value.toJSON());
        }
    }
    private static void addAsEconomicsEnum(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        EconomicsEnum value = (EconomicsEnum) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.put(json_key, value.name());
        }
    }
    private static void addAsText_track(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
        Text_track value = (Text_track) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value.toJSON());
        }
    }
    private static void addAsJSONArray(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        ArrayNode value = (ArrayNode) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value);
        }
    }
    private static void addAsImages(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        ArrayNode value = (ArrayNode) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value);
        }
    }
    private static void addAsPoster(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
        Poster value = (Poster) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value.toJSON());
        }
    }
    private static void addAsThumbnail(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
        Thumbnail value = (Thumbnail) f.get(obj);
        if (value != null && !value.toString().equals(Constants.NULLSTRING)) {
            json.set(json_key, value.toJSON());
        }
    }
    private static void addAsProjection(Field f, Object obj, ObjectNode json, String json_key) throws IllegalAccessException{
        Projection value = (Projection) f.get(obj);
        if (value.type != null) {
            if (value.type.isEmpty()) {
                json.putNull(json_key);
            } else {
                json.put(json_key, value.type);
            }
        }
    }
    private static void addFieldToJson(Field f, Object obj, ObjectNode json, String json_key) throws Exception {
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
        } else if (f.getType().equals(ArrayNode.class)) {
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
    public static ObjectNode toJSON(Object obj, String[] fields) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
