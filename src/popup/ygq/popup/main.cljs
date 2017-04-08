(ns ygq.popup.main
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ygq.popup.core :as core]
            [cljs.core.async :as async]
            [chrome.rpc :as rpc]))

(core/init)

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
