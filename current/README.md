Adobe AEM Brightcove Connector
==============================

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

Pre-compiled Content Packages can be found on the [Releases Page](https://github.com/coresecure/Adobe-AEM-Brightcove-Connector/releases)

## Maven Setup

This project was generated from the com.cqblueprints.archetypes:multi-module Maven Archetype.
- Archetype background from [CQ Blueprints](http://www.cqblueprints.com/setup/maven.html)

To compile this project you will need access to the **Adobe** and **CQ Blueprints** Maven Repositories:
- [Connecting to the CQ Blueprints Maven Repository](http://www.cqblueprints.com/setup/cqmavenrepo.html)
- [Connecting to the Adobe Maven Repository](http://www.cqblueprints.com/setup/adobemavenrepo.html)


## Building

The following Maven commands should be run from the *Project Root* directory:

- ``mvn -Pauto-deploy-all clean install``
   - Build the *services*, *view*, and *config* packages and install to a CQ instance.

- ``mvn -Pauto-deploy-view clean install``
   - Build the *view* and *services* packages and install to a CQ instance.

- ``mvn -Pauto-deploy-services clean install``
   - Build the *services* bundle and install to a CQ instance.

- ``mvn -Pauto-deploy-config clean install``
   - Build the *config* bundle and install to a CQ instance.


### Specifying CRX Host/Port

The CRX host and port can be specified on the command line with:
mvn -Dcq.host=otherhost -Dcq.port=5502 <goals>

## Using with VLT

To use vlt with this project, first build and install the package to your local CQ instance as described above. Then cd to `brightcove-view/src/main/content/jcr_root` and run

    vlt --credentials admin:admin checkout --force http://localhost:4502/crx

Once the working copy is created, you can use the normal ``vlt up`` and ``vlt ci`` commands.


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

