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

(defmethod mutate 'youtube.video/navigate [_ _ {::video/keys [id]}]
  {:action (fn []
             (let [video-url (str "https://www.youtube.com/watch?v=" id)]
               (js/chrome.tabs.update #js {:url video-url})))})

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn get-load-query [comp]
  (conj (om/get-query comp) :ui/fetch-state))

(om/defui ^:once QueuedVideo
  static uc/InitialAppState
  (initial-state [_ title] {::video/id    (random-uuid)
                            ::video/title title})

  static om/IQuery
  (query [_] [::video/id
              {::video/snippet
               [::video/title
                ::video/channel-title
                {::video/thumbnails
                 [{:youtube.thumbnail/default
                   [:youtube.thumbnail/url]}]}]}])

  static om/Ident
  (ident [_ props] [::video/by-id (::video/id props)])

  Object
  (componentDidMount [this]
    (when-not (-> this om/props ::video/snippet)
      (df/load this (om/get-ident this) QueuedVideo
        {:parallel true})))

  (render [this]
    (let [{::video/keys [id snippet]} (om/props this)
          {::video/keys [title channel-title thumbnails]} snippet]
      (dom/div #js {:className "video--row"}
        (dom/img #js {:src       (get-in thumbnails [:youtube.thumbnail/default :youtube.thumbnail/url])
                      :className "video--thumbnail"})
        (dom/div nil
          (dom/div #js {:className "video--title"}
            (dom/a #js {:href    "#"
                        :onClick (pd #(om/transact! this `[(video/navigate {::video/id ~id})]))}
              title))
          (dom/div #js {:className "channel--title"} channel-title))))))

(def queued-video (om/factory QueuedVideo))

(defn load-text [text]
  (dom/div #js {:className "loading-container"} text))

(om/defui ^:once Root
  static uc/InitialAppState
  (initial-state [_ _] {})

  static om/IQuery
  (query [_] [{:video/queue (get-load-query QueuedVideo)} :ui/react-key])

  Object
  (render [this]
    (let [{:keys [ui/react-key video/queue]} (om/props this)]
      (dom/div #js {:key react-key}
        (dom/div nil
          (if (df/loading? (:ui/fetch-state queue))
            (load-text "Loading video list...")
            (mapv queued-video queue)))))))

(def root (om/factory Root))
