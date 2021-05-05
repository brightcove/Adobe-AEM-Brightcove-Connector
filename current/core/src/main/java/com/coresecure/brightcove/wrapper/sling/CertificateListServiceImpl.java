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
package com.coresecure.brightcove.wrapper.sling;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(service = CertificateListService.class)
@Designate(ocd = CertificateListServiceImpl.Config.class)

/**
 * This class is used to get the certificate per domain. These certificate
 * is used to fix the Fortify scan issue.
 * Ref URL https://vulncat.fortify.com/en/vulncat/java/insecure_ssl_overly_broad_certificate_trust.html
 * @author ubaliy
 * 
 */
public class CertificateListServiceImpl implements CertificateListService {

	@ObjectClassDefinition(name = "Brightcove Certificate Service", description = "Brightcove Certificate Service Configuration")
	public static @interface Config {

		@AttributeDefinition(name = "Certificate Path Mappings", description = "Certificate path mapping for different urls, should be like url:::certificate path")
		String[] certificatePathMappings() default {
			"https://players.api.brightcove.com/v2:::D:/cert/players_api.cer",
			"https://cms.api.brightcove.com/v1:::D:/cert/cms_api.cer",
			"https://ingest.api.brightcove.com/v1:::D:/cert/ingest_api.cer",
			"https://oauth.brightcove.com/v4/access_token:::D:/cert/oath_brightcove.cer"
		};

		@AttributeDefinition(name = "Enable Trusted Certificate", description = "To Enable Enable Trusted Certificate, Value should be YES else NO")
		String enableTrustedCertificate() default "NO";
	}
	
	private static Logger logger = LoggerFactory
			.getLogger(CertificateListService.class);

	private static final String SEPRATOR = ":::";
	private String enableTrustedCertificate = "NO";
	private String[] certificatePathMappings;

	@Activate
	void activate(final Config config) {
		this.enableTrustedCertificate = config.enableTrustedCertificate();
		this.certificatePathMappings = config.certificatePathMappings();
	}

	/**
	 * This method is used to read the configured property of secure url and
	 * respective certificate paths. This method is added for the Fortify scan
	 * fixes. Ref URL https://vulncat.fortify.com/en/vulncat/java/
	 * insecure_ssl_overly_broad_certificate_trust.html
	 * 
	 */
	public Map<String, String> getCertificatePaths() {
		Map<String, String> urlsPath = new HashMap<String, String>();

		if (this.certificatePathMappings != null) {
			return cleanStringArrayPaths(this.certificatePathMappings);
		}

		return urlsPath;
	}

	/**
	 * This method is used to find the enable / disable the certificate flag.
	 * 
	 * @return
	 */
	public String getEnableTrustedCertificate() {
		return this.enableTrustedCertificate;
	}

	private Map<String, String> cleanStringArrayPaths(String[] input) {
		Map<String, String> pathMaps = new HashMap<String, String>();
		for (String s : input) {
			if (s != null && s.trim().length() > 0) {
				String url = s.substring(0, s.indexOf(SEPRATOR));
				String certPath = s.substring(s.indexOf(SEPRATOR) + 3, s.length());
				pathMaps.put(url, certPath);
			}
		}

		logger.debug("cleanStringArrayPaths(): {}", pathMaps.toString());

		return pathMaps;
	}
}
