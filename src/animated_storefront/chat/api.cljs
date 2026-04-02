(ns animated-storefront.chat.api
  (:require [re-frame.core :as rf]
            [animated-storefront.chat.tools :as tools]
            [animated-storefront.config :as config]
            [animated-storefront.db :as product-db]
            [clojure.string :as str]))

(defn current-ui-state []
  {:view     (name @(rf/subscribe [:view]))
   :filters  @(rf/subscribe [:filters])
   :sort     @(rf/subscribe [:sort])
   :categories (vec (product-db/categories))})

(defn system-prompt []
  (let [state (current-ui-state)]
    (str "You are a helpful shopping assistant for an online storefront. "
         "You can control the UI by using the provided tools, and answer customer questions "
         "by querying the product catalog.\n\n"
         "Current UI state:\n"
         (str state) "\n\n"
         "When a customer asks to see products, change the view, or filter results, "
         "use the appropriate tools. Always explain what you're doing in a friendly tone.")))

(defn send-message!
  "Send a message to Claude and handle tool use + text response."
  [user-text]
  (rf/dispatch [:chat/append-message {:role "user" :content user-text}])
  (rf/dispatch [:chat/set-loading true])
  (let [messages @(rf/subscribe [:chat :messages])
        body     (cond-> {:messages (mapv #(select-keys % [:role :content]) messages)
                          :system   (system-prompt)
                          :tools    tools/tool-definitions}
                   config/dev-mode (assoc :model "claude-haiku-4-5-20251001"
                                          :max_tokens 1024))
        headers  (cond-> {"Content-Type"      "application/json"
                          "anthropic-version" "2023-06-01"
                          "anthropic-dangerous-direct-browser-access" "true"}
                   config/dev-mode (assoc "x-api-key" config/api-key))]
    (-> (js/fetch (config/chat-url)
                  (clj->js {:method  "POST"
                            :headers headers
                            :body    (js/JSON.stringify (clj->js body))}))
        (.then #(.json %))
        (.then (fn [resp]
                 (let [resp-clj (js->clj resp :keywordize-keys true)
                       content  (:content resp-clj)
                       ;; Execute all tool calls
                       tool-results (doall
                                     (for [block content
                                           :when (= (:type block) "tool_use")]
                                       (do
                                         (tools/dispatch-tool-call! block)
                                         {:type       "tool_result"
                                          :tool_use_id (:id block)
                                          :content    (str (tools/execute-read-tool block))})))
                       ;; Extract text
                       text-blocks  (filter #(= (:type %) "text") content)]
                   (when (seq text-blocks)
                     (rf/dispatch [:chat/append-message
                                   {:role    "assistant"
                                    :content (str/join "\n" (map :text text-blocks))}]))
                   (rf/dispatch [:chat/set-loading false]))))
        (.catch (fn [err]
                  (js/console.error "Chat API error:" err)
                  (rf/dispatch [:chat/set-loading false]))))))
