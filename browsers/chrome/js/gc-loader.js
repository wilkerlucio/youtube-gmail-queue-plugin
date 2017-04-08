var requireFile = function (src) {
  var script = document.createElement("script");
  script.src = chrome.extension.getURL(src);
  script.defer = false;
  script.async = false;

  document.body.appendChild(script);
};

setTimeout(function () {
  requireFile("js/overrides.js");
  requireFile("js/dev/goog/base.js");
  requireFile("js/dev/goog/deps.js");
  requireFile("js/dev/cljs_deps.js");
  requireFile("js/start.js");
}, 10);

