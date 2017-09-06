package com.coresecure.brightcove.wrapper.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import com.coresecure.brightcove.wrapper.objects.Account;
import com.coresecure.brightcove.wrapper.objects.Token;
import com.coresecure.brightcove.wrapper.utils.JsonReader;

public class DI_API {
	private Account account;
	private final static int DEFAULT_LIMIT = 20;
	private final static int DEFAULT_OFFSET = 0;
	
	public DI_API(Account aAccount){
		account= aAccount;
	}
	public JSONObject getVideo(String ID) {
		JSONObject json = new JSONObject();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String urlParameters = "";
			String targetURL = "/accounts/"+account.getAccount_ID()+"/videos/"+ID;
			try {
				String response = account.platform.getAPI(targetURL, urlParameters , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} 
		return json;
	}
	public JSONArray getVideoSources(String ID) {
		JSONArray json = new JSONArray();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String targetURL = "/accounts/"+account.getAccount_ID()+"/videos/"+ID+"/sources";
			try {
				String response =  account.platform.getAPI(targetURL, "" , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return json;
	}
	public JSONObject getVideoImages(String ID) {
		JSONObject json = new JSONObject();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String targetURL ="/accounts/"+account.getAccount_ID()+"/videos/"+ID+"/images";
			try {
				String response =  account.platform.getAPI(targetURL, "" , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return json;
	}
	public JSONArray getVideos(String q, int limit, int offset, String sort) {
		JSONArray json = new JSONArray();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String urlParameters = "q="+q+"&limit="+limit+"&offset="+offset+"&sort="+sort;
			String targetURL = "/accounts/"+account.getAccount_ID()+"/videos";
			try {
				String response =  account.platform.getAPI(targetURL, urlParameters , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
				if (json.length() ==0  && !q.isEmpty()) json.put(getVideo(q));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return json;
	}
	
	public JSONObject getVideoByRef(String ref_ID) {
		JSONObject json = new JSONObject();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String targetURL = "/accounts/"+account.getAccount_ID()+"/videos/ref:"+ref_ID;
			try {
				String response =  account.platform.getAPI(targetURL, "" , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return json;
	}
	public JSONArray getVideoSourcesByRef(String ref_ID) {
		JSONArray json = new JSONArray();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String targetURL = "/accounts/"+account.getAccount_ID()+"/videos/ref:"+ref_ID+"/sources";
			try {
				String response =  account.platform.getAPI(targetURL, "" , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return json;
	}
	public JSONObject getVideoImagesByRef(String ref_ID) {
		JSONObject json = new JSONObject();
		account.login();
		Token authToken = account.getToken();
		if (authToken != null) {
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("Authorization", authToken.getTokenType()+" "+authToken.getToken());
			String targetURL = "/accounts/"+account.getAccount_ID()+"/videos/ref:"+ref_ID+"/images";
			try {
				String response =  account.platform.getAPI(targetURL, "" , headers);
				if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return json;
	}
	
	// Command permutations
	public JSONArray getVideos() {
		return getVideos("",DEFAULT_LIMIT,DEFAULT_OFFSET,"");
	}
	public JSONArray getVideos(String q) {
		return getVideos(q,DEFAULT_LIMIT,DEFAULT_OFFSET,"");
	}
	public JSONArray getVideos(String q, String sort) {
		return getVideos(q,DEFAULT_LIMIT,DEFAULT_OFFSET,sort);
	}
	public JSONArray getVideos(String q, int limit) {
		return getVideos(q,limit,DEFAULT_OFFSET,"");
	}
	public JSONArray getVideos(String q, int limit, int offset) {
		return getVideos(q,limit,offset,"");
	}
	public JSONArray getVideos(int limit, int offset, String sort) {
		return getVideos("",limit,offset,sort);
	}
	
}
