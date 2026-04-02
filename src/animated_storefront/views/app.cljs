(ns animated-storefront.views.app
  (:require [re-frame.core :as rf]
            [animated-storefront.views.grid :as grid]
            [animated-storefront.views.chat :as chat]))

(defn tab-bar []
  (let [view @(rf/subscribe [:view])]
    [:div {:class "flex gap-1 px-4 border-b border-gray-200 bg-white"}
     (for [[label kw] [["Grid" :grid] ["List" :list]]]
       ^{:key kw}
       [:button {:class    (str "px-4 py-3 text-sm font-medium border-b-2 -mb-px transition-colors "
                                (if (= view kw)
                                  "border-blue-600 text-blue-600"
                                  "border-transparent text-gray-500 hover:text-gray-700"))
                 :on-click #(rf/dispatch [:change-view kw])}
        label])]))

(defn main-content []
  (let [view @(rf/subscribe [:view])]
    (case view
      :grid [grid/grid-view]
      :list [grid/list-view]
      ;; compare and pdp stubs — to be built
      [:div {:class "flex-1 flex items-center justify-center text-gray-400"}
       (str "View " " (name view) " " coming soon")])))

(defn app []
  [:div {:class "flex flex-col h-screen"}
   ;; Top nav
   [:header {:class "bg-white border-b border-gray-200 px-6 py-3 flex items-center gap-4 flex-shrink-0"}
    [:h1 {:class "text-lg font-bold tracking-tight"} "Storefront"]
    [:span {:class "text-xs text-gray-400"} "AI-directed UI demo"]]
   ;; Tab bar
   [tab-bar]
   ;; Body: content + chat
   [:div {:class "flex flex-1 overflow-hidden"}
    [main-content]
    [chat/chat-panel]]])
