(ns common.async
  (:refer-clojure :exclude [test]))

(defmacro dochan [[binding chan] & body]
  `(let [chan# ~chan]
     (cljs.core.async.macros/go
       (loop []
         (if-let [~binding (cljs.core.async/<! chan#)]
           (do
             ~@body
             (recur))
           :done)))))

(defmacro <? [ch]
  `(common.async/throw-err (cljs.core.async/<! ~ch)))

(defmacro go-catch [& body]
  `(cljs.core.async.macros/go
     (try
       ~@body
       (catch :default e e))))

(defmacro <!expand [value]
  `(cljs.core.async/<! (common.async/expand-value ~value)))

(defmacro <!cache [cache key & body]
  `(if-let [[_ v#] (find (deref ~cache) ~key)]
     v#
     (let [v# (cljs.core.async/<! (do ~@body))]
       (swap! ~cache assoc ~key (with-meta v# {:updated-at (inst-ms (js/Date.))}))
       v#)))
