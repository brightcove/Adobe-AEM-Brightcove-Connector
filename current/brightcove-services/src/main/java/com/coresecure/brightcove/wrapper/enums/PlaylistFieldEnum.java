package com.coresecure.brightcove.wrapper.enums;

import java.util.EnumSet;

public enum PlaylistFieldEnum {
    ID("ID", "id"),
    REFERENCEID("REFERENCEID", "referenceId"),
    ACCOUNTID("ACCOUNTID", "accountId"),
    NAME("NAME", "name"),
    SHORTDESCRIPTION("SHORTDESCRIPTION", "shortDescription"),
    VIDEOIDS("VIDEOIDS", "video_ids"),
    VIDEOS("VIDEOS", "videos"),
    PLAYLISTTYPE("PLAYLISTTYPE", "playlistType"),
    FILTERTAGS("FILTERTAGS", "filterTags"),
    THUMBNAILURL("THUMBNAILURL", "thumbnailUrl");

    private final String definition;
    private final String jsonName;
    PlaylistFieldEnum(String definition, String jsonName){
        this.definition = definition;
        this.jsonName   = jsonName;
    }

    public String getDefinition() {
        return definition;
    }
    public String getJsonName() {
        return jsonName;
    }

    public static EnumSet<PlaylistFieldEnum> CreateEmptyEnumSet(){
        return EnumSet.noneOf(PlaylistFieldEnum.class);
    }

    public static EnumSet<PlaylistFieldEnum> CreateFullEnumSet(){
        return EnumSet.allOf(PlaylistFieldEnum.class);
    }
}