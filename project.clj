(defproject ggp-base "0.1.1"
  :description "CLojure library to develop GGP players"
  :url "http://E"
  :license {:name "MIT license"
            :url "http://opensource.org/licenses/MIT"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [junit/junit "4.11"]
                 [org.json/json "20140107"]
                 [org.python/jython "2.7-b1"]
                 [org.apache.xmlgraphics/batik-transcoder "1.7"]
                 [org.xhtmlrenderer/flying-saucer-core "9.0.4"]
                 [nu.validator.htmlparser/htmlparser "1.4"]
                 [com.google.guava/guava "16.0"]
                 [org.reflections/reflections "0.9.9-RC1"]]
  :java-source-paths ["src"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:deprecation"]
  :global-vars {*warn-on-reflection* true
                *assert* false}
  )
