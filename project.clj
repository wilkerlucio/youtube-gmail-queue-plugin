(defproject youtube-gmail-queue-plugin "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.660"]
                 [org.clojure/core.async "0.3.442"]
                 [com.cognitect/transit-cljs "0.8.239"]

                 [org.omcljs/om "1.0.0-beta1"]
                 [awkay/untangled "1.0.0-beta2-SNAPSHOT"]
                 [untangled/om-css "1.1.0"]
                 [com.gfredericks/test.chuck "0.2.6"]
                 [com.rpl/specter "1.0.2"]
                 [cljsjs/moment "2.17.1-1"]

                 [figwheel-sidecar "0.5.9"]
                 [binaryage/devtools "0.9.2"]]

  :plugins [[lein-figwheel "0.5.9"]
            [lein-cljsbuild "1.1.6"]]

  :source-paths ["src/popup" "src/background" "src/shared"]

  :cljsbuild {:builds [{:id           "popup-dev"
                        :figwheel     true
                        :source-paths ["src/popup" "src/shared"]
                        :compiler     {:main                 ygq.popup.main
                                       :output-to            "browsers/chrome/js/dev/ygq.js"
                                       :output-dir           "browsers/chrome/js/dev"
                                       :asset-path           "js/dev"
                                       :source-map-timestamp true
                                       :preloads             [devtools.preload]
                                       :optimizations        :none}}

                       {:id           "background-dev"
                        :figwheel     true
                        :source-paths ["src/background" "src/shared"]
                        :compiler     {:main                 ygq.background.main
                                       :output-to            "browsers/chrome/js/background-dev/ygq.js"
                                       :output-dir           "browsers/chrome/js/background-dev"
                                       :asset-path           "js/background-dev"
                                       :source-map-timestamp true
                                       :preloads             [devtools.preload]
                                       :optimizations        :none}}

                       {:id           "popup"
                        :source-paths ["src/popup" "src/shared"]
                        :compiler     {:main                 ygq.popup.main
                                       :output-to            "browsers/chrome-prod/js/popup/ygq.js"
                                       :output-dir           "browsers/chrome-prod/js/popup"
                                       :asset-path           "js/popup"
                                       :parallel-build       true
                                       :source-map-timestamp true
                                       :optimizations        :advanced}}

                       {:id           "background"
                        :source-paths ["src/background" "src/shared"]
                        :compiler     {:main                 ygq.background.main
                                       :output-to            "browsers/chrome-prod/js/background/ygq.js"
                                       :output-dir           "browsers/chrome-prod/js/background"
                                       :asset-path           "js/background"
                                       :parallel-build       true
                                       :source-map-timestamp true
                                       :optimizations        :advanced}}]})
