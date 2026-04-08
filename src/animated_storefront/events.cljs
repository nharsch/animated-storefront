(ns animated-storefront.events
  (:require [re-frame.core :as rf]
            [animated-storefront.db :as product-db]))

;; -- Initial state ------------------------------------------------------------

(defn default-db []
  (let [mobile? (< (.-innerWidth js/window) 768)]
    {:view           (if mobile? :list :grid)  ;; :grid | :list, mobile defaults to list
     :filters        {}
     :sort           {:field :price :dir :asc}
     :result-ids     nil        ;; when set, grid/list show only these products
     :pdp-product-id nil        ;; when set, PDP modal is open
     :chat           {:messages    []
                      :loading     false
                      :last-search-ids []}  ;; IDs from most recent search_products call
     :chat-open      false      ;; mobile chat panel visibility
     :categories     []         ;; populated after products load
     :all-products   []         ;; all products, cached from DataScript
     :db-version     0}))

;; -- App lifecycle ------------------------------------------------------------

(rf/reg-event-db
 :initialize
 (fn [_ _] (default-db)))

;; -- View ---------------------------------------------------------------------

(rf/reg-event-db
 :change-view
 (fn [db [_ view product-ids]]
   (cond-> (assoc db :view view)
     product-ids       (assoc :result-ids product-ids)
     (not product-ids) (assoc :result-ids nil))))

(rf/reg-event-db
 :set-last-search-ids
 (fn [db [_ ids]]
   (assoc-in db [:chat :last-search-ids] ids)))

;; -- PDP modal ----------------------------------------------------------------

(rf/reg-event-db
 :open-pdp
 (fn [db [_ product-id]]
   (assoc db :pdp-product-id product-id)))

(rf/reg-event-db
 :close-pdp
 (fn [db _]
   (assoc db :pdp-product-id nil)))

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
                                        (remove (fn [[_ v]] (or (nil? v) (= v ""))))
                                        (into {}))))]
     (if had-results?
       (ui-note db' "User applied a manual filter — chat result cleared.")
       db'))))

(rf/reg-event-db
 :clear-filters
 (fn [db _]
   (let [had-results? (seq (:result-ids db))
         db' (assoc db :filters {} :result-ids nil)]
     (if had-results?
       (ui-note db' "User cleared filters and chat result.")
       db'))))

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

(rf/reg-event-db
 :toggle-chat
 (fn [db _]
   (update db :chat-open not)))

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
   (let [all-prods (product-db/all-products)
         ;; Extract categories directly from products to avoid DataScript query issues in release builds
         categories (into #{} (map :category products))]
     (-> db
         (assoc :products-loading false)
         (assoc :categories categories)
         (assoc :all-products all-prods)
         (update :db-version inc)))))
