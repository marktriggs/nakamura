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

import com.google.common.base.Joiner;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Component(immediate = true)
@Properties({
    @Property(name = LocaleUtilsImpl.LOCALE_LANGUAGE_PROP, value = LocaleUtilsImpl.DEFAULT_LANGUAGE),
    @Property(name = LocaleUtilsImpl.LOCALE_COUNTRY_PROP, value = LocaleUtilsImpl.DEFAULT_COUNTRY) })
public class LocaleUtilsImpl implements LocaleUtils {
  private static final Logger LOG = LoggerFactory.getLogger(LocaleUtilsImpl.class);

  public static final String DEFAULT_LANGUAGE = LocaleUtils.DEFAULT_LANGUAGE;
  public static final String DEFAULT_COUNTRY = LocaleUtils.DEFAULT_COUNTRY;
  public static final String LOCALE_LANGUAGE_PROP = "locale.language";
  public static final String LOCALE_COUNTRY_PROP = "locale.country";

  protected static final String LOCALE_FIELD = "locale";
  protected static final String TIMEZONE_FIELD = "timezone";
  // ^[a-zA-Z]{2}([_]?([a-zA-Z]{2}|[0-9]{3}))?$");
  protected static final String LANGUAGE_PATTERN = "([a-zA-Z]{2})";
  protected static final String COUNTRY_PATTERN = "([a-zA-Z]{2}|[0-9]{3})";
  protected static final String LOCALE_PATTERN = "^%s(_%s)?$";
  protected static final Pattern LOCALE_REGEX = Pattern.compile(String.format(
      LOCALE_PATTERN, LANGUAGE_PATTERN, COUNTRY_PATTERN));

  protected String defaultLanguage = DEFAULT_LANGUAGE;
  protected String defaultCountry = DEFAULT_COUNTRY;

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getLocale(org.sakaiproject.nakamura.api.lite.authorizable.Authorizable)
   */
  @Override
  public Locale getLocale(final Authorizable authorizable) {
    final Map<String, Object> properties = getProperties(authorizable);
    return getLocale(properties);
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getLocale(java.util.Map)
   */
  @Override
  public Locale getLocale(final Map<String, Object> properties) {
    /* Get the correct locale */
    String localeLanguage = defaultLanguage;
    String localeCountry = defaultCountry;
    if (properties.containsKey(LOCALE_FIELD)) {
      final String localeProp = String.valueOf(properties.get(LOCALE_FIELD));
      final Matcher localeMatcher = LOCALE_REGEX.matcher(localeProp);
      if (localeMatcher.matches()) {
        localeLanguage = localeMatcher.group(1);
        if (localeMatcher.groupCount() == 3 && localeMatcher.group(3) != null) {
          localeCountry = localeMatcher.group(3).toUpperCase();
        } else {
          localeCountry = "";
        }
      } else {
        LOG.debug("Using default locale [{}_{}] instead of locale setting [{}]",
            new Object[] { localeLanguage, localeCountry, localeProp });
      }
    } else {
      LOG.debug("Using default locale [{}_{}]; no locale setting found", new Object[] {
          localeLanguage, localeCountry });
    }

    final Locale locale = new Locale(localeLanguage, localeCountry);
    return locale;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getTimeZone(org.sakaiproject.nakamura.api.lite.authorizable.Authorizable)
   */
  @Override
  public TimeZone getTimeZone(final Authorizable authorizable) {
    final Map<String, Object> properties = getProperties(authorizable);
    return getTimeZone(properties);
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getTimeZone(java.util.Map)
   */
  @Override
  public TimeZone getTimeZone(final Map<String, Object> properties) {
    /* Get the correct time zone */
    TimeZone tz = TimeZone.getDefault();
    if (properties.containsKey(TIMEZONE_FIELD)) {
      String timezone = String.valueOf(properties.get(TIMEZONE_FIELD));
      tz = TimeZone.getTimeZone(timezone);
    }
    return tz;
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getProperties(org.sakaiproject.nakamura.api.lite.authorizable.Authorizable)
   */
  @Override
  public Map<String, Object> getProperties(final Authorizable authorizable) {
    final Map<String, Object> result = new HashMap<String, Object>();
    if (authorizable != null) {
      for (final String propName : authorizable.getSafeProperties().keySet()) {
        if (propName.startsWith("rep:")) {
          continue;
        }
        final Object o = authorizable.getProperty(propName);
        if (o instanceof Object[]) {
          final Object[] values = (Object[]) o;
          switch (values.length) {
          case 0:
            continue;
          case 1:
            result.put(propName, values[0]);
            break;
          default: {
            final String valueString = Joiner.on(',').join(values);
            result.put(propName, valueString);
          }
          }
        } else {
          result.put(propName, o);
        }
      }
    }
    return result;
  }

  @Activate
  @Modified
  protected void activate(final Map<?, ?> props) {
    defaultLanguage = PropertiesUtil.toString(props.get(LOCALE_LANGUAGE_PROP),
        defaultLanguage);
    defaultCountry = PropertiesUtil.toString(props.get(LOCALE_COUNTRY_PROP),
        defaultCountry).toUpperCase();
  }

  /**
   * 
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getIso3Country(java.util.Locale)
   */
  @Override
  public String getIso3Country(final Locale locale) {
    String iso3Country = null;
    try {
      iso3Country = locale.getISO3Country();
    } catch (MissingResourceException e) {
      LOG.debug("Unable to find ISO3 country [{}]", locale);
      iso3Country = "";
    }
    return iso3Country;
  }

  /**
   * 
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getIso3Language(java.util.Locale)
   */
  @Override
  public String getIso3Language(final Locale locale) {
    String iso3Language = null;
    try {
      iso3Language = locale.getISO3Language();
    } catch (MissingResourceException e) {
      LOG.debug("Unable to find ISO3 language [{}]", locale);
      iso3Language = "";
    }
    return iso3Language;
  }

  /**
   * 
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.util.LocaleUtils#getOffset(java.util.TimeZone)
   */
  @Override
  public int getOffset(final TimeZone timezone) {
    int daylightSavingsOffset = timezone.inDaylightTime(new Date()) ? timezone
        .getDSTSavings() : 0;
    int offset = timezone.getRawOffset() + daylightSavingsOffset;
    return offset / 3600000;
  }

}
