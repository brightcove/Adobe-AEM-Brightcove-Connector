package com.coresecure.brightcove.wrapper.sling;

import org.apache.sling.api.SlingHttpServletRequest;

import java.util.Set;

public interface ConfigurationGrabber {

    ConfigurationService getConfigurationService(String key);

    Set<String> getAvailableServices();

    Set<String> getAvailableServices(SlingHttpServletRequest request);

}

