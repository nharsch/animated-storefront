(ns animated-storefront.views.grid
  (:require [re-frame.core :as rf]
            [animated-storefront.views.product-card :as card]))

(defn filter-bar []
  (let [categories @(rf/subscribe [:categories])
        filters    @(rf/subscribe [:filters])
        sort-state @(rf/subscribe [:sort])]
    [:div {:class "flex flex-wrap gap-3 mb-6 items-center"}
     ;; Category filter
     [:select {:class     "text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white"
               :value     (or (:category filters) "")
               :on-change #(rf/dispatch [:change-filters {:category (let [v (.. % -target -value)]
                                                                      (when (seq v) v))}])}
      [:option {:value ""} "All categories"]
      (doall (for [cat (sort categories)]
               ^{:key cat} [:option {:value cat} cat]))]
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
     (when (seq filters)
       [:button {:class    "text-sm text-gray-400 hover:text-gray-600"
                 :on-click #(rf/dispatch [:clear-filters])}
        "Clear filters"])]))

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
