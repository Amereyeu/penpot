;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2015-2020 Andrey Antukh <niwi@niwi.nz>
;; Copyright (c) 2015-2020 Juan de la Cruz <delacruzgarciajuan@gmail.com>

(ns uxbox.main.ui.dashboard.library
  (:require
   [lentes.core :as l]
   [rumext.alpha :as mf]
   [cuerdas.core :as str]
   [uxbox.util.router :as rt]
   [uxbox.util.i18n :as i18n :refer [t tr]]
   [uxbox.util.color :as uc]
   [uxbox.util.dom :as dom]
   [uxbox.main.data.library :as dlib]
   [uxbox.main.data.icons :as dico]
   [uxbox.main.data.images :as dimg]
   [uxbox.main.data.colors :as dcol]
   [uxbox.builtins.icons :as i]
   [uxbox.main.store :as st]
   [uxbox.main.refs :as refs]
   [uxbox.main.ui.components.context-menu :refer [context-menu]]
   [uxbox.main.ui.modal :as modal]
   [uxbox.main.ui.confirm :refer [confirm-dialog]]
   [uxbox.main.ui.colorpicker :refer [colorpicker most-used-colors]]
   [uxbox.main.ui.components.editable-label :refer [editable-label]]
   ))

(mf/defc modal-create-color
  [{:keys [on-accept on-cancel] :as ctx}]
  (let [state (mf/use-state { :current-color "#406280" })]
    (letfn [(accept [event]
              (dom/prevent-default event)
              (modal/hide!)
              (when on-accept (on-accept (:current-color @state))))

            (cancel [event]
              (dom/prevent-default event)
              (modal/hide!)
              (when on-cancel (on-cancel)))]
      [:div.modal-create-color
       [:h3.modal-create-color-title (tr "modal.create-color.new-color")]
       [:& colorpicker {:value (:current-color @state)
                        :colors (into-array @most-used-colors)
                        :on-change #(swap! state assoc :current-color %)}]
     
       [:input.btn-primary {:type "button"
                            :value (tr "ds.button.save")
                            :on-click accept}]

       [:a.close {:href "#" :on-click cancel} i/close]])))

(defn create-library [section team-id]
  (let [name (str (str (str/title (name section)) " " (gensym "Library ")))]
    (st/emit! (dlib/create-library section team-id name))))

(defmulti create-item (fn [x _ _] x))

(defmethod create-item :icons [_ library-id data]
  (let [files (->> data
                  (dom/get-target)
                  (dom/get-files)
                  (array-seq))]
    (st/emit! (dico/create-icons library-id files))))

(defmethod create-item :images [_ library-id data]
  (let [files (->> data
                  (dom/get-target)
                  (dom/get-files)
                  (array-seq))]
    (st/emit! (dimg/create-images library-id files))))

(defmethod create-item :palettes [_ library-id]
  (letfn [(dispatch-color [color]
            (st/emit! (dcol/create-color library-id color)))]
    (modal/show! modal-create-color {:on-accept dispatch-color})))

(mf/defc library-header
  [{:keys [section team-id] :as props}]
  (let [icons? (= section :icons)
        images? (= section :images)
        palettes? (= section :palettes)
        locale (i18n/use-locale)]
    [:header#main-bar.main-bar
     [:h1.dashboard-title "Libraries"]
     [:nav.library-header-navigation
      [:a.library-header-navigation-item
       {:class-name (when icons? "current")
        :on-click #(st/emit! (rt/nav :dashboard-library-icons-index {:team-id team-id}))}
       (t locale "dashboard.library.menu.icons")]
      [:a.library-header-navigation-item
       {:class-name (when images? "current")
        :on-click #(st/emit! (rt/nav :dashboard-library-images-index {:team-id team-id}))}
       (t locale "dashboard.library.menu.images")]
      [:a.library-header-navigation-item
       {:class-name (when palettes? "current")
        :on-click #(st/emit! (rt/nav :dashboard-library-palettes-index {:team-id team-id}))}
       (t locale "dashboard.library.menu.palettes")]]]))

(mf/defc library-sidebar
  [{:keys [section items team-id library-id]}]
  (let [locale (i18n/use-locale)]
    [:aside.library-sidebar
     [:button.library-sidebar-add-item
      {:type "button"
       :on-click #(create-library section team-id)}
      (t locale (str "dashboard.library.add-library." (name section)))]
     [:ul.library-sidebar-list
      (for [item items]
        [:li.library-sidebar-list-element
         {:key (:id item)
          :class-name (when (= library-id (:id item)) "current")
          :on-click
          (fn []
            (let [path (keyword (str "dashboard-library-" (name section)))]
              (dico/fetch-icon-library (:id item))
              (st/emit! (rt/nav path {:team-id team-id :library-id (:id item)}))))}
         [:& editable-label {:value (:name item)
                             :on-change #(st/emit! (dlib/rename-library section library-id %))}]
         ])]]))

(mf/defc library-top-menu
  [{:keys [selected section library-id team-id]}]
  (let [state (mf/use-state {:is-open false
                             :editing-name false})
        locale (i18n/use-locale)
        stop-editing #(swap! state assoc :editing-name false)]
    [:header.library-top-menu
     [:div.library-top-menu-current-element
      [:& editable-label {:edit (:editing-name @state)
                          :on-change #(do
                                        (stop-editing)
                                        (st/emit! (dlib/rename-library section library-id %)))
                          :on-cancel #(swap! state assoc :editing-name false)
                          :class-name "library-top-menu-current-element-name"
                          :value (:name selected)}]
      [:a.library-top-menu-current-action
       { :on-click #(swap! state update :is-open not)}
       [:span i/arrow-down]]
      [:& context-menu
       {:show (:is-open @state)
        :on-close #(swap! state update :is-open not)
        :options [[(t locale "ds.button.rename")
                   #(swap! state assoc :editing-name true)]

                  [(t locale "ds.button.delete")
                   #(let [path (keyword (str "dashboard-library-" (name section) "-index"))]
                      (do
                        (st/emit! (dlib/delete-library section library-id))
                        (st/emit! (rt/nav path {:team-id team-id}))))]]}]]

     [:div.library-top-menu-actions
      [:a.library-top-menu-actions-delete i/trash]

      (if (= section :palettes)
        [:button.btn-dashboard
         {:on-click #(create-item section library-id)}
         (t locale (str "dashboard.library.add-item." (name section)))]

        [:*
         [:label {:for "file-upload" :class-name "btn-dashboard"}
          (t locale (str "dashboard.library.add-item." (name section)))]
         [:input {:on-change #(create-item section library-id %)
                  :id "file-upload"
                  :type "file"
                  :multiple true
                  :accept (case section
                            :images "image"
                            :icons "image/svg+xml"
                            "")
                  :style {:display "none"}}]])]]))

(mf/defc library-icon-card
  [{:keys [id name url content metadata library-id]}]
  (let [locale (i18n/use-locale)
        state (mf/use-state {:is-open false})]
    [:div.library-card.library-icon
     [:div.input-checkbox.check-primary
      [:input {:type "checkbox"
               :id (str "icon-" id)
               :on-change #(println "toggle-selection")
               #_(:checked false)}]
      [:label {:for (str "icon-" id)}]]
     [:div.library-card-image
      #_[:object { :data url :type "image/svg+xml" }]
      [:svg {:view-box (->> metadata :view-box (str/join " "))
             :width (:width metadata)
             :height (:height metadata) 
             :dangerouslySetInnerHTML {:__html content}}]]
     
     [:div.library-card-footer
      [:div.library-card-footer-name name]
      [:div.library-card-footer-timestamp "Less than 5 seconds ago"]
      [:div.library-card-footer-menu
       { :on-click #(swap! state update :is-open not) }
       i/actions]
      [:& context-menu
       {:show (:is-open @state)
        :on-close #(swap! state update :is-open not)
        :options [[(t locale "ds.button.delete")
                   #(st/emit! (dlib/delete-item :icons library-id id))]]}]]]))

(mf/defc library-image-card
  [{:keys [id name thumb-uri library-id]}]
  (let [locale (i18n/use-locale)
        state (mf/use-state {:is-open false})]
    [:div.library-card.library-image
     [:div.input-checkbox.check-primary
      [:input {:type "checkbox"
               :id (str "image-" id)
               :on-change #(println "toggle-selection")
               #_(:checked false)}]
      [:label {:for (str "image-" id)}]]
     [:div.library-card-image
      [:img {:src thumb-uri}]]
     [:div.library-card-footer
      [:div.library-card-footer-name name]
      [:div.library-card-footer-timestamp "Less than 5 seconds ago"]
      [:div.library-card-footer-menu
       { :on-click #(swap! state update :is-open not) }
       i/actions]
      [:& context-menu
       {:show (:is-open @state)
        :on-close #(swap! state update :is-open not)
        :options [[(t locale "ds.button.delete")
                   #(st/emit! (dlib/delete-item :images library-id id))]]}]]]))

(mf/defc library-color-card
  [{ :keys [ id content library-id] }]
  (when content
    (let [locale (i18n/use-locale)
         state (mf/use-state {:is-open false})]
     [:div.library-card.library-color
      [:div.input-checkbox.check-primary
       [:input {:type "checkbox"
                :id (str "color-" id)
                :on-change #(println "toggle-selection")
                #_(:checked false)}]
       [:label {:for (str "color-" id)}]]
      [:div.library-card-image
       { :style { :background-color content }}]
      [:div.library-card-footer
       [:div.library-card-footer-name content ]
       [:div.library-card-footer-color
        [:span.library-card-footer-color-label "RGB"]
        [:span.library-card-footer-color-rgb (str/join " " (uc/hex->rgb content))]]
       [:div.library-card-footer-menu
        { :on-click #(swap! state update :is-open not) }
        i/actions]
       [:& context-menu
        {:show (:is-open @state)
         :on-close #(swap! state update :is-open not)
         :options [[(t locale "ds.button.delete")
                    #(st/emit! (dlib/delete-item :palettes library-id id))]]}]]])))

(defn libraries-ref [section]
  (-> (comp (l/key :library) (l/key section))
      (l/derive st/state)))

(defn selected-items-ref [library-id]
  (-> (comp (l/key :library) (l/key :selected-items) (l/key library-id))
      (l/derive st/state)))

(mf/defc library-page
  [{:keys [team-id library-id section]}]
  (let [libraries (mf/deref (libraries-ref section))
        items (mf/deref (selected-items-ref library-id))
        selected-library (first (filter #(= (:id %) library-id) libraries))]

    (mf/use-effect {:fn #(st/emit! (dlib/retrieve-libraries section team-id))                  
                    :deps (mf/deps section team-id)})
    (mf/use-effect {:fn #(when library-id
                           (st/emit! (dlib/retrieve-library-data section library-id)))
                    :deps (mf/deps library-id)})
    (mf/use-effect {:fn #(if (and (nil? library-id) (> (count libraries) 0))
                           (let [path (keyword (str "dashboard-library-" (name section)))]
                             (st/emit! (rt/nav path {:team-id team-id :library-id (:id (first libraries))}))))
                    :deps (mf/deps library-id section team-id)})

    [:div.library-page
     [:& library-header {:section section :team-id team-id}]
     [:& library-sidebar {:items libraries :team-id team-id :library-id library-id :section section}]

     (if library-id
       [:section.library-content
        [:& library-top-menu {:selected selected-library :section section :library-id library-id :team-id team-id}]
        [:*
         ;; TODO: Fix the chunked list
         #_[:& chunked-list {:items items
                             :initial-size 30
                             :chunk-size 30}
            (fn [item]
              (let [item (assoc item :key (:id item))]
                (case section
                  :icons [:& library-icon-card item]
                  :images [:& library-image-card item]
                  :palettes [:& library-color-card item ])))]
         (if (> (count items) 0)
           [:div.library-page-cards-container
            (for [item items]
              (let [item (assoc item :key (:id item))]
                (case section
                  :icons [:& library-icon-card item]
                  :images [:& library-image-card item]
                  :palettes [:& library-color-card item ])))]
           [:div.library-content-empty
            [:p.library-content-empty-text "You still have no elements in this library"]])]]

       [:div.library-content-empty
        [:p.library-content-empty-text "You still have no image libraries."]])]))
