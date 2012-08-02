#!/bin/bash

# Creates cookie and returns it
user="$1"
pass="$2"
url="$3"

session=$(curl -s --cookie-jar - -H "Referer: ${url}/dev" \
      -F'sakaiauth:login=1' \
      -F"sakaiauth:un=$user" \
      -F"sakaiauth:pw=$pass" \
      "$url/system/sling/formlogin" 2>&1 \
      | grep sakai-trusted-authn | awk '{print $7}')

echo $session
