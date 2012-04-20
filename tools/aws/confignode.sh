#!/bin/sh
#
#  This scipt is run on the AWS instance to configure the instance ready to run.


NAK_VERSION=$1
if [[ ! -d jdk-6u24-linux-x64 ]]
then
  sh jdk-6u24-linux-x64.bin
fi
rm java
ln -s jdk-6u24-linux-x64 java

cat << EOF > start.sh
#!/bin/sh
export JAVA_HOME=$HOME/java
export PATH=$PATH:$HOME/java/bin

running=\`ps aux | grep org.sakaiproject.nakamura.app-${NAK_VERSION} | grep -v grep | cut -c5-15\`
if [[ a\$running != a ]]
then 
  ps aux | grep org.sakaiproject.nakamura.app-${NAK_VERSION} | grep -v grep
  echo Instance already running
else
  echo Starting new instance....
  java -Dfile.encoding=UTF8 -Xmx1024m -XX:PermSize=256m -server -jar org.sakaiproject.nakamura.app-${NAK_VERSION}.jar 1> run.log 2>&1 &
  echo Waiting for 5...  
  sleep 5
  cat run.log
  echo .... 
fi
EOF

cat << EOF > stop.sh
#!/bin/sh
running=\`ps aux | grep org.sakaiproject.nakamura.app--${NAK_VERSION} | grep -v grep | cut -c5-15\`
if [[ a\$running != a ]]
then
    ps aux | grep org.sakaiproject.nakamura.app--${NAK_VERSION} | grep -v grep
    echo Killing Process $running 
    kill \$running
    echo Waiting for 10...  
    sleep 10
    ps aux | grep org.sakaiproject.nakamura.app--${NAK_VERSION} | grep -v grep
else
    echo Not running
fi

EOF


chmod 700 start.sh
chmod 700 stop.sh

