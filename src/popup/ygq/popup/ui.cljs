(ns ygq.popup.ui
  (:require [cljsjs.moment]
            [cljs.spec :as s]
            [clojure.string :as str]
            [common.local-storage :as local-storage]
            [goog.string :as gstr]
            [om.dom :as dom]
            [om.next :as om]
            [untangled.client.core :as uc]
            [untangled.client.data-fetch :as df]
            [untangled.client.mutations :refer [mutate]]
            [youtube.channel :as channel]
            [youtube.video :as video]
            [com.rpl.specter :as sp :include-macros true]))

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

(defmethod mutate 'youtube.channel/navigate [_ _ {::channel/keys [id]}]
  {:action (fn []
             (let [channel-url (str "https://www.youtube.com/channel/" id)]
               (js/chrome.tabs.update #js {:url channel-url})))})

(defmethod mutate 'queue/compute-categories [{:keys [state]} _ _]
  {:action (fn []
             (if (sequential? (-> @state :video/queue))
               (let [channels (->> @state
                                   :video/queue
                                   (map #(get-in @state %))
                                   (filter #(some? (get-in % [::video/snippet ::video/channel-id])))
                                   (group-by #(get-in % [::video/snippet ::video/channel-id]))
                                   (sp/transform [sp/MAP-VALS]
                                     (fn [videos]
                                       {::channel/id     (-> videos first ::video/snippet ::video/channel-id)
                                        ::channel/title  (-> videos first ::video/snippet ::video/channel-title)
                                        ::channel/videos (mapv #(vector ::video/by-id (::video/id %)) videos)})))]
                 (swap! state assoc ::channel/by-id channels)
                 (swap! state assoc :channel/list (->> channels
                                                       (sort-by #(-> % second ::channel/title .toLowerCase))
                                                       (mapv (comp #(vector ::channel/by-id %)
                                                                   first)))))))})

(defmethod mutate 'ui/set-main-view [{:keys [state]} _ {:keys [view]}]
  {:action (fn []
             (swap! state assoc :ui/main-view view)
             (local-storage/set! :ui/main-view view))})

(defmethod mutate 'window/close [_ _ _]
  {:action (fn []
             (js/window.close))})

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn get-load-query [comp]
  (conj (om/get-query comp) :ui/fetch-state :app/error))

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
                ::video/channel-id
                ::video/channel-title
                ::video/published-at
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
          {::video/keys [title channel-id channel-title thumbnails published-at]} snippet
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
          (dom/div #js {:className "video--channel-title"}
            (dom/a #js {:href "#"
                        :onClick (pd #(om/transact! this `[(channel/navigate {::channel/id ~channel-id})
                                                           (window/close)]))}
              channel-title))
          (dom/div #js {:className "video--channel-published-at"} (-> (js/moment published-at) .fromNow))

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
                (icon "repeat")))))))))

(def queued-video (om/factory QueuedVideo))

(defn center-text [text]
  (dom/div #js {:className "loading-container"} text))

(om/defui ^:once CategoryGroup
  static om/IQuery
  (query [_] [::channel/id ::channel/title
              {::channel/videos (om/get-query QueuedVideo)}])

  static om/Ident
  (ident [_ props] [::channel/by-id (::channel/id props)])

  Object
  (render [this]
    (let [{::channel/keys [id title videos]} (om/props this)]
      (dom/div nil
        (dom/a #js {:className "category-group"
                    :onClick   (pd #(om/transact! this `[(channel/navigate {::channel/id ~id})
                                                         (window/close)]))}
          title)
        (mapv queued-video videos)))))

(def category-group (om/factory CategoryGroup))

(defn main-view-link [{:ui/keys [main-view label component]}]
  (dom/a #js {:className (cond-> "main-view-chooser--item"
                           (= (-> (om/props component) :ui/main-view) main-view)
                           (str " main-view-chooser--item-active"))
              :onClick   (pd #(om/transact! component `[(ui/set-main-view {:view ~main-view}) :ui/main-view]))}
    label))

(om/defui ^:once Root
  static uc/InitialAppState
  (initial-state [_ _] {:ui/main-view (local-storage/get :ui/main-view ::main-view.latest)})

  static om/IQuery
  (query [_] [{:video/queue (get-load-query QueuedVideo)}
              {:channel/list (om/get-query CategoryGroup)}
              :ui/react-key
              :ui/main-view])

  Object
  (render [this]
    (let [{:keys [ui/react-key video/queue channel/list ui/main-view]} (om/props this)]
      (dom/div #js {:key react-key}
        (dom/div nil
          (dom/div #js {:className "main-actions-row"}
            (dom/div #js {:className "main-actions-title"} "Youtube Gmail Queue")
            (dom/div #js {:className "flex-space"})
            (dom/a #js {:href      "#"
                        :className "main-actions-row--reload"
                        :onClick   (pd #(df/load this :video/queue QueuedVideo {:params {:clear-cache true}
                                                                                :post-mutation 'queue/compute-categories}))}
              "Reload queue"))
          (cond
            (df/loading? (:ui/fetch-state queue))
            (center-text "Loading video list...")

            (empty? queue)
            (center-text "No videos left to watch.")

            :else
            (dom/div nil
              (dom/div #js {:className "main-view-chooser"}
                (main-view-link #:ui{:label     "Latest Uploads"
                                     :main-view ::main-view.latest
                                     :component this})
                (main-view-link #:ui{:label     "Channels"
                                     :main-view ::main-view.channels
                                     :component this}))
              (case main-view
                ::main-view.latest (mapv queued-video queue)
                ::main-view.channels (mapv category-group list)))))))))

(def root (om/factory Root))
