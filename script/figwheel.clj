(require '[figwheel-sidecar.repl-api :as ra])
(require '[figwheel-sidecar.system :as fig])

(defn project-builds []
  (let [[_ _ _ & {:as settings}] (-> (slurp "project.clj")
                                     (read-string))]
    (get-in settings [:cljsbuild :builds])))

(ra/start-figwheel! {:build-ids ["background-dev" "popup-dev"]
                     :all-builds (project-builds)})
(ra/cljs-repl)
