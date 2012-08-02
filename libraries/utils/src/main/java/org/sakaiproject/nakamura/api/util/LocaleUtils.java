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
package org.sakaiproject.nakamura.api.util;

import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public interface LocaleUtils {

  public static final String DEFAULT_LANGUAGE = "en";
  public static final String DEFAULT_COUNTRY = "US";

  /**
   * Get a valid {@link Locale}. Checks <code>authorizable</code> for a locale setting.
   * Defaults to the server configured language and country code.
   * 
   * @param authorizable
   * @return
   */
  public abstract Locale getLocale(final Authorizable authorizable);

  /**
   * Get a valid {@link Locale}. Checks <code>properties</code> for a locale setting.
   * Defaults to the server configured language and country code.
   * 
   * @param properties
   * @return
   */
  public abstract Locale getLocale(final Map<String, Object> properties);

  /**
   * Get a TimeZone the user has selected for the default TimeZone.
   * 
   * @param authorizable
   * @return
   */
  public abstract TimeZone getTimeZone(final Authorizable authorizable);

  /**
   * Get a TimeZone the user has selected for the default TimeZone.
   * 
   * @param properties
   * @return
   */
  public abstract TimeZone getTimeZone(final Map<String, Object> properties);

  /**
   * Safe read the properties from an {@link Authorizable}. Useful if you want to parse
   * properties once and then pass to {@link #getLocale(Map)} and
   * {@link #getTimeZone(Map)} methods.
   * <p>
   * Copied from LiteMeServlet#getProperties(Authorizable authorizable)
   * 
   * @param authorizable
   * @return In a worst case it will return an empty Map (i.e. never null).
   */
  public abstract Map<String, Object> getProperties(Authorizable authorizable);

  /**
   * Helper method to return ISO3Country
   * 
   * @param locale
   * @return empty string if not available (i.e. never null)
   */
  public abstract String getIso3Country(final Locale locale);

  /**
   * Helper method to return ISO3Language
   * 
   * @param locale
   * @return empty string if not available (i.e. never null)
   */
  public abstract String getIso3Language(final Locale locale);

  /**
   * Helper method to calculate GMT offset consistently across bundles.
   * 
   * @param timezone
   * @return number of hours offset from GMT
   */
  public abstract int getOffset(final TimeZone timezone);
}