(ns forms.responses
  "Answers as a table — the shape every form eventually has to produce.

  A form collects a map per response, keyed by field id. Nobody reads
  responses that way: they read them as a grid, one row per response and one
  column per question, which is what a spreadsheet is and what Google Forms
  makes when it says it is sending answers to a sheet.

  ## Columns come from the form, not from the answers

  Deriving columns from the keys present in the responses is the obvious
  implementation and it is wrong in two ways. A question nobody answered
  disappears, so the table stops matching the form. And two responses with
  different keys produce rows of different widths, which is not a table —
  the values slide sideways under headings that belong to other questions.

  So the columns are the form's fields, in the form's order, and a response
  with no answer for one leaves that cell empty. Order is what carries the
  meaning in a grid; a blank is information and a shifted column is a lie.

  ## Answers to questions that no longer exist are kept

  A field deleted from the form leaves its answers in every response that
  was already collected. Dropping them would silently discard data somebody
  gave you. They become trailing columns, after the fields, labelled with the
  raw key and flagged so a caller can say what they are.

  ## No sheets dependency

  This returns `{:columns … :rows …}` and stops. Turning it into a workbook
  needs both this library and `sheets`, so it belongs to whoever already has
  both rather than making every consumer of `forms` carry `sheets`."
  (:require [clojure.string :as str]))

(defn cell
  "One answer as the text a cell holds.

  Everything becomes text because a CSV field and a `sheets` cell are both
  text, and because the answer came in over JSON where a number and the
  string that looks like one are already indistinguishable. Reading it back
  as a number is the guess `sheets.csv` refuses.

  A checkbox answer is several values; they join with `, `, which is what
  Google Forms writes and therefore what anyone opening the file will
  expect. That is ambiguous when an answer itself contains `, ` — stated
  rather than solved, because the alternative is a separator nobody
  recognises."
  [value]
  (cond
    (nil? value) ""
    (string? value) value
    (or (sequential? value) (set? value)) (str/join ", " (map cell value))
    :else (str value)))

(def ^:private metadata-columns
  [{:key :submitted-at :label "送信日時" :source :metadata}
   {:key :author :label "回答者" :source :metadata}])

(defn columns
  "The columns of the response table: when and who, then the form's fields
  in order, then any answered key the form no longer has.

  `:source` says where each came from — `:metadata`, `:field`, or
  `:orphan` — so a caller can render the third differently without matching
  on labels."
  [form submissions]
  (let [fields (vec (:forms/fields form))
        field-ids (set (map :forms/id fields))
        orphans (->> submissions
                     (mapcat #(keys (:answers %)))
                     (map name)
                     distinct
                     (remove field-ids)
                     sort)]
    (vec
     (concat metadata-columns
             (map (fn [f]
                    {:key (:forms/id f)
                     :label (or (not-empty (str (:forms/label f))) (:forms/id f))
                     :source :field})
                  fields)
             (map (fn [k] {:key k :label k :source :orphan}) orphans)))))

(defn table
  "`{:columns [...] :rows [[text ...]]}` — the responses as a grid.

  Rows are in the order given. That is submission order as the application
  stores them, which is the order a person expects and the only one that
  needs no explanation."
  [form submissions]
  (let [cols (columns form submissions)]
    {:columns cols
     :rows (mapv (fn [sub]
                   (mapv (fn [{:keys [key source]}]
                           (cell (if (= :metadata source)
                                   (get sub key)
                                   (get (:answers sub) key
                                        (get (:answers sub) (keyword key))))))
                         cols))
                 submissions)}))

(defn rows-with-header
  "`table` flattened to a header row followed by the data rows.

  What a CSV writer and a `sheets` tab both want, and the one place the
  header's position is decided."
  [form submissions]
  (let [{:keys [columns rows]} (table form submissions)]
    (into [(mapv :label columns)] rows)))
