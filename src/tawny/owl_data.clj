;; The contents of this file are subject to the LGPL License, Version 3.0.
;;
;; Copyright (C) 2013, Phillip Lord, Newcastle University
;;
;; This program is free software: you can redistribute it and/or modify it
;; under the terms of the GNU Lesser General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or (at your
;; option) any later version.
;;
;; This program is distributed in the hope that it will be useful, but WITHOUT
;; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
;; FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
;; for more details.
;;
;; You should have received a copy of the GNU Lesser General Public License
;; along with this program. If not, see http://www.gnu.org/licenses/.
(in-ns 'tawny.owl)




(defbdontfn add-data-domain
  {:doc "Adds a domain to a data property."
   :arglists '([property & domains] [o property & domains])}
  [o property domain]
  (add-axiom o
   (.getOWLDataPropertyDomainAxiom
    ontology-data-factory
    (ensure-data-property o property)
    (ensure-class o domain))))

(defbdontfn add-data-range
  {:doc "Adds a range to a data property."
   :arglists '([property & ranges] [ontology property & ranges])}
  [o property range]
  (add-axiom
   (.getOWLDataPropertyRangeAxiom
    ontology-data-factory
    (ensure-data-property o property)
    range)))

(defbdontfn add-data-superproperty
  [o property super]
  (add-axiom o
             (.getOWLSubDataPropertyOfAxiom
              ontology-data-factory
              (ensure-data-property o property)
              (ensure-data-property o super))))
(def
  ^{:private true}
  datacharfuncs
  {
   :functional #(.getOWLFunctionalDataPropertyAxiom %1 %2)
   })

(defbdontfn add-data-characteristics
  "Add a list of characteristics to the property."
  [o property characteristic]
  (when-not (get datacharfuncs characteristic)
    (throw (IllegalArgumentException.
            "Characteristic is not recognised:" characteristic)))
  (add-axiom o
             ((get datacharfuncs characteristic)
              ontology-data-factory (ensure-data-property o property))))

(defbdontfn add-data-equivalent
  [o property equivalent]
  (add-axiom
   o (.getOWLEquivalentDataPropertiesAxiom
      ontology-data-factory
      (ensure-data-property o property)
      (ensure-data-property o equivalent))))

(def ^{:private true}
  datatypeproperty-handlers
  {:annotation add-annotation,
   :domain add-data-domain,
   :range add-data-range,
   :subproperty add-data-superproperty
   :characteristic add-data-characteristics
   :equivalent add-data-equivalent
   :comment add-comment
   :label add-label})

(defdontfn datatypeproperty-explicit
  "Define a new datatype property with an explicit map"
  [o name frames]
  (let [o (or (first (get frames :ontology)) o)
        property (ensure-data-property o name)]
    (.addAxiom owl-ontology-manager
               o
               (.getOWLDeclarationAxiom
                ontology-data-factory
                property))
    (add-a-name-annotation o property name)
    (doseq [[k f] datatypeproperty-handlers]
      (f o property (get frames k)))
    property))

(defdontfn datatypeproperty
  "Define a new datatype property"
  [o name & frames]
  (let [keys
        (list* :ontology (keys datatypeproperty-handlers))]
    (datatypeproperty-explicit
     o name
     (util/check-keys
      (util/hashify-at
       keys frames)
      keys))))

(defmacro defdproperty
  [dataname & frames]
  `(let [namestring# (name '~dataname)
         datatype# (tawny.owl/datatypeproperty namestring#
                                               ~@frames)]
     (def
       ~(vary-meta dataname
                   merge
                   {:owl true})
       datatype#)))


(defmontfn literal
  "Returns a OWL2 literal.

`literal' is the value of the literal and must be a string or a number. Anything
else must by coerced into a string manually. Options can also be specified,
with :lang definining the language where `literal' is a string, and :type
which is an OWLDatatype object.
"
  [o literal & {:keys [lang type]}]
  (cond
   lang
   (.getOWLLiteral ontology-data-factory literal lang)
   type
   (.getOWLLiteral ontology-data-factory
                   literal
                   (ensure-datatype o type))
   :default
   (.getOWLLiteral ontology-data-factory literal)))


(defbdontfn add-datatype-equivalent
  [o datatype equivalent]
  (add-axiom
   o (.getOWLDatatypeDefinitionAxiom
      ontology-data-factory datatype
      (ensure-datatype o equivalent))))

(def ^{:private true}
  datatype-handlers
  {:annotation add-annotation
   :comment add-comment
   :label add-label
   :equivalent add-datatype-equivalent})

(defdontfn datatype-explicit [o name frames]
  (let [o
        (or (first (get frames :ontology))
            o)
        datatype
        (.getOWLDatatype
         ontology-data-factory
         (iriforname name))]
    (add-axiom o
     (.getOWLDeclarationAxiom ontology-data-factory datatype))
    (add-a-name-annotation o datatype name)
    (doseq [[k f] datatype-handlers]
      (f o datatype (get frames k)))
    datatype))

(defdontfn datatype [o name & frames]
  (let [keys
        (list* :ontology
               (keys datatype-handlers))]
    (datatype-explicit
     o name
     (util/check-keys
      (util/hashify-at keys frames)
      keys))))

(defmacro defdatatype
  [dataname & frames]
  `(let [namestring# (name '~dataname)
         datatype# (tawny.owl/datatype namestring#
                                       ~@frames)]
     (def
       ~(vary-meta dataname
                   merge
                   {:owl true})
       datatype#)))

(defmontfn data-and
  [_ & types]
  (.getOWLDataIntersectionOf
   ontology-data-factory
   (into #{} types)))

(.addMethod owland :data data-and)

(defmontfn data-or
  [o & types]
  (.getOWLDataUnionOf
   ontology-data-factory
   (into #{} (map (partial ensure-datarange o) types))))

(.addMethod owlor :data data-or)

(defmontfn data-not
  [o type]
  (.getOWLDataComplementOf
   ontology-data-factory
   (ensure-datarange o type)))

(.addMethod owlnot :data data-not)

(defbmontfn data-some
  [o property datarange]
  (.getOWLDataSomeValuesFrom
   ontology-data-factory
   (ensure-data-property o property)
   (ensure-datarange o datarange)))

(.addMethod owlsome :data data-some)

(defbmontfn data-only
  [o property datatype]
  (.getOWLDataAllValuesFrom
   ontology-data-factory
   (ensure-data-property o property)
   (ensure-datarange o datatype)))


(.addMethod only :data data-only)

(defmontfn data-oneof [o & literal]
  (.getOWLDataOneOf
   ontology-data-factory
   (into #{}
         literal)))

(.addMethod oneof :literal data-oneof)

(defmontfn data-hasvalue [o property literal]
  (.getOWLDataHasValue ontology-data-factory
   (ensure-data-property o property)
   (if (instance? OWLLiteral literal)
     literal
     (tawny.owl/literal literal))))

(.addMethod hasvalue :data data-hasvalue)


(defmontfn data-exactly [o number property]
  (.getOWLDataExactCardinality
   ontology-data-factory
   number (ensure-data-property o property)))

(.addMethod exactly :data data-exactly)

(defmontfn data-atmost [o number property]
  (.getOWLDataMaxCardinality
   ontology-data-factory number
   (ensure-data-property o property)))

(.addMethod atmost :data data-atmost)

(defmontfn data-atleast [o number property]
  (.getOWLDataMinCardinality
   ontology-data-factory number
   (ensure-data-property o property)))

(.addMethod atleast :data data-atleast)

(defn owlmin [from]
  (.getOWLDatatypeMinExclusiveRestriction
   ontology-data-factory from))

(defn owlmax [to]
  (.getOWLDatatypeMaxExclusiveRestriction
   ontology-data-factory to))

(defn minmax
  [from to]
  (.getOWLDatatypeMinMaxExclusiveRestriction
   ontology-data-factory from to))

(defn mininc [from]
  (.getOWLDatatypeMinInclusiveRestriction
   ontology-data-factory from))

(defn maxinc [to]
  (.getOWLDatatypeMaxInclusiveRestriction
   ontology-data-factory to))

(defn minmaxinc
  [from to]
  (.getOWLDatatypeMinMaxInclusiveRestriction
   ontology-data-factory from to))

(defmacro span
  [comparitor & args]
  (cond
   (= comparitor '<)
   `(apply owlmax '~args)
   (= comparitor '>)
   `(apply owlmin '~args)
   (= comparitor '><)
   `(apply minmax '~args)
   (= comparitor '<=)
   `(apply maxinc '~args)
   (= comparitor '>=)
   `(apply mininc '~args)
   (= comparitor '>=<)
   `(apply minmaxinc '~args)
   :default
   (throw (IllegalArgumentException. (str "Unknown comparitor" comparitor)))))


(defmontfn data-getfact [o property from to]
  (.getOWLDataPropertyAssertionAxiom
   ontology-data-factory
   (ensure-data-property o property) from to))

(.addMethod getfact :data data-getfact)

(defmontfn data-getfactnot [o property from to]
  (.getOWLNegativeDataPropertyAssertionAxiom
   ontology-data-factory
   (ensure-data-property o property) from to))

(.addMethod getfactnot :data data-getfactnot)
