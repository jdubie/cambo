(ns examples.ladder
  (:require [com.athuey.cambo.core :as core :refer [path-value]]
            [com.athuey.cambo.router :as router :refer [KEYS INTEGERS RANGES]]
            [com.athuey.cambo.model :as model]))

(def current-user
  {:user/name "Ricky Bobby"
   :user/gender :male
   :user/age 27
   :user/email "ricky@aol.com"
   :user/zipcode "94025"
   :user/quote (core/ref [:quote/by-id 1])})

(def id->quote
  {1 {:quote/term 10
      :quote/policy "imma policy"
      :quote/monthly-premium 30
      :quote/carrier (core/ref [:carrier/by-id 2])}})

(def id->carrier
  {2 {:carrier/description "it's really great"
      :carrier/name "Ladder"}})

(def routes
  [{:route [:user/current [:user/name
                           :user/email
                           :user/gender
                           :user/age
                           :user/zipcode
                           :user/quote]]
    :get (fn [[_ fields]]
           (for [field fields]
             (path-value [:user/current field]
                         (get current-user field))))}

   {:route [:user/current :user/quote [:quote/policy
                                       :quote/term
                                       :quote/monthly-premium]]}

   {:route [:quote/by-id INTEGERS [:quote/policy
                                   :quote/term
                                   :quote/monthly-premium
                                   :quote/carrier]]
    :get (fn [[_ ids fields]]
          (for [id ids
                field fields]
            (path-value [:user/current :user/quote field]
                        (get-in id->quote [id field]))))}

   {:route [:carrier/by-id INTEGERS [:carrier/description
                                     :carrier/name]]
    :get (fn [[_ ids fields]]
           (for [id ids
                 field fields]
             (path-value [:carrier/by-id id field]
                         (get-in id->carrier [id field]))))}])

(comment
  (core/get (router/router routes)
            [[:user/current [:user/name :user/email :user/zipcode]]
             [:user/current :user/quotes [10 30] [:db/id :quote/term :policy/coverage]]])

  (let [m (model/model {:datasource (router/router routes)})]
    (model/get m [[:user/current [:user/name :user/email :user/zipcode]]
                  [:user/current :user/quotes [10 30] [:db/id :quote/term :policy/coverage]]])))

(defn gett
  [fragments]
  (let [m (model/model {:datasource (router/router routes)})]
    (model/get m fragments)))
