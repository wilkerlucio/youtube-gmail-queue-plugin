(defproject youtube-gmail-queue-plugin "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.494"]
                 [figwheel-sidecar "0.5.9"]
                 [org.omcljs/om "1.0.0-alpha48"]
                 [navis/untangled-client "0.8.2-SNAPSHOT"]
                 [binaryage/devtools "0.9.2"]]

  :plugins [[lein-figwheel "0.5.9"]]

  :source-paths ["src/popup" "src/dev"]

  :cljsbuild {:builds [{:id           "dev"
                        :figwheel     true
                        :source-paths ["src/popup" "src/dev"]
                        :compiler     {:main                 cljs.user
                                       :output-to            "browsers/chrome/js/dev/ygq.js"
                                       :output-dir           "browsers/chrome/js/dev"
                                       :asset-path           "js/dev"
                                       :source-map-timestamp true
                                       :preloads             [devtools.preload]
                                       :optimizations        :none}}]})
