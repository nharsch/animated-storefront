(ns animated-storefront.views.grid
  (:require [re-frame.core :as rf]
            [animated-storefront.views.product-card :as card]))

(defn category-checkbox [cat selected? selected-categories]
  [:label {:class "flex items-center gap-2 px-3 py-2 hover:bg-gray-50 cursor-pointer"}
   [:input {:type "checkbox"
            :class "rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            :checked selected?
            :on-change #(let [checked (.. % -target -checked)
                              updated (if checked
                                        (conj selected-categories cat)
                                        (disj selected-categories cat))]
                          (rf/dispatch [:change-filters {:categories (when (seq updated) updated)}]))}]
   [:span {:class "text-sm"} cat]])

(defn filter-bar []
  (let [categories  @(rf/subscribe [:categories])
        filters     @(rf/subscribe [:filters])
        sort-state  @(rf/subscribe [:sort])
        result-ids  @(rf/subscribe [:result-ids])
        selected-categories (or (:categories filters) #{})]
    [:div {:class "flex flex-wrap gap-3 mb-6 items-center"}
     ;; Category filter (multi-select dropdown)
     [:div {:class "relative"}
      [:details {:class "group"
                 :ref (fn [el]
                        (when el
                          (let [handler (fn [e]
                                          (when-not (.contains el (.-target e))
                                            (set! (.-open el) false)))]
                            ;; Store handler on element so we can clean up
                            (when-not (.-clickHandler el)
                              (set! (.-clickHandler el) handler)
                              (.addEventListener js/document "click" handler true))
                            ;; Cleanup on unmount
                            (set! (.-cleanup el)
                                  (fn []
                                    (when (.-clickHandler el)
                                      (.removeEventListener js/document "click" (.-clickHandler el) true)))))))}
       [:summary {:class "text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white cursor-pointer hover:bg-gray-50 list-none flex items-center justify-between gap-2 min-w-[200px]"}
        [:span (if (seq selected-categories)
                 (str (count selected-categories) " categories")
                 "All categories")]
        [:span {:class "text-gray-400 group-open:rotate-180 transition-transform"} "▼"]]
       [:div {:class "absolute top-full left-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-10 max-h-64 overflow-y-auto min-w-[200px]"}
        (doall (for [cat (sort categories)]
                 ^{:key cat} [category-checkbox cat (contains? selected-categories cat) selected-categories]))]]]
     ;; Sort
     [:select {:class     "text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white"
               :value     (str (name (:field sort-state)) "-" (name (:dir sort-state)))
               :on-change #(let [[field dir] (clojure.string/split (.. % -target -value) #"-")]
                             (rf/dispatch [:change-sort (keyword field) (keyword dir)]))}
      [:option {:value "price-asc"} "Price: Low to High"]
      [:option {:value "price-desc"} "Price: High to Low"]
      [:option {:value "rating-desc"} "Top Rated"]
      [:option {:value "title-asc"} "Name A–Z"]]
     ;; Clear
     (when (or (seq filters) (seq result-ids))
       [:button {:class    "text-sm text-gray-400 hover:text-gray-600"
                 :on-click #(rf/dispatch [:clear-filters])}
        (if (seq result-ids) "Clear chat filter" "Clear filters")])]))

(defn grid-view []
  (let [products @(rf/subscribe [:products])
        loading  @(rf/subscribe [:products-loading])]
    [:div {:class "flex-1 overflow-y-auto p-6"}
     [filter-bar]
     (cond
       loading
       [:div {:class "flex items-center justify-center h-64 text-gray-400"} "Loading products…"]

       (empty? products)
       [:div {:class "flex items-center justify-center h-64 text-gray-400"} "No products match your filters."]

       :else
       [:div {:class "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4"}
        (for [product products]
          ^{:key (:product/id product)}
          [card/product-card product])])]))

(defn list-view []
  (let [products @(rf/subscribe [:products])
        loading  @(rf/subscribe [:products-loading])]
    [:div {:class "flex-1 overflow-y-auto p-6"}
     [filter-bar]
     (cond
       loading
       [:div {:class "flex items-center justify-center h-64 text-gray-400"} "Loading products…"]

       (empty? products)
       [:div {:class "flex items-center justify-center h-64 text-gray-400"} "No products match your filters."]

       :else
       [:div {:class "flex flex-col gap-2"}
        (for [product products]
          ^{:key (:product/id product)}
          [card/product-card-compact product])])]))
