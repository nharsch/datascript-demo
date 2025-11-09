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

(defui org-chart-graph [{:keys [staff departments edges]}]
  (let [ref (uix/use-ref nil)
        ;; Create sets for quick lookup
        staff-set (set staff)
        dept-set (set departments)
        ;; Helper to format node with appropriate shape
        format-node (fn [node-name]
                      (cond
                        (dept-set node-name) (str node-name "([" node-name "])") ;; Stadium shape for departments
                        (staff-set node-name) (str node-name "[" node-name "]") ;; Rectangle for staff
                        :else (str node-name "[" node-name "]"))) ;; Default to rectangle
        ;; Convert edges to Mermaid syntax
        mermaid-code (str "graph TD\n"
                          (when (seq edges)
                            (clojure.string/join "\n"
                                                 (for [[from to annot] edges]
                                                   (if annot
                                                     (str "    " (format-node from) "--" annot "-->" (format-node to) ";")
                                                     (str "    " (format-node from) "-->" (format-node to) ";"))))))]
    (println "org-chart-graph mermaid-code:" mermaid-code)
    ;; Re-render mermaid when code changes
    (uix/use-effect
     (fn []
       (when-let [elem @ref]
         (-> (.render mermaid (str "mermaid-" (random-uuid)) mermaid-code)
             (.then (fn [result]
                      (set! (.-innerHTML elem) (.-svg result)))))))
     [mermaid-code])
    ($ :div {:ref ref})))
