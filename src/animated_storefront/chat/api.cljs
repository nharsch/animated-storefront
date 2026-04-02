(ns animated-storefront.chat.api
  (:require [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [animated-storefront.chat.tools :as tools]
            [animated-storefront.config :as config]
            [animated-storefront.db :as product-db]
            [clojure.string :as str]))

(defn current-ui-state []
  (let [db @rf-db/app-db]
    {:view       (name (:view db))
     :filters    (:filters db)
     :sort       (:sort db)
     :result-ids (:result-ids db)
     :categories (vec (product-db/categories))}))

;; TODO: maybe put this in a seperate markdown file? would need templating...
(defn system-prompt []
  (let [state (current-ui-state)]
    (str "You are a helpful shopping assistant for an online storefront. "
         "You can control the UI by using the provided tools, and answer customer questions "
         "by querying the product catalog.\n\n"
         "## Current UI state\n"
         "view: " (:view state) "\n"
         "filters: " (str (:filters state)) "\n"
         "sort: " (str (:sort state)) "\n"
         (when (seq (:result-ids state))
           (str "pinned-result-ids: " (str (:result-ids state)) "\n"))
         "\n"
         "## Product catalog\n"
         "Products have these attributes: title, price, category, rating, tags (many), thumbnail, stock.\n"
         "Available categories: " (str/join ", " (:categories state)) "\n\n"
         "## DataScript schema\n"
         "Entity attributes: :product/id (int), :product/title (str), :product/description (str), "
         ":product/price (float), :product/category (str), :product/rating (float), "
         ":product/stock (int), :product/thumbnail (str), :product/tags (many str)\n\n"
         "Simple query example:\n"
         "[:find [(pull ?e [:product/id :product/title :product/price :product/category]) ...]\n"
         " :where [?e :product/category \"mens-watches\"]]\n\n"
         "Rules example (for cross-category concepts):\n"
         "query: \"[:find [(pull ?e [:product/id :product/title :product/category]) ...] :in $ % :where (wearable? ?e)]\"\n"
         "rules: \"[[(wearable? ?e) [?e :product/category \\\"mens-shoes\\\"]]\n"
         "         [(wearable? ?e) [?e :product/category \\\"mens-shirts\\\"]]\n"
         "         [(wearable? ?e) [?e :product/tags ?t] [(contains? #{\\\"clothing\\\" \\\"footwear\\\" \\\"watches\\\"} ?t)]]]\"\n\n"
         "Always pull at minimum :product/id, :product/title, :product/price, :product/category.\n"
         "Review datalog_query results before changing the UI — only proceed if results match intent.\n\n"
         "## Behavior\n"
         "- Use tools to change the UI when the customer wants to browse or navigate.\n"
         "- Use datalog_query for complex or cross-category searches; query_products for simple lookups.\n"
         "- When a customer asks about a specific product or type (e.g. 'is the nail polish good?'), "
         "always query the catalog first and answer based on what you find. Never ask for clarification "
         "when you can just look it up.\n"
         "- Always explain what you're doing in a friendly, concise tone.")))

(defn fetch-claude [messages]
  (let [body    (cond-> {:messages messages
                         :system   (system-prompt)
                         :tools    tools/tool-definitions}
                  config/dev-mode (assoc :model "claude-haiku-4-5-20251001"
                                         :max_tokens 1024))
        headers (cond-> {"Content-Type"      "application/json"
                         "anthropic-version" "2023-06-01"
                         "anthropic-dangerous-direct-browser-access" "true"}
                  config/dev-mode (assoc "x-api-key" config/api-key))]
    (-> (js/fetch (config/chat-url)
                  (clj->js {:method "POST" :headers headers
                            :body   (js/JSON.stringify (clj->js body))}))
        (.then #(.json %)))))

(defn run-agentic-loop!
  "Recursively calls Claude, executing tool calls and feeding results back
  until stop_reason is end_turn or max iterations are reached."
  [messages iterations]
  (if (> iterations 5)
    (rf/dispatch [:chat/set-loading false])
    (-> (fetch-claude messages)
        (.then (fn [resp]
                 (let [resp-clj    (js->clj resp :keywordize-keys true)
                       content     (:content resp-clj)
                       stop-reason (:stop_reason resp-clj)
                       text-blocks (filter #(= (:type %) "text") content)
                       tool-blocks (filter #(= (:type %) "tool_use") content)]
                   ;; Append any text to chat
                   (when (seq text-blocks)
                     (rf/dispatch [:chat/append-message
                                   {:role    "assistant"
                                    :content (str/join "\n" (map :text text-blocks))}]))
                   (if (and (= stop-reason "tool_use") (seq tool-blocks))
                     ;; Execute tools, collect results, loop
                     (let [tool-results (mapv (fn [block]
                                                (tools/dispatch-tool-call! block)
                                                {:type        "tool_result"
                                                 :tool_use_id (:id block)
                                                 :content     (str (tools/execute-read-tool block))})
                                              tool-blocks)
                           next-messages (conj messages
                                               {:role "assistant" :content content}
                                               {:role "user"      :content tool-results})]
                       (run-agentic-loop! next-messages (inc iterations)))
                     ;; Done
                     (rf/dispatch [:chat/set-loading false])))))
        (.catch (fn [err]
                  (js/console.error "Chat API error:" err)
                  (rf/dispatch [:chat/set-loading false]))))))

(defn send-message! [user-text]
  (rf/dispatch-sync [:chat/append-message {:role "user" :content user-text}])
  (rf/dispatch [:chat/set-loading true])
  (let [messages (mapv #(select-keys % [:role :content])
                       (:messages (:chat @rf-db/app-db)))]
    (run-agentic-loop! messages 0)))
