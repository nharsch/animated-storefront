(ns animated-storefront.core
  (:require [reagent.dom.client :as rdom]
            [re-frame.core :as rf]
            [animated-storefront.events]
            [animated-storefront.subs]
            [animated-storefront.effects]
            [animated-storefront.views.app :as app]))

(defonce root (atom nil))

(defn mount-root []
  (when-not @root
    (reset! root (rdom/create-root (.getElementById js/document "app"))))
  (rdom/render @root [app/app]))

(defn ^:dev/after-load re-render []
  (rf/clear-subscription-cache!)
  (mount-root))

(defn init []
  (rf/dispatch-sync [:initialize])
  (rf/dispatch [:load-products])
  (mount-root))
