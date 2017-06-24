(ns common.js
  (:require [goog.object :as gobj]))

(defn call [root path & args]
  (let [context (gobj/getValueByKeys root (clj->js (butlast path)))
        f       (gobj/getValueByKeys root (clj->js path))]
    (.apply f context (clj->js args))))
