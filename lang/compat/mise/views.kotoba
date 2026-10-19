(ns mise.views
  "Pure-hiccup EC UI components (.cljc, no reagent import).

  Built on shitsuke.components (button/field/input/select/card) and
  shitsuke.style/class-name (mise__* stable classes). The SAME hiccup renders
  live via reagent (cljs) and to HTML via shitsuke.hiccup/->html (SSR). Styling
  lives in stable class names + var(--shitsuke-*) token refs; the visual CSS is
  emitted by the consumer's :pages shadow-css build.

  Interaction uses the data-act convention (see shitsuke ADR): on cljs the host
  wraps :on-click #(rf/dispatch [act ...]); on SSR the host emits data-act and
  a thin enhancer dispatches. Keeps SSR HTML == live DOM."
  (:require [shitsuke.components :as c]
            [shitsuke.style :as sstyle]
            [mise.pricing :as pricing]
            [mise.catalog :as catalog]))

(defn class-name [component] (sstyle/class-name component))

(defn price
  "Render a Price (or {:amount :currency}) as a styled string."
  ([p] (price p nil))
  ([p opts]
   [:span {:class (str (class-name :price)
                       (when-let [c (:class opts)] (str " " c)))}
    (pricing/format-price p opts)]))

(defn qty-stepper
  "A [- qty +] control. opts: :act-dec :act-inc (data-act keywords)."
  ([qty]
   (qty-stepper qty nil))
  ([qty opts]
   [:div {:class (class-name :qty-stepper)}
    (c/icon-button "–" {:act (:act-dec opts) :title "decrease"})
    [:span {:class (class-name :qty-value)} qty]
    (c/icon-button "+" {:act (:act-inc opts) :title "increase"})]))

(defn product-card
  "A product tile: image + brand + name + price + add-to-cart button.
  opts: :act-add (data-act for :cart/add), :image-fallback."
  ([product]
   (product-card product nil))
  ([product {:keys [act-add image-fallback] :as opts}]
   (let [sku (catalog/default-sku product)
         unit-price (:price sku (:price product))
         img (or (first (:images product)) image-fallback)]
     [:article {:class (class-name :product-card)
                :data-product (:id product)}
      (when img [:img {:src img :alt (:name product) :loading "lazy"}])
      [:div {:class (class-name :product-card-body)}
       [:p {:class (class-name :product-brand)} (:brand product)]
       [:h3 {:class (class-name :product-name)} (:name product)]
       (price unit-price)
       (c/button "Add to cart" {:act (or act-add :cart/add) :type "button"})]])))

(defn cart-line
  "A cart row: name, unit-price, qty-stepper, line-total, remove button.
  opts: :act-inc :act-dec :act-remove."
  ([line]
   (cart-line line nil))
  ([line {:keys [act-inc act-dec act-remove] :as opts}]
   (let [unit (:unit-price line (:price line))
         qty (:qty line 1)]
     [:div {:class (class-name :cart-line) :data-sku (:sku line)}
      [:div {:class (class-name :cart-line-name)} (:name line (:sku line))]
      (price unit)
      (qty-stepper qty {:act-inc act-inc :act-dec act-dec})
      (price (pricing/line-total unit qty))
      (c/icon-button "✕" {:act act-remove :title "remove"})])))

(defn cart-summary
  "Totals block: subtotal, tax, shipping, total."
  ([totals]
   (cart-summary totals nil))
  ([totals opts]
   [:section {:class (class-name :cart-summary)}
    [:div [:span "Subtotal"] (price (:subtotal totals))]
    [:div [:span "Discount"] (price (:discount totals))]
    [:div [:span "Tax"] (price (:tax totals))]
    [:div [:span "Shipping"] (price (:shipping totals))]
    [:div {:class (class-name :cart-summary-total)}
     [:span "Total"] (price (:total totals))]]))

(defn checkout-form
  "Render the fields for a checkout stage. opts: :act-submit (data-act for
  next/place-order). Fields are controlled by the host (values passed in)."
  ([checkout]
   (checkout-form checkout nil))
  ([{:keys [stage contact shipping payment] :as checkout}
    {:keys [act-submit act-back values] :as opts}]
   (let [vals (or values {})]
     [:section {:class (class-name :checkout-form)}
      [:h2 (str "Checkout — " (name stage))]
      (case stage
        :contact
        [:div
         (c/field "Email" (c/input {:id :email :value (:email vals)
                                    :data-field "contact.email" :type "email"}) {})
         (c/field "Name" (c/input {:id :name :value (:name vals)
                                   :data-field "contact.name"}) {})]
        :shipping
        [:div
         (c/field "Address" (c/input {:id :address :value (:address vals)
                                      :data-field "shipping.address"}) {})
         (c/field "City" (c/input {:id :city :value (:city vals)
                                   :data-field "shipping.city"}) {})
         (c/field "Postal" (c/input {:id :postal :value (:postal vals)
                                     :data-field "shipping.postal"}) {})
         (c/field "Country" (c/input {:id :country :value (:country vals)
                                      :data-field "shipping.country"}) {})]
        :payment
        [:div
         (c/field "Payment method" (c/select [["card" "Credit card"]
                                              ["cod" "Cash on delivery"]]
                                              {:value (:payment-method-ref vals)
                                               :data-field "payment.method"}) {})]
        :review
        [:div [:p "Review your order, then place it."]]
        :confirmed
        [:div [:p "Order confirmed."]]
        [:div (str "stage " stage)])
      (when act-back (c/button "Back" {:act act-back :type "button"}))
      (when (and act-submit (not= stage :confirmed))
        (c/button (if (= stage :review) "Place order" "Next")
                  {:act act-submit :type "submit"}))])))

(defn order-confirmation
  "Render a confirmed order."
  ([order]
   (order-confirmation order nil))
  ([order opts]
   [:section {:class (class-name :order-confirmation)}
    [:h2 "Thank you!"]
    [:p "Your order " [:code (:id order)] " has been received."]
    [:p "Status: " (name (:status order))]
    (when-let [totals (:totals order)]
      (cart-summary totals))]))
