(ns datascript-app.core
  (:require [datascript.core :as d]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [datascript-app.mermaid :as mermaid]))


(def app-schema
  {
   :db/ident {:db/unique :db.unique/identity}
   ;; :staff/id {:db/unique :db.unique/identity}
   :staff/name {:db/unique :db.unique/identity}
   :staff/department {:db/valueType :db.type/ref}

   :department/name {:db/unique :db.unique/identity}
   :department/manager {:db/valueType :db.type/ref}

   :app/staff {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}
   :app/departments {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}
   :app/selected-department {:db/valueType :db.type/ref
                             :db/cardinality :db.cardinality/one}
   })

;; RULES: rules allow for query composition and reuse
(def rules '[
             [(manages-department ?manager ?dep)
              [?dep :department/manager ?manager]]
             [(manages-staff ?manager ?staff)
              (manages-department ?manager ?dep)
              [?staff :staff/department ?dep]]
             ])


(defonce db (d/create-conn app-schema))

(defn add-staff! [name dep-name]
  (println "Added staff" name)
  (d/transact! db [{:staff/id (random-uuid)
                    :staff/name name
                    :staff/department [:department/name  dep-name]}]
               ))

(defn add-department!
  ([name]
   (println "Added department" name)
   (d/transact! db [{:department/name name}]))
  ([name dep-manager-name]
   (println "Added department" name "with manager" dep-manager-name)
   (d/transact! db [{:department/name name
                     :department/manager [:staff/name dep-manager-name]}])))

(defn get-all-staff [db]
  (d/q '[:find ?id ?name ?depname
         :where
         [?e :staff/name ?name]
         [?e :staff/id ?id]
         [?e :staff/department ?depid]
         [?depid :department/name ?depname]
         ]
       db))

(defn get-selected-departments [db]
  (d/q '[:find ?name
         :where
         [_ :app/selected-department ?dep]
         [?dep :department/name ?name]
         ]
       db))

(defn get-selected-staff [db]
  (d/q '[:find ?id ?name
         :where
         [?e :staff/name ?name]
         [?e :staff/id ?id]
         [?e :staff/department ?depid]
         [?depid :department/enabled true]
         ]
       db))

(comment
  (get-selected-departments @db)
  (get-selected-staff @db)
  )

(defn get-all-departments [db]
  (d/q '[:find ?name
         :where
         [?e :department/name ?name]
         ]
       db))

(defn set-selected-department! [dep]
  (d/transact! db [{:db/ident :app/singleton
                    :app/selected-department [:department/name dep]}])
  )


