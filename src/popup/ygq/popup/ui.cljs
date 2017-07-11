(ns ygq.popup.ui
  (:require [cljsjs.moment]
            [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [common.js :as cjs]
            [common.local-storage :as local-storage]
            [goog.string :as gstr]
            [om-css.css :as css]
            [om.dom :as dom]
            [om.next :as om]
            [untangled.client.core :as uc]
            [untangled.client.data-fetch :as df]
            [untangled.client.mutations :refer [mutate]]
            [youtube.channel :as channel]
            [youtube.thumbnail :as thumbnail]
            [youtube.video :as video]
            [com.rpl.specter :as sp :include-macros true]))

(defn update-tab-url [url]
  (cjs/call js/window ["chrome" "tabs" "update"] {:url url}))

(defmethod mutate 'auth/token-received [{:keys [state]} _ {:keys [token]}]
  {:action (fn []
             (swap! state assoc :app/user-token token))})

(defmethod mutate 'youtube.video/navigate [_ _ {::video/keys [id]}]
  {:action (fn []
             (let [video-url (str "https://www.youtube.com/watch?v=" id)]
               (update-tab-url video-url)))})

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
               (update-tab-url channel-url)))})

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
                 [{::thumbnail/default
                   [::thumbnail/url]}]}]}
              {::video/content-details
               [::video/duration]}])

  static om/Ident
  (ident [_ props] [::video/by-id (::video/id props)])

  static css/CSS
  (local-rules [_] [[:.row {:display        "flex"
                            :border-bottom  "2px solid #3e3e3e"
                            :padding-top    "10px"
                            :padding-bottom "6px"
                            :height         "96px"
                            :background     "#fff"}]
                    [:.watched {:background "#ccc"}]
                    [:.thumbnail-container {:position "relative"
                                            :overflow "hidden"
                                            :margin   "0 9px"}]
                    [:.thumbnail {:width  "120px"
                                  :height "90px"}]
                    [:.video-duration {:position    "absolute"
                                       :right       "5px"
                                       :bottom      "5px"
                                       :background  "rgba(0, 0, 0, 0.56)"
                                       :color       "#fff"
                                       :padding     "2px 9px"
                                       :font-size   "12px"
                                       :text-shadow "1px 1px 1px #000"}]
                    [:.video-title {:font-weight   "bold"
                                    :white-space   "nowrap"
                                    :text-overflow "ellipsis"
                                    :width         "350px"}]
                    [:.video-action {:color   "#000"
                                     :margin  "0 5px 0"
                                     :padding "3px 3px 4px 5px"}]
                    [:.s-published-at {:font-size "11px"}]])
  (include-children [_] [])

  Object
  (componentDidMount [this]
    (when-not (-> this om/props ::video/snippet)
      (df/load this (om/get-ident this) QueuedVideo
               {:parallel true})))

  (render [this]
    (let [{::video/keys [id snippet content-details watched?]} (om/props this)
          {::video/keys [title channel-id channel-title thumbnails published-at]} snippet
          {::video/keys [duration]} content-details
          {:keys [row watched video-duration video-title thumbnail-container
                  thumbnail video-action s-published-at]} (css/get-classnames QueuedVideo)]
      (dom/div #js {:className (cond-> row
                                 watched? (str " " watched))}
        (dom/div #js {:className thumbnail-container}
          (dom/img #js {:src       (get-in thumbnails [::thumbnail/default ::thumbnail/url])
                        :className thumbnail})
          (dom/div #js {:className video-duration} (duration-str (str duration))))
        (dom/div #js {:className "flex-column"}
          (dom/div #js {:className video-title}
            (dom/a #js {:href    "#"
                        :title   title
                        :onClick (pd #(om/transact! this `[(video/navigate {::video/id ~id})
                                                           (video/mark-watched {::video/id ~id})
                                                           (window/close)]))}
              title))
          (dom/div nil
            (dom/a #js {:href    "#"
                        :onClick (pd #(om/transact! this `[(channel/navigate {::channel/id ~channel-id})
                                                           (window/close)]))}
              channel-title))
          (dom/div #js {:className s-published-at} (-> (js/moment published-at) .fromNow))

          (dom/div #js {:className "flex-space"})
          (dom/div nil
            (if-not watched?
              (dom/a #js {:className video-action
                          :href      "#"
                          :onClick   (pd #(om/transact! this `[(video/mark-watched {::video/id ~id})]))}
                (icon "ok"))
              (dom/a #js {:className video-action
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

  static css/CSS
  (local-rules [_] [[:.group {:color         "#fff"
                              :cursor        "pointer"
                              :display       "block"
                              :background    "#3bc1a5"
                              :border-bottom "2px solid #000"
                              :font-size     "18px"
                              :font-weight   "bold"
                              :padding       "3px 10px"}]])
  (include-children [_] [])

  Object
  (render [this]
    (let [{::channel/keys [id title videos]} (om/props this)
          {:keys [group]} (css/get-classnames CategoryGroup)]
      (dom/div nil
        (dom/a #js {:className group
                    :onClick   (pd #(om/transact! this `[(channel/navigate {::channel/id ~id})
                                                         (window/close)]))}
          title)
        (mapv queued-video videos)))))

(def category-group (om/factory CategoryGroup))

(declare Root)

(om/defui ^:once MainViewLink
  static css/CSS
  (local-rules [_] [[:.item {:background     "antiquewhite"
                             :color          "#000"
                             :cursor         "pointer"
                             :flex           "1"
                             :text-align     "center"
                             :padding        "8px"
                             :font-weight    "bold"
                             :text-transform "uppercase"}]
                    [:.active {:background "#ffce8c"}]])
  (include-children [_] [])

  static om/IQuery
  (query [_] [:ui/main-view])

  Object
  (render [this]
    (let [{:ui/keys [main-view label component]} (om/props this)
          {:keys [menu-item menu-item-active]} (css/get-classnames Root)]
      (dom/a #js {:className (cond-> menu-item
                               (= (-> (om/props component) :ui/main-view) main-view)
                               (str " " menu-item-active))
                  :onClick   (pd #(om/transact! component `[(ui/set-main-view {:view ~main-view}) :ui/main-view]))}
        label))))

(defn main-view-link [{:ui/keys [main-view label component]}]
  (let [{:keys [menu-item menu-item-active]} (css/get-classnames Root)]
    (dom/a #js {:className (cond-> menu-item
                             (= (-> (om/props component) :ui/main-view) main-view)
                             (str " " menu-item-active))
                :onClick   (pd #(do
                                  (om/transact! component `[(ui/set-main-view {:view ~main-view}) :ui/main-view])))}
      label)))

(om/defui ^:once Root
  static uc/InitialAppState
  (initial-state [_ _] {:ui/main-view (local-storage/get :ui/main-view ::main-view.latest)})

  static om/IQuery
  (query [_] [{:video/queue (get-load-query QueuedVideo)}
              {:channel/list (om/get-query CategoryGroup)}
              :ui/react-key
              :ui/main-view])

  static css/CSS
  (local-rules [_] [[:.main-row {:background    "#cc181e"
                                 :color         "#fff"
                                 :display       "flex"
                                 :border-bottom "2px solid #3e3e3e"
                                 :padding       "5px"
                                 :align-items   "baseline"}]

                    [:.action-title {:font-weight "bold"
                                     :font-size   "27px"
                                     :margin-left "10px"}]

                    [:.reload-button
                     :.reload-button:hover
                     :.reload-button:focus
                     {:color           "#fff"
                      :outline         "none"
                      :text-decoration "none"}]

                    [:.video-chooser {:display "flex"}]

                    [:.menu-item {:background     "antiquewhite"
                                  :color          "#000"
                                  :cursor         "pointer"
                                  :flex           "1"
                                  :text-align     "center"
                                  :padding        "8px"
                                  :font-weight    "bold"
                                  :text-transform "uppercase"}]
                    [:.menu-item-active {:background "#ffce8c"}]])
  (include-children [_] [MainViewLink
                         QueuedVideo
                         CategoryGroup])

  static css/Global
  (global-rules [_]
    [[:body {:background "#3e3e3e"
             :width      "500px"
             :height     "500px"}]
     [:.loading-container {:display         "flex"
                           :align-items     "center"
                           :width           "100vw"
                           :height          "100vh"
                           :justify-content "center"
                           :color           "#68b2f3"
                           :font-weight     "bold"
                           :font-size       "28px"}]
     [:.flex-space {:flex "1"}]
     [:.flex-column {:display        "flex"
                     :flex-direction "column"}]])

  Object
  (render [this]
    (let [{:keys [ui/react-key video/queue channel/list ui/main-view]} (om/props this)
          {:keys [main-row action-title reload-button video-chooser]} (css/get-classnames Root)]
      (dom/div #js {:key react-key}
        (dom/div nil
          (dom/div #js {:className main-row}
            (dom/div #js {:className action-title} "Youtube Gmail Queue")
            (dom/div #js {:className "flex-space"})
            (if-not (df/loading? (:ui/fetch-state queue))
              (dom/a #js {:href      "#"
                          :className reload-button
                          :onClick   (pd #(df/load this :video/queue QueuedVideo {:params        {:clear-cache true}
                                                                                  :post-mutation 'queue/compute-categories}))}
                "Reload queue")))
          (cond
            (df/loading? (:ui/fetch-state queue))
            (center-text "Loading video list...")

            (empty? queue)
            (center-text "No videos left to watch.")

            :else
            (dom/div nil
              (dom/div #js {:className video-chooser}
                (main-view-link #:ui{:label     "Latest Uploads"
                                     :main-view ::main-view.latest
                                     :component this})
                (main-view-link #:ui{:label     "Channels"
                                     :main-view ::main-view.channels
                                     :component this}))
              (case main-view
                ::main-view.latest (mapv queued-video queue)
                ::main-view.channels (mapv category-group list)))))))))

(css/upsert-css "ygq" Root)
