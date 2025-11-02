(ns datascript-app.core
  (:require [datascript.core :as d]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(def schema {:person/name {:db/unique :db.unique/identity}
             :person/age {}
             :person/friend {:db/valueType :db.type/ref
                            :db/cardinality :db.cardinality/many}})

(defonce db (d/create-conn schema))

(defn add-person! [name age]
  (println "Added person:" name "age:" age)
  (d/transact! db [{:person/id (random-uuid)
                    :person/name name
                    :person/age (js/parseInt age)}]))

(defn get-all-people [db]
  (d/q '[:find ?name ?age
         :where
         [?e :person/name ?name]
         [?e :person/age ?age]]
       db))


(defui add-person-form []
  (let [[name set-name!] (uix/use-state "")
        [age set-age!] (uix/use-state "")
        handle-submit (fn [e]
                        (.preventDefault e)
                        (when (and (seq name) (seq age))
                          (add-person! name age)
                          (set-name! "")
                          (set-age! "")))]
    ($ :div
       ($ :h2 "Add Person")
       ($ :form {:on-submit handle-submit}
          ($ :div
             ($ :label "Name: ")
             ($ :input {:type "text"
                       :value name
                       :on-change #(set-name! (.. % -target -value))}))
          ($ :div
             ($ :label "Age: ")
             ($ :input {:type "number"
                       :value age
                       :on-change #(set-age! (.. % -target -value))}))
          ($ :button {:type "submit"} "Add Person")))))

(defui all-people []
  (let [[people set-people!] (uix/use-state (get-all-people @db))]
    (uix/use-effect
      (fn []
        (let [listener-key (d/listen! db (fn [tx-report]
                                          (println "db state change")
                                          (set-people! (get-all-people @db))))]
          #(d/unlisten! db listener-key)))
      [])
    ($ :div
       ($ :h2 "All People")
       ($ :ul
          (for [[name age] people]
            ($ :li {:key name} name " (age: " age ")"))))))

(defui app []
  ($ :div
     ($ :h1 "DataScript App with UIX2")
     ($ add-person-form)
     ($ all-people)))

(defonce root (uix.dom/create-root (js/document.getElementById "app")))


(defn init []
  (js/console.log "DataScript app with UIX2 starting...")
  (add-person! "Alice" 30)
  (add-person! "Bob" 25)
  (uix.dom/render-root ($ app) root))

(init)
