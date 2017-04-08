(ns ygq.background.parser
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [pathom.core :as p]
            [om.next :as om]
            [google.api :as g]
            [cljs.core.async :refer [<!]]))

(defmulti mutate om/dispatch)

(defmethod mutate 'some/action [_ _ _])

(defn pread [{:keys [entity ast] :as env}]
  (case (:dispatch-key ast)
    :thread/id (get entity :threadId)
    :video/id (go
                (-> (g/gmail-thread (assoc env :gmail.thread/id (:threadId entity)))
                    <!
                    :messages first
                    g/message->youtube-id))
    (get entity (:dispatch-key ast))))

(def root-endpoints
  {:video/queue
   (fn [{:keys [::g/access-token] :as env}]
     (go
       (let [videos (<! (g/youtube-queue-ids env))]
         (js/console.log videos)
         (<! (p/read-chan-seq
               #(p/read-chan-values
                  (p/continue-with-reader (assoc env :entity %)
                                          pread))
               videos)))))})

(def parser (om/parser {:read   p/read
                        :mutate mutate}))

(def auth-token @ygq.background.main/auth-token)
(comment


  (go
    (-> (parser {::p/reader       root-endpoints
                 ::g/access-token auth-token}
                [{:video/queue [:youtube.video/id]}])
        (p/read-chan-values)
        <! js/console.log)))
