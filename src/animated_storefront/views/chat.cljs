(ns animated-storefront.views.chat
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [animated-storefront.chat.api :as api]))

(defn message-bubble [{:keys [role content]}]
  (cond
    (= role "ui")
    [:div {:class "flex justify-center mb-3"}
     [:span {:class "text-xs text-gray-400 italic"} content]]

    :else
    (let [user? (= role "user")]
      [:div {:class (str "flex " (if user? "justify-end" "justify-start") " mb-3")}
       [:div {:class (str "max-w-[80%] rounded-2xl px-4 py-2 text-sm "
                          (if user?
                            "bg-blue-600 text-white rounded-br-sm"
                            "bg-white border border-gray-100 text-gray-800 rounded-bl-sm"))}
        content]])))

(defn chat-panel []
  (let [input    (r/atom "")
        scroll-ref (r/atom nil)]
    (r/create-class
     {:component-did-update
      (fn [this]
        (when-let [el @scroll-ref]
          (set! (.-scrollTop el) (.-scrollHeight el))))
      :reagent-render
      (fn []
        (let [{:keys [messages loading]} @(rf/subscribe [:chat])
              chat-open @(rf/subscribe [:chat-open])]
          [:div {:class (str "w-80 flex-shrink-0 bg-gray-50 border-l border-gray-200 flex flex-col h-full "
                             "fixed md:relative top-0 right-0 md:inset-auto z-50 md:z-auto "
                             "transition-transform duration-300 "
                             (if chat-open
                               "translate-x-0"
                               "translate-x-full md:translate-x-0"))}
           ;; Header
           [:div {:class "px-4 py-3 border-b border-gray-200 bg-white flex items-start justify-between"}
            [:div
             [:h2 {:class "font-semibold text-gray-900 text-sm"} "Shopping Assistant"]
             [:p {:class "text-xs text-gray-400"} "Ask me anything or let me help you browse"]]
            [:button {:class "md:hidden text-gray-400 hover:text-gray-600 -mt-1"
                      :on-click #(rf/dispatch [:toggle-chat])}
             "✕"]]
           ;; Messages
           [:div {:class "flex-1 overflow-y-auto p-4"
                  :ref   (fn [el] (reset! scroll-ref el))}
            (if (empty? messages)
              [:p {:class "text-xs text-gray-400 text-center mt-8"}
               "Hi! I can help you find products, filter results, or compare items."]
              (for [[i msg] (map-indexed vector messages)]
                ^{:key i} [message-bubble msg]))
            (when loading
              [:div {:class "flex justify-start mb-3"}
               [:div {:class "bg-white border border-gray-100 rounded-2xl rounded-bl-sm px-4 py-2"}
                [:span {:class "text-gray-400 text-sm"} "..."]]])]
           ;; Input
           [:div {:class "p-3 border-t border-gray-200 bg-white"}
            [:div {:class "flex gap-2"}
             [:input {:class       "flex-1 text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      :type        "text"
                      :placeholder "Ask about products…"
                      :value       @input
                      :on-change   #(reset! input (.. % -target -value))
                      :on-key-down (fn [e]
                                     (when (= (.-key e) "Enter")
                                       (when-let [text (not-empty (clojure.string/trim @input))]
                                         (api/send-message! text)
                                         (reset! input ""))))}]
             [:button {:class    "bg-blue-600 text-white rounded-lg px-3 py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
                       :disabled loading
                       :on-click (fn []
                                   (when-let [text (not-empty (clojure.string/trim @input))]
                                     (api/send-message! text)
                                     (reset! input "")))}
              "→"]]]]))})))
