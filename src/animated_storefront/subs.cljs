(ns animated-storefront.subs
  (:require [re-frame.core :as rf]
            [animated-storefront.db :as product-db]
            [datascript.core :as d]))

(rf/reg-sub :view (fn [db _] (:view db)))
(rf/reg-sub :filters (fn [db _] (:filters db)))
(rf/reg-sub :sort (fn [db _] (:sort db)))
(rf/reg-sub :chat (fn [db _] (:chat db)))
(rf/reg-sub :products-loading (fn [db _] (:products-loading db)))
(rf/reg-sub :db-version (fn [db _] (:db-version db)))
(rf/reg-sub :result-ids (fn [db _] (:result-ids db)))
(rf/reg-sub :pdp-product-id (fn [db _] (:pdp-product-id db)))

(rf/reg-sub
 :pdp-product
 :<- [:pdp-product-id]
 :<- [:db-version]
 (fn [[id _] _]
   (when id
     (first (d/q '[:find [(pull ?e [*]) ...]
                   :in $ ?id
                   :where [?e :product/id ?id]]
                 @product-db/conn id)))))

(rf/reg-sub
 :products
 :<- [:filters]
 :<- [:sort]
 :<- [:db-version]
 :<- [:result-ids]
 (fn [[filters {:keys [field dir]} _ result-ids] _]
   (let [base     (if (seq result-ids)
                    ;; pinned result set — preserve order, still apply filters
                    (let [by-id (into {} (map (juxt :product/id identity) (product-db/all-products)))]
                      (keep by-id result-ids))
                    (product-db/all-products))
         filtered (cond->> base
                    (:category filters)  (filter #(= (:product/category %) (:category filters)))
                    (:max-price filters) (filter #(<= (:product/price %) (:max-price filters))))
         sort-fn  (fn [p] (get p (keyword "product" (name field))))
         sorted   (sort-by sort-fn filtered)]
     (if (= dir :desc) (reverse sorted) sorted))))

(rf/reg-sub
 :categories
 :<- [:db-version]
 (fn [_ _]
   (product-db/categories)))

