/*
 *  Copyright 2014 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.coresecure.brightcove.tests.impl;

import com.coresecure.brightcove.wrapper.models.BrightcoveAdminPage;
import junitx.util.PrivateAccessor;
import org.apache.sling.settings.SlingSettingsService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simple JUnit test verifying the HelloServiceImpl
 */
public class TestBrightcoveModels {
	
	private BrightcoveAdminPage brightcoveAdminPage;
	
	private String slingId;
	
	@Before
	public void setup() throws Exception {
		brightcoveAdminPage = new BrightcoveAdminPage();
	}
	
	@Test
	public void testBrightCoveModel() throws Exception {
		// some very basic junit tests
		assertNotNull(brightcoveAdminPage.getConfigurationServices());
	}

}
