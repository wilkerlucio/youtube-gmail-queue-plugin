(ns ygq.popup.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [untangled.client.core :as uc]
            [cljs.core.async :refer [<!]]
            [ygq.popup.ui :as ui]
            [cljs.core.async :as async]
            [om.next :as om]))

(defn get-auth-token [options]
  (let [c (async/promise-chan)]
    (.getAuthToken js/chrome.identity
                   (clj->js options)
                   #(do
                      (async/put! c %)
                      (async/close! c)))
    c))

(defn request-token [reconciler]
  (go
    (let [token (<! (get-auth-token {:interactive true}))]
      (om/transact! reconciler `[(auth/token-received {:token ~token})]))))

(defonce app
  (atom (uc/new-untangled-client
          :started-callback (fn [app]
                              (request-token (:reconciler app))))))

(defn init []
  (swap! app uc/mount ui/Root "app-container"))
