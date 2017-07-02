(ns google.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<!]]
            [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test.check.generators]
            [common.js :as cjs]
            [common.async :as ca]
            [goog.crypt.base64 :as gb]

            [goog.string :as gstr]
            [pathom.core :as p])
  (:import goog.Uri
           goog.Uri.QueryData))

(s/def :youtube.video/id string?)
(s/def :youtube.video/title string?)
(s/def :youtube.video/published-at inst?)

(s/def :youtube.video/view-count nat-int?)
(s/def :youtube.video/like-count nat-int?)
(s/def :youtube.video/dislike-count nat-int?)
(s/def :youtube.video/comment-count nat-int?)

(s/def ::gmail-query string?)

(defn map->flatten-ns [ns m]
  (reduce
    (fn [m [k v]]
      (let [k (if (or (string? k) (simple-keyword? k))
                (-> k name gstr/toSelectorCase) k)]
        (if (map? v)
          (merge m (map->flatten-ns (str ns "." k) v))
          (let [k (if (string? k) (keyword ns k) k)]
            (assoc m k v)))))
    {}
    m))

(s/fdef map->flatten-ns
  :args (s/cat :ns string? :map map?)
  :ret map?)

(defn flatten-ns->map [ns m]
  (reduce
    (fn [m [k v]]
      (let [path (if (= (namespace k) ns)
                   [(name k)]
                   (-> (str (str/replace (namespace k) (js/RegExp. (str "^" ns "\\.")) "")
                            "."
                            (name k))
                       (str/split ".")))
            path (map gstr/toCamelCase path)]
        (assoc-in m path v)))
    {}
    m))

(s/fdef flatten-ns->map
  :args (s/cat :ns string? :map map?)
  :ret map?)

(defn get-auth-token [options]
  (let [c (async/promise-chan)]
    (cjs/call js/window ["chrome" "identity" "getAuthToken"]
      options
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

(defn gmail-messages [{::keys [gmail-query]}]
  (go
    (-> (fetch (api-uri "gmail/v1/users/me/messages" [[:includeSpamTrash false]
                                                      [:q gmail-query]]))
        <! :messages)))

(defn gmail-message [{:gmail.message/keys [id]}]
  (fetch (api-uri (str "gmail/v1/users/me/messages/" id) [])))

(s/fdef gmail-message
  :args (s/cat :request (s/keys :req [:gmail.message/id])))

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
    (->> (gmail-messages {::gmail-query "from:noreply@youtube.com is:unread"}) <!
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

