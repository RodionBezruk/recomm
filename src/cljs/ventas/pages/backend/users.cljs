(ns ventas.pages.backend.users
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [re-frame.core :as rf]
            [bidi.bidi :as bidi]
            [re-frame-datatable.core :as dt]
            [ventas.pages.interface :refer [pages]]
            [soda-ash.core :as sa]
            [ventas.util :refer [go-to dispatch-page-event]]
            [ventas.routes :refer [app-routes]]))
(defn users-datatable [action-column sub-key]
  [dt/datatable (keyword (gensym "users")) [sub-key]
    [{::dt/column-key   [:id] ::dt/column-label "#"
      ::dt/sorting      {::dt/enabled? true}}
     {::dt/column-key   [:name] ::dt/column-label "Nombre"}
     {::dt/column-key   [:email] ::dt/column-label "Email"
      ::dt/sorting      {::dt/enabled? true}}
     {::dt/column-key   [:actions] ::dt/column-label "Acciones"
      ::dt/render-fn action-column}]
    {::dt/pagination    {::dt/enabled? true
                         ::dt/per-page 5}
    ::dt/table-classes ["ui" "table" "celled"]}])
(defmethod pages :backend.users []
  (fn page-app-users []
    (let [action-column
      (fn [_ row]
        [:div
          [sa/Button {:icon true :on-click #(go-to app-routes :backend.users.edit {:id (:id row)})}
            [sa/Icon {:name "edit"}]]
          [sa/Button {:icon true :on-click #(rf/dispatch [:app/entity-remove {:id (:id row)} [:users]])}
            [sa/Icon {:name "remove"}]]])]
      (fn []
        [:div
          [users-datatable action-column :app.users/users]
          [sa/Button {:onClick #(go-to app-routes :backend.users.edit {:id 0})} "Crear usuario"]]))))
