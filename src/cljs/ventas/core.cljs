(ns ventas.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [re-frame.core :as rf]
            [bidi.bidi :as bidi]
            [accountant.core :as accountant]
            [cljsjs.react-bootstrap]
            [clojure.string :as s]
            [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf
                                                       trace debug info warn error]]
            [cljs.core.async :refer [<! >! put! close! timeout chan]]
            [chord.client :refer [ws-ch]]
            [chord.format.fressian :as chord-fressian]
            [cljs.reader :as edn]
            [cljs.pprint :as pprint]
            [ventas.ws :as ws]
            [ventas.subs :as subs]
            [ventas.util :as util :refer [go-to dispatch-page-event]]
            [re-frame-datatable.core :as dt]
            [soda-ash.core :as sa]
            [clairvoyant.core :refer-macros [trace-forms]]
            [re-frame-tracer.core :refer [tracer]]
            [ventas.routes :refer [route-parents routes]]
            [ventas.pages.interface :as p]
            )
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [ventas.util-macros :as util-macros :refer [swap-input-value! require-pages require-plugins]]))
(enable-console-print!)
(timbre/set-level! :debug)
(require-pages)
(require-plugins)
(def route-names {
  :frontend "Frontend"
  :frontend.index "Inicio"
  :backend "Inicio"
  :backend.users "Usuarios"
  :backend.users.edit "Nuevo usuario"
  :backend.login "Login"
  :backend.register "Registro"
})
(debug "Rutas:" (with-out-str (cljs.pprint/pprint routes)))
(comment
  (rf/reg-event-fx :users.all
    (fn [cofx event]
      {:ws-request {:name :users.get}})))
(comment
  (rf/reg-event-db :users.all.success
    (fn [db [_ data]]
      (assoc db :users data))))
(comment
  (rf/reg-event-fx :users.special
    (fn [cofx event]
      {:ws-request {:name :users.get :params {:special true} :success :users.special.success}})))
(comment
  (rf/reg-event-db :users.special.success
    (fn [db [_ data]]
      (assoc db :special-users data))))
(defn effect-ws-request [request]
  (ws/send-request!
    {:name (:name request)
     :params (:params request)
     :request-params (:request-params request)
     :callback  #(cond (:success request) (rf/dispatch [(:success request) (:data %1)])
                       (:success-fn request) ((:success-fn request) (:data %1)))}))
(def ws-upload-chunk-size (* 1024 50))
(defn effect-ws-upload-request
  ([request] (effect-ws-upload-request request false 0))
  ([request file-id start]
    (let [data (:upload-data request)
          data-length (-> data .-byteLength)
          raw-end (+ start ws-upload-chunk-size)
          is-last (> raw-end data-length)
          is-first (zero? start)
          end (if is-last data-length raw-end)
          chunk (.slice data start end)]
      (ws/send-request!
        {:name (:name request)
         :params (-> (:params request) (assoc (:upload-key request) chunk) (assoc :is-last is-last) (assoc :is-first is-first) (assoc :file-id file-id))
         :request-params {:binary true :chunked true}
         :callback  (fn [response]
                      (debug "Executing upload callback" start end is-first is-last)
                      (if-not is-last
                        (effect-ws-upload-request request (if is-first (:data response) file-id) end)
                        (fn [a] ((:success-fn request) (:data response)))))}))))
(rf/reg-fx :ws-request effect-ws-request)
(rf/reg-fx :ws-request-multi (fn [requests] (doseq [request requests] (effect-ws-request request))))
(rf/reg-event-fx :effects/ws-request
  (fn [cofx [_ data]]
    (debug ":effects/ws-request")
    {:ws-request data}))
(rf/reg-fx :ws-upload-request effect-ws-upload-request)
(rf/reg-event-fx :effects/ws-upload-request
  (fn [cofx [_ data]]
    (debug ":effects/ws-upload-request")
    {:ws-upload-request data}))
(rf/reg-fx :go-to
  (fn effect-go-to [data]
    (go-to routes (get data 0) (get data 1))))
