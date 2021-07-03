(defproject jawt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [mount/mount "0.1.16"]

                 ;; logging
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [org.clojure/tools.logging "1.1.0"]

                 ;; db
                 [com.github.seancorfield/next.jdbc "1.2.674"]
                 [hikari-cp "2.13.0"]
                 [org.xerial/sqlite-jdbc "3.36.0.1"]
                 [migratus "1.3.5"]]
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :repl-options {:init-ns jawt.core}

  :profiles 
  {:dev {:dependencies []
         :plugins []
         :source-paths ["dev/clj" "dev/cljc" "dev/cljs"]
         :repl-options {:init-ns user
                        :timeout 120000}}
   :profiles/dev {}
   :profiles/test {}}
  )
