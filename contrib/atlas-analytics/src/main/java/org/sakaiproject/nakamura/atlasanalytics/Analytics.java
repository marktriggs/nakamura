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
package org.sakaiproject.nakamura.atlasanalytics;


import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class Analytics
{
  private File outputDir;
  private SimpleDateFormat dateFormatter;
  private String filePattern;

  private String currentDate;
  private PrintWriter currentOutput;

  private SimpleDateFormat local;
  private SimpleDateFormat utc;

  private Logger LOGGER = LoggerFactory.getLogger(AnalyticsServlet.class);


  public Analytics(File outputDir, String datePattern, String filePattern) {
    this.outputDir = outputDir;
    this.dateFormatter = new SimpleDateFormat(datePattern);
    this.filePattern = filePattern;

    this.local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    this.utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    this.utc.setTimeZone(TimeZone.getTimeZone("UTC"));
  }


  private PrintWriter getOutput(Date now) throws IOException {
    String output = dateFormatter.format(now);

    if (!output.equals(currentDate)) {
      close();

      currentDate = output;
      File outfile = new File(this.outputDir, String.format(this.filePattern, this.currentDate));
      currentOutput = new PrintWriter(new FileOutputStream(outfile, true));

      currentOutput.println("# ms since epoch\tUTC time\tLocal time\tuser\tdata");
    }

    return currentOutput;
  }


  synchronized public void close () {
    if (currentOutput != null) {
      currentOutput.close();
      currentOutput = null;
    }
  }


  synchronized public void record (String user, String data) {
    long now = System.currentTimeMillis();
    Date nowDate = new Date(now);

    String timestamp = String.format("%s\t%s\t%s\t",
        String.valueOf(now), utc.format(nowDate), local.format(nowDate));

    try {
      PrintWriter output = getOutput(nowDate);
      output.println(timestamp + user + "\t" + data);
      output.flush();
    } catch (IOException e) {
      LOGGER.warn("Exception when writing analytics log: {}", e);
      e.printStackTrace();
    }
  }
}
