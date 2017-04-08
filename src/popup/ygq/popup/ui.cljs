(ns ygq.popup.ui
  (:require [om.next :as om]
            [om.dom :as dom]))

(om/defui ^:once Root
  Object
  (render [this]
    (let [{:keys []} (om/props this)]
      (dom/div nil
        "UI Root Initialized"))))

(def root (om/factory Root))
