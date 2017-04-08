(ns ygq.popup.core
  (:require [untangled.client.core :as uc]
            [ygq.popup.ui :as ui]))

(defonce app (atom (uc/new-untangled-client)))

(defn init []
  (swap! app uc/mount ui/Root "app-container"))
