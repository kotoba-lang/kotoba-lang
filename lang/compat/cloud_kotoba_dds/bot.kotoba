(ns cloud-kotoba-dds.bot
  "Bot identity mount points. Load resources/cloud_kotoba_dds/bot.css and bot.js,
   then call cloudKotobaBot.mountAll(). This namespace owns no app state.")
(defn avatar
  "Accessible or decorative hydration mount. id is stable identity, not a name.
   status must come from the host; motion :off is an explicit local pause."
  [{:keys [id color glyph status label motion]}]
  [:span (cond-> {:class "ck-bot" :data-ck-bot-id (str id)
                  :data-status (name (or status :unknown))
                  :data-motion (name (or motion :auto))}
           color (assoc :data-color (name color))
           glyph (assoc :data-glyph (name glyph))
           label (assoc :role "img" :aria-label label)
           (not label) (assoc :aria-hidden "true"))])
