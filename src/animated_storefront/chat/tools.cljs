(ns animated-storefront.chat.tools
  (:require [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [cljs.reader :as edn]
            [datascript.core :as d]
            [animated-storefront.db :as product-db]))

(defn text-search [q products]
  (let [q (clojure.string/lower-case q)
        title+tag-matches (filter
                           #(or (clojure.string/includes? (clojure.string/lower-case (get % :product/title "")) q)
                                (some (fn [t] (clojure.string/includes? (clojure.string/lower-case t) q))
                                      (get % :product/tags [])))
                           products)]
    (if (seq title+tag-matches)
      title+tag-matches
      ;; fall back to description only if title+tag search is empty
      (filter #(clojure.string/includes? (clojure.string/lower-case (get % :product/description "")) q)
              products))))

;; Tool definitions sent to Claude API

(def tool-definitions
  [{:name        "change_view"
    :description "Switch the storefront to a different view. Pass product_ids to pin a specific result set in grid or list views (e.g. after a datalog_query). Required for compare/pdp. Omit product_ids to return to the normal filtered view."
    :input_schema {:type       "object"
                   :properties {:view        {:type "string"
                                              :enum ["grid" "list"]
                                              :description "The view to switch to"}
                                :product_ids {:type  "array"
                                              :items {:type "integer"}
                                              :description "Product IDs to display. Pins these products in grid/list; required for compare/pdp."}}
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

   {:name        "search_products"
    :description "Search the product catalog by keyword and return results without changing the UI. Use this to check if something exists or get product details before deciding what to show. If you want to search AND display results, use search_and_show instead."
    :input_schema {:type       "object"
                   :properties {:query {:type "string" :description "Word or phrase to search for"}}
                   :required   ["query"]}}

   {:name        "remove_from_view"
    :description "Remove a product from the current pinned result set by name. Use when the customer doesn't want to see a specific item anymore."
    :input_schema {:type       "object"
                   :properties {:product_title {:type "string" :description "Name of the product to remove"}}
                   :required   ["product_title"]}}

   {:name        "show_results"
    :description "Add the results from the most recent search_products call to the screen, joining with anything already pinned. Use this after reviewing search_products results and deciding they match what the customer wants."
    :input_schema {:type "object" :properties {}}}

   {:name        "search_and_show"
    :description "Search for products by keyword and add matching results to the screen. Combines with any existing pinned results so the customer sees everything together. Use this whenever you want to find items AND display them — no need to handle product IDs manually."
    :input_schema {:type       "object"
                   :properties {:query {:type "string" :description "Word or phrase to search for in product titles and descriptions"}}
                   :required   ["query"]}}

   {:name        "open_pdp"
    :description "Open the product detail modal for a specific product. Provide either product_id (from a tool result) or product_title (exact or partial name) — prefer product_title to avoid ID errors."
    :input_schema {:type       "object"
                   :properties {:product_id    {:type "integer" :description "The product ID (only use if taken directly from a tool result)"}
                                :product_title {:type "string"  :description "Product name to look up — safer than product_id"}}}}

   {:name        "get_current_products"
    :description "Returns the products currently visible on screen, respecting any active filters and pinned result set. Use this when the customer asks about 'these', 'what I'm looking at', 'which of these', etc."
    :input_schema {:type "object" :properties {}}}

   {:name        "datalog_query"
    :description "Run a DataScript Datalog query against the product catalog. Use for complex or cross-category searches. Results are returned to you first so you can verify they match the customer's intent before changing the UI. If the query has a parse or execution error it will be returned starting with 'ERROR:' — fix and retry."
    :input_schema {:type       "object"
                   :properties {:query      {:type        "string"
                                             :description "DataScript Datalog query in EDN format."}
                                :rules      {:type        "string"
                                             :description "Optional Datalog rules in EDN format, required when query uses :in $ %."}
                                :result_ids {:type        "array"
                                             :items       {:type "integer"}
                                             :description "Optional list of product IDs to query within (e.g. the current pinned-result-ids). Use when query has :in $ [?id ...]."}}
                   :required   ["query"]}}])

;; Dispatch a tool call from Claude to the re-frame event bus

(defn dispatch-tool-call! [{:keys [name input]}]
  (case name
    "change_view"
    (rf/dispatch-sync [:change-view
                       (keyword (:view input))
                       (:product_ids input)])

    "change_filters"
    (rf/dispatch-sync [:change-filters
                       (cond-> {}
                         (:category input)   (assoc :category (:category input))
                         (:max_price input)  (assoc :max-price (:max_price input))
                         (:min_rating input) (assoc :min-rating (:min_rating input)))])

    "change_sort"
    (rf/dispatch-sync [:change-sort
                       (keyword (:field input))
                       (keyword (:dir input))])

    "remove_from_view"
    (let [title   (clojure.string/lower-case (:product_title input))
          current (or (:result-ids @rf-db/app-db) [])
          all     (product-db/all-products)
          by-id   (into {} (map (juxt :product/id identity) all))
          pruned  (vec (remove (fn [id]
                                 (when-let [p (get by-id id)]
                                   (clojure.string/includes?
                                    (clojure.string/lower-case (:product/title p))
                                    title)))
                               current))
          view    (:view @rf-db/app-db)]
      (rf/dispatch-sync [:change-view view (when (seq pruned) pruned)]))

    "show_results"
    (let [current    (or (:result-ids @rf-db/app-db) [])
          last-ids   (or (:last-search-ids (:chat @rf-db/app-db)) [])
          joined     (vec (distinct (concat current last-ids)))
          view       (:view @rf-db/app-db)]
      (rf/dispatch-sync [:change-view view joined]))

    "search_and_show"
    (let [q        (clojure.string/lower-case (:query input))
          matches  (text-search q (product-db/all-products))
          new-ids  (mapv :product/id matches)
          current  (or (:result-ids @rf-db/app-db) [])
          joined   (vec (distinct (concat current new-ids)))
          view     (:view @rf-db/app-db)]
      (rf/dispatch-sync [:change-view view joined]))

    "search_products"
    nil ;; read-only

    "query_products"
    nil ;; read-only

    "open_pdp"
    (let [by-title (when-let [t (:product_title input)]
                     (first (filter #(clojure.string/includes?
                                      (clojure.string/lower-case (:product/title %))
                                      (clojure.string/lower-case t))
                                    (product-db/all-products))))
          pid      (or (:product/id by-title) (:product_id input))
          exists?  (when pid (seq (d/q '[:find [?e ...]
                                         :in $ ?id
                                         :where [?e :product/id ?id]]
                                       @product-db/conn pid)))]
      (if exists?
        (rf/dispatch [:open-pdp pid])
        (js/console.warn "open_pdp: could not resolve product" (clj->js input))))

    "get_current_products"
    nil ;; read-only

    "datalog_query"
    nil ;; read-only — Claude reviews results before deciding to change UI

    (js/console.warn "Unknown tool:" name)))

