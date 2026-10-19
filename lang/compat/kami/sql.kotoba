(ns kami.sql
  "SQL DDL as data — 'hiccup for schemas'. CREATE TABLE / INDEX / INSERT statements map onto EDN
   directly, so a relational schema is composable data you fork and diff. A fresh relational-data domain
   in the kami.* family. `.cljc`.

   Statements (identifiers are kebab→snake: :user-id → user_id):
     [:col :id :integer {:primary-key true}]      → id INTEGER PRIMARY KEY
     [:col :name [:varchar 255] {:not-null true}] → name VARCHAR(255) NOT NULL
        constraints: :primary-key :not-null :unique :default v :references [table col]
     [:create-table :users col…]                  → CREATE TABLE users ( … );
     [:create-index :ix_name :users [:email]]      → CREATE INDEX ix_name ON users (email);
     [:insert :users [:id :name] [1 \"Ann\"]]        → INSERT INTO users (id, name) VALUES (1, 'Ann');
     [:drop-table :users]                          → DROP TABLE users;
   Top level: (sql stmt…)"
  (:require [clojure.string :as str]))

(defn- ident [x] (str/replace (name x) "-" "_"))

(defn- sqltype [t]
  (if (vector? t)
    (str (str/upper-case (ident (first t))) "(" (str/join ", " (rest t)) ")")   ;; [:varchar 255] → VARCHAR(255)
    (str/upper-case (ident t))))

(defn- sqlval [v]
  (cond
    (string? v)  (str "'" (str/replace v "'" "''") "'")
    (boolean? v) (if v "1" "0")
    (keyword? v) (str/upper-case (ident v))    ;; bareword: :current-timestamp → CURRENT_TIMESTAMP
    :else        (str v)))

(defn- constraints [m]
  (str/join " "
    (remove nil?
      [(when (:primary-key m) "PRIMARY KEY")
       (when (:not-null m)    "NOT NULL")
       (when (:unique m)      "UNIQUE")
       (when (contains? m :default) (str "DEFAULT " (sqlval (:default m))))
       (when-let [[t c] (:references m)] (str "REFERENCES " (ident t) "(" (ident c) ")"))])))

(defn- column [[_ nm typ opts]]
  (str (ident nm) " " (sqltype typ)
       (let [c (constraints opts)] (when (seq c) (str " " c)))))

(defn stmt
  "Compile one EDN DDL statement to a SQL string."
  [form]
  (let [[op & more] form]
    (case op
      :create-table (let [[nm & cols] more]
                      (str "CREATE TABLE " (ident nm) " (\n"
                           (str/join ",\n" (map #(str "  " (column %)) cols)) "\n);"))
      :create-index (let [[nm tbl cols] more]
                      (str "CREATE INDEX " (ident nm) " ON " (ident tbl)
                           " (" (str/join ", " (map ident cols)) ");"))
      :insert       (let [[tbl cols vals] more]
                      (str "INSERT INTO " (ident tbl) " (" (str/join ", " (map ident cols))
                           ") VALUES (" (str/join ", " (map sqlval vals)) ");"))
      :drop-table   (str "DROP TABLE " (ident (first more)) ";")
      (str (ident op) ";"))))

(defn sql
  "Compile a sequence of DDL statements to a SQL script."
  [& stmts] (str/join "\n\n" (map stmt stmts)))
