#!/usr/bin/env bash

rm -rf browsers/chrome-prod
mkdir -p browsers/chrome-prod
cp -r browsers/chrome/css browsers/chrome-prod
cp -r browsers/chrome/images browsers/chrome-prod
sed -e 's/gc-loader/popup\/ygq/' browsers/chrome/queue-popup.html > browsers/chrome-prod/queue-popup.html
jq 'setpath(["background","scripts", 0]; "js/background/ygq.js")' browsers/chrome/manifest.json > browsers/chrome-prod/manifest.json

lein cljsbuild once popup background

cd browsers/chrome-prod
zip -r ygq.zip .