(rf/reg-event-db :app/entity-update.next
  (fn [db [_ where what]]
    (debug "entity-update, where: " where)
    (assoc-in db where (map #(if (= (:id %1) (:id what)) what %1) (get-in db where)))))
(rf/reg-event-db :app/entity-query.next
  (fn [db [_ where what]]
    (debug "entity-query, where: " where)
    (assoc-in db where what)))
(rf/reg-event-fx :app/entity-remove
  (fn [cofx [_ data key-vec]]
    {:ws-request {:name :entities.remove 
                  :params data 
                  :success-fn #(rf/dispatch [:app/entity-remove.next key-vec (:id data)])}}))
(rf/reg-event-db :app/entity-remove.next
  (fn [db [_ where what]]
    (debug "entity-remove, where: " where ", what: " what ", where value: " (get-in db where))
    (assoc-in db where (filter #(not (= (:id %1) what)) (get-in db where)))))
(rf/reg-event-fx :app/upload
  (fn [cofx [_ {:keys [source file]}]]
    (let [fr (js/FileReader.)]
      (set! (.-onload fr) #(rf/dispatch [:effects/ws-upload-request {:name :upload
                                                                     :upload-key :bytes
                                                                     :upload-data (-> fr .-result)
                                                                     :params {:source source}
                                                      }]))
      (.readAsArrayBuffer fr file))))
(defmulti page-start (fn [page cofx] page))
(defmethod page-start :default [page cofx]
  {})
(defmethod page-start :backend.users [page cofx]
  {:ws-request {:name :users.list :success-fn #(rf/dispatch [:app/entity-query.next [:users] %])}})
(defmethod page-start :backend.users.edit [page cofx]
  (let [id (js/parseInt (get-in (session/get :route) [:route-params :id]))]
    (if (> id 0)
      {:ws-request-multi [
        {:name :entities.find :params {:id id} :success-fn #(rf/dispatch [:app/entity-query.next [:form] %])}
        {:name :users.comments.list :params {:id id} :success-fn #(rf/dispatch [:app/entity-query.next [:form :comments] %])}
        {:name :users.images.list :params {:id id} :success-fn #(rf/dispatch [:app/entity-query.next [:form :images] %])}
        {:name :users.own-images.list :params {:id id} :success-fn #(rf/dispatch [:app/entity-query.next [:form :own-images] %])}
        {:name :users.friends.list :params {:id id} :success-fn #(rf/dispatch [:app/entity-query.next [:form :friends] %])}
        {:name :users.made-comments.list :params {:id id} :success-fn #(rf/dispatch [:app/entity-query.next [:form :made-comments] %])}
        {:name :backend.reference/user.role :success-fn #(rf/dispatch [:app/entity-query.next [:reference :user.role] %])}
       ]}
      {:db (assoc (:db cofx) :form {:name "" :password "" :email "" :description "" :roles []})})))
(defmulti page-end (fn [page cofx] page))
(defmethod page-end :backend.users.edit [_ cofx]
  {:db (assoc (:db cofx) :form {})})
(defmethod page-end :default [_ _]
  (debug "No end function")
  {})
(rf/reg-event-db :app/notifications.add
  (fn event-notifications-add [db [_ notification]]
    (let [sym (gensym)
          notification (assoc notification :sym sym)]
      (go
        (<! (timeout 4000))
        (rf/dispatch [:app/notifications.remove sym]))
      (if (vector? (:notifications db))
        (assoc db :notifications (conj (:notifications db) notification))
        (assoc db :notifications [notification])))))
(rf/reg-event-db :app/notifications.remove
  (fn event-notifications-remove [db [_ sym]]
    (assoc db :notifications (remove #(= (:sym %) sym) (:notifications db)))))
(rf/reg-event-fx :backend.users/edit
  (fn event-users-edit [cofx [_ data]]
    {:go-to [:backend.users.edit data]}))
(rf/reg-event-fx :backend.users.edit/submit
  (fn event-users-edit-submit [cofx [_ data]]
    {:ws-request {:name :users.save :params data :success :backend.users.edit/submit.next}}))
(rf/reg-event-fx :backend.users.edit/submit.next
  (fn event-users-edit-submit-next [cofx [_ data]]
    (debug "About to dispatch go-to app.users")
    {:dispatch [:app/notifications.add {:message "Usuario guardado satisfactoriamente" :theme "success"}]
     :go-to [:backend.users]}))
(rf/reg-event-db :backend.users.edit/comments.edit
  (fn [db [_ id key-vec modal-key]]
    (-> db
        (assoc modal-key {:data (first (filter #(= (:id %) id) (get-in db key-vec)))
                               :open true}))))
(rf/reg-event-db :backend.users.edit/comments.modal
  (fn event-users-edit-comments-modal [db [_ k data]]
    (assoc db k data)))
(rf/reg-event-fx :backend.users.edit/comments.modal.submit
  (fn [fx [_ key-vec comm]]
    {:ws-request {:name :comments.save :params comm :success-fn #(rf/dispatch [:app/entity-update.next key-vec %])}}))
(rf/reg-event-fx :navigation-start
  (fn event-navigation-start [cofx [_ new-page old-page]]
    (debug "event-navigation-start" new-page old-page)
    (let [start (page-start new-page cofx)
          end (page-end old-page cofx)]
      (merge start end))))
(rf/reg-event-db :initialize
  (fn event-initialize [_ _]
    {:users {} :messages [] :session {}}))
(rf/reg-event-db :users-initialize
  (fn event-users-initialize [db [_ users]]
    (assoc db :users users)))
(rf/reg-event-db :session-initialize
  (fn event-session-initialize [db [_ session]]
    (assoc db :session session)))
(rf/reg-event-db :users-add
  (fn event-users-add [db [_ user]]
    (assoc-in db [:users (:id user)] user)))
(rf/reg-event-db :users-replace
  (fn event-users-replace [db [_ users]]
    (assoc db :users users)))
(rf/reg-event-db :messages-add
  (fn event-messages-add [db [_ message]]
    (debug ":messages-add" message)
    (let [messages (take 10 (conj (:messages db) (cond
                                                   (nil? message) {:type :connection-closed}
                                                   :else message)))]
      (debug "messages:" messages)
      (assoc-in db [:messages] messages))))
(def Form (reagent/adapt-react-class (aget js/ReactBootstrap "Form")))
(def Label (reagent/adapt-react-class (aget js/ReactBootstrap "Label")))
(def FormGroup (reagent/adapt-react-class (aget js/ReactBootstrap "FormGroup")))
(def FormControl (reagent/adapt-react-class (aget js/ReactBootstrap "FormControl")))
(def Button (reagent/adapt-react-class (aget js/ReactBootstrap "Button")))
(defn page []
  (info "Rendering...")
  (let [current-page (:current-page (session/get :route))
        route-params (:route-params (session/get :route))]
    [p/pages current-page]))
(defn init []
  (accountant/configure-navigation!
   {:nav-handler (fn
                   [path]
                   (let [match (bidi/match-route routes path)
                         current-page (:handler match)
                         route-params (:route-params match)]
                         (info "Current page: " current-page)
                     (rf/dispatch [:navigation-start current-page (:current-page (session/get :route))])
                     (session/put! :route {:current-page current-page
                                           :route-params route-params})))
    :path-exists? (fn [path]
                    (boolean (bidi/match-route routes path)))})
  (ws/init
    (fn init-callback []
      (accountant/dispatch-current!)
      (reagent/render [page] (js/document.getElementById "app")))))
(defn start []
  (infof "Starting CLJS")
  (init))
(defn stop []
  (infof "Stopping CLJS"))
(defonce figwheel-once (start))
(defn on-figwheel-reload []
  (debug "Reloading...")
  (reagent/render [page] (js/document.getElementById "app")))
