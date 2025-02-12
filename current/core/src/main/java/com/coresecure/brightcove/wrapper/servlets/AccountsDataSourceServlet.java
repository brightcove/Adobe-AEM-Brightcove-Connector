/*
 *
 */
package com.coresecure.brightcove.wrapper.servlets;

import static org.apache.sling.api.servlets.HttpConstants.METHOD_GET;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_PATHS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.engine.SlingRequestProcessor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.commerce.common.ValueMapDecorator;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.coresecure.brightcove.wrapper.utils.SlingUtils;
import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.google.gson.Gson;

/**
 * Servlet which handles Brightcove accounts export json.
 */
@Component(immediate = true, service = Servlet.class, property = { SLING_SERVLET_PATHS + "=/bin/accounts/exports",
		SLING_SERVLET_METHODS + "=" + METHOD_GET })
public class AccountsDataSourceServlet extends SlingSafeMethodsServlet {

	Logger logger = LoggerFactory.getLogger(AccountsDataSourceServlet.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 7211849230412200970L;

	/** The Constant BRIGHTCOVE_API_PATH. */
	private static final String BRIGHTCOVE_API_PATH = "/bin/brightcove/accounts";

	/** The request response factory. */
	@Reference
	private RequestResponseFactory requestResponseFactory;

	/** The request processor. */
	@Reference
	private SlingRequestProcessor requestProcessor;

	/**
	 * Do get.
	 *
	 * @param request  the request
	 * @param response the response
	 * @throws ServletException the servlet exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {

		String apiUrl = BRIGHTCOVE_API_PATH + ".json";

		ResourceResolver resourceResolver = request.getResourceResolver();

		logger.debug("api path is " + apiUrl);
		String jsonString = SlingUtils.makeSlingRequest(this.requestResponseFactory, this.requestProcessor,
				resourceResolver, apiUrl);
		logger.debug("account json is " + jsonString);

		Gson gson = new Gson();
		Option[] accounts = gson.fromJson(jsonString, Option[].class);
		List<Resource> optionResourceList = new ArrayList<Resource>();

		for (Option opt : accounts) {
			ValueMap vm = getOptionValueMap(opt);
			optionResourceList
					.add(new ValueMapResource(resourceResolver, new ResourceMetadata(), "nt:unstructured", vm));
		}

		DataSource source = new SimpleDataSource(optionResourceList.iterator());
		request.setAttribute(DataSource.class.getName(), source);
	}

	private ValueMap getOptionValueMap(Option opt) {
		ValueMap vm = new ValueMapDecorator(new HashMap<String, Object>());

		vm.put("value", opt.getValue());
		vm.put("text", opt.getText());
		if (opt.isSelected()) {
			vm.put("selected", true);
		}
		if (opt.isDisabled()) {
			vm.put("disabled", true);
		}
		return vm;
	}

	private class Option {
		String text;
		String value;
		boolean selected;
		boolean disabled;

		public String getText() {
			return text;
		}

		public String getValue() {
			return value;
		}

		public boolean isSelected() {
			return selected;
		}

		public boolean isDisabled() {
			return disabled;
		}
	}
}
