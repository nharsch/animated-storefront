(ns animated-storefront.views.pdp-modal
  (:require [re-frame.core :as rf]))

(defn pdp-modal []
  (let [product @(rf/subscribe [:pdp-product])]
    (when product
      (let [{:product/keys [title description price category rating stock thumbnail tags]} product]
        [:div {:class    "fixed inset-0 z-50 flex items-center justify-center p-4"
               :on-click #(when (= (.-target %) (.-currentTarget %))
                            (rf/dispatch [:close-pdp]))}
         ;; Backdrop
         [:div {:class "absolute inset-0 bg-black/40"}]
         ;; Panel
         [:div {:class "relative bg-white rounded-2xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto z-10"}
          ;; Close button
          [:button {:class    "absolute top-4 right-4 text-gray-400 hover:text-gray-600 text-xl leading-none"
                    :on-click #(rf/dispatch [:close-pdp])}
           "✕"]
          ;; Image
          [:img {:src thumbnail :alt title
                 :class "w-full h-64 object-cover rounded-t-2xl"}]
          ;; Content
          [:div {:class "p-6"}
           [:p {:class "text-xs text-gray-400 uppercase tracking-wide mb-1"} category]
           [:h2 {:class "text-xl font-semibold text-gray-900 mb-3"} title]
           ;; Price + rating row
           [:div {:class "flex items-center gap-4 mb-4"}
            [:span {:class "text-2xl font-bold text-gray-900"} (str "$" (.toFixed price 2))]
            (when rating
              [:span {:class "text-sm text-yellow-500 font-medium"} (str "★ " (.toFixed rating 1))])
            (when stock
              [:span {:class "text-sm text-gray-400"} (str stock " in stock")])]
           ;; Tags
           (when (seq tags)
             [:div {:class "flex flex-wrap gap-1 mb-4"}
              (for [tag tags]
                ^{:key tag}
                [:span {:class "text-xs bg-gray-100 text-gray-600 rounded-full px-2 py-0.5"} tag])])
           ;; Description
           (when description
             [:p {:class "text-sm text-gray-600 leading-relaxed"} description])]]]))))
