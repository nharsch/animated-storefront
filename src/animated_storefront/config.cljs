(ns animated-storefront.config)

(goog-define api-key "")
(goog-define dev-mode true)

(def anthropic-url "https://api.anthropic.com/v1/messages")
(goog-define proxy-url "/api/chat")

(defn chat-url [] (if dev-mode anthropic-url proxy-url))
