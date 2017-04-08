(ns google.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [common.async :as ca]
            [cljs.core.async :as async :refer [<!]]
            [goog.crypt.base64 :as gb]
            [clojure.string :as str]
            [pathom.core :as p])
  (:import goog.Uri
           goog.Uri.QueryData))

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
      (reset! auth-token token))))

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

(defn gmail-threads [{::keys [access-token]}]
  (go
    (-> (fetch (api-uri "gmail/v1/users/me/messages" [[:includeSpamTrash false]
                                                      [:labelIds "Label_97618004354056322"]
                                                      [:labelIds "UNREAD"]
                                                      [:access_token access-token]]))
        <! :messages)))

(defn gmail-thread [{::keys             [access-token]
                     :gmail.thread/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/threads/" id) [[:access_token access-token]])))

(defn extract-youtube-id [msg]
  (if-let [[_ id] (re-find #"youtube\.com/watch\?v=([^&]+)" msg)]
    id))

(defn message->youtube-id [msg]
  (->> msg
       :payload :parts
       (filter (comp #{"text/plain" "text/html"} :mimeType))
       (map (comp extract-youtube-id
                  #(gb/decodeString % true)
                  :data :body))
       (filter some?)
       first))

(defn thread->youtube-ids [thread]
  (->> thread
       :messages
       (map message->youtube-id)
       (filter some?)))

(defn youtube-queue-ids [{::keys [access-token] :as options}]
  (go
    (->> (gmail-threads options) <!
         (map :threadId)
         (distinct)
         (map #(assoc options :gmail.thread/id %))
         (p/read-chan-seq gmail-thread) <!
         (mapcat thread->youtube-ids)
         (map #(hash-map :youtube.video/id %)))))

(defn youtube-details [{::keys              [access-token]
                        :youtube.video/keys [id parts]}]
  (go
    (-> (fetch (api-uri "youtube/v3/videos" {:access_token access-token
                                             :id           id
                                             :part         (str/join "," parts)}))
        <! :items first)))

(comment
  (go
    (-> (youtube-queue-ids {::access-token auth-token}) <!
        js/console.log))

  (go
    (let [results (<! (gmail-threads {::access-token auth-token}))]
      (js/console.log "Res" results)))

  (go
    (let [thread (->> (gmail-thread {::access-token   auth-token
                                     :gmail.thread/id "15b4a26a9e0bddff"})
                      (<!)
                      thread->youtube-ids)]
      (js/console.log thread)))

  (go
    (let [video (->> (youtube-details #:youtube.video {::access-token auth-token
                                                       :id            "qyy7GaH415U"
                                                       :parts         ["snippet"]})
                     (<!))]
      (js/console.log video))))