;; Execute a read tool and return results to Claude

(defn visible-product-ids []
  (let [db         @rf-db/app-db
        result-ids (seq (:result-ids db))
        filters    (:filters db)
        all        (product-db/all-products)
        base       (if result-ids
                     (let [by-id (into {} (map (juxt :product/id identity) all))]
                       (keep by-id result-ids))
                     all)
        visible    (cond->> base
                     (:category filters)  (filter #(= (:product/category %) (:category filters)))
                     (:max-price filters) (filter #(<= (:product/price %) (:max-price filters))))]
    (mapv :product/id visible)))

(defn execute-read-tool [{:keys [name input]}]
  (case name
    "change_view"
    (let [all-ids (set (map :product/id (product-db/all-products)))
          invalid (vec (remove #(contains? all-ids %) (or (:product_ids input) [])))]
      (when (seq invalid)
        (js/console.warn "change_view: unknown product_ids" (clj->js invalid)))
      (js/JSON.stringify (clj->js {:product_ids (visible-product-ids)
                                   :invalid_ids  invalid})))

    "change_filters"
    (js/JSON.stringify (clj->js {:product_ids (visible-product-ids)}))

    "change_sort"
    (js/JSON.stringify (clj->js {:product_ids (visible-product-ids)}))

    "search_and_show"
    (let [q       (clojure.string/lower-case (:query input))
          matches (text-search q (product-db/all-products))]
      (js/JSON.stringify (clj->js {:found    (mapv #(select-keys % [:product/id :product/title :product/price :product/category :product/rating]) matches)
                                   :on_screen (visible-product-ids)})))

    "remove_from_view"
    (js/JSON.stringify (clj->js {:on_screen (visible-product-ids)}))

    "show_results"
    (js/JSON.stringify (clj->js {:on_screen (visible-product-ids)}))

    "search_products"
    (let [q       (clojure.string/lower-case (:query input))
          results (text-search q (product-db/all-products))]
      (when (seq results)
        (rf/dispatch [:set-last-search-ids (mapv :product/id results)]))
      (js/JSON.stringify
       (clj->js (mapv #(select-keys % [:product/id :product/title :product/price
                                       :product/category :product/rating
                                       :product/tags :product/description])
                      results))))

    "query_products"
    (let [all      (product-db/all-products)
          filtered (cond->> all
                     (:category input)  (filter #(= (:product/category %) (:category input)))
                     (:max_price input) (filter #(<= (:product/price %) (:max_price input)))
                     (:query input)     (#(text-search (:query input) %)))]
      (clj->js (mapv #(select-keys % [:product/id :product/title :product/price
                                      :product/category :product/rating])
                     filtered)))
    "open_pdp"
    (let [by-title (when-let [t (:product_title input)]
                     (first (filter #(clojure.string/includes?
                                      (clojure.string/lower-case (:product/title %))
                                      (clojure.string/lower-case t))
                                    (product-db/all-products))))
          pid      (or (:product/id by-title) (:product_id input))
          exists?  (when pid (seq (d/q '[:find [?e ...]
                                         :in $ ?id
                                         :where [?e :product/id ?id]]
                                       @product-db/conn pid)))]
      (if exists?
        (js/JSON.stringify (clj->js {:ok true :product_id pid}))
        (str "ERROR: could not find product. Provide product_title instead of product_id to avoid ID errors.")))

    "get_current_products"
    (let [db         @rf-db/app-db
          result-ids (seq (:result-ids db))
          filters    (:filters db)
          all        (product-db/all-products)
          base       (if result-ids
                       (let [by-id (into {} (map (juxt :product/id identity) all))]
                         (keep by-id result-ids))
                       all)
          visible    (cond->> base
                       (:category filters)  (filter #(= (:product/category %) (:category filters)))
                       (:max-price filters) (filter #(<= (:product/price %) (:max-price filters))))]
      (js/JSON.stringify
       (clj->js (mapv #(select-keys % [:product/id :product/title :product/price
                                       :product/category :product/rating :product/tags])
                      visible))))

    "datalog_query"
    (try
      (let [q          (edn/read-string (:query input))
            rules      (when (:rules input) (edn/read-string (:rules input)))
            result-ids (:result_ids input)
            results    (cond
                         result-ids (d/q q @product-db/conn result-ids)
                         rules      (d/q q @product-db/conn rules)
                         :else      (d/q q @product-db/conn))]
        (js/JSON.stringify (clj->js results)))
      (catch :default e
        (str "ERROR: " (.-message e))))

    nil))
