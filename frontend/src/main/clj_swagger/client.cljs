(ns clj-swagger.client
  (:require [cljs.reader]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]
            [re-frame.core :as rf]
            [reagent.dom]
            [reagent.core :as r]))

(glogi-console/install!)

(log/set-levels
 {:glogi/root   :info    ;; Set a root logger level, this will be inherited by all loggers
  ;; 'my.app.thing :trace   ;; Some namespaces you might want detailed logging
  })

(defn app []
  )

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start! []
  (js/console.log "start")
  (reagent.dom/render [:h2 "Hello World"] (js/document.getElementById "root")))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (js/console.log "init")
  (start!))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "stop"))
