(ns marginalia.browser
  (:require
   [clojure.java.io :as io]
   ;[clojure.tools.namespace.find :as find]
   [clojure.tools.namespace :as ns :refer [read-ns-decl read-file-ns-decl]]
   [compojure.core :refer [defroutes GET]]
   [compojure.handler :as handler]
   [compojure.route :as route]
   [hiccup.page :refer [html5]]
   [marginalia.core :as marg]
   [marginalia.html :as mh]
   [marginalia.parser :as mp]))

(defn ns-in-files [root ns-sym]
  {:pre [(instance? java.io.File root)]}
  (first
   (keep #(when-let [ns-sym (#{ns-sym} (second (read-file-ns-decl %)))]
            [ns-sym %])
         (ns/find-clojure-sources-in-dir root))))

(defn source-resource [ns-sym]
  (try
    (let [src (:file (meta (second (first (ns-publics ns-sym)))))]
      (or (io/resource src)))
    (catch Exception _ (.printStackTrace _))))

(defn jar-path-to-doc [uri]
  (let [ns (-> uri
               io/reader
               java.io.PushbackReader.
               (read-ns-decl)
               (second)
               (str))
        groups (mp/parse-file uri)]
    {:ns ns
     :groups groups}))

(defroutes sken-routes
  (GET "/:ns" [ns]
       (let [css nil
             js nil
             version nil
             name ns
             desc nil
             deps nil
             multi nil
             marg-opts nil
             sources (distinct (marg/format-sources nil))
             project-clj (when (.exists (io/file "project.clj"))
                           (marg/parse-project-file))
             choose #(or %1 %2)
             marg-opts (merge-with choose
                                   {:css (when css (.split css ";"))
                                    :javascript (when js (.split js ";"))}
                                   (:marginalia project-clj))
             opts (merge-with choose
                              {:name name
                               :version version
                               :description desc
                               :dependencies (marg/split-deps deps)
                               :multi multi
                               :marginalia marg-opts}
                              project-clj)
             docs (map marg/path-to-doc sources)
             ns-doc (or (first (filter (comp #{ns} :ns) docs))
                        (jar-path-to-doc (source-resource (symbol ns))))]
         (when ns-doc (mh/single-page-html opts ns-doc docs))))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> sken-routes
      handler/site))
