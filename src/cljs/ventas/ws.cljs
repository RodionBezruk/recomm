(ns ventas.ws
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
            [ventas.util :as util]
            [ventas.common.util :as cutil]
            [clairvoyant.core :refer-macros [trace-forms]]
            [re-frame-tracer.core :refer [tracer]]
            )
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]))
(enable-console-print!)
(def output-channel (atom nil))
(def input-channel (atom nil))
(def output-binary-channel (atom nil))
(def input-binary-channel (atom nil))
(def active-channels (atom {}))
(defn send-request! [{:keys [name params callback request-params]}]
  (let [ch (chan)
        id (rand-int 999999)]
    (debug "send-request! | output-channel" output-channel "name" name "params" params "request-params" request-params "callback" callback)
    (swap! active-channels assoc id ch)
    (go
      (>! (if (:binary request-params) @output-binary-channel @output-channel) {:type :request :id id :name name :params params})
      (loop []
        (let [message (<! ch)]
          (callback message)
          (if (:realtime request-params)
            (recur)
            (swap! active-channels dissoc id)))))))
(defn ws-response-dispatch [message]
  (let [ch (get @active-channels (:id message))]
    (when-not (nil? ch)
      (go (>! ch message)))))
(defmulti ws-event-dispatch (fn [message] (:name message)))
(declare init)
(defmethod ws-event-dispatch :server-restarted [message client-id ws-channel]
  (init (constantly true)))
(defmethod ws-event-dispatch :default [message]
  (debug "Unhandled event: " message))
(defn receive-messages! [input-channel server-ch]
  (go-loop []
    (let [message (cutil/process-input-message (:message (<! server-ch)))]
      (case (:type message)
        :event (ws-event-dispatch message)
        :response (ws-response-dispatch message)
        (js/console.warn "Unhandled message: " message))
      (if (and message (:type message))
        (recur)
        (init (constantly true))))))
(defn send-messages! [output-channel server-ch]
  (go-loop []
    (when-let [message (cutil/process-output-message (<! output-channel))]
      (>! server-ch message)
      (recur))))
(defn receive-binary-messages! [input-channel server-ch]
  (go-loop []
    (let [message (:message (<! server-ch))]
      (debug "receive-binary-messages!" message)
      (ws-response-dispatch message)
      (when message
        (recur)))
))
(defn send-binary-messages! [output-channel server-ch]
  (go-loop []
    (when-let [message (<! output-channel)]
      (debug "send-binary-messages!" message)
      (>! server-ch message)
      (recur))))
(defn init
  ([callback] (init callback 0))
  ([callback retries]
  (go
    (infof "Starting channels")
    (try
      (let [ws-result (<! (ws-ch "ws://localhost:3450/ws" {:format :json-kw}))
            ws-binary-result (<! (ws-ch "ws://localhost:3450/binary-ws" {:format :fressian}))]
        (when (:error ws-result)
          (throw (js/Error. "Error conectándose al Websocket: " (:error ws-result))))
        (when (:error ws-binary-result)
          (throw (js/Error. "Error conectándose al Websocket: " (:error ws-binary-result))))
        (infof "Starting input-channel")
        (reset! input-channel (doto (atom []) (receive-messages! (:ws-channel ws-result))))
        (infof "Starting output-channel")
        (reset! output-channel (doto (chan) (send-messages! (:ws-channel ws-result))))
        (infof "Starting input-binary-channel")
        (reset! input-binary-channel (doto (atom []) (receive-binary-messages! (:ws-channel ws-binary-result))))
        (infof "Starting output-binary-channel")
        (reset! output-binary-channel (doto (chan) (send-binary-messages!  (:ws-channel ws-binary-result))))
        (callback))
      (catch :default e
        (error "Error de conexión: " e)
        (when (< retries 3)
          (do (warn "Intentando reconectar") 
              (<! (timeout 1000)) 
              (init callback (inc retries)))))))))
