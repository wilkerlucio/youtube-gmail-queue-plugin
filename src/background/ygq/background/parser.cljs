(ns ygq.background.parser
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [common.async :refer [<!cache]])
  (:require [pathom.core :as p]
            [om.next :as om]
            [om.util :as omu]
            [goog.string :as gstr]
            [google.api :as g]
            [youtube.video :as video]
            [cljs.core.async :refer [<!]]))

(defonce ^:private cache* (atom {}))

(defmulti mutate om/dispatch)

(defmethod mutate 'youtube.video/mark-watched [{:keys [::g/access-token ::cache]} _ {::video/keys [id]}]
  {:remote true
   :action (fn []
             (swap! cache assoc-in [[::video/by-id id] ::video/watched?] true)
             (g/mark-message-read {::g/access-token  access-token
                                   :gmail.message/id (get @g/video-id->message-id id)}))})

(defmethod mutate 'youtube.video/mark-unwatched [{:keys [::g/access-token ::cache]} _ {::video/keys [id]}]
  {:remote true
   :action (fn []
             (swap! cache assoc-in [[::video/by-id id] ::video/watched?] false)
             (g/mark-message-unread {::g/access-token  access-token
                                     :gmail.message/id (get @g/video-id->message-id id)}))})

(defn camel-key-reader [{{:keys [key]} :ast :keys [::entity parser query] :as env}]
  (if (contains? entity key)
    (get entity key)
    (let [key'  (-> key name gstr/toCamelCase keyword)
          value (get entity key' ::p/continue)]
      (if (map? value)
        (parser (assoc env ::entity value) query)
        (p/coerce key value)))))

(def root-endpoints
  {:video/queue
   (fn [{:keys [::g/access-token ::cache ast] :as env}]
     (go
       (if (get-in ast [:params :clear-cache]) (swap! cache dissoc ::queue-ids))
       (let [videos (<!cache cache ::queue-ids (g/youtube-queue-ids env))]
         (<! (p/read-chan-seq
               #(p/read-chan-values
                  (p/continue-with-reader (assoc env ::entity %)
                                          camel-key-reader))
               videos)))))})

(defn query->parts [query]
  (->> (om/query->ast query)
       :children
       (map (comp gstr/toCamelCase name :key))
       (set)))

(defn youtube-reader [{:keys [query parser ::g/access-token ::cache] :as env}]
  (let [key (get-in env [:ast :key])]
    (if-let [[k id] (and (omu/ident? key) key)]
      (case k
        :youtube.video/by-id
        (go
          (let [video (<!cache cache key
                               (g/youtube-details #:youtube.video{:id              id
                                                                  :parts           (query->parts query)
                                                                  ::g/access-token access-token}))]
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
    (-> (parser {::p/reader       root-endpoints
                 ::g/access-token @ygq.background.main/auth-token}
                [{:video/queue [:youtube.video/id]}])
        (p/read-chan-values)
        <! js/console.log))


  (go
    (-> (parser {::p/reader       youtube-reader
                 ::g/access-token @g/auth-token}
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
