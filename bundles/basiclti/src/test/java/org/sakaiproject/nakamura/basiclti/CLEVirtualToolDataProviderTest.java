/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.basiclti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.CLE_BASICLTI_FRAME_HEIGHT;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.CLE_BASICLTI_FRAME_HEIGHT_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.CLE_BASICLTI_KEY;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.CLE_BASICLTI_SECRET;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.CLE_SERVER_URL;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.DEFAULT_TOOL_LIST;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_DEBUG;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_DEBUG_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_KEY_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_RELEASE_EMAIL;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_RELEASE_EMAIL_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_RELEASE_NAMES;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_RELEASE_NAMES_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_RELEASE_PRINCIPAL;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_RELEASE_PRINCIPAL_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_SECRET_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.LTI_URL_LOCK;
import static org.sakaiproject.nakamura.basiclti.CLEVirtualToolDataProvider.TOOL_LIST;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class CLEVirtualToolDataProviderTest {

  CLEVirtualToolDataProvider cleVirtualToolDataProvider;
  @Mock
  ComponentContext componentContext;
  @Mock
  Dictionary properties;

  protected String cleUrl;
  protected String ltiKey;
  protected String ltiSecret;
  protected Long frameHeight;
  protected boolean frameHeightLock;
  protected boolean urlLock;
  protected boolean keyLock;
  protected boolean secretLock;
  protected boolean releaseNames;
  protected boolean releaseNamesLock;
  protected boolean releaseEmail;
  protected boolean releaseEmailLock;
  protected boolean releasePrincipal;
  protected boolean releasePrincipalLock;
  protected boolean debug;
  protected boolean debugLock;
  protected List<String> toolList = Arrays.asList(DEFAULT_TOOL_LIST);

  @Before
  public void setUp() {
    cleVirtualToolDataProvider = new CLEVirtualToolDataProvider();
    cleUrl = cleVirtualToolDataProvider.cleUrl;
    ltiKey = cleVirtualToolDataProvider.ltiKey;
    ltiSecret = cleVirtualToolDataProvider.ltiSecret;
    frameHeight = cleVirtualToolDataProvider.frameHeight;
    frameHeightLock = cleVirtualToolDataProvider.frameHeightLock;
    urlLock = cleVirtualToolDataProvider.urlLock;
    keyLock = cleVirtualToolDataProvider.keyLock;
    secretLock = cleVirtualToolDataProvider.secretLock;
    releaseNames = cleVirtualToolDataProvider.releaseNames;
    releaseNamesLock = cleVirtualToolDataProvider.releaseNamesLock;
    releaseEmail = cleVirtualToolDataProvider.releaseEmail;
    releaseEmailLock = cleVirtualToolDataProvider.releaseEmailLock;
    releasePrincipal = cleVirtualToolDataProvider.releasePrincipal;
    releasePrincipalLock = cleVirtualToolDataProvider.releasePrincipalLock;
    debug = cleVirtualToolDataProvider.debug;
    debugLock = cleVirtualToolDataProvider.debugLock;

    applyWhenConditions();
  }

  /**
   * Simple case where all configuration is same as defaults.
   */
  @Test
  public void testActivateUsingDefaultValues() {
    cleVirtualToolDataProvider.activate(componentContext);
    verifyConfiguration();
  }

  /**
   * Test all non-default values to make sure they stick.
   */
  @Test
  public void testActivateUsingNonDefaultValues() {
    setupNonDefaultValues();

    cleVirtualToolDataProvider.activate(componentContext);

    verifyConfiguration();
  }

  /**
   * Normal use case; return default tool list.
   */
  @Test
  public void testGetSupportedVirtualToolIds() {
    final List<String> supportedToolIds = cleVirtualToolDataProvider
        .getSupportedVirtualToolIds();
    assertNotNull(supportedToolIds);
    assertEquals(toolList, supportedToolIds);
  }

  /**
   * Defensive edge case where tool list might be null. Empty list should be returned
   * according to contract.
   */
  @Test
  public void testGetSupportedVirtualToolIdsNullToolList() {
    cleVirtualToolDataProvider.toolList = null;
    final List<String> supportedToolIds = cleVirtualToolDataProvider
        .getSupportedVirtualToolIds();
    assertNotNull(supportedToolIds);
    assertTrue(supportedToolIds.isEmpty());
  }

  /**
   * Test normal use case where we have a matching tool id.
   */
  @Test
  public void testGetKeySecret() {
    final Map<String, Object> keySecret = cleVirtualToolDataProvider
        .getKeySecret(toolList.get(0));
    assertNotNull(keySecret);
    assertEquals(ltiKey, keySecret.get(BasicLTIAppConstants.LTI_KEY));
    assertEquals(ltiSecret, keySecret.get(BasicLTIAppConstants.LTI_SECRET));
  }

  /**
   * Negative test case passing null argument to method.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetKeySecretNullToolId() {
    cleVirtualToolDataProvider.getKeySecret(null);
  }

  /**
   * Negative test case passing empty string to method.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetKeySecretEmptyToolId() {
    cleVirtualToolDataProvider.getKeySecret("");
  }

  /**
   * Test uncommon case where caller passes a toolId that cannot be matched.
   */
  @Test
  public void testGetKeySecretNoToolIdFound() {
    final Map<String, Object> keySecret = cleVirtualToolDataProvider
        .getKeySecret("/foo/bar/baz");
    assertNull(keySecret);
  }

  /**
   * Test normal use case and expected launch values.
   */
  @Test
  public void testGetLaunchValuesDefaultValues() {
    final Map<String, Object> launchValues = cleVirtualToolDataProvider
        .getLaunchValues(toolList.get(0));
    verifyLaunchValues(launchValues);
  }

  /**
   * Test launch values when using non-default configuration.
   */
  @Test
  public void testGetLaunchValuesNonDefaultValues() {
    setupNonDefaultValues();
    cleVirtualToolDataProvider.activate(componentContext);

    final Map<String, Object> launchValues = cleVirtualToolDataProvider
        .getLaunchValues(toolList.get(0));

    verifyLaunchValues(launchValues);
  }

  /**
   * Test edge case where null argument is passed.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetLaunchValuesNullArgument() {
    cleVirtualToolDataProvider.getLaunchValues(null);
  }

  /**
   * Test edge case where empty string is passed.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testGetLaunchValuesEmptyString() {
    cleVirtualToolDataProvider.getLaunchValues("");
  }

  /**
   * Test uncommmon case where caller asks for a toolId we cannot match.
   */
  @Test
  public void testGetLaunchValuesNoToolIdFound() {
    final Map<String, Object> launchValues = cleVirtualToolDataProvider
        .getLaunchValues("/foo/bar/bazooey");
    assertNull(launchValues);
  }

  // --------------------------------------------------------------------------

  private void setupNonDefaultValues() {
    cleUrl = "altCleUrl";
    assertNotSame(cleUrl, cleVirtualToolDataProvider.cleUrl);
    ltiKey = "altLtiKey";
    assertNotSame(ltiKey, cleVirtualToolDataProvider.ltiKey);
    ltiSecret = "altLtiSecret";
    assertNotSame(ltiSecret, cleVirtualToolDataProvider.ltiSecret);
    frameHeight = 99999999999L;
    assertNotSame(frameHeight, cleVirtualToolDataProvider.frameHeight);
    frameHeightLock = false;
    assertNotSame(frameHeightLock, cleVirtualToolDataProvider.frameHeightLock);
    urlLock = false;
    assertNotSame(urlLock, cleVirtualToolDataProvider.urlLock);
    keyLock = false;
    assertNotSame(keyLock, cleVirtualToolDataProvider.keyLock);
    secretLock = false;
    assertNotSame(secretLock, cleVirtualToolDataProvider.secretLock);
    releaseNames = false;
    assertNotSame(releaseNames, cleVirtualToolDataProvider.releaseNames);
    releaseNamesLock = false;
    assertNotSame(releaseNamesLock, cleVirtualToolDataProvider.releaseNamesLock);
    releaseEmail = false;
    assertNotSame(releaseEmail, cleVirtualToolDataProvider.releaseEmail);
    releaseEmailLock = false;
    assertNotSame(releaseEmailLock, cleVirtualToolDataProvider.releaseEmailLock);
    releasePrincipal = false;
    assertNotSame(releasePrincipal, cleVirtualToolDataProvider.releasePrincipal);
    releasePrincipalLock = false;
    assertNotSame(releasePrincipalLock, cleVirtualToolDataProvider.releasePrincipalLock);
    debug = true;
    assertNotSame(debug, cleVirtualToolDataProvider.debug);
    debugLock = false;
    assertNotSame(debugLock, cleVirtualToolDataProvider.debugLock);
    toolList = new ArrayList<String>();
    toolList.add("foo.bar.baz");
    assertNotSame(toolList, cleVirtualToolDataProvider.toolList);

    // re-apply when statements with new values
    applyWhenConditions();
  }

  private void verifyConfiguration() {
    assertEquals(cleUrl, cleVirtualToolDataProvider.cleUrl);
    assertEquals(ltiKey, cleVirtualToolDataProvider.ltiKey);
    assertEquals(ltiSecret, cleVirtualToolDataProvider.ltiSecret);
    assertEquals(frameHeight, cleVirtualToolDataProvider.frameHeight);
    assertEquals(frameHeightLock, cleVirtualToolDataProvider.frameHeightLock);
    assertEquals(urlLock, cleVirtualToolDataProvider.urlLock);
    assertEquals(keyLock, cleVirtualToolDataProvider.keyLock);
    assertEquals(secretLock, cleVirtualToolDataProvider.secretLock);
    assertEquals(releaseNames, cleVirtualToolDataProvider.releaseNames);
    assertEquals(releaseNamesLock, cleVirtualToolDataProvider.releaseNamesLock);
    assertEquals(releaseEmail, cleVirtualToolDataProvider.releaseEmail);
    assertEquals(releaseEmailLock, cleVirtualToolDataProvider.releaseEmailLock);
    assertEquals(releasePrincipal, cleVirtualToolDataProvider.releasePrincipal);
    assertEquals(releasePrincipalLock, cleVirtualToolDataProvider.releasePrincipalLock);
    assertEquals(debug, cleVirtualToolDataProvider.debug);
    assertEquals(debugLock, cleVirtualToolDataProvider.debugLock);
    assertEquals(toolList, cleVirtualToolDataProvider.toolList);
  }

  private void applyWhenConditions() {
    when(componentContext.getProperties()).thenReturn(properties);
    when(properties.get(CLE_SERVER_URL)).thenReturn(cleUrl);
    when(properties.get(CLE_BASICLTI_KEY)).thenReturn(ltiKey);
    when(properties.get(CLE_BASICLTI_SECRET)).thenReturn(ltiSecret);
    when(properties.get(CLE_BASICLTI_FRAME_HEIGHT)).thenReturn(frameHeight);
    when(properties.get(CLE_BASICLTI_FRAME_HEIGHT_LOCK)).thenReturn(frameHeightLock);
    when(properties.get(LTI_URL_LOCK)).thenReturn(urlLock);
    when(properties.get(LTI_KEY_LOCK)).thenReturn(keyLock);
    when(properties.get(LTI_SECRET_LOCK)).thenReturn(secretLock);
    when(properties.get(LTI_RELEASE_NAMES)).thenReturn(releaseNames);
    when(properties.get(LTI_RELEASE_NAMES_LOCK)).thenReturn(releaseNamesLock);
    when(properties.get(LTI_RELEASE_EMAIL)).thenReturn(releaseEmail);
    when(properties.get(LTI_RELEASE_EMAIL_LOCK)).thenReturn(releaseEmailLock);
    when(properties.get(LTI_RELEASE_PRINCIPAL)).thenReturn(releasePrincipal);
    when(properties.get(LTI_RELEASE_PRINCIPAL_LOCK)).thenReturn(releasePrincipalLock);
    when(properties.get(LTI_DEBUG)).thenReturn(debug);
    when(properties.get(LTI_DEBUG_LOCK)).thenReturn(debugLock);
    when(properties.get(TOOL_LIST)).thenReturn(toolList);
  }

  private void verifyLaunchValues(final Map<String, Object> launchValues) {
    assertNotNull(launchValues);
    assertEquals(cleUrl + "/imsblti/provider/" + toolList.get(0),
        launchValues.get(BasicLTIAppConstants.LTI_URL));
    assertEquals(frameHeight, launchValues.get(BasicLTIAppConstants.FRAME_HEIGHT));
    assertEquals(frameHeightLock,
        launchValues.get(BasicLTIAppConstants.FRAME_HEIGHT_LOCK));
    assertEquals(urlLock, launchValues.get(BasicLTIAppConstants.LTI_URL_LOCK));
    assertEquals(keyLock, launchValues.get(BasicLTIAppConstants.LTI_KEY_LOCK));
    assertEquals(secretLock, launchValues.get(BasicLTIAppConstants.LTI_SECRET_LOCK));
    assertEquals(releaseNames, launchValues.get(BasicLTIAppConstants.RELEASE_NAMES));
    assertEquals(releaseNamesLock,
        launchValues.get(BasicLTIAppConstants.RELEASE_NAMES_LOCK));
    assertEquals(releaseEmail, launchValues.get(BasicLTIAppConstants.RELEASE_EMAIL));
    assertEquals(releaseEmailLock,
        launchValues.get(BasicLTIAppConstants.RELEASE_EMAIL_LOCK));
    assertEquals(releasePrincipal,
        launchValues.get(BasicLTIAppConstants.RELEASE_PRINCIPAL_NAME));
    assertEquals(releasePrincipalLock,
        launchValues.get(BasicLTIAppConstants.RELEASE_PRINCIPAL_NAME_LOCK));
    assertEquals(debug, launchValues.get(BasicLTIAppConstants.DEBUG));
    assertEquals(debugLock, launchValues.get(BasicLTIAppConstants.DEBUG_LOCK));
  }
}
