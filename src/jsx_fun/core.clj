(ns jsx-fun.core
  (:require [clojure.java.io :as io])
  (:import [javax.script ScriptEngineManager]))

(defn jsx-engine []
  (let [nashorn (.getEngineByName (ScriptEngineManager.) "nashorn")]
    (doto nashorn
      (.eval "var global = {};")
      (.eval (io/reader (io/resource "com/facebook/jsx.js"))))))

(defn transform
  [jsx-eng src dest]
  (let [options
        (.eval jsx-eng
          "(function(){ return {stripTypes: true, harmony: true};})()")
        jsxt (.eval jsx-eng "global.JSXTransformer")]
    (spit (io/file dest)
      (.get
        (.invokeMethod jsx-eng jsxt "transform"
          (object-array [(slurp src) options]))
        "code"))))

(comment
  (def jsx-eng (jsx-engine))

  (transform jsx-eng
    (io/file "resources/ScrollResponder.js")
    (io/file "resources/ScrollResponder.out.js"))
  )