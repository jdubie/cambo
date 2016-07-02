(ns examples.client
  (:require
    [examples.ladder :as ladder]))

(def concatv (comp vec concat))

(defn prefix
  [p frags]
  (mapv #(concatv p %) frags))

;;
;; component hierarchy
;;
;; relay component -> container -> renderer -> routes

(def quote-details
  {:fragments
    {:quote [[[:quote/term :quote/policy :quote/monthly-premium]]
             [:quote/carrier [:carrier/name :carrier/description]]]
     :user [[[:user/name]]]}})

;; creates two react components :: container (talks to the store), component (normal react component -- Cambo agnostic)
;; goal is to be able to define these as two separate things or conveniently as one (shown here).

;; fragments are data requirements ... a map of pathsets.  the key in the map is purely descriptive -- used in
;; `get-fragment` (shown below) and what prop the data is exposed under, e.g. `(-> this props :user :user/name)`

(def user-quote
  {:fragments
   {:user (concatv [[[:user/name :user/age :user/gender]]]
                   (prefix [:user/quote] (-> quote-details :fragments :quote))
                   (-> quote-details :fragments :user))}})

(comment
  user-quote)

;; here `UsersQuote` has embedded fragments from the child -- this is DIFFERENT than how om does it
;; -- `into` vs `join`

(def quote-screen
  {:component user-quote
   :paths (fn [{:keys [user-id]}]
            #_{:user [:user/by-id user-id]}
            {:user [:user/current]})})

(defn get-query
  [{:keys [paths component]}]
  (let [{:keys [fragments]} component
        obj->root (paths {:user-id 123})]
    (vec
      (mapcat
        (fn [[n v]]
          (prefix (obj->root n) v))
        fragments))))

(comment
  (get-query quote-screen)
  (ladder/gett (get-query quote-screen)))

;; finally a renderer `roots` the query (paths) into something that can be sent to a datasource.
;; paths is that root per fragment ... e.g. all fragments named `user` will be rooted at `[:user/by-id ?user-id]`.
;;
;; component is what gets rendered once the data is loaded (data passed as props to this component)

;; this hierarchy creates the query:

[[:user/by-id 4 [:user/name :user/age :user/gender]]
 [:user/by-id 4 :user/quote [:quote/term :quote/policy :quote/monthly-premium]]
 [:user/by-id 4 :user/quote :quote/carrier [:carrier/name :carrier/description]]]

;; this is different from om in that:
;;  a) a component can expose multiple fragments.
;;     this is useful when you want to thread the current user or selected item through the tree (om uses links for this)
;;  b) you can include a child inline with your query.
;;     in the above both the parent and child component want `:user/name`.  In om you'd have to add nesting to compose
;;     queries.  here when you add a fragment its basically put `into` the current query, not `joined`.
;;  c) the container nests the child -- om `defui` macro creates a single component which is a mixture of om logic and your
;;     own logic.  this sort of ends up with the `computed` vs `props` stuff as well as `om` caring about state and stuff.
;;     If you keep them separate components the `container` can have smart logic and the component is just dumb -- here are
;;     props, do what you want.
;;  d) since fragments compose via `into` vs `join` there are no intermediate ignorable / ui-only keys in the query -- it
;;     matches the server data model directly -- no process-roots, no writing your own recursive parser to ignore keys, etc

;; finally there are renderers which root the query ...

(comment
  (defrenderer UserScreen ... tbd ...))

;; the big thing with renderers is that:
;;
;; a) they have hooks on the ready-state of the data -- is it partial, is it stale, is it done, etc
;;    they can then decide what to render based on that.  this lets components default to relying on 100% data existing.
;;    but you can also show a loading screen which renders different components, or if you have 100% data but its stale,
;;    maybe you load it but have an semi-opaque overlay letting the user see the data but not interact with it while
;;    it finishes loading, etc.  all this logic is just a function on the `renderer`
;; b) they are normal react components and can be nested.  this lets you have disjoint queries.  For example consider the
;;    route
;;
;; /users/:id
;;
;; it might look like this:

;;  <UsersScreen> ;; <-- renderer
;; <UserList />
;; <UserScreen id=id /> ;; <-- renderer
;; <UsersScreen>)

;; the idea is that `UsersScreen` renderer would request a list of users and render the UserList (based on UserList fragments).
;; it would then have another renderer as a child -- `UserScreen` -- which all it does is take the currently selected `id` from
;; the route and plug into it.
;;
;; Routing in this case is just changing rendered components / their parameters
;;
;; So if you have 10 top level routes (/, /about, /jobs, /get-quote, etc....) you make each a `renderer` and then have a root
;; which just switches on them based on the route.  You'd want to make this mapping declarative such that you can take a url
;; and determine which `renderers` will be shown so that you can pre-fetch all the data (SSR for example).

;; sorta like ...

(comment
  [{:path "about"
    :component AboutScreen
    :children []}
   {:path "users"
    :component UsersScreen
    :children [{:path ""
                :component SelectUserScreen
                :children []}
               {:path ":id"
                :component UserScreen
                :children []}]}])

;; basically I'd just look at how react router works, but just use the data format vs their pretending things are components.

;; react router works vs passing children to components for nested routes so `UserScreen` would actually look like:

;; <UsersScreen>
;; <UserList />
;; (children this)
;; <UsersScreen>

;; So `UsersScreen` actually doesn't know what its rendering ... it just has a hole that it plugs children into.  The
;; route declarations then plug in the holes depending on the routes.