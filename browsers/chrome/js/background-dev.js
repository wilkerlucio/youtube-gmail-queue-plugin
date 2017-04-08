var requireFile = function (src) {
  var script = document.createElement("script");
  script.src = chrome.extension.getURL(src);
  script.defer = false;
  script.async = false;

  document.body.appendChild(script);
};

setTimeout(function () {
  requireFile("js/overrides.js");
  requireFile("js/background-dev/goog/base.js");
  requireFile("js/background-dev/goog/deps.js");
  requireFile("js/background-dev/cljs_deps.js");
  requireFile("js/background-dev-start.js");
}, 10);
