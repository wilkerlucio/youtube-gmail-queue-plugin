(ns ygq.popup.ui
  (:require [om.next :as om]
            [om.dom :as dom]
            [youtube.video :as video]
            [untangled.client.core :as uc]
            [untangled.client.mutations :refer [mutate]]
            [untangled.client.data-fetch :as df]))

(defmethod mutate 'auth/token-received [{:keys [state]} _ {:keys [token]}]
  {:action (fn []
             (swap! state assoc :app/user-token token))})

(om/defui ^:once QueuedVideo
  static uc/InitialAppState
  (initial-state [_ title] {::video/id   (random-uuid)
                            :video/title title})

  static om/IQuery
  (query [_] [::video/id
              {::video/snippet [::video/title]}])

  static om/Ident
  (ident [_ props] [::video/by-id (::video/id props)])

  Object
  (componentDidMount [this]
    (when-not (-> this om/props ::video/snippet)
      (df/load this (om/get-ident this) QueuedVideo
        {:parallel true})))

  (render [this]
    (let [{::video/keys [id snippet]} (om/props this)
          {::video/keys [title]} snippet]
      (dom/div nil
        "Video " (str title)))))

(def queued-video (om/factory QueuedVideo))

(om/defui ^:once Root
  static uc/InitialAppState
  (initial-state [_ _] {:video/queue []})

  static om/IQuery
  (query [_] [{:video/queue (om/get-query QueuedVideo)} :ui/react-key :app/user-token])

  Object
  (render [this]
    (let [{:keys     [ui/react-key video/queue]
           :app/keys [user-token]} (om/props this)]
      (dom/div #js {:key react-key}
        (if user-token
          (dom/div nil
            (mapv queued-video queue))
          "Logging in...")))))

(def root (om/factory Root))
