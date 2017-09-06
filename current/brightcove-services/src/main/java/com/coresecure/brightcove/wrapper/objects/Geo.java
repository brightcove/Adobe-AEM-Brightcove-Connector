package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.enums.GeoFilterCodeEnum;
import com.coresecure.brightcove.wrapper.utils.ObjectSerializer;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;

public class Geo {
    public boolean exclude_countries;
    public boolean restricted;
    public Collection<GeoFilterCodeEnum> countries;

    public Geo(boolean aExclude_countries, boolean aRestricted, Collection<GeoFilterCodeEnum> aCountries) {
        exclude_countries = aExclude_countries;
        restricted = aRestricted;
        countries = aCountries;
    }

    public Geo(JSONObject aGeo) throws JSONException {
        exclude_countries = aGeo.getBoolean("exclude_countries");
        restricted = aGeo.getBoolean("restricted");
        countries = new ArrayList<GeoFilterCodeEnum>();
        for (int i = 0; i < aGeo.getJSONArray("countries").length(); i++) {
            countries.add(GeoFilterCodeEnum.lookupByCode(aGeo.getJSONArray("countries").getString(i)));
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = ObjectSerializer.toJSON(this, new String[]{"exclude_countries", "restricted", "countries"});
        return json;
    }

    public String toString() {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            return null;
        }
    }

}
