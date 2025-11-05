(ns datascript-app.core
  (:require [datascript.core :as d]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]))



;; TODO:
;;
;; define reusable queries
;; - staff/managed-staff
;; - department/staff
;;
;; staff selection should be multi select
;; keep track of selected state on the department
;; - department/selected true

(def app-schema
  ;; todo: how to define staff as a set of people
  {
   :db/ident {:db/unique :db.unique/identity}
   ;; :staff/id {:db/unique :db.unique/identity}
   :staff/name {:db/unique :db.unique/identity}
   ;; TODO: if possible, I'd like to use the department name as the ref
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

(defn add-department! [name]
  (println "Added department" name)
  (d/transact! db [{:department/name name}]))

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

(defn clear-selected-department! []
  (d/transact! db [[:db/retract [:db/ident :app/singleton] :app/selected-department]]))

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



(defui staff-list []
  (let [[staff set-staff!] (uix/use-state (get-selected-staff @db))]
    (uix/use-effect
     (fn []
       ;; TODO: should only recalculate this when the query changes
       (let [listener-key (d/listen! db (fn [tx-report]
                                          (set-staff! (get-selected-staff @db))))]
         #(d/unlisten! db listener-key)))
     [])
    ($ :div
       ($ :h2 "Staff")
       ($ :ul
          (for [[id name] staff]
            ($ :li {:key id} name))))))


(defui all-departments []
  (let [[departments set-departments!] (uix/use-state (get-all-departments @db))]
    (uix/use-effect
     (fn []
       ;; TODO: should only recalculate this when the query changes
       (let [listener-key (d/listen! db (fn [tx-report]
                                          (set-departments! (get-all-departments @db))))]
         #(d/unlisten! db listener-key)))
     [])
    ($ :div
       ($ :h2 "Departments")
       ($ :ul
          (for [[name] departments]
            ($ :li {:key name} ($ department-pill {:dep-name name})))))))

(defui app []
  ($ :div
     ($ :h1 "DataScript App with UIX2")
     ($ all-departments)
     ($ staff-list)
     ($ add-staff-form)
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
  ;; TODO: but this does not
  (add-staff! "Alice" "Facilities")
  (get-all-staff @db)
  (set-selected-department! "IT")
  (set-selected-department! nil)
  (d/q '[:find ?name
         :where
         [_ :app/selected-department ?dep]
         [?dep :department/name ?name]
         ]
       @db)
  ;; TODO: Claude, is it possible to make these composable?
  (d/q '[:find ?department-name
         :where
         [?manager-id :staff/name "Benedict"]
         [?dep :department/manager ?manager-id]
         [?dep :department/name ?department-name]
         ]
       @db)
  ;; TODO: Claude, I'm doing the above query again here, I'd like to do something like :staff/managed-deps
  (d/q '[:find ?staff-name
         :where
         [?manager-id :staff/name "Benedict"]
         [?dep :department/manager ?manager-id]
         [?staff-id :staff/department ?dep]
         [?staff-id :staff/name ?staff-name]]
       @db)
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
                    :department/manager [:staff/name "Benedict"]}])
  (uix.dom/render-root ($ app) root))

(init)
