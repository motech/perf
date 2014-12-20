#!/bin/bash

HOST=localhost
SLOTS=(1m 2m 3m 1r 2r 3r)
DAYS=(1 2 3 4 5 6 7)
TFDIST=(1 9)


if [ -z $1 ]; then
	echo "no count given, using 10"
	COUNT=10
else
	COUNT=$1
fi

let "ACTIVE_COUNT = $COUNT * TFDIST[0]"
let "INACTIVE_COUNT = $COUNT * TFDIST[1]"

for SLOT in ${SLOTS[@]}; do
	for DAY in ${DAYS[@]}; do
		curl "http://$HOST:8080/motech-platform-server/module/kil2/create-recipients/$SLOT/$DAY/true/$ACTIVE_COUNT"
		curl "http://$HOST:8080/motech-platform-server/module/kil2/create-recipients/$SLOT/$DAY/false/$INACTIVE_COUNT"
	done
done

exit 0
