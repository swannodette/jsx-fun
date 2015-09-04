(ns jsx-fun.core
  (:refer-clojure :exclude [compile])
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cljs.closure :as cl])
  (:import [java.io File]
           [com.google.common.base Predicates]
           [javax.script ScriptEngineManager]
           [com.google.javascript.jscomp
            ES6ModuleLoader ProcessCommonJSModules CompilerOptions SourceFile
            CompilerOptions$LanguageMode NodeUtil NodeUtil$Visitor
            BasicErrorManager]
           [com.google.javascript.jscomp.deps
            DepsGenerator DepsGenerator$InclusionStrategy]
           [com.google.javascript.jscomp.parsing
            Config Config$LanguageMode ParserRunner]
           [com.google.javascript.rhino
            Node Token ErrorReporter SimpleErrorReporter]))

(defprotocol JSTransformer
  (source-path [this])
  (js-name [this]))

(deftype JSX []
  JSTransformer
  (source-path [_] "com/facebook/jsx.js")
  (js-name [_] "JSXTransformer"))

(deftype Babel []
  JSTransformer
  (source-path [_] "io/babeljs/browser.min.js")
  (js-name [_] "babel"))

(defn set-options [opts ^CompilerOptions compiler-options]
  (doseq [[k v] opts]
    (condp = k
      :lang-in
      (case v
        :es5 (.setLanguageIn compiler-options
               CompilerOptions$LanguageMode/ECMASCRIPT5))
      :pretty-print
      (set! (.prettyPrint compiler-options) v)))
  compiler-options)

(defn jsx-engine
  ([] (jsx-engine (JSX.)))
  ([transformer]
   (let [nashorn (.getEngineByName (ScriptEngineManager.) "nashorn")]
     (doto nashorn
       (.eval "var global = {};")
       (.eval (io/reader (io/resource (source-path transformer))))))))

(defn transform-jsx
  ([src]
   (transform-jsx (JSX.) src))
  ([transformer src]
   (let [jsx-eng (jsx-engine transformer)
         options
         (.eval jsx-eng
           "(function(){ return {stripTypes: true, harmony: true};})()")
         jsxt (.eval jsx-eng (str "global." (js-name transformer)))]
     (.get
       (.invokeMethod jsx-eng jsxt "transform"
         (object-array [src options]))
       "code"))))

(defn provides [^String src]
  (last
    (string/split
      (some #(when (re-find #"@providesModule" %) %)
        (string/split src #"\n"))
      #"\s+")))

(defn transform-commonjs
  ([src] (transform-commonjs (provides src) src))
  ([provides src]
   (let [js [(SourceFile/fromCode provides src)]
         options (set-options {:lang-in :es5 :pretty-print true}
                   (CompilerOptions.))
         comp (doto (cl/make-closure-compiler)
                (.init '() js options))
         root (.parse comp (first js))]
     (.process
       (ProcessCommonJSModules. comp
         (ES6ModuleLoader. comp "./")
         false)
       nil root)
     (.toSource comp root))))

(defn node-visitor []
  (reify
    NodeUtil$Visitor
    (visit [_ node]
      (println (Token/name (.getType node)))
      (when-let [js-doc (.getJSDocInfo node)]
        (println (.toStringVerbose js-doc))))))

(defn js-files-in
  "Return a sequence of all .js files in the given directory."
  [dir]
  (filter
    #(let [name (.getName ^File %)]
       (and (.endsWith name ".js")
         (not= \. (first name))))
    (file-seq dir)))

(defn deps-graph
  ([goog-abs-path dirs]
   (deps-graph goog-abs-path '() dirs))
  ([goog-abs-path deps dirs]
   (.computeDependencyCalls
     (DepsGenerator.
       deps
       (map #(SourceFile/fromFile %)
         (mapcat (comp js-files-in io/file)
           dirs))
       DepsGenerator$InclusionStrategy/ALWAYS
       goog-abs-path
       (proxy [BasicErrorManager] []
         (report [level error]
           (println error))
         (println [level error]
           (println error)))))))

(defn compile
  ([dir]
   (compile dir "out"))
  ([dir outdir]
   (letfn [(dir? [^File x] (.isDirectory x))
           (android? [^File x]
             (some #{"android"} (string/split (.getName x) #"\.")))]
     (.mkdir (io/file outdir))
     (doseq [file (remove #(or (dir? %) (android? %))
                    (file-seq (io/file dir)))]
       (let [out-file (io/file outdir (.getName file))]
         (spit out-file
           (transform-commonjs
             (transform-jsx (slurp file)))))))))

;(defn transform-commonjs
;  [filename src]
;  (let [^List externs '()
;        ^List inputs [(SourceFile/fromCode filename src)]
;        ^CompilerOptions options (set-options {:lang-in :es5 :type :commonjs} (CompilerOptions.))
;        compiler (cl/make-closure-compiler)
;        ^Result result (.compile compiler externs inputs options)]
;    (if (.success result)
;      (.toSource compiler)
;      (cl/report-failure result))))

(comment
  (spit
    (io/file "resources/inputs/ScrollResponder.out.js")
    (transform-jsx (slurp (io/file "resources/inputs/ScrollResponder.js"))))

  ;; still doesn't work
  (spit
    (io/file "resources/inputs/ScrollResponder.out.js")
    (transform-jsx (Babel.) (slurp (io/file "resources/inputs/ScrollResponder.js"))))

  ;; the file name is used as the module name
  ;; we will have to extract this from @providesModule
  (println
    (transform-commonjs
      (transform-jsx (slurp (io/file "resources/inputs/ScrollResponder.js")))))

  (println
    (transform-commonjs
      (transform-jsx (slurp (io/file "resources/inputs/StatusBarIOS.ios.js")))))

  (provides
    (transform-jsx (slurp (io/file "resources/inputs/StatusBarIOS.ios.js"))))

  (deps-graph
    (.getAbsolutePath (io/file "deps/closure-library/closure"))
    ["out"])

  (compile "resources")

  ;; Parsing example
  ;; based on http://slieb.org/blog/parseJavaScriptWithGoogleClosure/
  (let [config       (ParserRunner/createConfig
                       true Config$LanguageMode/ECMASCRIPT5 true nil)
        err-reporter (SimpleErrorReporter.)
        source-file  (SourceFile/fromFile
                       (io/file "resources/inputs/ScrollResponder.js"))
        parse-result (ParserRunner/parse
                       source-file (.getCode source-file)
                       config err-reporter)
        visitor      (node-visitor)]
    (println
      (.comments parse-result))
    (NodeUtil/visitPreOrder
      (.ast parse-result) visitor (Predicates/alwaysTrue)))
  )