(ns ygq.popup.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [untangled.client.core :as uc]
            [cljs.core.async :refer [<!]]
            [ygq.popup.ui :as ui]
            [cljs.core.async :as async]
            [om.next :as om]
            [chrome.rpc :as rpc]
            [google.api :refer [get-auth-token]]))

(defn request-token [reconciler]
  (go
    (let [token (<! (get-auth-token {:interactive true}))]
      (om/transact! reconciler `[(auth/token-received {:token ~token})]))))

(defonce app
  (atom (uc/new-untangled-client
          :started-callback (fn [app]
                              (request-token (:reconciler app))))))

(defn setup-comm []
  (go
    (let [rpc (rpc/listen (async/chan 10))]
      (loop []
        (when-let [{::rpc/keys [payload send-response]} (<! rpc)]
          (let [[k x] payload]
            (case k
              :app/transact! (send-response (om/transact! (-> @app :reconciler) x))
              :app/ping (send-response {:complex "Pong"})
              (do
                (js/console.info "Can't handle message" k)
                nil)))
          (recur))))))

(defonce comm-listener (setup-comm))

(defn init []
  (swap! app uc/mount ui/Root "app-container"))

(comment
  (go
    (let [rpc (rpc/listen (async/chan 10))]
      (loop []
        (when-let [msg (<! rpc)]
          (js/console.log "Got Message" msg)
          (recur)))))

  (go
    (let [res (<! (rpc/send [:app/ping]))]
      (js/console.log "Background response" res)))

  (go
    (let [res (<! (rpc/send [:app/graph [{:video/queue [:youtube.video/id]}]]))]
      (js/console.log "Background response" res))))
