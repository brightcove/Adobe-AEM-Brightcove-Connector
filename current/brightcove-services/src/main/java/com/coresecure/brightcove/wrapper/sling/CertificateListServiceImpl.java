package com.coresecure.brightcove.wrapper.sling;

import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(immediate = true, metatype = true, label = "Brightcove Certificate Service", description = "Brightcove Certificate Service Configuration")
@Service(value = CertificateListService.class)
@Properties({
		@Property(name = "certificate_paths", label = "Certificate Path Mappings", description = "Certificate path mapping for different urls, should be like url:::certificate path", value = {
				"https://players.api.brightcove.com/v2:::D:/cert/players_api.cer",
				"https://cms.api.brightcove.com/v1:::D:/cert/cms_api.cer",
				"https://ingest.api.brightcove.com/v1:::D:/cert/ingest_api.cer",
				"https://oauth.brightcove.com/v4/access_token:::D:/cert/oath_brightcove.cer" }),
		@Property(name = "enable-trusted-certificate", label = "Enable Trusted Certificate", description = "To Enable Enable Trusted Certificate, Value should be YES else NO", value = { "YES" }) })
/**
 * This class is used to get the certificate per domain. These certificate
 * is used to fix the Fortify scan issue.
 * Ref URL https://vulncat.fortify.com/en/vulncat/java/insecure_ssl_overly_broad_certificate_trust.html
 * @author ubaliy
 * 
 */
public class CertificateListServiceImpl implements CertificateListService {
	private ComponentContext componentContext;
	private static Logger loggerVar = LoggerFactory
			.getLogger(CertificateListService.class);
	private static final String CERTIFICATE_PATHS = "certificate_paths";
	private static final String ENABLE_TRUSTED_CERTIFICATE = "enable-trusted-certificate";
	private static final String SEPRATOR = ":::";
	private Dictionary<String, Object> prop;

	private Dictionary<String, Object> getProperties() {
		if (prop == null)
			return new Hashtable<String, Object>();
		return prop;
	}

	@Activate
	void activate(ComponentContext aComponentContext) {
		this.componentContext = aComponentContext;
		this.prop = componentContext.getProperties();
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
		Object p = getProperties().get(CERTIFICATE_PATHS);
		if (p instanceof String[]) {
			return cleanStringArrayPaths((String[]) p);
		}
		return urlsPath;
	}

	/**
	 * This method is used to find the enable / disable the certificate flag.
	 * 
	 * @return
	 */
	public String getEnableTrustedCertificate() {
		return (String) getProperties().get(ENABLE_TRUSTED_CERTIFICATE);
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

		return pathMaps;
	}
}
