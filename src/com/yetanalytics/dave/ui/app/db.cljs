(ns com.yetanalytics.dave.ui.app.db
  "Handle top-level app state & persistence"
  (:require [re-frame.core :as re-frame]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.dave.datalog :as d]
            [com.yetanalytics.dave.ui.app.nav :as nav]
            [com.yetanalytics.dave.ui.app.picker :as picker]
            [cognitect.transit :as t]
            [datascript.transit :as dt]
            [com.yetanalytics.dave.workbook :as workbook]
            [com.yetanalytics.dave.ui.interceptor :as i]
            [com.cognitect.transit.types :as ty]
            [com.yetanalytics.dave.util.spec :as su]
            [com.yetanalytics.dave.ui.app.dialog :as dialog]
            [com.yetanalytics.dave.ui.app.template :as template])
  (:import [goog.storage Storage]
           [goog.storage.mechanism HTML5LocalStorage]))

;; Make transit UUIDs work with the uuid? spec pred
(extend-type ty/UUID
  cljs.core/IUUID)

(def read-handlers
  dt/read-handlers)

(def write-handlers
  dt/write-handlers)

;; Persistence
(defonce w (t/writer :json {:handlers write-handlers}))
(defonce r (t/reader :json {:handlers read-handlers}))

(defonce storage
  (Storage. (HTML5LocalStorage.)))

(defn storage-get
  "Get a value from storage, if it exists deserialize it from transit."
  [k]
  (when-let [data-str (.get storage k)]
    (t/read r data-str)))

(defn storage-set
  "Set a value in storage, serializing it to transit."
  [k v]
  (.set storage k (t/write w
                           v)))

(defn storage-remove
  "Remove a value from storage"
  [k]
  (.remove storage k))

;; compose state specs
(s/def ::id uuid?)
(s/def ::nav nav/nav-spec)
(s/def ::picker picker/picker-spec)
(s/def ::dialog dialog/dialog-spec)
;; Install an index on workbook
;; These are not present in the base spec so workbooks can stand on their own.
(s/def ::workbook/index
  su/index-spec)

(s/def ::workbooks
  (s/and (s/map-of ::workbook/id
                   (s/merge workbook/workbook-spec
                            (s/keys :req-un [::workbook/index])))
         (comp su/sequential-indices? vals)))

(s/def ::db-version
  #{"0.1.0"})

(def db-state-spec
  (s/keys
   :req-un [::db-version]
   :opt-un [::id
            ::nav
            ::workbooks
            ::dialog
            ]))

;; This will include the default workbooks for DAVE
(def db-default
  {:db-version "0.1.0"
   :workbooks {#uuid "f1d0bd64-0868-43ec-96c6-a51c387f5fc8"
               {:id #uuid "f1d0bd64-0868-43ec-96c6-a51c387f5fc8"
                :title "DAVE Alpha Demo"
                :description "A tour of DAVE Alpha features."
                :index 0
                :data {:title "test dataset"
                       :type :com.yetanalytics.dave.workbook.data/file
                       :uri "data/dave/ds.json"
                       :built-in? true
                       :state {:statement-idx -1
                               :db (d/empty-db)}}
                :analyses {#uuid "609851e5-5bb0-4980-963a-725422312214"
                           {:id    #uuid "609851e5-5bb0-4980-963a-725422312214"
                            :text  "Test Analysis"
                            :index 0
                            :query (:query-1 template/queries)                            
                            :query-data
                            '{:find [?x ?y ?c]
                              :where
                              [[?s :statement/timestamp ?t]
                               [?s :statement.result.score/scaled ?y]
                               [?s :statement/actor ?a]
                               [?a :agent/mbox ?c]
                               [->unix ?t ?x]]}
                            :vega  (:viz-1 template/visualizations)  
                            :visualization
                            {:autosize "fit", :legends [{:fill "color"}], :axes [{:orient "bottom", :scale "x", :labelAngle 60, :labelAlign "left", :labelLimit 112, :labelOverlap true, :labelSeparation -35} {:orient "left", :scale "y"}], :width 500, :scales [{:name "x", :type "time", :range "width", :domain {:data "result", :field "?x"}} {:name "y", :type "linear", :range "height", :nice true, :zero true, :domain {:data "result", :field "?y"}} {:name "color", :type "ordinal", :range "category", :domain {:data "result", :field "?c"}}], :padding 5, :marks [{:type "group", :from {:facet {:name "series", :data "result", :groupby "?c"}}, :marks [{:type "symbol", :from {:data "series"}, :encode {:enter {:size {:value 50}, :x {:scale "x", :field "?x"}, :y {:scale "y", :field "?y"}, :fill {:scale "color", :field "?c"}}}}]}], :$schema "https://vega.github.io/schema/vega/v4.json", :signals [{:name "interpolate", :value "linear"}], :height 200}}}}}})

