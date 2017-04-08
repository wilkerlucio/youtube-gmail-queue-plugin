(ns gmail.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [common.async :as ca]
            [goog.crypt.base64 :as gb])
  (:import goog.Uri
           goog.Uri.QueryData))

(defn fetch [uri options]
  (go
    (let [response (<! (ca/promise->chan (js/fetch uri (clj->js options))))]
      (js->clj (<! (ca/promise->chan (.json response))) :keywordize-keys true))))

(defn make-query [m]
  (reduce (fn [d [k v]] (.add d (name k) v))
          (QueryData.)
          m))

(defn api-uri [path query]
  (let [uri (Uri. (str "https://www.googleapis.com/gmail/v1/users/me/" path))]
    (.setQueryData uri (make-query query))
    (.toString uri)))

(defn make-headers [m]
  (reduce (fn [d [k v]] (.append d (name k) v))
          (js/Headers.)
          m))

(defn threads [{::keys [auth-token]}]
  (fetch (api-uri "messages" [[:includeSpamTrash false]
                             [:labelIds "Label_97618004354056322"]
                             [:labelIds "UNREAD"]
                             [:access_token auth-token]])
         {}))

(defn thread [{::keys             [auth-token]
               :gmail.thread/keys [id]}]
  (fetch (api-uri (str "threads/" id) [[:access_token auth-token]])
         {}))

(defn auth-token []
  (some-> ygq.popup.core/app deref :reconciler :config :state deref :app/user-token))

(defn extract-youtube-id [msg]
  (if-let [[_ id] (re-find #"youtube\.com/watch\?v=([^&]+)" msg)]
    id))

(comment
  (re-find #"youtube\.com/watch\?v=([^&]+)" "Canal do Otario acabou de enviar um vÃ\u00ADdeo\nEUA Xerife do Mundo, Uber no Senado, InflaÃ§Ã£o â\u0080\u009CControladaâ\u0080\u009D e PrejuÃ\u00ADzo da  \nFifa\nhttp://www.youtube.com/watch?v=qyy7GaH415U&feature=em-uploademail\nVocÃª pode cancelar a inscriÃ§Ã£o para as notificaÃ§Ãµes desse usuÃ¡rio visitando  \no respectivo resumo do perfil dele.\nhttp://www.youtube.com/subscription_manager")
  (re-find #"youtube\.com/watch\?v=([^&]+)" "")

  (api-uri "messages" [[:includeSpamTrash false]
                       [:labelIds "Label_97618004354056322"]
                       [:labelIds "UNREAD"]])

  (go
    (let [results (<! (threads {::auth-token (auth-token)}))]
      (js/console.log "Res" results)))

  (go
    (js/console.log "ID:" (-> (thread {::auth-token     (auth-token)
                                       :gmail.thread/id "15b4b11027d9a567"})
                              (<!)
                              :messages first
                              :payload :parts first
                              :body :data
                              (gb/decodeString true)
                              extract-youtube-id)))

  (+ 1 2))
