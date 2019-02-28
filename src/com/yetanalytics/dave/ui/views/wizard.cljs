(ns com.yetanalytics.dave.ui.views.wizard
  (:require [re-frame.core :refer [dispatch subscribe]]
            [com.yetanalytics.dave.ui.views.form.textfield
             :as textfield]
            [com.yetanalytics.dave.ui.views.form.select
             :as select]))

;; Some form helpers
(defn wizard-field
  "Simple text field for string fields"
  [field-key label]
  [:div.wizard-field
   [textfield/textfield
    :label label
    :value @(subscribe [:wizard.form/field field-key])
    :on-change (fn [e]
                 (dispatch [:wizard.form/set-field!
                            field-key
                            (-> e .-target .-value)]))]
   [textfield/helper-text
    :text (str @(subscribe [:wizard.form.field/problem field-key]))
    :persistent? true
    :validation? true]])

(defn wizard-textarea
  "Simple text area"
  [field-key label]
  [:div.wizard-field
   [textfield/textarea
    :label label
    :value @(subscribe [:wizard.form/field field-key])
    :on-change (fn [e]
                 (dispatch [:wizard.form/set-field!
                            field-key
                            (-> e .-target .-value)]))]
   [textfield/helper-text
    :text (str @(subscribe [:wizard.form.field/problem field-key]))
    :persistent? true
    :validation? true]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Structure ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;    wizard-progress
;;    wizard-header
;; wizard-form | wizard-info
;;     wizard-problems


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Step 1: Workbook ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; top level, workbook
(defn step-1-header
  []
  [:div.wizard-header
   [:h1 "Step 1: Create a Workbook"]])

(defn step-1-form
  []
  [:div.wizard-form
   [wizard-field :title "Title"]
   [wizard-textarea :description "Description"]])

(defn step-1-info
  []
  [:div.wizard-info
   [:p "Give your workbook a name and a short description."]])

(defn step-1-problems
  []
  [:div.wizard-problems
   [:p (if @(subscribe [:wizard.form/spec-errors])
         "Please fill out all fields."
         "Looks good, click NEXT to continue!")]])

(defn step-1-workbook
  []
  [:div.wizard.wizard-workbook
   [:div ;; inner
    [step-1-header]
    [step-1-form] [step-1-info]
    [step-1-problems]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Step 2: Data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn step-2-header
  []
  [:div.wizard-header
   [:h1 "Step 2: Select A Data Source"]])

(defn step-2-form
  []
  (let [data-type @(subscribe [:wizard.form/field :type])]
    (cond-> [:div.wizard-form
             [:button
              {:on-click #(dispatch [:wizard.data/offer-picker])}
              (if data-type
                "Choose Another Data Source"
                "Choose Data Source")]]
      (= :com.yetanalytics.dave.workbook.data/lrs
         data-type)
      (conj [:h2 "LRS Data Source"]
            [wizard-field :title "LRS Title"]
            [wizard-field :endpoint "LRS Endpoint"]
            [wizard-field [:auth :username] "HTTP Basic Auth Username"]
            [wizard-field [:auth :password] "HTTP Basic Auth Password"])
      (= :com.yetanalytics.dave.workbook.data/file
         data-type)
      (conj [:h2 "DAVE Test Dataset"]))))

(defn step-2-info
  []
  [:div.wizard-info
   "Select a source for the xAPI data that you want to consider in your workbook."])

(defn step-2-problems
  []
  [:div.wizard-problems
   [:p
    (let [data-type @(subscribe [:wizard.form/field :type])
          spec-errors @(subscribe [:wizard.form/spec-errors])]
      (cond
        (nil? data-type) "Please select a data source using the button above."
        spec-errors "Please fill out all fields."
        :else (cond-> "Looks good, click NEXT to continue."
                (= :com.yetanalytics.dave.workbook.data/lrs
                   data-type)
                (str " DAVE will attempt to contact the LRS before proceeding."))))]])

(defn step-2-data
  []
  [:div.wizard.wizard-data
   [:div
    [step-2-header]
    [step-2-form] [step-2-info]
    [step-2-problems]]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Step 3: Question ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn step-3-header
  []
  [:div.wizard-header
   [:h1 "Step 3: Ask a Question"]])

(defn step-3-info
  []
  [:div.wizard-info
   "Write out the question you would like to answer, and select a function to answer it."])

(defn step-3-form
  []
  [:div.wizard-form
   [wizard-field :text "Question Text"]
   [:button
    {:on-click #(dispatch [:wizard.question.function/offer-picker])}
    (if-not @(subscribe [:wizard.form/field :function])
      "Choose A Function"
      "Choose Another Function")]])

(defn step-3-problems
  []
  [:div.wizard-problems
   (str @(subscribe [:wizard.form/field []]))])

(defn step-3-question
  []
  [:div.wizard.wizard-question
   [:div
    [step-3-header]
    [step-3-form] [step-3-info]
    [step-3-problems]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Step 4: Vis ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn step-4-visualization-form
  []
  [:div.wizard-form
   [wizard-field :title "Title"]])
(defn step-4-visualization
  []
  [:div.wizard.wizard-visualization "vis"
   [step-4-visualization-form]
   [:button
    {:on-click #(dispatch [:wizard.question.visualization/offer-picker])}
    "Choose Visualization"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Step 5: Done ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn step-5-done
  []
  [:div.wizard.wizard-done "done"])

(defn wizard
  [_]
  [:div.wizard-container
   (case @(subscribe [:wizard/step])
     :com.yetanalytics.dave.ui.app.wizard/workbook
     [step-1-workbook]
     :com.yetanalytics.dave.ui.app.wizard/data
     [step-2-data]
     :com.yetanalytics.dave.ui.app.wizard/question
     [step-3-question]
     :com.yetanalytics.dave.ui.app.wizard/visualization
     [step-4-visualization]
     :com.yetanalytics.dave.ui.app.wizard/done
     [step-5-done])
   #_[:p (str @(subscribe [:wizard.form/spec-errors]))]])
