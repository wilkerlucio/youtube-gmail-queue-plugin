(ns ygq.background.parser
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [common.async :refer [<!cache]])
  (:require [pathom.core :as p]
            [om.next :as om]
            [om.util :as omu]
            [goog.string :as gstr]
            [google.api :as g]
            [youtube.video :as video]
            [cljs.core.async :refer [<!]]
            [cljs.spec :as s]))

(defonce ^:private cache* (atom {}))

(defmulti mutate om/dispatch)

(defmethod mutate 'youtube.video/mark-watched [{:keys [::cache]} _ {::video/keys [id]}]
  {:remote true
   :action (fn []
             (swap! cache assoc-in [[::video/by-id id] ::video/watched?] true)
             (g/mark-message-read {:gmail.message/id (get @g/video-id->message-id id)}))})

(defmethod mutate 'youtube.video/mark-unwatched [{:keys [::cache]} _ {::video/keys [id]}]
  {:remote true
   :action (fn []
             (swap! cache assoc-in [[::video/by-id id] ::video/watched?] false)
             (g/mark-message-unread {:gmail.message/id (get @g/video-id->message-id id)}))})

(defn camel-key-reader [{{:keys [key]} :ast :keys [::entity parser query] :as env}]
  (if (contains? entity key)
    (get entity key)
    (let [key'  (-> key name gstr/toCamelCase keyword)
          value (get entity key' ::p/continue)]
      (if (map? value)
        (parser (assoc env ::entity value) query)
        (p/coerce key value)))))

(defn query->parts [query]
  (->> (om/query->ast query)
       :children
       (map (comp gstr/toCamelCase name :key))
       (set)))

(def youtube-label-name "[YGQ] Youtube Queue")

(defn find-or-create-youtube-label []
  (go
    (or (->> (g/gmail-labels) <!
             (filter (comp #{youtube-label-name} :gmail.label/name))
             (first))
        (-> (g/gmail-create-label #:gmail.label{:name                  youtube-label-name
                                                :label-list-visibility "labelHide"})
            <!))))

(s/fdef find-or-create-youtube-label
  :ret :gmail.label/api-entity)

(defn find-youtube-filter [filters]
  (->> filters
       (filter (filter (comp #{"noreply@youtube.com"} :gmail.filter.criteria/from)))
       (first)))

(defn gmail-youtube-label-id []
  (go
    (some-> (g/gmail-filters) <! find-youtube-filter
            :gmail.filter.action/add-label-ids first)))

(def root-endpoints
  {:video/queue
   (fn [{:keys [::cache ast query] :as env}]
     (go
       (if-let [label (<!cache cache :gmail.label/id (gmail-youtube-label-id))]
         (do
           (if (and cache (or (get-in ast [:params :clear-cache])
                              (empty? (get @cache ::queue-ids))))
             (swap! cache dissoc ::queue-ids))
           (let [videos (<!cache cache ::queue-ids (g/youtube-queue-ids {:gmail.label/id label}))]
             (<! (p/read-chan-seq
                   #(go
                      (let [video (<!cache cache [::video/by-id (::video/id %)]
                                           (g/youtube-details (assoc % ::video/parts (query->parts query))))]
                        (p/continue-with-reader (assoc env ::entity video)
                                                camel-key-reader)))
                   videos))))
         [{:app/error ::missing-gmail-label}])))})

(defn youtube-reader [{:keys [query ::cache] :as env}]
  (let [key (get-in env [:ast :key])]
    (if-let [[k id] (and (omu/ident? key) key)]
      (case k
        ::video/by-id
        (go
          (let [video (<!cache cache key
                               (g/youtube-details #:youtube.video{:id    id
                                                                  :parts (query->parts query)}))]
            (p/continue-with-reader (assoc env ::entity video)
                                    camel-key-reader)))

        ::p/continue)
      ::p/continue)))

(def parser (om/parser {:read   p/read
                        :mutate mutate}))

(defn parse [env tx]
  (-> (parser
        (assoc env
          ::p/reader [root-endpoints youtube-reader]
          ::cache cache*)
        tx)
      (p/read-chan-values)))

(comment
  (go
    (-> (parser {::p/reader root-endpoints}
                [{:video/queue [:youtube.video/id]}])
        (p/read-chan-values)
        <! js/console.log))


  (go
    (-> (parser {::p/reader youtube-reader}
                [{[:youtube.video/by-id "0SE3l1RI8ow"]
                  [:youtube.video/id
                   {:youtube.video/snippet
                    [:youtube.video/title
                     :youtube.video/published-at]}

                   {:youtube.video/statistics
                    [:youtube.video/view-count
                     :youtube.video/like-count
                     :youtube.video/dislike-count
                     :youtube.video/comment-count]}]}])
        (p/read-chan-values)
        <! js/console.log)))
