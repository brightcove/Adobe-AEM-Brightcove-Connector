package com.coresecure.brightcove.wrapper.objects;

import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.JsonReader;
import org.apache.jackrabbit.util.Base64;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Account {
    private String client_id;
    private String client_secret;
    private String account_id;
    private Token authToken;
    public Platform platform;
    private static final Logger LOGGER = LoggerFactory.getLogger(Account.class);

    public Account(Platform aPlatform, String aClient_id, String aClient_secret, String aAccount_id) {
        client_id = aClient_id;
        client_secret = aClient_secret;
        account_id = aAccount_id;
        platform = aPlatform;
    }

    public String getAccount_ID() {
        return account_id;
    }

    public boolean login() {
        boolean result = false;
        authToken = null;
        LOGGER.debug("getAccount_ID");
        String token = Base64.encode(client_id + ":" + client_secret);
        LOGGER.debug("token: "+token);

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Basic " + token);
        String urlParameters = "grant_type=client_credentials";
        String targetURL = platform.getOAUTH_Url() + "/access_token";
        try {
            JSONObject response = JsonReader.readJsonFromString(HttpServices.executePost(targetURL, urlParameters, headers));
            LOGGER.debug("response: "+response.toString());

            if (response.getString("access_token") != null && response.getString("token_type") != null) {
                authToken = new Token(response.getString("access_token"), response.getString("token_type"), response.getInt("expires_in"));
                result = true;
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return result;
    }

    public Token getToken() {
        return authToken;
    }

}
