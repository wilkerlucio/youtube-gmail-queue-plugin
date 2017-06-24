(ns chrome.rpc
  (:require [clojure.core.async :as async]
            [common.js :as cjs]
            [cognitect.transit :as t]
            [om.transit :as ot]))

(set! *warn-on-infer* true)

(defn encode [x] (-> (ot/writer) (t/write x)))
(defn decode [x] (-> (ot/reader) (t/read x)))

(defn send [msg]
  (let [c (async/promise-chan)
        out (encode msg)]
    (cjs/call js/window ["chrome" "runtime" "sendMessage"]
      out
      #(async/put! c (if %
                       (decode %)
                       {:error "Nil"})))
    c))

(defn listen [c]
  (cjs/call js/window ["chrome" "runtime" "onMessage" "addListener"]
    (fn [request sender send-response]
      (let [payload (decode request)
            send-response' #(-> % encode send-response)]
        (async/put! c {::payload       payload
                       ::sender        sender
                       ::send-response send-response'}))))
  c)
