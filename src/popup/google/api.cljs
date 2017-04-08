(ns google.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [common.async :as ca]
            [cljs.core.async :refer [<!]]
            [goog.crypt.base64 :as gb]
            [clojure.string :as str])
  (:import goog.Uri
           goog.Uri.QueryData))

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
  (fetch (api-uri "gmail/v1/users/me/messages" [[:includeSpamTrash false]
                                                [:labelIds "Label_97618004354056322"]
                                                [:labelIds "UNREAD"]
                                                [:access_token access-token]])))

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

(defn youtube-details [{::keys              [access-token]
                        :youtube.video/keys [id parts]}]
  (go
    (-> (fetch (api-uri "youtube/v3/videos" {:access_token access-token
                                             :id           id
                                             :part         (str/join "," parts)}))
        <! :items first)))

(defn auth-token []
  (some-> ygq.popup.core/app deref :reconciler :config :state deref :app/user-token))

(comment
  (go
    (let [results (<! (gmail-threads {::access-token (auth-token)}))]
      (js/console.log "Res" results)))

  (go
    (let [thread (->> (gmail-thread {::access-token   (auth-token)
                                     :gmail.thread/id "15b4b11027d9a567"})
                      (<!)
                      :messages first
                      message->youtube-id)]
      (js/console.log thread)))

  (go
    (let [video (->> (youtube-details #:youtube.video {::access-token (auth-token)
                                                       :id            "qyy7GaH415U"
                                                       :parts         ["snippet"]})
                     (<!))]
      (js/console.log video))))
