(ns animated-storefront.chat.api
  (:require [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [animated-storefront.chat.tools :as tools]
            [animated-storefront.config :as config]
            [animated-storefront.db :as product-db]
            [clojure.string :as str]))

(defn visible-products []
  (let [db         @rf-db/app-db
        result-ids (seq (:result-ids db))
        filters    (:filters db)
        all        (product-db/all-products)
        base       (if result-ids
                     (let [by-id (into {} (map (juxt :product/id identity) all))]
                       (keep by-id result-ids))
                     all)]
    (cond->> base
      (:category filters)  (filter #(= (:product/category %) (:category filters)))
      (:max-price filters) (filter #(<= (:product/price %) (:max-price filters))))))

(defn current-ui-state []
  (let [db       @rf-db/app-db
        visible  (visible-products)
        pdp-id   (:pdp-product-id db)
        pdp-product (when pdp-id
                      (first (filter #(= (:product/id %) pdp-id)
                                     (product-db/all-products))))]
    {:view          (name (:view db))
     :filters       (:filters db)
     :sort          (:sort db)
     :result-ids    (:result-ids db)
     :visible       visible
     :pdp-product   pdp-product
     :categories    (vec (product-db/categories))}))

;; TODO: maybe put this in a seperate markdown file? would need templating...
(defn format-product [p]
  (str (:product/title p) " ($" (.toFixed (:product/price p) 2) ", ★" (:product/rating p) ", id:" (:product/id p) ")"))

(defn system-prompt []
  (let [state   (current-ui-state)
        visible (:visible state)]
    (str "You are a helpful shopping assistant for an online storefront. "
         "You can control the UI by using the provided tools, and answer customer questions "
         "by querying the product catalog.\n\n"
         "## Current UI state\n"
         "view: " (:view state) "\n"
         "filters: " (str (:filters state)) "\n"
         "sort: " (str (:sort state)) "\n"
         (if (seq (:result-ids state))
           (str "pinned-result-ids: " (str (:result-ids state)) "\n"
                (when (<= (count visible) 20)
                  (str "on screen (" (count visible) " products): "
                       (str/join ", " (map format-product visible)) "\n")))
           (str "showing all products (" (count visible) " total)\n"))
         (when (:pdp-product state)
           (str "customer is viewing: " (format-product (:pdp-product state)) "\n"))
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
         "After a datalog_query, if you want to show the results in the UI, extract the :product/id "
         "values from the results and pass them as product_ids to change_view. Do not call change_view "
         "without product_ids after a datalog_query — without IDs the UI will show all products.\n"
         "NEVER pass product_ids to change_view unless they came directly from a datalog_query or "
         "query_products result in this conversation. Never guess, invent, or infer product IDs.\n\n"
         "Narrowing within a pinned result set: when pinned-result-ids are set and the customer wants "
         "to narrow further (e.g. 'just show me drinks' within groceries), use datalog_query with "
         ":in $ [?id ...] to query within that set, then call change_view with the filtered ids.\n"
         "Example — filter pinned set [42 39 29 34 32] to only beverages:\n"
         "query: \"[:find [(pull ?e [:product/id :product/title :product/price :product/category]) ...] "
         ":in $ [?id ...] :where [?e :product/id ?id] [?e :product/tags \\\"beverages\\\"]]\"\n"
         "Pass the pinned-result-ids as the second argument (after $) in the query input. "
         "Do NOT pass rules when using :in $ [?id ...] — just the query.\n\n"
         "## Behavior\n"
         "- By default, always show products on screen when you find them. Use search_and_show to find "
         "and display in one step, or use search_products to review results first, then show_results "
         "to display them — show_results always uses the last search_products results, no IDs needed.\n"
         "- When adding more items to what's already on screen, use search_and_show or search_products "
         "+ show_results — both automatically join with what's already pinned. Never manually copy or "
         "combine product IDs.\n"
         "- When the customer doesn't want to see a specific item, use remove_from_view with the "
         "product name. Never reconstruct the remaining IDs manually with change_view.\n"
         "- Use tools to change the UI when the customer wants to browse or navigate.\n"
         "- When the customer says 'show me X' or 'can you show me', you MUST actually filter the UI — "
         "use change_filters for simple category/price filters, or datalog_query + change_view for anything else. "
         "Calling change_view without product_ids when no filter is set does nothing useful.\n"
         "- Use search_products first when the customer names a specific product, ingredient, or item — "
         "it's the simplest way to confirm something exists and get its ID. Use datalog_query for "
         "complex or cross-category searches.\n"
         "- If search_products returns no results or only irrelevant results, you MUST try at least "
         "2 more searches with alternate or more specific terms before telling the customer something "
         "is unavailable. Break broad terms into specifics: 'citrus' → search 'lemon', then 'lime', "
         "then 'orange'. 'seafood' → search 'fish', then 'shrimp'. Always exhaust specific synonyms "
         "before concluding an item doesn't exist.\n"
         "- When the customer wants to narrow or filter what's visible, always update the UI with "
         "change_view — never just describe the subset in text without changing what's on screen.\n"
         "- When the customer asks for the 'best', 'top rated', 'cheapest', or wants results ordered, "
         "use change_sort to reorder what's on screen. Sort applies within the current result set — "
         "it does not clear pinned results.\n"
         "- When a customer asks about a specific product or wants to see its details, use open_pdp "
         "with product_title (e.g. {product_title: \"Citrus Squeezer Yellow\"}) — this is safer than "
         "product_id and avoids ID errors. Only use product_id if you just received it from a tool result.\n"
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
                                                (js/console.log "[tool-call]" (:name block) (clj->js (:input block)))
                                                (tools/dispatch-tool-call! block)
                                                (let [result (str (tools/execute-read-tool block))]
                                                  (js/console.log "[tool-result]" (:name block) result)
                                                  {:type        "tool_result"
                                                   :tool_use_id (:id block)
                                                   :content     result}))
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
  (let [messages (->> (:messages (:chat @rf-db/app-db))
                      (remove #(= (:role %) "ui"))
                      (mapv #(select-keys % [:role :content])))]
    (run-agentic-loop! messages 0)))
