(ns animated-storefront.events
  (:require [re-frame.core :as rf]
            [animated-storefront.db :as product-db]))

;; -- Initial state ------------------------------------------------------------

(def default-db
  {:view        :grid        ;; :grid | :list | :compare | :pdp
   :filters     {}
   :sort        {:field :price :dir :asc}
   :selected-ids []          ;; for compare / pdp
   :result-ids  nil          ;; when set, grid/list show only these products
   :active-query nil         ;; the datalog query that produced current result-ids
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
     product-ids       (assoc :result-ids product-ids)
     (not product-ids) (assoc :result-ids nil)
     (#{:compare :pdp} view) (assoc :selected-ids product-ids))))

(rf/reg-event-db
 :set-active-query
 (fn [db [_ query]]
   (assoc db :active-query query)))

;; -- Filters ------------------------------------------------------------------

(defn ui-note [db text]
  (update-in db [:chat :messages] conj {:role "ui" :content text}))

(rf/reg-event-db
 :change-filters
 (fn [db [_ new-filters]]
   (let [had-results? (seq (:result-ids db))
         db' (-> db
                 (assoc :result-ids nil)
                 (update :filters #(->> (merge % new-filters)
                                        (remove (fn [[_ v]] (nil? v)))
                                        (into {}))))]
     (if had-results?
       (ui-note (assoc db' :active-query nil) "User applied a manual filter — chat result cleared.")
       db'))))

(rf/reg-event-db
 :clear-filters
 (fn [db _]
   (let [had-results? (seq (:result-ids db))
         db' (assoc db :filters {} :result-ids nil :active-query nil)]
     (if had-results?
       (ui-note db' "User cleared filters and chat result.")
       db'))))

;; -- Sort ---------------------------------------------------------------------

(rf/reg-event-db
 :change-sort
 (fn [db [_ field dir]]
   (assoc db :sort {:field field :dir dir} :result-ids nil :active-query nil)))

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
