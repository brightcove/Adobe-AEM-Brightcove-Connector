package com.coresecure.brightcove.wrapper.sling;

import java.util.Map;

/**
 * This interface is used to get the certificate per domain. These certificate
 * is used to fix the Fortify scan issue.
 * Ref URL https://vulncat.fortify.com/en/vulncat/java/insecure_ssl_overly_broad_certificate_trust.html
 * @author ubaliy
 * 
 */
public interface CertificateListService {
	/**
	 * This method is used to find the certificate paths for the requested URL.
	 * @return
	 */
	Map<String, String> getCertificatePaths();
	/**
	 * This method is used to find enable / disable the certificate.
	 * @return
	 */
	String getEnableTrustedCertificate();
}
