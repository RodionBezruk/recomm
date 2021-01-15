(defproject ventas "0.0.1"
  :description "The Bubbles Social Network"
  :url "https://kazer.es"
  :license {:name "GPL"
            :url "https://opensource.org/licenses/GPL-2.0"}
  :repositories {"my.datomic.com"
                 {:url "https://my.datomic.com/repo"}}
  :dependencies [
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.293" :scope "provided"]
                 [org.clojure/core.async "0.2.395"
                  :exclusions [org.clojure/tools.reader]]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [org.clojure/data.json "0.2.6"]
                 [com.cognitect/transit-clj "0.8.297"]
                 [clj-http "2.3.0"]
                 [com.taoensso/timbre       "4.8.0"]
                 [http-kit "2.2.0"]
                 [buddy "1.2.0"]
                 [cheshire "5.5.0"]
                 [com.cognitect/transit-clj "0.8.275"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [org.clojure/data.fressian "0.2.1"]
                 [ring "1.5.1"]
                 [ring/ring-defaults "0.2.2"]
                 [bk/ring-gzip "0.2.1"]
                 [onelog "0.4.6"]
                 [ring.middleware.logger "0.5.0" :exclusions [log4j onelog]]
                 [ring/ring-json "0.4.0" :exclusions [cheshire]] ;; see: buddy
                 [compojure "1.5.2"]
                 [cprop "0.1.10"]
                 [reagent "0.6.0"]
                 [reagent-utils "0.1.7"]
                 [re-frame "0.9.1"]
                 [re-frame-datatable "0.5.1"]
                 [soda-ash "0.2.0"]
                 [bidi "2.0.16"]
                 [venantius/accountant "0.1.7"]
                 [selmer "1.10.5" :exclusions [cheshire]] ;; see: buddy
                 [cljsjs/react-bootstrap "0.30.7-0"]
                 [mount "0.1.11"]
                 [me.raynes/conch "0.8.0"]
                 [com.datomic/datomic-pro "0.9.5394" :exclusions [org.slf4j/slf4j-nop org.slf4j/slf4j-log4j12]]
                 [io.rkn/conformity "0.4.0"]
                 [im.chit/adi "0.3.4"]
                 [io.aviso/pretty "0.1.33"]
                 [danlentz/clj-uuid "0.1.6"]
                 [slingshot "0.12.2"]
                 [byte-streams "0.2.2"]
                 [com.novemberain/pantomime "2.9.0"]
                 [com.walmartlabs/lacinia "0.15.0" :exclusions [clojure-future-spec]]
                 [clj-time "0.13.0"]
                 [binaryage/devtools "0.8.3"]
                 [prone "1.1.4"]
                 [org.clojars.stumitchell/clairvoyant "0.2.0"]
                 [day8/re-frame-tracer "0.1.1-SNAPSHOT"]
                ]
  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-environ "1.0.3"]
            [lein-sassc "0.10.4"]
            [lein-auto "0.1.2"]
            [lein-ancient "0.6.10"]
            [lein-git-deps "0.0.1-SNAPSHOT"]
            [venantius/ultra "0.5.1"]]
  :min-lein-version "2.6.1"
  :source-paths ["src/clj" "src/cljs" "src/cljc" "custom-lib" "plugins"]
  :test-paths ["test/clj" "test/cljc"]
  :jvm-opts ["-Xverify:none" "-XX:-OmitStackTraceInFastThrow"]
  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js"]
  :uberjar-name "ventas.jar"
  :main ventas.server
  :repl-options {:init-ns user}
  :aliases {"config" ["run" "-m" "outpace.config.generate"]}
  :cljsbuild {:builds
              [{:id "app"
                :source-paths ["src/cljs" "src/cljc" "custom-lib"]
                :figwheel {:on-jsload "ventas.core/on-figwheel-reload"}
                :compiler {:main ventas.core
                           :asset-path "js/compiled/out"
                           :closure-defines {"clairvoyant.core.devmode" true}
                           :output-to "resources/public/js/compiled/ventas.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               {:id "test"
                :source-paths ["src/cljs" "test/cljs" "src/cljc" "test/cljc" "custom-lib"]
                :compiler {:output-to "resources/public/js/compiled/testable.js"
                           :main ventas.test-runner
                           :optimizations :none}}
               {:id "min"
                :source-paths ["src/cljs" "src/cljc" "custom-lib"]
                :jar true
                :compiler {:main ventas.core
                           :output-to "resources/public/js/compiled/ventas.js"
                           :output-dir "target"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false
                           :externs ["externs.js"]}}]}
  :figwheel {;; :http-server-root "public"       ;; serve static assets from resources/public/
             :css-dirs ["resources/public/css"]  ;; watch and update CSS
             :open-file-command "open-with-subl3"
             :server-logfile "log/figwheel.log"
             :repl false
             }
  :doo {:build "test"}
  :sassc [{:src "src/scss/style.scss"
           :output-to "resources/public/css/style.css"}]
  :auto {"sassc" {:file-pattern  #"\.(scss)$"
                  :paths ["src/scss"]}}
  :profiles { :dev {:dependencies [[figwheel "0.5.8"]
                                   [figwheel-sidecar "0.5.8"]
                                   [com.cemerick/piggieback "0.2.1"]
                                   [org.clojure/tools.nrepl "0.2.12"]
                                   [com.cemerick/pomegranate "0.3.1"]
                                   [org.clojure/test.check "0.9.0"]
                                   [com.gfredericks/test.chuck "0.2.7"]]
                    :plugins [[lein-figwheel "0.5.4-4"]
                              [lein-doo "0.1.6"]]
                    :source-paths ["dev"]
                    :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
              :repl {:dependencies [[org.clojure/test.check "0.9.0"]
                                    [com.gfredericks/test.chuck "0.2.7"]]}
              :uberjar {:source-paths ^:replace ["src/clj" "src/cljc" "custom-lib"]
                        :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                        :hooks [leiningen.sassc]
                        :omit-source true
                        :aot :all}})
