# Youtube Gmail Queue

This repository contains the source code for the [Chrome Extension Youtube Gmail Queue](https://chrome.google.com/webstore/detail/youtube-gmail-queue/addckdaiclaekgbnkkijijefabgmejoa?hl=en-US&utm_source=chrome-ntp-launcher).

## Development

To start a development compilation run the follwing command:

```
lein figwheel background-dev popup-dev
```

Then on Chrome:

1. Go at chrome://extensions
2. Click `Load unpacked extension...`
3. Select folder `./browsers/chrome`

## Building

If you want to run a release build (which runs faster) use the script:

```
./script/build.sh
```

Then on Chrome:

1. Go at chrome://extensions
2. Click `Load unpacked extension...`
3. Select folder `./browsers/chrome-prod`

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
