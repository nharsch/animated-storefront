(ns animated-storefront.views.product-card
  (:require [re-frame.core :as rf]))

(defn product-card [{:product/keys [id title price category thumbnail rating]}]
  [:div {:class    "bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
         :on-click #(rf/dispatch [:open-pdp id])}
   [:img {:src thumbnail :alt title
          :class "w-full h-48 object-cover"}]
   [:div {:class "p-4"}
    [:p {:class "text-xs text-gray-400 uppercase tracking-wide mb-1"} category]
    [:h3 {:class "font-medium text-gray-900 text-sm leading-snug mb-2 line-clamp-2"} title]
    [:div {:class "flex items-center justify-between"}
     [:span {:class "text-lg font-semibold text-gray-900"} (str "$" (.toFixed price 2))]
     [:span {:class "text-xs text-yellow-500"} (str "★ " rating)]]]])

(defn product-card-compact [{:product/keys [id title price category thumbnail rating]}]
  [:div {:class    "bg-white rounded-lg border border-gray-100 flex gap-3 p-3 hover:shadow-sm transition-shadow cursor-pointer"
         :on-click #(rf/dispatch [:open-pdp id])}
   [:img {:src thumbnail :alt title
          :class "w-16 h-16 object-cover rounded-md flex-shrink-0"}]
   [:div {:class "flex-1 min-w-0"}
    [:p {:class "text-xs text-gray-400 mb-0.5"} category]
    [:h3 {:class "text-sm font-medium text-gray-900 truncate"} title]
    [:div {:class "flex items-center gap-2 mt-1"}
     [:span {:class "text-sm font-semibold"} (str "$" (.toFixed price 2))]
     [:span {:class "text-xs text-yellow-500"} (str "★ " rating)]]]])
