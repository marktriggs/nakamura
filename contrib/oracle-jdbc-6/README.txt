Overview:
---------
This bundle will wrap a oracle ojdbc6 jar that comes with oracle 11g releases 11.1.0.6, 11.1.0.7, 11.2.0.1.0, 11.2.0.2.0, 11.2.0.3 into a osgi bundle.
The oracle ojdbc6.jar file for a particular oracle 11g release is available here:
http://www.oracle.com/technetwork/database/features/jdbc/index-091264.html

Please note ojdbc6.jar file contains classes for use with JDK 1.6

Steps 1)
Download the ojdbc6.jar file for the oracle server you are connecting to, into a temporary directory.

In the temporary directory issue the following maven command to install the oracle ojdbc6.jar file into the local maven repository:
mvn install:install-file -DgroupId=com.oracle -DartifactId=ojdbc6 -Dversion=<oracle-version> -Dpackaging=jar -Dfile=ojdbc6.jar

where <oracle-version> is one of the following: 
11.1.0.6, 11.1.0.7, 11.2.0.1.0, 11.2.0.2.0, 11.2.0.3

For example to install ojdbc6.jar file for 11.2.0.3, issue the following command
mvn install:install-file -DgroupId=com.oracle -DartifactId=ojdbc6 -Dversion=11.2.0.3 -Dpackaging=jar -Dfile=ojdbc6.jar

step 2)
In the contrib/oracle-jdbc-6 directory issue the "mvn -P <oracle-version> clean install" command to wrap the ojdbc6.jar file in a osgi bundle.

where <oracle-version> is one of the following: 
11.1.0.6, 11.1.0.7, 11.2.0.1.0, 11.2.0.2.0, 11.2.0.3

For example to version 11.2.0.3 ojdbc.jar file in a osgi bundle, issue the following command
mvn -P 11.2.0.3 clean install 