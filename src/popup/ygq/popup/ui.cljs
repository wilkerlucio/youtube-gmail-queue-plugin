(ns ygq.popup.ui
  (:require [om.next :as om]
            [om.dom :as dom]
            [goog.string :as gstr]
            [youtube.video :as video]
            [untangled.client.core :as uc]
            [untangled.client.mutations :refer [mutate]]
            [untangled.client.data-fetch :as df]
            [cljs.spec :as s]
            [clojure.string :as str]))

(defmethod mutate 'auth/token-received [{:keys [state]} _ {:keys [token]}]
  {:action (fn []
             (swap! state assoc :app/user-token token))})

(defmethod mutate 'youtube.video/navigate [_ _ {::video/keys [id]}]
  {:action (fn []
             (let [video-url (str "https://www.youtube.com/watch?v=" id)]
               (js/chrome.tabs.update #js {:url video-url})))})

(defmethod mutate 'youtube.video/mark-watched [{:keys [state ref]} _ {::video/keys [id]}]
  {:remote true
   :action (fn []
             (swap! state assoc-in [::video/by-id id ::video/watched?] true))})

(defmethod mutate 'youtube.video/mark-unwatched [{:keys [state ref]} _ {::video/keys [id]}]
  {:remote true
   :action (fn []
             (swap! state assoc-in [::video/by-id id ::video/watched?] false))})

(defmethod mutate 'window/close [_ _ _]
  {:action (fn []
             (js/console.log "Close window")
             (js/window.close))})

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn get-load-query [comp]
  (conj (om/get-query comp) :ui/fetch-state))

(defn icon [name]
  (dom/span #js {:className (str "glyphicon glyphicon-" name)}))

(defn duration-str [duration]
  (if-let [[h m s] (some->> (re-find #"(?:(\d+)H)?(?:(\d+)M)(?:(\d+)S)?$" duration)
                            next)]
    (let [items (->> (filter some? [h (or m 0) (or s 0)])
                     (map #(some-> % (gstr/padNumber 2))))]
      (str/join ":" items))))

(s/def ::duration string?)

(s/fdef duration-str
  :args (s/cat :duration ::duration)
  :ret (s/and string? #(re-find #"^(\d{2}:)?\d{2}:\d{2}$" %)))

(om/defui ^:once QueuedVideo
  static uc/InitialAppState
  (initial-state [_ title] {::video/id    (random-uuid)
                            ::video/title title})

  static om/IQuery
  (query [_] [::video/id
              ::video/watched?
              {::video/snippet
               [::video/title
                ::video/channel-title
                {::video/thumbnails
                 [{:youtube.thumbnail/default
                   [:youtube.thumbnail/url]}]}]}
              {::video/content-details
               [::video/duration]}])

  static om/Ident
  (ident [_ props] [::video/by-id (::video/id props)])

  Object
  (componentDidMount [this]
    (when-not (-> this om/props ::video/snippet)
      (df/load this (om/get-ident this) QueuedVideo
        {:parallel true})))

  (render [this]
    (let [{::video/keys [id snippet content-details watched?]} (om/props this)
          {::video/keys [title channel-title thumbnails]} snippet
          {::video/keys [duration]} content-details]
      (dom/div #js {:className (cond-> "video--row"
                                 watched? (str " video--row--watched"))}
        (dom/div #js {:className "video--thumbnail--container"}
          (dom/img #js {:src       (get-in thumbnails [:youtube.thumbnail/default :youtube.thumbnail/url])
                        :className "video--thumbnail"})
          (dom/div #js {:className "video--duration"} (duration-str (str duration))))
        (dom/div #js {:className "flex-column"}
          (dom/div #js {:className "video--title"}
            (dom/a #js {:href    "#"
                        :title   title
                        :onClick (pd #(om/transact! this `[(video/navigate {::video/id ~id})
                                                           (video/mark-watched {::video/id ~id})
                                                           (window/close)]))}
              title))
          (dom/div #js {:className "video--channel-title"} channel-title)

          (dom/div #js {:className "flex-space"})
          (dom/div #js {:className "video--actions"}
            (if-not watched?
              (dom/a #js {:className "video--action"
                          :href      "#"
                          :onClick   (pd #(om/transact! this `[(video/mark-watched {::video/id ~id})]))}
                (icon "ok"))
              (dom/a #js {:className "video--action"
                          :href      "#"
                          :onClick   (pd #(om/transact! this `[(video/mark-unwatched {::video/id ~id})]))}
                (icon "repeat")))
            #_(dom/a #js {:className "video--action"}
                (dom/a #js {:className "video--action"
                            :href      "#"
                            :onClick   (pd #(om/transact! this `[(video/mark-watched {::video/id ~id})
                                                                 (video/remove {::video/id ~id})]))}
                  (icon "remove")))))))))

(def queued-video (om/factory QueuedVideo))

(defn center-text [text]
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
          (dom/div #js {:className "main-actions-row"}
            (dom/div #js {:className "main-actions-title"} "Youtube Gmail Queue")
            (dom/div #js {:className "flex-space"})
            (dom/a #js {:href      "#"
                        :className "main-actions-row--reload"
                        :onClick   (pd #(df/load this :video/queue QueuedVideo {:params {:clear-cache true}}))}
              "Reload queue"))
          (if (df/loading? (:ui/fetch-state queue))
            (center-text "Loading video list...")
            (if (empty? queue)
              (center-text "No videos left to watch.")
              (mapv queued-video queue))))))))

(def root (om/factory Root))
