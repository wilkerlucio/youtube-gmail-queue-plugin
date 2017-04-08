(ns ygq.background.main
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<!]]))

(defn get-auth-token [options]
  (let [c (async/promise-chan)]
    (.getAuthToken js/chrome.identity
                   (clj->js options)
                   #(do
                      (async/put! c %)
                      (async/close! c)))
    c))

(defonce auth-token (atom nil))

(defn request-token []
  (go
    (let [token (<! (get-auth-token {:interactive true}))]
      (reset! auth-token token))))

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
(request-token)
