(ns ventas.subs
  (:require
    [re-frame.core :as rf]
    [clairvoyant.core :refer-macros [trace-forms]]
    [clojure.pprint :as pprint]
    [re-frame-tracer.core :refer [tracer]]))
(enable-console-print!)
(rf/reg-sub :all
  (fn [db _] (pprint/write db :stream nil)))
(rf/reg-sub :messages
  (fn [db _] (-> db :messages)))
(rf/reg-sub :session
  (fn [db _] (-> db :session)))
(rf/reg-sub :app/form
  (fn [db _] (-> db :form)))
(rf/reg-sub :backend.users.edit/comment-modal
  (fn [db _] (-> db :comment-modal)))
(rf/reg-sub :backend.users.edit/made-comment-modal
  (fn [db _] (-> db :made-comment-modal)))
(rf/reg-sub :app.users/users
  (fn [db _] (-> db :users)))
(rf/reg-sub :backend.users.edit/comments
  (fn [db _] (-> db :form :comments)))
(rf/reg-sub :backend.users.edit/made-comments
  (fn [db _](-> db :form :made-comments)))
(rf/reg-sub :backend.users.edit/friends
  (fn [db _] (-> db :form :friends)))
(rf/reg-sub :backend.users.edit/own-images
  (fn [db _] (-> db :form :own-images)))
(rf/reg-sub :backend.users.edit/images
  (fn [db _] (-> db :form :images)))
(rf/reg-sub :app/notifications
  (fn [db _] (-> db :notifications)))
(rf/reg-sub :app.reference/user.role
  (fn [db _] (-> db :reference :user.role)))
