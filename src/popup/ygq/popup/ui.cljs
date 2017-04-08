(ns ygq.popup.ui
  (:require [om.next :as om]
            [om.dom :as dom]
            [untangled.client.core :as uc]
            [untangled.client.mutations :refer [mutate]]))

(defmethod mutate 'auth/token-received [{:keys [state]} _ {:keys [token]}]
  {:action (fn []
             (swap! state assoc :app/user-token token))})

(om/defui ^:once QueuedVideo
  static uc/InitialAppState
  (initial-state [_ title] {:video/id    (random-uuid)
                            :video/title title})

  static om/IQuery
  (query [_] [:video/id :video/title])

  static om/Ident
  (ident [_ props] [:video/by-id (:video/id props)])

  Object
  (render [this]
    (let [{:video/keys [id title]} (om/props this)]
      (dom/div nil
        "Video" title))))

(def queued-video (om/factory QueuedVideo))

(om/defui ^:once Root
  static uc/InitialAppState
  (initial-state [_ _] {:app/queue [(uc/get-initial-state QueuedVideo "Video One")
                                    (uc/get-initial-state QueuedVideo "Video Two")]})

  static om/IQuery
  (query [_] [{:app/queue (om/get-query QueuedVideo)} :ui/react-key :app/user-token])

  Object
  (render [this]
    (let [{:keys [ui/react-key]
           :app/keys [queue user-token]} (om/props this)]
      (dom/div #js {:key react-key}
        (if user-token
          (dom/div nil
            (mapv queued-video queue))
          "Logging in...")))))

(def root (om/factory Root))
