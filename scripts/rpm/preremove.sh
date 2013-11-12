/bin/echo "preremove script started [$1]"

prefixDir=/usr/local/onix
identifier=onix.jar

isJettyRunning=`pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l`
if [ $isJettyRunning -eq 0 ]
then
  /bin/echo "Onix is not running"
else
  sleepCounter=0
  sleepIncrement=2
  waitTimeOut=600
  /bin/echo "Timeout is $waitTimeOut seconds"
  /bin/echo "Onix is running, stopping service"
  /sbin/service onix stop &
  myPid=$!
  
  until [ `pgrep java -lf | grep $identifier | cut -d" " -f1 | /usr/bin/wc -l` -eq 0 ]  
  do
    if [ $sleepCounter -ge $waitTimeOut ]
    then
      /usr/bin/pkill -KILL -f '$identifier'
      /bin/echo "Killed Onix"
      break
    fi
    sleep $sleepIncrement
    sleepCounter=$(($sleepCounter + $sleepIncrement))
  done

  wait $myPid

  /bin/echo "Onix down"
fi

if [ "$1" = 0 ]
then
  /sbin/chkconfig --del onix
else
  /sbin/chkconfig --list onix
fi

/bin/echo "preremove script finished"
exit 0
