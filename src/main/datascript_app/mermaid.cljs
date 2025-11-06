(ns datascript-app.mermaid
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            ["mermaid" :default mermaid]))

;; Initialize mermaid once
(.initialize mermaid #js {:startOnLoad false})

(defui simple-graph [{:keys [edges]}]
  (let [ref (uix/use-ref nil)
        ;; Convert edges to a stable string representation for comparison
        mermaid-code (str "graph TD\n"
                         (when (seq edges)
                           (clojure.string/join "\n"
                             (for [[from to annot] edges]
                               (if annot
                                 (str "    " from "--" annot "-->" to ";")
                                 (str "    " from "-->" to ";"))))))]
    (println "simple-graph mermaid-code:" mermaid-code)
    ;; Re-render mermaid when code changes
    (uix/use-effect
     (fn []
       (when-let [elem @ref]
         (-> (.render mermaid (str "mermaid-" (random-uuid)) mermaid-code)
             (.then (fn [result]
                      (set! (.-innerHTML elem) (.-svg result)))))))
     [mermaid-code])
    ($ :div {:ref ref})))
