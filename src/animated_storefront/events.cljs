(ns animated-storefront.events
  (:require [re-frame.core :as rf]
            [animated-storefront.db :as product-db]))

;; -- Initial state ------------------------------------------------------------

(def default-db
  {:view        :grid        ;; :grid | :list | :compare | :pdp
   :filters     {}
   :sort        {:field :price :dir :asc}
   :selected-ids []          ;; for compare / pdp
   :chat        {:messages [] :loading false}
   :db-version  0})

;; -- App lifecycle ------------------------------------------------------------

(rf/reg-event-db
 :initialize
 (fn [_ _] default-db))

;; -- View ---------------------------------------------------------------------

(rf/reg-event-db
 :change-view
 (fn [db [_ view product-ids]]
   (cond-> (assoc db :view view)
     product-ids (assoc :selected-ids product-ids))))

;; -- Filters ------------------------------------------------------------------

(rf/reg-event-db
 :change-filters
 (fn [db [_ new-filters]]
   (update db :filters #(->> (merge % new-filters)
                             (remove (fn [[_ v]] (nil? v)))
                             (into {})))))

(rf/reg-event-db
 :clear-filters
 (fn [db _]
   (assoc db :filters {})))

;; -- Sort ---------------------------------------------------------------------

(rf/reg-event-db
 :change-sort
 (fn [db [_ field dir]]
   (assoc db :sort {:field field :dir dir})))

;; -- Chat ---------------------------------------------------------------------

(rf/reg-event-db
 :chat/append-message
 (fn [db [_ message]]
   (update-in db [:chat :messages] conj message)))

(rf/reg-event-db
 :chat/set-loading
 (fn [db [_ loading?]]
   (assoc-in db [:chat :loading] loading?)))

;; -- Product data -------------------------------------------------------------

(rf/reg-event-fx
 :load-products
 (fn [{:keys [db]} _]
   {:db       (assoc db :products-loading true)
    :fetch-products nil}))

(rf/reg-event-db
 :products-loaded
 (fn [db [_ products]]
   (product-db/load-products! products)
   (-> db
       (assoc :products-loading false)
       (update :db-version inc))))
