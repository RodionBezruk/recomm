(comment
 (defmethod ventas.plugin/widgets ::link
   (fn [:a "Enlace"])))
(comment
 (defmethod ventas.plugin/actions :ventas.entities.cart/checkout
   (fn []
     "Something")))
(comment
 (defmethod ventas.plugin/filters :ventas.routes/compile
   (fn [routes context]
     routes)))
