/bin/echo "postinstall script started [$1]"

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add onix
else
  /sbin/chkconfig --list onix
fi

mkdir -p /var/log/onix

chown -R onix:onix /var/log/onix

ln -s /var/log/onix /usr/local/onix/log

chown onix:onix /usr/local/onix

/bin/echo "postinstall script finished"
exit 0
