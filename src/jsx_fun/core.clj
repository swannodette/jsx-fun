(ns jsx-fun.core
  (:require [clojure.java.io :as io]
            [cljs.closure :as cl])
  (:import [javax.script ScriptEngineManager]
           [java.util List]
           [java.util.logging Level]
           [com.google.javascript.jscomp
            ES6ModuleLoader ProcessCommonJSModules CompilerOptions SourceFile
            Result JSModule JSError CompilerOptions$LanguageMode
            AbstractCompiler]
           [com.google.javascript.jscomp.Compiler]))

(defn set-options [opts ^CompilerOptions compiler-options]
  (doseq [[k v] opts]
    (condp = k
      :type
      (case v
        :commonjs (.setProcessCommonJSModules compiler-options true)
        :amd (doto compiler-options
               (.setProcessCommonJSModules true)
               (.setTransformAMDToCJSModules true))
        :es6 (doto compiler-options
               (.setLanguageIn CompilerOptions$LanguageMode/ECMASCRIPT6)
               (.setLanguageOut CompilerOptions$LanguageMode/ECMASCRIPT5)))
      :lang-in
      (case v
        :es5 (.setLanguageIn compiler-options
               CompilerOptions$LanguageMode/ECMASCRIPT5))))

  compiler-options)

(defn jsx-engine []
  (let [nashorn (.getEngineByName (ScriptEngineManager.) "nashorn")]
    (doto nashorn
      (.eval "var global = {};")
      (.eval (io/reader (io/resource "com/facebook/jsx.js"))))))

(defn transform-jsx
  [jsx-eng src]
  (let [options
        (.eval jsx-eng
          "(function(){ return {stripTypes: true, harmony: true};})()")
        jsxt (.eval jsx-eng "global.JSXTransformer")]
    (.get
      (.invokeMethod jsx-eng jsxt "transform"
        (object-array [src options]))
      "code")))

#_(defn transform-commonjs
  [filename src]
  (let [^List externs '()
        ^List inputs [(SourceFile/fromCode filename src)]
        ^CompilerOptions options (set-options {:lang-in :es5 :type :commonjs} (CompilerOptions.))
        compiler (cl/make-closure-compiler)
        ^Result result (.compile compiler externs inputs options)]
    (if (.success result)
      (.toSource compiler)
      (cl/report-failure result))))

(defn transform-commonjs
  [filename src]
  (let [js      [(SourceFile/fromCode
                   filename
                   src)]
        options (set-options
                  {:lang-in :es5 :type :commonjs}
                  (CompilerOptions.))
        comp    (doto (cl/make-closure-compiler)
                  (.init '() js options))
        root    (.parse comp (first js))]
    (.process
      (ProcessCommonJSModules. comp
        (ES6ModuleLoader. comp "./")
        false)
      nil root)
    (.toSource comp root)))

(comment
  (def jsx-eng (jsx-engine))

  (spit
    (io/file "resources/ScrollResponder.out.js")
    (transform-jsx jsx-eng
      (slurp (io/file "resources/ScrollResponder.js"))))

  (transform-commonjs
    "ScrollResponder.out.js"
    (transform-jsx jsx-eng
      (slurp (io/file "resources/ScrollResponder.js"))))

  ;; NOTE: wasted some time processing the wrong thing!
  ;; need to process the JSX compiled thing

  ;; getting warmer!

  (let [js      [(SourceFile/fromCode
                   "ScrollResponder.out.js"
                   (slurp (io/file "resources/ScrollResponder.out.js")))]
        options (set-options
                  {:lang-in :es5 :type :commonjs}
                  (CompilerOptions.))
        comp    (doto (cl/make-closure-compiler)
                  (.init '() js options))
        ;module  (JSModule. "[singleton]")
        ;input   (do
        ;          (doseq [x js]
        ;             (.add module x))
        ;          (first (.getInputs module)))
        root (.parse comp
               (first js))]
    (.process
      (ProcessCommonJSModules. comp
        (ES6ModuleLoader. comp "./")
        false)
      nil root)
    (.toSource comp root))

)