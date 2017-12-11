Adobe AEM Brightcove Connector

This project integrates the **[Brightcove Video Cloud](http://docs.brightcove.com/en/video-cloud/ )** platform into Adobe Experience Manager (CQ5)

<http://www.coresecure.com/brightcove-aem-integration>
#### Supports
 - AEM 6.2, 6.3

## Features
- Components for Authoring Videos and Playlists
   - **HTML5** and **Smart Player** API Support
- Integrated *Library Managment Console* built on top of the **Video Cloud CMS API**
- DAM Integration
- Support for **Multiple Accounts**

## Screenshots
<img src="https://cloud.githubusercontent.com/assets/1116995/11013626/17a9f018-84e3-11e5-8038-b7541751af06.png" width="23%"></img> 
<img src="https://cloud.githubusercontent.com/assets/1116995/11013713/48651914-84e6-11e5-8b25-6e203168726c.png" width="23%"></img>
<img src="https://cloud.githubusercontent.com/assets/1116995/11013720/7d8310f6-84e6-11e5-9eb0-d44041e4d73b.png" width="23%"></img>
<img src="https://cloud.githubusercontent.com/assets/1116995/11013721/852384ee-84e6-11e5-9fae-1ec0a69266a9.png" width="23%" class="player"></img> 

## Content Packages

To build all the modules run in the project root directory the following command with Maven 3:

    mvn clean install

If you have a running AEM instance you can build and package the whole project and deploy into AEM with  

    mvn clean install -PautoInstallPackage
    
Or to deploy it to a publish instance, run

    mvn clean install -PautoInstallPackagePublish
    
Or to deploy only the bundle to the author, run

    mvn clean install -PautoInstallBundle

## Maven settings

The project comes with the auto-public repository configured. To setup the repository in your Maven settings, refer to:

    http://helpx.adobe.com/experience-manager/kb/SetUpTheAdobeMavenRepository.html

##License

	Copyright (C) 2017 **[Coresecure Inc.](https://www.coresecure.com)**

#####Authors:    
   - Alessandro Bonfatti
   - Yan Kisen
   - Pablo Kropilnicki

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