(s/def ::saved
  (s/keys :req-un [::workbooks]))

(s/fdef load-cofx
  :args (s/cat :cofx
               map?)
  :ret (s/keys ::opt-un [::saved]))

(defn- load-cofx [cofx]
  (let [saved (storage-get "dave.ui.db")]
    (cond
      (and (not-empty saved)
           (s/valid? db-state-spec saved))
      (assoc cofx :saved saved)

      (some? saved) ;; it must not be valid. Let's delete it
      (do
        (.warn js/console "DB invalid! %o" saved)
        (s/explain db-state-spec saved)
        (storage-remove "dave.ui.db")
        cofx)
      ;; otherwise, doesn't add any cofx
      :else cofx)))

(re-frame/reg-cofx
 ::load
 load-cofx)


;; Hold on to the last saved state so we can compare and save
(def last-saved
  (atom nil))

(s/fdef save?
  :args (s/cat :db db-state-spec)
  :ret boolean?)

(defn save?
  "Should we save this DB?"
  [db]
  (if (s/valid? db-state-spec db)
    (and
     (not= @last-saved db)

     ;; TODO: figure out any cases where we canny save
     )
    (.error js/console "DB State Invalid, not saving!" (s/explain-str db-state-spec
                                                                      db))))

(s/fdef save!-fx
  :args (s/cat :db-state db-state-spec))

(defn- save!-fx
  [db-state]
  (let [to-save (dissoc db-state
                        ;; Dissoc ID
                        :id
                        ;; Dissoc nav, as this would force navigation in
                        ;; multi-tab situations
                        :nav
                        ;; picker is ephemeral
                        :picker
                        ;; so is the dialog
                        :dialog
                        ;; Don't save dave.debug state, as it might be huge
                        :debug)]
    (when (save? to-save)
      (do #_(.log js/console "saving to LocalStorage")
          (storage-set "dave.ui.db"
                       to-save)
          (reset! last-saved to-save))
      #_(.log js/console "Skipped save..."))))
(re-frame/reg-fx
 :db/save!
 save!-fx)

(re-frame/reg-fx
 :db/destroy!
 (fn [_]
   (storage-remove "dave.ui.db")))

(re-frame/reg-event-fx
 :db/init
 [(re-frame/inject-cofx ::load)
  i/persist-interceptor]
 (fn [{:keys [saved
              db] :as ctx} [_ instance-id]]
   {:db (merge
         db ;; merge DB so it works with reset!
         saved
         (when-not saved
           (.log js/console "Creating new DAVE ui DB...")
           db-default)
         {:id instance-id})}))

(re-frame/reg-event-fx
 :db/reset!
 (fn [{{:keys [id]} :db
       :as ctx} _]
   {:db/destroy! true
    :dispatch [:db/init id]}))

(re-frame/reg-event-fx
 :db/save!
 (fn [{:keys [db]
       :as ctx} _]
   {:db/save! db}))

(re-frame/reg-event-fx
 :db/save
 (fn [{:keys [db]} _]
   {:dispatch-debounce
    [::save!
     [:db/save!]
     3000]}))



;; Top-level sub for form-2 subs
(re-frame/reg-sub
 :dave/db
 (fn [db _]
   db))

(re-frame/reg-sub
 :db/debug
 (fn [db _]
   db))

(re-frame/reg-sub
 :db/transit-str
 (fn [db _]
   (t/write w db)))

(re-frame/reg-sub
 :db/edn-str
 (fn [db _]
   (pr-str db)))
