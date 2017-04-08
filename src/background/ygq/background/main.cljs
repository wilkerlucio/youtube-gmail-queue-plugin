(ns ygq.background.main
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<!]]
            [chrome.rpc :as rpc]
            [ygq.background.parser :as p]
            [google.api :as g]))

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
(g/request-token)

(defonce comm-listener
  (go
    (let [rpc (rpc/listen (async/chan 10))]
      (loop []
        (when-let [{::rpc/keys [payload send-response]} (<! rpc)]
          (let [[k x] payload]
            (case k
              :app/graph (send-response (<! (p/parse (g/auth-env) x)))
              :app/ping (send-response {:complex "Pong"})
              (do
                (js/console.info "Can't handle message" k)
                nil)))
          (recur))))))

(comment
  (go
    (let [rpc (rpc/listen (async/chan 10))]
      (loop []
        (when-let [msg (<! rpc)]
          (js/console.log "Got Message" msg)
          (recur)))))

  (go
    (let [res (<! (rpc/send [:app/transact! '[(some/mutation {:a 1})]]))]
      (js/console.log "RES" res))))
