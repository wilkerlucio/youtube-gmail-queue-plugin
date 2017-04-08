(ns chrome.rpc
  (:require [clojure.core.async :as async]
            [cognitect.transit :as t]
            [om.transit :as ot]))

(defn encode [x] (-> (ot/writer) (t/write x)))
(defn decode [x] (-> (ot/reader) (t/read x)))

(defn send [msg]
  (let [c (async/promise-chan)
        out (encode msg)]
    (js/chrome.runtime.sendMessage
      out
      #(async/put! c (if %
                       (decode %)
                       {:error "Nil"})))
    c))

(defn listen [c]
  (js/chrome.runtime.onMessage.addListener
    (fn [request sender send-response]
      (let [payload (decode request)
            send-response' #(-> % encode send-response)]
        (async/put! c {::payload       payload
                       ::sender        sender
                       ::send-response send-response'}))))
  c)
