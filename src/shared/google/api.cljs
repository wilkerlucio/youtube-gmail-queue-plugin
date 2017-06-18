(ns google.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<!]]
            [cljs.spec :as s]
            [clojure.string :as str]
            [common.async :as ca]
            [goog.crypt.base64 :as gb]
            [goog.string :as gstr]
            [pathom.core :as p])
  (:import goog.Uri
           goog.Uri.QueryData))

(s/def :youtube.video/title string?)
(s/def :youtube.video/published-at inst?)

(s/def :youtube.video/view-count nat-int?)
(s/def :youtube.video/like-count nat-int?)
(s/def :youtube.video/dislike-count nat-int?)
(s/def :youtube.video/comment-count nat-int?)

(s/def :gmail.label/id string?)
(s/def :gmail.label/name string?)
(s/def :gmail.label/label-list-visibility #{"labelHide" "labelShow" "labelShowIfUnread"})
(s/def :gmail.label/message-list-visibility #{"show" "hide"})
(s/def :gmail.label/type #{"user" "system"})

(s/def :gmail.filter/id string?)
(s/def :gmail.filter.criteria/from string?)
(s/def :gmail.filter.action/add-label-ids (s/coll-of :gmail.label/id))
(s/def :gmail.filter.action/remove-label-ids (s/coll-of :gmail.label/id))

(defn map->flatten-ns [ns m]
  (reduce
    (fn [m [k v]]
      (let [k (-> k name gstr/toSelectorCase)]
        (if (map? v)
          (merge m (map->flatten-ns (str ns "." (name k)) v))
          (let [k (if (or (string? k) (simple-keyword? k))
                        (keyword ns (name k))
                        k)]
            (assoc m k v)))))
    {}
    m))

(defn get-auth-token [options]
  (let [c (async/promise-chan)]
    (.getAuthToken js/chrome.identity
                   (clj->js options)
                   #(do
                      (async/put! c %)
                      (async/close! c)))
    c))

(defonce auth-token (atom nil))

(defn request-token []
  (go
    (let [token (<! (get-auth-token {:interactive true}))]
      (reset! auth-token token)
      token)))

(defn auth-env [] {::access-token @auth-token})

(defn uri-set-query-param [uri key value]
  (let [uri (Uri. uri)]
    (-> uri .getQueryData (.set key value))
    (.toString uri)))

(defn fetch
  ([uri] (fetch uri {}))
  ([uri options]
   (go
     (let [uri      (uri-set-query-param uri "access_token" (<! (request-token)))
           response (<! (ca/promise->chan (js/fetch uri (clj->js options))))]
       (js->clj (<! (ca/promise->chan (.json response))) :keywordize-keys true)))))

(defn make-query [m]
  (reduce (fn [d [k v]] (.add d (name k) v))
          (QueryData.)
          m))

(defn api-uri
  ([path] (api-uri path {}))
  ([path query]
   (let [uri (Uri. (str "https://www.googleapis.com/" path))]
     (.setQueryData uri (make-query query))
     (.toString uri))))

(defn gmail-messages [{:keys [gmail.label/id]}]
  (go
    (-> (fetch (api-uri "gmail/v1/users/me/messages" [[:includeSpamTrash false]
                                                      [:labelIds id]
                                                      [:labelIds "UNREAD"]]))
        <! :messages)))

(s/fdef gmail-messages
  :args (s/cat :request (s/keys :req [:gmail.label/id])))

(defn gmail-thread [{:gmail.thread/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/threads/" id) [])))

(s/fdef gmail-thread
  :args (s/cat :request (s/keys :req [:gmail.thread/id])))

(defn gmail-message [{:gmail.message/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/messages/" id) [])))

(s/fdef gmail-message
  :args (s/cat :request (s/keys :req [:gmail.message/id])))

(defn gmail-filters []
  (go
    (->> (fetch (api-uri (str "gmail/v1/users/me/settings/filters") []))
         <! :filter
         (map (partial map->flatten-ns "gmail.filter")))))

(s/fdef gmail-filters
  :ret map?)

(defn gmail-create-filter [{:gmail.filter/keys []}]
  )

(s/fdef gmail-create-filter
  :args (s/cat )
  :ret any?)

(defn gmail-labels []
  (go
    (->> (fetch (api-uri (str "gmail/v1/users/me/labels") []))
         <! :labels
         (map (partial map->flatten-ns "gmail.label")))))

(s/def :gmail.label/api-entity
  (s/keys :req [:gmail.label/id :gmail.label/name :gmail.label/type]
          :opt [:gmail.label/label-list-visibility :gmail.label/message-list-visibility]))

(s/fdef gmail-labels
  :ret (s/coll-of :gmail.label/api-entity))

(defn gmail-create-label [{:gmail.label/keys [name label-list-visibility message-list-visibility]
                           :or               {label-list-visibility   "labelShow"
                                              message-list-visibility "show"}}]
  (go
    (->> (fetch (api-uri "gmail/v1/users/me/labels")
                {:method  "post"
                 :headers {"Content-Type" "application/json"}
                 :body    (js/JSON.stringify #js {:name                  name
                                                  :labelListVisibility   label-list-visibility
                                                  :messageListVisibility message-list-visibility})})
         <! (map->flatten-ns "gmail.label"))))

(s/fdef gmail-create-label
  :args (s/cat :label (s/keys :req [:gmail.label/name]
                              :opt [:gmail.label/label-list-visibility :gmail.label/message-list-visibility]))
  :ret :gmail.label/api-entity)

(defn extract-youtube-id [msg]
  (if-let [[_ id] (re-find #"youtube\.com/watch\?v=([^&]+)" msg)]
    id))

(s/fdef extract-youtube-id
  :args (s/cat :message string?)
  :ret (s/nilable :youtube.video/id))

(defonce video-id->message-id (atom {}))

(defn message->youtube-id [{:keys [id] :as msg}]
  (let [youtube-id (->> msg
                        :payload :parts
                        (filter (comp #{"text/plain" "text/html"} :mimeType))
                        (map (comp extract-youtube-id
                                   #(gb/decodeString % true)
                                   :data :body))
                        (filter some?)
                        first)]
    (swap! video-id->message-id assoc youtube-id id)
    youtube-id))

(defn youtube-queue-ids [options]
  (go
    (->> (gmail-messages options) <!
         (map :id)
         (distinct)
         (map #(assoc options :gmail.message/id %))
         (p/read-chan-seq gmail-message) <!
         (map message->youtube-id)
         (filter some?)
         (map #(hash-map :youtube.video/id %)))))

(def video-parts #{"contentDetails" "fileDetails" "id" "liveStreamingDetails" "localizations" "player"
                   "processingDetails" "recordingDetails" "snippet" "statistics" "status" "suggestions"
                   "topicDetails"})

(defn youtube-details [{:youtube.video/keys [id parts] :as video}]
  (go
    (or (-> (fetch (api-uri "youtube/v3/videos" {:id   id
                                                 :part (str/join "," (filter video-parts parts))}))
            <! :items first)
        video)))

(defn mark-message-read [{::keys              [access-token]
                          :gmail.message/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/messages/" id "/modify") [[:access_token access-token]])
         {:method  "post"
          :headers {"Content-Type" "application/json"}
          :body    (js/JSON.stringify #js {:removeLabelIds #js ["UNREAD"]})}))

(defn mark-message-unread [{::keys              [access-token]
                            :gmail.message/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/messages/" id "/modify") [[:access_token access-token]])
         {:method  "post"
          :headers {"Content-Type" "application/json"}
          :body    (js/JSON.stringify #js {:addLabelIds #js ["UNREAD"]})}))

(comment
  (js/console.log @video-id->message-id)

  (go
    (-> (gmail-youtube-label-id) <! js/console.log))

  (go
    (-> (request-token) <! js/console.log))
  (go
    (-> (youtube-queue-ids {}) <!
        js/console.log))

  (go
    (let [results (<! (gmail-messages {:gmail.label/id "Label_97618004354056322"}))]
      (js/console.log "Res" results)))

  (go
    (let [thread (->> (gmail-thread {::access-token   @auth-token
                                     :gmail.thread/id "15b4a26a9e0bddff"})
                      (<!))]
      (js/console.log thread)))

  (go
    (let [message (->> (gmail-message {::access-token    @auth-token
                                       :gmail.message/id "15b4f81b65e0b951"})
                       (<!))]
      (js/console.log message)))

  (go
    (let [message (->> (mark-message-unread {::access-token    @auth-token
                                             :gmail.message/id "15b4f81b65e0b951"})
                       (<!))]
      (js/console.log message)))

  (go
    (let [video (->> (youtube-details #:youtube.video {::access-token auth-token
                                                       :id            "4b85UX-bxWc"
                                                       :parts         ["snippet"]})
                     (<!)
                     )]
      (js/console.log video)))

  (let [uri (Uri. (str "https://www.googleapis.com/?access-token=123"))]

    (.toString uri)
    (.getQueryData uri))

  (uri-set-query-param "https://www.googleapis.com/?access-token=123" "access-token" "over"))
