(ns animated-storefront.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub :view (fn [db _] (:view db)))
(rf/reg-sub :filters (fn [db _] (:filters db)))
(rf/reg-sub :sort (fn [db _] (:sort db)))
(rf/reg-sub :chat (fn [db _] (:chat db)))
(rf/reg-sub :chat-open (fn [db _] (:chat-open db)))
(rf/reg-sub :products-loading (fn [db _] (:products-loading db)))
(rf/reg-sub :db-version (fn [db _] (:db-version db)))
(rf/reg-sub :result-ids (fn [db _] (:result-ids db)))
(rf/reg-sub :pdp-product-id (fn [db _] (:pdp-product-id db)))
(rf/reg-sub :all-products (fn [db _] (:all-products db)))

(rf/reg-sub
 :pdp-product
 :<- [:pdp-product-id]
 :<- [:all-products]
 (fn [[id all-products] _]
   (when id
     (first (filter #(= (:product/id %) id) all-products)))))

(rf/reg-sub
 :products
 :<- [:filters]
 :<- [:sort]
 :<- [:all-products]
 :<- [:result-ids]
 (fn [[filters {:keys [field dir]} all-products result-ids] _]
   (let [base     (if (seq result-ids)
                    ;; pinned result set — preserve order, still apply filters
                    (let [by-id (into {} (map (juxt :product/id identity) all-products))]
                      (keep by-id result-ids))
                    all-products)
         filtered (cond->> base
                    (:category filters)  (filter #(= (:product/category %) (:category filters)))
                    (:max-price filters) (filter #(<= (:product/price %) (:max-price filters))))
         sort-fn  (fn [p] (get p (keyword "product" (name field))))
         sorted   (sort-by sort-fn filtered)]
     (if (= dir :desc) (reverse sorted) sorted))))

(rf/reg-sub
 :categories
 (fn [db _]
   (:categories db)))

