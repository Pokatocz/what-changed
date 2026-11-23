#!/bin/bash
# gradle-retry.sh <log> <gradle args...> : runs gradle, retrying while Maven Central answers 429.
LOG=$1; shift
for i in 1 2 3 4 5 6 7 8; do
  timeout 1800 "${GRADLE:-gradle}" "$@" > "$LOG" 2>&1 && exit 0
  grep -q " 429 \|Too Many Requests" "$LOG" || exit 1
  sleep $((30 + i * 15))
done
exit 1
