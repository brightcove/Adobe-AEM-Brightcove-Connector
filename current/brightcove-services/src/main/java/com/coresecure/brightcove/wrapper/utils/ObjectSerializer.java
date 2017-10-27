package com.coresecure.brightcove.wrapper.utils;

import com.coresecure.brightcove.wrapper.enums.EconomicsEnum;
import com.coresecure.brightcove.wrapper.objects.*;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class ObjectSerializer {
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
                    } else {
//                        JSONArray itemCollection = new JSONArray();
//                        json.put(field, itemCollection);
                    }
                } else if (f.getType().equals(String.class)) {
                    String value = (String) f.get(obj);
                    if (value != null)  json.put(field, value);
                } else if (f.getType().equals(Map.class)) {
                    Map value = (Map) f.get(obj);
                    if (value != null) {
                        JSONObject itemObj = new JSONObject((Map) f.get(obj));
                        json.put(field, itemObj);
                    } else {
                        //json.put(field, new JSONObject());
                    }
                } else if (f.getType().equals(Boolean.class)) {
                    Boolean value = (Boolean) f.get(obj);
                    if (value != null) json.put(field, value);
                } else if (f.getType().equals(RelatedLink.class)) {
                    RelatedLink value = (RelatedLink) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    } else {
                       // json.put(field, new JSONObject());
                    }
                } else if (f.getType().equals(Geo.class)) {
                    Geo value = (Geo) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    } else {
                       // json.put(field, new JSONObject());
                    }
                } else if (f.getType().equals(Schedule.class)) {
                    Schedule value = (Schedule) f.get(obj);
                    if (value != null) {
                        json.put(field, value.toJSON());
                    } else {
                        //json.put(field, new JSONObject());
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
                    } else {
                        //json.put(field, new JSONObject());
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
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return json;
    }
}
