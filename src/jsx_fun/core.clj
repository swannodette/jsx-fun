(ns jsx-fun.core
  (:require [clojure.java.io :as io]
            [cljs.closure :as cl])
  (:import [javax.script ScriptEngineManager]
           [java.util.logging Level]
           [com.google.javascript.jscomp
            ProcessCommonJSModules CompilerOptions SourceFile Result
            JSError CompilerOptions$LanguageMode]))

(defn jsx-engine []
  (let [nashorn (.getEngineByName (ScriptEngineManager.) "nashorn")]
    (doto nashorn
      (.eval "var global = {};")
      (.eval (io/reader (io/resource "com/facebook/jsx.js"))))))

(defn transform
  [jsx-eng src]
  (let [options
        (.eval jsx-eng
          "(function(){ return {stripTypes: true, harmony: true};})()")
        jsxt (.eval jsx-eng "global.JSXTransformer")]
    (.get
      (.invokeMethod jsx-eng jsxt "transform"
        (object-array [src options]))
      "code")))

(comment
  (def jsx-eng (jsx-engine))

  (spit
    (io/file "resources/ScrollResponder.out.js")
    (transform jsx-eng
      (slurp (io/file "resources/ScrollResponder.js"))))
  )