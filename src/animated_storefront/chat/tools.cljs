(ns animated-storefront.chat.tools
  (:require [re-frame.core :as rf]
            [cljs.reader :as edn]
            [datascript.core :as d]
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
                   :properties {:category  {:type "string" :description "Filter by category"}
                                :max_price {:type "number" :description "Maximum price"}
                                :query     {:type "string" :description "Text to match against title/description"}}}}

   {:name        "datalog_query"
    :description "Run a DataScript Datalog query against the product catalog. Use for complex or cross-category searches. Results are returned to you first so you can verify they match the customer's intent before changing the UI. If the query has a parse or execution error it will be returned starting with 'ERROR:' — fix and retry."
    :input_schema {:type       "object"
                   :properties {:query {:type        "string"
                                        :description "DataScript Datalog query in EDN format."}
                                :rules {:type        "string"
                                        :description "Optional Datalog rules in EDN format, required when query uses :in $ %."}}
                   :required   ["query"]}}])

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
    nil ;; read-only

    "datalog_query"
    nil ;; read-only — Claude reviews results before deciding to change UI

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
    "datalog_query"
    (try
      (let [q       (edn/read-string (:query input))
            rules   (when (:rules input) (edn/read-string (:rules input)))
            results (if rules
                      (d/q q @product-db/conn rules)
                      (d/q q @product-db/conn))]
        (js/JSON.stringify (clj->js results)))
      (catch :default e
        (str "ERROR: " (.-message e))))

    nil))
