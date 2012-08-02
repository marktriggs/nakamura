/**
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
package org.sakaiproject.nakamura.util;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class LocaleUtilsImplTest {
  LocaleUtilsImpl localeUtils;
  Map<?, ?> props;
  Map<String, Object> authzProps;

  @Mock
  Authorizable authorizable;

  @SuppressWarnings("rawtypes")
  @Before
  public void setUp() throws Exception {
    localeUtils = new LocaleUtilsImpl();
    props = new HashMap();
    localeUtils.activate(props);

    authzProps = new HashMap<String, Object>();
    authzProps.put("rep:foo", "foo");
    when(authorizable.getSafeProperties()).thenReturn(authzProps);
  }

  @Test
  public void testDefaultLocaleAuthz() {
    Locale locale = localeUtils.getLocale(authorizable);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());
  }

  @Test
  public void getDefaultLocale() {
    Locale locale = localeUtils.getLocale(Collections.<String, Object> emptyMap());
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());
  }

  @Test
  public void getCustomLocale() {
    // common la_CO format
    Map<String, Object> props = ImmutableMap.<String, Object> of("locale",
        Locale.GERMANY.toString());
    Locale locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.GERMANY.getLanguage());
    assertEquals(locale.getCountry(), Locale.GERMANY.getCountry());

    // the funky format that prompted all this es_419
    props = ImmutableMap.<String, Object> of("locale", "es_419");
    locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), "es");
    assertEquals(locale.getCountry(), "419");
  }

  @Test
  public void getCustomLanguage() {
    Map<String, Object> props = ImmutableMap.<String, Object> of("locale",
        Locale.GERMAN.toString());
    Locale locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.GERMAN.getLanguage());
    assertEquals(locale.getCountry(), "");
  }

  @Test
  public void getInvalidLocale() {
    // this is of bad form
    Map<String, Object> props = ImmutableMap.<String, Object> of("locale", "bad_WRONG");
    Locale locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is of bad form
    props = ImmutableMap.<String, Object> of("locale", "123_456");
    locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is of valid form but not a real locale
    props = ImmutableMap.<String, Object> of("locale", "xx_XX");
    locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), "xx");
    assertEquals(locale.getCountry(), "XX");

    // this is just weird
    props = ImmutableMap.<String, Object> of("locale", "_");
    locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is jibberish
    props = ImmutableMap.<String, Object> of("locale", "jibberish");
    locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());

    // this is for utf8 testing
    props = ImmutableMap.<String, Object> of("locale", "ŠšĐđČčĆćŽž");
    locale = localeUtils.getLocale(props);
    assertEquals(locale.getLanguage(), Locale.US.getLanguage());
    assertEquals(locale.getCountry(), Locale.US.getCountry());
  }

}