(defui add-staff-form []
  (let [[name set-name!] (uix/use-state "")
        [department set-department!] (uix/use-state "")
        handle-submit (fn [e]
                        (.preventDefault e)
                        (when (seq name)
                          (add-staff! name department)
                          ;; TODO: gotta be a better way to reset form
                          (set-name! "")
                          (set-department! "")))]
    ($ :div
       ($ :h2 "Add Staff")
       ($ :form {:on-submit handle-submit}
          ($ :div
             ($ :label "Name: ")
             ($ :input {:type "text"
                        :value name
                        :on-change #(set-name! (.. % -target -value))})
             ($ :label " Department: ")
             ($ :select {:value department
                         :on-change #(set-department! (.. % -target -value))}
                ($ :option {:value ""} "-- Select Department --")
                (for [[dep-name] (get-all-departments @db)]
                  ($ :option {:key dep-name :value dep-name} dep-name)))
             )
          ($ :button {:type "submit"} "Add Staff")))))

(defui add-department-form []
  (let [[name set-name!] (uix/use-state "")
        [manager set-manager] (uix/use-state "")
        handle-submit (fn [e]
                        (.preventDefault e)
                        (cond
                          (and (seq name) (seq manager)) (add-department! name manager)
                          (seq name) (add-department! name))
                        (set-name! "")
                        (set-manager "")
                        )]
    ($ :div
       ($ :h2 "Add Department")
       ($ :form {:on-submit handle-submit}
          ($ :div
             ($ :label "Name: ")
             ($ :input {:type "text"
                        :value name
                        :on-change #(set-name! (.. % -target -value))})
             ($ :label "Manager (optional): ")
             ($ :select {:value manager
                         :on-change #(set-manager (.. % -target -value))}
                ($ :option {:value ""} "-- Select Manager --")
                (for [[_ manager-name _2] (get-all-staff @db)]
                  ($ :option {:key manager-name :value manager-name} manager-name))))

          ($ :button {:type "submit"} "Add Department"))))
  )


(defn department-enabled? [dep-name]
  (boolean
   (ffirst
    (d/q '[:find ?enabled
           :in $ ?dep-name
           :where
           [?e :department/name ?dep-name]
           [?e :department/enabled ?enabled]]
         @db dep-name))))

(comment
  (department-enabled? "IT")
  (department-enabled? "Operations")
  (enabled-deparments)
  )

(defn toggle-department-enabled! [dep-name]
  (if (department-enabled? dep-name)
    (d/transact! db [[:db/retract [:department/name dep-name] :department/enabled true]])
    (d/transact! db [{:department/name dep-name
                      :department/enabled true}])))

(defn enabled-deparments []
  (d/q '[:find ?name
         :where
         [?e :department/name ?name]
         [?e :department/enabled true]]
       @db))



(defui department-pill [{:keys [dep-name]}]
  (let [[enabled set-enabled!] (uix/use-state (department-enabled? dep-name))
        handle-click (fn [e]
                       (.preventDefault e)
                       (js/console.log "Toggling department" dep-name)
                       (toggle-department-enabled! dep-name))]
    (uix/use-effect
     (fn []
       (let [listener-key (d/listen! db (fn [tx-report]
                                          (set-enabled! (department-enabled? dep-name))))]
         #(d/unlisten! db listener-key)))
     [dep-name])
    ($ :button {:on-click handle-click
                :style {:background-color (if enabled "lightgreen" "lightgray")}}
       dep-name)))


;; Generic hook for live queries - automatically updates when db changes
(defn use-live-query [query-fn]
  (let [[results set-results!] (uix/use-state (query-fn @db))]
    (uix/use-effect
     (fn []
       (let [listener-key (d/listen! db (fn [tx-report]
                                          (set-results! (query-fn @db))))]
         #(d/unlisten! db listener-key)))
     [query-fn])
    results))

(defui staff-list []
  (let [staff (use-live-query get-selected-staff)]
    ($ :div
       ($ :h2 "Staff")
       ($ :ul
          (for [[id name] staff]
            ($ :li {:key id} name))))))


(defui all-departments []
  (let [departments (use-live-query get-all-departments)]
    ($ :div
       ($ :h2 "Departments")
       (for [[name] departments]
         ($ :span {:key name :style {:margin "4px"}}
            ($ department-pill {:dep-name name}))))))



(defn get-dep-staff-edges [db]
  (vec
   (d/q '[:find ?department-name ?staff-name
          :where
          [?dep :department/enabled true]
          [?dep :department/name ?department-name]
          [?staff :staff/department ?dep]
          [?manager :staff/name ?manager-name]
          [?staff :staff/name ?staff-name]
          ]
        db)))

(defn get-manager-dep-edges [db]
  (map #(conj % "manages")
   (d/q '[:find ?manager-name ?dep-name
          :where
          [?dep :department/name ?dep-name]
          [?dep :department/manager ?manager-id]
          [?dep :department/enabled true]
          [?manager-id :staff/name ?manager-name]]
        db)))

(comment
  (get-dep-staff-edges @db)
  (get-manager-dep-edges @db)
  )

(defui org-chart []
  (let [dep-staff-edges (use-live-query get-dep-staff-edges)
        manager-edges (use-live-query get-manager-dep-edges)
        edges (concat dep-staff-edges manager-edges)
        ]
    (println "org chart edges" edges)
    ($ :div
       ($ :h3 "Organization Chart")
       ($ mermaid/simple-graph {:edges edges}))))

(defui app []
  ($ :div
     ($ :h1 "Org Chart Visualizer")
     ($ :p "Built with DataScript and UIX2")
     ($ all-departments)
     ;; ($ staff-list)
     ($ org-chart)
     ($ add-staff-form)
     ($ add-department-form)
     ))

(defonce root (uix.dom/create-root (js/document.getElementById "app")))


(comment
  (run! add-department! ["Facilities"
                         "Sales"
                         "Operations"
                         "IT"
                         ])
  (get-all-departments @db)
  (run! add-staff! ["Alice" "Facilities"
                    "Bob" "IT"
                    "Mary" "Sales"
                    ])
  (add-staff! "Alice" "Facilities")
  (get-all-staff @db)
  (set-selected-department! "IT")
  (set-selected-department! nil)
  )


(defn init []
  (js/console.log "starting...")
  (run! add-department! ["Facilities"
                         "Sales"
                         "Operations"
                         "IT"])
  (run! #(apply add-staff! %) [["Alice" "Facilities"]
                               ["Benedict" "Operations"]
                               ["Sonya" "Sales"]
                               ["Bob" "IT"]
                               ["Mary" "Sales"]])
  (d/transact! db [{:department/name "IT"
                    :department/manager [:staff/name "Benedict"]
                    :department/enabled true}])

  (uix.dom/render-root ($ app) root))

(init)
