(ns animated-storefront.db
  (:require [datascript.core :as d]))

;; DataScript schema for product catalog
(def product-schema
  {:product/id       {:db/unique :db.unique/identity}
   :product/category {:db/index true}
   :product/tags     {:db/cardinality :db.cardinality/many}})

(defonce conn (d/create-conn product-schema))

(defn load-products! [products]
  (d/transact! conn
               (mapv (fn [p]
                       (->> {:product/id          (:id p)
                             :product/title       (:title p)
                             :product/description (:description p)
                             :product/price       (:price p)
                             :product/category    (:category p)
                             :product/thumbnail   (:thumbnail p)
                             :product/rating      (get-in p [:rating :rate])
                             :product/stock       (:stock p)
                             :product/tags        (or (:tags p) [])}
                            (remove (fn [[_ v]] (nil? v)))
                            (into {})))
                     products)))

(defn query-products
  "Run a raw datalog query against the product DB."
  [query & args]
  (apply d/q query @conn args))

(defn all-products []
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :product/id _]]
       @conn))

(defn products-by-category [category]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?cat
         :where [?e :product/category ?cat]]
       @conn category))

(defn categories []
  (d/q '[:find [?cat ...]
         :where [_ :product/category ?cat]]
       @conn))
