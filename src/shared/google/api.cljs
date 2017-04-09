(ns google.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [common.async :as ca]
            [cljs.core.async :as async :refer [<!]]
            [goog.crypt.base64 :as gb]
            [clojure.string :as str]
            [pathom.core :as p]
            [cljs.spec :as s])
  (:import goog.Uri
           goog.Uri.QueryData))

(s/def :youtube.video/title string?)
(s/def :youtube.video/published-at inst?)

(s/def :youtube.video/view-count nat-int?)
(s/def :youtube.video/like-count nat-int?)
(s/def :youtube.video/dislike-count nat-int?)
(s/def :youtube.video/comment-count nat-int?)

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

(defn fetch
  ([uri] (fetch uri {}))
  ([uri options]
   (go
     (let [response (<! (ca/promise->chan (js/fetch uri (clj->js options))))]
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

(defn gmail-messages [{::keys [access-token]}]
  (go
    (-> (fetch (api-uri "gmail/v1/users/me/messages" [[:includeSpamTrash false]
                                                      [:labelIds "Label_97618004354056322"]
                                                      [:labelIds "UNREAD"]
                                                      [:access_token access-token]]))
        <! :messages)))

(defn gmail-thread [{::keys             [access-token]
                     :gmail.thread/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/threads/" id) [[:access_token access-token]])))

(defn gmail-message [{::keys              [access-token]
                      :gmail.message/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/messages/" id) [[:access_token access-token]])))

(defn extract-youtube-id [msg]
  (if-let [[_ id] (re-find #"youtube\.com/watch\?v=([^&]+)" msg)]
    id))

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

(defn youtube-queue-ids [{::keys [access-token] :as options}]
  (go
    (->> (gmail-messages options) <!
         (map :id)
         (distinct)
         (map #(assoc options :gmail.message/id %))
         (p/read-chan-seq gmail-message) <!
         (map message->youtube-id)
         (map #(hash-map :youtube.video/id %)))))

(def video-parts #{"contentDetails" "fileDetails" "id" "liveStreamingDetails" "localizations" "player"
                   "processingDetails" "recordingDetails" "snippet" "statistics" "status" "suggestions"
                   "topicDetails"})

(defn youtube-details [{::keys              [access-token]
                        :youtube.video/keys [id parts]}]
  (go
    (-> (fetch (api-uri "youtube/v3/videos" {:access_token access-token
                                             :id           id
                                             :part         (str/join "," (filter video-parts parts))}))
        <! :items first)))

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
  (go
    (-> (youtube-queue-ids {::access-token @auth-token}) <!
        js/console.log))

  (go
    (let [results (<! (gmail-messages {::access-token @auth-token}))]
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
                                                       :id            "qyy7GaH415U"
                                                       :parts         ["snippet"]})
                     (<!)
                     )]
      (js/console.log video))))
