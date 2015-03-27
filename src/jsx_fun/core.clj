(ns jsx-fun.core
  (:require [clojure.java.io :as io])
  (:import (javax.script ScriptEngineManager Invocable)
           (java.io FileReader)
           (jdk.nashorn.api.scripting ScriptObjectMirror)))

(def eng (.getEngineByName (ScriptEngineManager.) "nashorn"))

(comment
  (.eval eng "var global = {};")
  (.eval eng "")
  (.eval eng (io/reader (io/resource "com/facebook/jsx.js")))

  (def jsx (.eval eng "global.JSXTransformer"))
  (def options (.eval eng "(function(){ return {stripTypes: true, harmony: true};})()"))

  (.invokeMethod eng jsx "transform"
    (object-array
      [(slurp (io/resource "ScrollResponder.js")) options]))

  (def json (.eval eng "JSON"))
  (.invokeMethod eng json "stringify" (object-array [options]))

  (.invokeMethod eng json "stringify" (object-array [1]))

  )