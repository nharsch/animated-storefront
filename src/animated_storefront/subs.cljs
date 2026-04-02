(ns animated-storefront.subs
  (:require [re-frame.core :as rf]
            [animated-storefront.db :as product-db]
            [datascript.core :as d]))

(rf/reg-sub :view (fn [db _] (:view db)))
(rf/reg-sub :filters (fn [db _] (:filters db)))
(rf/reg-sub :sort (fn [db _] (:sort db)))
(rf/reg-sub :selected-ids (fn [db _] (:selected-ids db)))
(rf/reg-sub :chat (fn [db _] (:chat db)))
(rf/reg-sub :products-loading (fn [db _] (:products-loading db)))
(rf/reg-sub :db-version (fn [db _] (:db-version db)))

(rf/reg-sub
 :products
 :<- [:filters]
 :<- [:sort]
 :<- [:db-version]
 (fn [[filters {:keys [field dir]} _] _]
   (let [all   (product-db/all-products)
         ;; apply category filter if set
         filtered (if-let [cat (:category filters)]
                    (filter #(= (:product/category %) cat) all)
                    all)
         ;; apply price range if set
         filtered (if-let [max-price (:max-price filters)]
                    (filter #(<= (:product/price %) max-price) filtered)
                    filtered)
         sort-fn  (fn [p] (get p (keyword "product" (name field))))
         sorted   (sort-by sort-fn filtered)]
     (if (= dir :desc) (reverse sorted) sorted))))

(rf/reg-sub
 :categories
 :<- [:db-version]
 (fn [_ _]
   (product-db/categories)))

(rf/reg-sub
 :selected-products
 :<- [:selected-ids]
 (fn [ids _]
   (when (seq ids)
     (d/q '[:find [(pull ?e [*]) ...]
            :in $ [?id ...]
            :where [?e :product/id ?id]]
          @product-db/conn ids))))
