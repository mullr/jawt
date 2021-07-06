(defproject jawt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]

                 ;; lifecycle
                 [mount/mount "0.1.16"]
                 [tolitius/mount-up "0.1.3"]
                 [spootnik/signal "0.2.4"]

                 ;; logging
                 [com.taoensso/timbre "5.1.2"] 
                 [com.fzakaria/slf4j-timbre "0.3.21"]

                 ;; db
                 [com.github.seancorfield/next.jdbc "1.2.674"]
                 [hikari-cp "2.13.0"]
                 [org.xerial/sqlite-jdbc "3.36.0.1"]
                 [migratus "1.3.5"]
                 [com.wsscode/pathom "2.3.2"]

                 ;; morphological analysis
                 [org.atilika.kuromoji/kuromoji "0.7.7"]
                 [com.atilika.kuromoji/kuromoji-ipadic "0.9.0"]
                 [com.atilika.kuromoji/kuromoji-unidic "0.9.0"]

                 ;; http
                 [ring/ring-core "1.9.3"]
                 [ring/ring-jetty-adapter "1.9.3"]
                 [metosin/reitit "0.5.13"]]

  :repositories [["atilika" "https://www.atilika.org/nexus/content/repositories/atilika"]]
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :repl-options {:init-ns jawt.core}

  :main jawt.core

  :profiles 
  {:dev {:dependencies []
         :plugins []
         :source-paths ["dev/clj" "dev/cljc" "dev/cljs"]
         :repl-options {:init-ns user
                        :timeout 120000}}
   :profiles/dev {}
   :profiles/test {}}
  )
