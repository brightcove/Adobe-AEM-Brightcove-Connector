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