(require '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel! '{:build-ids  ["background-dev" "popup-dev"]
                      :all-builds [{:id           "popup-dev"
                                    :figwheel     true
                                    :source-paths ["src/popup" "src/dev"]
                                    :compiler     {:main                 cljs.user
                                                   :output-to            "browsers/chrome/js/dev/ygq.js"
                                                   :output-dir           "browsers/chrome/js/dev"
                                                   :asset-path           "js/dev"
                                                   :source-map-timestamp true
                                                   :preloads             [devtools.preload]
                                                   :optimizations        :none}}

                                   {:id           "background-dev"
                                    :figwheel     true
                                    :source-paths ["src/background"]
                                    :compiler     {:main                 ygq.background.main
                                                   :output-to            "browsers/chrome/js/background-dev/ygq.js"
                                                   :output-dir           "browsers/chrome/js/background-dev"
                                                   :asset-path           "js/background-dev"
                                                   :source-map-timestamp true
                                                   :preloads             [devtools.preload]
                                                   :optimizations        :none}}]})
(ra/cljs-repl)
