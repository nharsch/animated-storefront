(ns animated-storefront.effects
  (:require [re-frame.core :as rf]))

(rf/reg-fx
 :fetch-products
 (fn [_]
   (-> (js/fetch "https://dummyjson.com/products?limit=100&select=id,title,description,price,category,thumbnail,rating,stock,tags,brand")
       (.then #(.json %))
       (.then (fn [resp]
                (let [products (-> (js->clj resp :keywordize-keys true) :products)]
                  (rf/dispatch [:products-loaded products]))))
       (.catch (fn [err]
                 (js/console.error "Failed to load products:" err)
                 (rf/dispatch [:products-loaded []]))))))
