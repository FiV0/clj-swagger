(ns clj-swagger.client
  (:require [cljs.reader]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]
            [re-frame.core :as rf]
            [reagent.dom :as reagent]
            [reagent.dom.client :as rdomc]
            [reagent.core :as r]))

(glogi-console/install!)

(log/set-levels
 {:glogi/root   :info    ;; Set a root logger level, this will be inherited by all loggers
  ;; 'my.app.thing :trace   ;; Some namespaces you might want detailed logging
  })

(defn app []
  [:div
   [:h1 "ClojureScript Swagger Client"]
   [:p "This is a simple ClojureScript application that uses Reagent for rendering."]])

(defonce root (delay (rdomc/create-root (.getElementById js/document "app"))))

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start! []
  (rdomc/render @root [app] ))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (js/console.log "init")
  (start!))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "stop"))
