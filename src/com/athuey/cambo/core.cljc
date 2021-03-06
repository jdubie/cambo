(ns com.athuey.cambo.core
  (:refer-clojure :exclude [get set atom ref keys range]))

;;; DATASOURCE

(defprotocol IDataSource
  (get [this pathsets])
  (set [this pathmaps]))

;;; GRAPH

(defrecord Atom [])

;; TODO: remember significance of empty atom vs nil value atom ;p
(defn atom
  ([]
   (Atom.))
  ([value]
   (map->Atom {:value value})))

(defn atom? [x]
  (instance? Atom x))

(defrecord Ref [path])

(defn ref [path]
  (Ref. path))

(defn ref? [x]
  (instance? Ref x))

(defn boxed?
  [value]
  (or (atom? value)
      (ref? value)))

(defrecord PathValue [path value])

(defn pv [path value]
  (PathValue. path value))

(defn path-value [path value]
  (PathValue. path value))

;; TODO:
;; - pathmap
;; - pathvalue

;;; KEYS

(defn key? [x]
  (or (string? x)
      (keyword? x)
      (integer? x)))

(defrecord Range [start end])

(defn range [start end]
  (Range. start end))

(defn range-keys [{:keys [start end]}]
  (clojure.core/range start end))

(defn range? [x]
  (instance? Range x))

;; TODO: remove duplicates
;; TODO: group into ranges?
(defn keyset [keys]
  (if (= 1 (count keys))
    (first keys)
    (into [] keys)))

(defn keyset? [x]
  (or (key? x)
      (range? x)
      (and (vector? x)
           (every? #(or (key? %) (range? %)) x))))

(defn keys [x]
  (cond
    (key? x) [x]
    (range? x) (range-keys x)
    (vector? x) (mapcat keys x)))

(defn keyset-seq [x]
  (cond
    (key? x) [x]
    (range? x) [x]
    (vector? x) x))

;;; PATHS

(defn path? [x]
  (and (vector? x)
       (every? key? x)))

(defn pathset? [x]
  (and (vector? x)
       (every? keyset? x)))

(defn expand-pathset
  [[keyset & pathset]]
  (if (seq pathset)
    (for [key (keys keyset)
          path (expand-pathset pathset)]
      (into [key] path))
    (map vector (keys keyset))))

(defn expand-pathsets [pathsets]
  (mapcat expand-pathset pathsets))

(def leaf ::leaf)
(defn leaf? [v] (= leaf v))

;; TODO: remember wtf this is doing -- this has to be accidentally quadratic
(defn pathsets*
  [tree]
  (if (leaf? tree)
    ['()]
    (->> tree
         (reduce (fn [m [key sub-tree]]
                   (let [paths (pathsets* sub-tree)]
                     (reduce #(update %1 %2 (fnil conj []) key)
                             m
                             paths)))
                 {})
         (map (fn [[path keys]]
                (conj path (keyset keys)))))))

;; TODO: kill this once I remember wtf pathsets* is doing
(defn pathsets [tree]
  (map vec (pathsets* tree)))

;; TODO: try and make tree stuff not do deep assoc-in -- maybe sort / recurse to minimize deep tree rebuilding
;; TODO: same as above but with transients

(defn length-tree
  [pathsets]
  (->> pathsets
       (group-by count)
       (map (fn [[length pathsets]]
              (loop [[p & ps] (expand-pathsets pathsets) tree {}]
                (if p
                  (recur ps (assoc-in tree p leaf))
                  [length tree]))))
       (into {})))

(defn length-tree-pathsets
  [length-tree]
  (mapcat (comp pathsets second) length-tree))

(defn tree [pathsets]
  (loop [[p & ps] (expand-pathsets pathsets) tree {}]
    (if p
      (recur ps (assoc-in tree p leaf))
      tree)))

(defn collapse [pathsets]
  (length-tree-pathsets (length-tree pathsets)))

(defn assert-inner-reference
  [cache ref]
  (loop [cache cache [key & rest] ref]
    (if (ref? cache)
      (throw (ex-info "inner reference" {:path ref}))
      (when (seq rest)
        (recur (clojure.core/get cache key) rest)))))

;; TODO: redo this ...
(defn optimize* [root cache [key & rest :as path] optimized]
  (cond
    (ref? cache) (let [ref-path (:path cache)]
                   (assert-inner-reference root ref-path)
                   (if (seq? path)
                     (optimize* root root (concat ref-path path) [])
                     [ref-path]))

    (atom? cache) []
    :else (mapcat (fn [key]
                    (if (map? cache)
                      (if (contains? cache key)
                        (optimize* root (clojure.core/get cache key) rest (conj optimized key))
                        [(into [] (concat optimized [key] rest))])
                      []))
                  (keys key))))

(defn optimize
  [cache paths]
  (mapcat (fn [path]
            (optimize* cache cache path []))
          paths))
