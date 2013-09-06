Name: onix1
Version: 1.0.4
Release: 1
Summary: RPM for Onix service
License: Nokia 2013
Requires: jdk >= 2000:1.6.0_31-fcs
autoprov: no
autoreq: no
BuildRoot: /home/mdaley/workspace/onix/onix1/buildroot

%description

%install
if [ -e $RPM_BUILD_ROOT ];
then
  mv /home/mdaley/workspace/onix/onix1/tmp-buildroot/* $RPM_BUILD_ROOT
else
  mv /home/mdaley/workspace/onix/onix1/tmp-buildroot $RPM_BUILD_ROOT
fi

%files

%attr(444,jetty,jetty) /usr/local/jetty/onix.jar
%attr(744,jetty,jetty) /usr/local/jetty/bin
%attr(744,-,-) /usr/local/deployment/onix1/bin
%attr(744,jetty,jetty) /etc/rc.d/init.d

%pre
/bin/echo "preinstall script started [$1]"

prefixDir=/usr/local/jetty
identifier=onix.jar

isJettyRunning=`pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l`
if [ $isJettyRunning -eq 0 ]
then
  /bin/echo "Jetty is not running"
else
  sleepCounter=0
  sleepIncrement=2
  waitTimeOut=600

  /bin/echo "Timeout is $waitTimeOut seconds"
  /bin/echo "Jetty is running, stopping service"
  /sbin/service jetty stop &
  myPid=$!

  until [ `pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l` -eq 0 ]
  do
    if [ $sleepCounter -ge $waitTimeOut ]
    then
      /usr/bin/pkill -KILL -f '$identifier'
      /bin/echo "Killed Jetty"
      break
    fi
    sleep $sleepIncrement
    sleepCounter=$(($sleepCounter + $sleepIncrement))
  done

  wait $myPid

  /bin/echo "Jetty down"
fi

rm -rf $prefixDir

if [ "$1" -le 1 ]
then
  mkdir -p $prefixDir
  /usr/sbin/useradd -r -s /sbin/nologin -d $prefixDir -m -c "Jetty user for the Jetty service" jetty 2> /dev/null || :
fi

/usr/bin/getent passwd jetty

/bin/echo "preinstall script finished"
exit 0

%post
/bin/echo "postinstall script started [$1]"

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add jetty
else
  /sbin/chkconfig --list jetty
fi

mkdir -p /var/log/jetty

chown -R jetty:jetty /var/log/jetty

ln -s /var/log/jetty /usr/local/jetty/log

chown jetty:jetty /usr/local/jetty

/bin/echo "postinstall script finished"
exit 0

%preun
/bin/echo "preremove script started [$1]"

prefixDir=/usr/local/jetty
identifier=onix.jar

isJettyRunning=`pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l`
if [ $isJettyRunning -eq 0 ]
then
  /bin/echo "Jetty is not running"
else
  sleepCounter=0
  sleepIncrement=2
  waitTimeOut=600
  /bin/echo "Timeout is $waitTimeOut seconds"
  /bin/echo "Jetty is running, stopping service"
  /sbin/service jetty stop &
  myPid=$!
  
  until [ `pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l` -eq 0 ]  
  do
    if [ $sleepCounter -ge $waitTimeOut ]
    then
      /usr/bin/pkill -KILL -f '$identifier'
      /bin/echo "Killed Jetty"
      break
    fi
    sleep $sleepIncrement
    sleepCounter=$(($sleepCounter + $sleepIncrement))
  done

  wait $myPid

  /bin/echo "Jetty down"
fi

if [ "$1" = 0 ]
then
  /sbin/chkconfig --del jetty
else
  /sbin/chkconfig --list jetty
fi

/bin/echo "preremove script finished"
exit 0

%postun
/bin/echo "postremove script started [$1]"

if [ "$1" = 0 ]
then
  /usr/sbin/userdel -r jetty 2> /dev/null || :
  /bin/rm -rf /usr/local/jetty
fi

/bin/echo "postremove script finished"
exit 0
