(ns animated-storefront.chat.tools
  (:require [re-frame.core :as rf]
            [animated-storefront.db :as product-db]))

;; Tool definitions sent to Claude API

(def tool-definitions
  [{:name        "change_view"
    :description "Switch the storefront to a different view. Use product_ids when switching to compare or pdp."
    :input_schema {:type       "object"
                   :properties {:view        {:type "string"
                                              :enum ["grid" "list" "compare" "pdp"]
                                              :description "The view to switch to"}
                                :product_ids {:type  "array"
                                              :items {:type "integer"}
                                              :description "Product IDs to show (required for compare/pdp)"}}
                   :required   ["view"]}}

   {:name        "change_filters"
    :description "Update product filters. Merges with existing filters."
    :input_schema {:type       "object"
                   :properties {:category  {:type "string" :description "Filter by category name"}
                                :max_price {:type "number" :description "Maximum price"}
                                :min_rating {:type "number" :description "Minimum rating (0-5)"}}}}

   {:name        "change_sort"
    :description "Change the sort order of the product listing."
    :input_schema {:type       "object"
                   :properties {:field {:type "string"
                                        :enum ["price" "rating" "title"]
                                        :description "Field to sort by"}
                                :dir   {:type "string"
                                        :enum ["asc" "desc"]
                                        :description "Sort direction"}}
                   :required   ["field" "dir"]}}

   {:name        "query_products"
    :description "Search the product catalog to answer customer questions. Returns matching products."
    :input_schema {:type       "object"
                   :properties {:category {:type "string" :description "Filter by category"}
                                :max_price {:type "number" :description "Maximum price"}
                                :query    {:type "string" :description "Text to match against title/description"}}}}])

;; Dispatch a tool call from Claude to the re-frame event bus

(defn dispatch-tool-call! [{:keys [name input]}]
  (case name
    "change_view"
    (rf/dispatch [:change-view
                  (keyword (:view input))
                  (:product_ids input)])

    "change_filters"
    (rf/dispatch [:change-filters
                  (cond-> {}
                    (:category input)   (assoc :category (:category input))
                    (:max_price input)  (assoc :max-price (:max_price input))
                    (:min_rating input) (assoc :min-rating (:min_rating input)))])

    "change_sort"
    (rf/dispatch [:change-sort
                  (keyword (:field input))
                  (keyword (:dir input))])

    "query_products"
    nil ;; read-only, result returned to Claude — no dispatch needed

    (js/console.warn "Unknown tool:" name)))

;; Execute a read tool and return results to Claude

(defn execute-read-tool [{:keys [name input]}]
  (case name
    "query_products"
    (let [all      (product-db/all-products)
          filtered (cond->> all
                     (:category input)  (filter #(= (:product/category %) (:category input)))
                     (:max_price input) (filter #(<= (:product/price %) (:max_price input)))
                     (:query input)     (filter #(some (fn [field]
                                                         (when-let [v (get % field)]
                                                           (clojure.string/includes?
                                                            (clojure.string/lower-case v)
                                                            (clojure.string/lower-case (:query input)))))
                                                       [:product/title :product/description])))]
      (clj->js (mapv #(select-keys % [:product/id :product/title :product/price
                                      :product/category :product/rating])
                     filtered)))
    nil))
