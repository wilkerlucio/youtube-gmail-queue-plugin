(ns ygq.background.main)

(defn setup-popup-activation []
  (js/chrome.runtime.onInstalled.addListener
    (fn []
      (js/chrome.declarativeContent.onPageChanged.removeRules
        (fn []
          (js/chrome.declarativeContent.onPageChanged.addRules
            #js [{:conditions
                  #js [(js/chrome.declarativeContent.PageStateMatcher.
                         #js {:pageUrl #js {:urlContains "youtube.com"}})]

                  :actions
                  #js [(js/chrome.declarativeContent.ShowPageAction.)]}]))))))

(setup-popup-activation)
(js/console.log "Initialized")
