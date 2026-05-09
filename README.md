# CraftplayShop

CraftplayShop ist ein proprietaeres Minecraft Shop-, Handels-, Markt- und Economy-Plugin fuer Craftplay.de.

## Projektziel

CraftplayShop soll langfristig ein modulares System fuer ServerShop/AdminShop, PlayerShop/ChestShop, AutoSellChest, DirectTrade, AuctionHouse, RankShop, PermissionShop, Referral-System, Importer und externe Integrationen werden.

## Zielumgebung

- Minecraft: Paper/Purpur 1.21.x
- Java: 21
- Build-System: Maven
- Main Class: `de.craftplay.shop.CraftplayShopPlugin`
- Economy: Vault
- Standarddatenbank: SQLite

## Aktueller Stand

**Version v1.0.0 ist der aktuelle Stand.** Der Stand umfasst Core, Config, Sprache, GUI-System, Vault, SQLite, MySQL/MariaDB, ServerShop/AdminShop, AutoSellChest, PlayerShop mit SELL/BUY/BUY_SELL/TRADE_ITEM, Protection-Hooks, DirectTrade, AuctionHouse, PermissionShop, RankShop, ReferralSystem und Importer.

### Core

- Maven-Projekt mit Java 21.
- `plugin.yml` mit Commands, Permissions, Vault-Depend und Softdepends.
- Modulare Service-Struktur.
- Automatische Erstellung fehlender Plugin-Ordner.
- Automatisches Kopieren fehlender Standarddateien aus den Plugin-Ressourcen.
- Automatisches Ergaenzen fehlender Keys in vorhandenen `config.yml`, Language- und GUI-Dateien.
- Reload ueber `/shop reload`, `/cshop reload` und konfigurierten Admin-Befehl.
- Konfigurierbarer Plugin-Hauptbefehl ueber `commands.pluginCommand`.
- Saubere Language- und GUI-Dateien fuer `de_DE` und `en_US`.

### Config, Sprache und GUI

- Externe GUI-Dateien unter `plugins/CraftplayShop/gui/<sprache>/`.
- Externe Sprachdateien unter `plugins/CraftplayShop/language/`.
- Spieler koennen ihre Sprache speichern und umstellen.
- Fallback-Sprache, falls Keys oder Dateien fehlen.
- CraftplayShop-Platzhalter und PlaceholderAPI-Platzhalter in GUI-Namen, GUI-Lores, Button-Texten und Sprachdateien.
- PlaceholderAPI-Hook optional; CMI, Jobs Reborn und andere Expansions werden durchgereicht, wenn PlaceholderAPI sie bereitstellt.
- HeadDatabase-Hook vorbereitet und optional.
- GUI-Sicherheitslogik gegen Item-Entnahme, Drag, Shift-Click und Number-Key.

### Economy und Datenbank

- Vault Economy Hook.
- Vault-Auszahlung an Offline-Spieler fuer PlayerShop-Verkaeufe.
- Sauberes Deaktivieren, wenn Vault benoetigt wird und fehlt.
- SQLite-Datenbankbasis.
- Tabellen fuer Transaktionen, Spieler-Einstellungen, Imports und Import-Mappings.
- Tabelle fuer Spieler-Favoriten im ServerShop.
- Tabelle fuer PlayerShop-Kistenshops.
- Async Transaktionslogging.
- Spieler-Einstellungen mit Sprache und DirectTrade-Status.

### ServerShop

- ServerShop wird aus `server_shop.yml` geladen.
- Kategorien mit ID, Anzeigename, Icon, Lore, Slot und Aktiv-Status.
- Items mit ID, Material, Anzeigename, Lore, Kaufpreis, Verkaufspreis, Kauf-/Verkaufsstatus und Slot.
- Haupt-GUI fuer Kategorien.
- Kategorie-GUI fuer Shop-Items.
- EconomyShopGUI-aehnliches Klicksystem fuer Spieler: Linksklick kauft 1, Shift-Linksklick kauft einen Stack.
- Rechtsklick verkauft 1, Shift-Rechtsklick verkauft einen Stack.
- Vorbereitete Mengenauswahl mit Mengenbuttons und Chat-Eingabe, aktuell nicht als Standard-Spielerklick belegt.
- Kaufen und Verkaufen sind unbegrenzt, wenn die jeweilige Funktion am Item aktiviert ist.
- Sell-All verkauft alle ankaufbaren Items ohne ServerShop-Stock- oder Mengenlimit.
- Konfigurierbare Bestaetigungs-GUI fuer teure Kaeufe.
- Creative- und Spectator-Verkaufsschutz gegen ServerShop-, Sell-Hand-, Sell-All- und Sell-GUI-Verkaeufe.
- Konfigurierbare ServerShop-Schutzregeln fuer Welten und GameModes.
- Verkauf beschaedigter Items kann blockiert werden.
- ServerShop-Suche nach Item-Name, Item-ID, Material, Kategorie-ID und Kategorie-Name.
- Favoriten-System pro Spieler mit SQLite-Speicherung.
- Favoriten-GUI und Such-GUI nutzen dasselbe EconomyShopGUI-aehnliche Klicksystem wie die Kategorie-GUI.
- ServerShop-Favoriten bleiben gespeichert und koennen ueber das Favoriten-GUI geoeffnet werden.
- Permission-Pruefungen fuer Kaufen und Verkaufen.
- Anti-Dupe-Grundlogik mit Pruefung vor Ausfuehrung und Rollback-Versuch.
- Sell Hand.
- Sell All.
- Sell GUI.
- Transaktionslogs fuer Kauf, Verkauf und Sell-All.
- Custom Category- und Item-Lore wird im ServerShop angezeigt.
- Hat ein ServerShop-Item eigene Lore, wird diese im Spieler-ServerShop vollständig allein angezeigt.
- Alte Kauflimit-, Verkaufslimit- und Bestand-Zeilen aus bestehenden GUI-Dateien werden beim Anzeigen herausgefiltert.
- Deaktivierte Kategorien sind im normalen ServerShop verborgen.

### AdminShop / Ingame Editor

- Ingame ServerShop-Editor ueber Admin-GUI.
- Kategorien anzeigen und bearbeiten.
- Items anzeigen und bearbeiten.
- Item-Kaufen und Item-Verkaufen aktivieren/deaktivieren.
- Kauf- und Verkaufspreise per GUI setzen.
- Item-Lore vollständig per Editor bearbeiten; Zeilen werden per `|` getrennt, `clear` oder `-` leert die Lore.
- Neue Kategorien per Materialauswahl oder Drag-and-Drop erstellen.
- Neue Items per Materialauswahl oder Drag-and-Drop erstellen.
- Kategorie-Icon ueber Materialauswahl oder Item-Drop setzen.
- Shop-Item-Material ueber Materialauswahl, Item-Drop oder Item aus Hand setzen.
- Anzeigename von Kategorien und Items per Chat bearbeiten.
- ID von Kategorien und Items per Chat bearbeiten.
- Wunsch-ID direkt nach dem Erstellen einer Kategorie oder eines Items per Chat setzen.
- Automatische ID bleibt erhalten, wenn `-`, `keep` oder `cancel` genutzt wird.
- Kollisionspruefung beim Umbenennen von Kategorie- und Item-IDs.
- Lore von Kategorien und Items per Chat bearbeiten.
- Kaufpreis und Verkaufspreis per Klick erhoehen oder verringern.
- Kaufpreis und Verkaufspreis direkt per Chat setzen.
- Kaufen/Verkaufen pro Item toggeln.
- Kategorien aktivieren/deaktivieren.
- Kategorien und Items duplizieren.
- Kategorien und Items loeschen mit Sicherheitsabfrage.
- Kategorien und Items per Shift-Klick sortieren/anordnen.
- Freie Kategorie- und Item-Slots werden im Editor sichtbar markiert.
- Freie Slots koennen direkt angeklickt werden, um neue Kategorien oder Items genau dort per Materialauswahl zu erstellen.
- Kategorie- und Item-Eintraege zeigen ihre Slotnummer in der Lore.
- Aktuelle Sortierauswahl wird optisch mit Glanz und Zusatzlore markiert.
- Material-Picker mit Seitennavigation.
- Material-Picker mit Suchfunktion.
- Automatische Backups vor Aenderungen an `server_shop.yml`.
- Manueller Backup-Button in der AdminShop-Kategorieuebersicht.
- Backups mit konfigurierbarem Ordner, Cooldown und Maximalanzahl.
- Manuelles ServerShop-Backup per GUI-Button und Admin-Befehl.
- Auflisten der letzten ServerShop-Backups per Admin-Befehl.
- Sicheres Wiederherstellen eines ServerShop-Backups per Admin-Befehl mit 60-Sekunden-Bestaetigung.
- Backup-Uebersicht im AdminShop mit Backup-Items.
- Backup-Detail-GUI mit Dateiname, Datum und Groesse.
- Restore-Bestaetigungs-GUI vor dem Wiederherstellen eines Backups.

### PlayerShop / ChestShop

- PlayerShops mit `SELL`, `BUY`, `BUY_SELL` und `TRADE_ITEM` sind aktiv.
- Spieler erstellen einen Kistenshop per Shift-Linksklick mit Item in der Hand auf eine Kiste.
- Der Chat-Assistent fragt ab, ob der Shop verkaufen, ankaufen, als Kombi-Shop oder als Tauschshop arbeiten soll.
- Danach fragt der Chat-Assistent die benoetigten Mengen und je nach Typ Preis oder Tauschmenge ab.
- CraftplayShop setzt automatisch ein Shop-Schild an die angeklickte Stelle der Kiste.
- Spieler koennen alternativ weiterhin manuell ein Schild mit `shop`, `sell`, `[shop]`, `[cshop]` oder `buy` neben eine Kiste setzen.
- Zeile 2 des Schilds bestimmt die Verkaufsmenge, Zeile 3 den Preis.
- Das verkaufte Item wird beim Erstellen aus dem Item in der Hand uebernommen.
- Rechtsklick auf SELL-Shop-Schild oder Kiste kauft die konfigurierte Menge aus dem Kistenbestand.
- Rechtsklick auf BUY-Shop-Schild oder Kiste verkauft die konfigurierte Menge an den Shop und legt die Items in die Shop-Kiste.
- `BUY_SELL`-Shops kombinieren beide Richtungen: Rechtsklick kauft aus dem Shop, Linksklick verkauft an den Shop.
- `TRADE_ITEM`-Shops tauschen ein Itembuendel gegen ein anderes Itembuendel ohne Geldfluss.
- PlayerShop-Nutzung auf PlotSquared-Plots benoetigt keine separate `use`-Flag; aktive Shop-Klicks werden vom Plugin vor dem normalen Plot-Use abgefangen.
- PlayerShop-Schilder erkennen automatisch, ob ein SELL-Shop genug passende Items in der Kiste hat.
- PlayerShop-Schilder erkennen automatisch, ob ein BUY-Shop genug Kistenplatz hat und der Besitzer genug Geld besitzt.
- PlayerShop-Schilder werden nach Kistenklicks, Inventar-Schließen, Hopper-Bewegungen und zusätzlich per kleinem Refresh-Takt aktualisiert.
- Die erste Schildzeile kann mit `%status_color%`, `%action%` und weiteren Platzhaltern gruen oder rot formatiert werden.
- Schildfarben, Statustexte und alle vier Schildzeilen sind unter `playerShops.sign` in der `config.yml` frei anpassbar.
- Die `config.yml` dokumentiert die Schild-Platzhalter wie `%action%`, `%amount%`, `%price%`, `%stock%`, `%space%`, `%status%`, `%available%`, `%status_color%` und `%stock_color%`.
- Vor dem Kauf werden Permission, Geld, Lagerbestand und Inventarplatz geprueft.
- Bei Fehlern versucht der Kauf ein Rollback von Geld und Items.
- Besitzer erhalten das Geld ueber Vault, auch wenn sie offline sind.
- BUY-Shops ziehen dem Besitzer die Kosten per Vault ab und zahlen sie dem verkaufenden Spieler aus.
- Ueber Kistenshops schwebt eine konfigurierbare Item-Anzeige.
- Verfuegbare Display-Typen: `NONE`, `ITEM`, `GLASS_CASE`, `LARGE_ITEM`, `ITEM_FRAME`.
- Der Standard-Display-Typ für neu erstellte PlayerShops ist über `playerShops.creation.defaultDisplayType` konfigurierbar.
- Der Display-Typ `ITEM` schwebt leicht auf und ab und dreht sich; die Animation nutzt Display-Interpolation und ist unter `playerShops.display.animation.item` konfigurierbar.
- Besitzer oder Admins oeffnen per Shift-Rechtsklick eine Bearbeitungs-GUI fuer Item, Menge, Preis bzw. Tauschmenge und Display-Typ.
- PlayerShop-Hauptmenue mit allen Shops, eigener Shopliste und Suche.
- Suche per Chat nach Item, Material, Besitzer oder Shop-Typ, mit deutschen und englischen Suchbegriffen.
- Eigene Shops koennen im GUI bearbeitet, per Rechtsklick am Schild besucht und per Shift-Linksklick mit Bestaetigungs-GUI geloescht werden.
- PlayerShop-Listen haben Seiten-Navigation fuer groessere Shopmengen.
- Shop-Lore in der Liste zeigt Typ, Besitzer, Menge, Preis bzw. Tauschdaten, Lagerbestand, Koordinaten, Display-Typ und Klickaktionen.
- PlayerShop-GUI-Titel, Slots, Buttons, Bearbeitungs-GUI und Shop-Lore sind ueber `gui/<sprache>/playershop.yml` konfigurierbar.
- Regelmaessiger Cleanup entfernt Shops aus Cache und Datenbank, wenn Kiste oder Schild physisch fehlen.
- Fremde Spieler koennen PlayerShop-Kisten nicht als normales Inventar oeffnen.
- Besitzer oder Admins koennen einen Shop durch Abbauen von Schild oder Kiste loeschen.
- PlayerShop-Kaeufe und Ankaeufe werden in den Transaktionslogs gespeichert.
- Optionaler PlotSquared-Hook entfernt PlayerShops aus Cache und Datenbank, wenn ein Plot geloescht wird.
- BUY-, BUY_SELL- und TRADE_ITEM-Shops sind aktiv und ueber Chat-Erstellung, Schildmarker und GUI-Bearbeitung eingebunden.

### Commands

- `/shop`
- `/shop reload`
- `/shop language`
- `/shop language <sprache>`
- `/shop sellhand`
- `/shop sellall`
- `/shop sellgui`
- `/shop search <suchbegriff>`
- `/shop favorites`
- `/shop playershop`
- `/shop playershop search`
- `/shop playershop mine`
- `/servershop`
- `/sellhand`
- `/sellall`
- `/sellgui`
- `/cshop reload`
- `/cshop admin`
- `/cshop admin editor`
- `/cshop admin servershop`
- `/cshop admin backup`
- `/cshop admin backup list`
- `/cshop admin backup restore <datei>`
- `/cshop admin backup confirm`
- `/cshop admin backup cancel`
- `/cshop admin backups`
- `/trade toggle`
- `/trade on`
- `/trade off`
- `/trade <spieler>`
- `/trade accept`
- `/trade deny`
- `/trade cancel`
- `/asc`
- `/asc list`
- `/asc create`
- `/asc give <spieler> [menge]`
- `/asc toggle`
- `/asc remove`
- `/asc admin [suche]`
- `/cshop admin import economyshopgui preview`
- `/cshop admin import economyshopgui apply [merge|replace]`
- `/cshop admin import economyshopgui rollback <importId>`
- `/cshop admin import shopintuitive preview`
- `/cshop admin import shopintuitive apply [merge|replace]`
- `/cshop admin import shopintuitive rollback <importId>`

### Permissions

- `craftplayshop.use` - Grundrecht für `/shop` und das Hauptmenü.
- `craftplayshop.admin` - Admin-Grundrecht für CraftplayShop.
- `craftplayshop.reload` - `/shop reload` und `/cshop reload`.
- `craftplayshop.servershop.use` - ServerShop öffnen.
- `craftplayshop.servershop.buy` - Items im ServerShop kaufen.
- `craftplayshop.servershop.sell` - Items im ServerShop verkaufen.
- `craftplayshop.servershop.sellhand` - `/sellhand` und `/shop sellhand`.
- `craftplayshop.servershop.sellall` - `/sellall` und `/shop sellall`.
- `craftplayshop.servershop.sellgui` - `/sellgui` und `/shop sellgui`.
- `craftplayshop.playershop.use` - PlayerShop-Menü, Shoplisten und Suche öffnen.
- `craftplayshop.playershop.create` - Kistenshops erstellen.
- `craftplayshop.playershop.buy` - Bei SELL-PlayerShops einkaufen.
- `craftplayshop.playershop.sell` - An BUY-PlayerShops verkaufen.
- `craftplayshop.playershop.edit` - Eigene PlayerShops per GUI bearbeiten.
- `craftplayshop.playershop.admin` - Fremde PlayerShops administrativ bearbeiten oder löschen.
- `craftplayshop.language` - Eigene Sprache umstellen.
- `craftplayshop.trade.use` - DirectTrade-Befehl grundsätzlich nutzen.
- `craftplayshop.trade.toggle` - `/trade toggle`, `/trade on`, `/trade off`.
- `craftplayshop.trade.request` - Handelsanfragen senden.
- `craftplayshop.trade.accept` - Handelsanfragen annehmen.
- `craftplayshop.autosellchest.use` - AutoSellChest-Menue und eigene Kisten anzeigen.
- `craftplayshop.autosellchest.create` - AutoSellChest-Spezialkisten platzieren.
- `craftplayshop.autosellchest.trust` - Trust-GUI fuer eigene/vertraute AutoSellChests nutzen.
- `craftplayshop.autosellchest.admin` - AutoSellChests administrativ geben, bearbeiten und entfernen.
- `craftplayshop.autosellchest.limit.<key>` - Konfigurierbare AutoSellChest-Limits aus `autoSellChest.maxChests.permissionOverrides`.

### AutoSellChest

- AutoSellChest ist fuer v0.2.0 integriert und produktiv nutzbar.
- Admins geben Spezialkisten ueber `/asc give <spieler> [menge]` aus.
- Spieler platzieren das Spezialitem, um eine AutoSellChest zu registrieren.
- Spieler koennen mit `/asc create` eine angeschaut normale Kiste als AutoSellChest registrieren, wenn sie die Erstell-Permission haben.
- AutoSellChests werden in SQLite gespeichert und beim Reload neu geladen.
- Bestehende AutoSellChest-Datensaetze werden automatisch um Upgrade-Spalten erweitert.
- Es werden nur bekannte Kisten verarbeitet; es gibt keinen Welt-Scan.
- Hopper- und Inventar-Events markieren Kisten als dirty, damit Einfuegen gebuendelt verkauft wird.
- Der Verkaufs-Takt, Dirty-Cooldown, maximale Kisten pro Durchlauf und maximale Items pro Scan sind in `config.yml` konfigurierbar.
- Kisten koennen eigene Intervall-Upgrades kaufen; dadurch verkauft die Kiste schneller.
- Kisten koennen eigene Multiplikator-Upgrades kaufen; dadurch zahlt der ServerShop-Verkauf mehr aus.
- Upgrade-Level, Namen, Preise, Zielwerte und Permissions sind in `autoSellChest.upgrades` konfigurierbar.
- Upgrades sind global deaktivierbar; Intervall- und Multiplikator-Kategorien sind getrennt deaktivierbar.
- Einzelne Upgrade-Level koennen per `enabled: false` aus der Config deaktiviert werden.
- Upgrade-Kosten werden erst nach Permission- und Geldpruefung ueber Vault abgezogen.
- Items werden nur verkauft, wenn sie im CraftplayShop ServerShop ankaufbar sind.
- Nicht ankaufbare Items bleiben in der Kiste.
- Beschaedigte Items koennen separat fuer AutoSellChest blockiert werden, ohne normale ServerShop-Verkaeufe zu veraendern.
- AutoSellChest kann zusaetzlich ueber eine eigene Material-Blocklist oder Allowlist gefiltert werden.
- Besitzer erhalten Vault-Geld, auch wenn sie offline sind.
- AutoSellChest-Verkaeufe werden in `craftplay_shop_autosell_logs` gespeichert.
- Wenn der Besitzer online ist, wird zusaetzlich ein Transaktionslog vom Typ `AUTOSELL_CHEST` geschrieben.
- Fremde Spieler koennen AutoSellChests nicht normal oeffnen, wenn der Schutz aktiv ist.
- Hopper-Extraktion, Hopper-Insertion und Explosionsschutz sind konfigurierbar.
- Shift-Rechtsklick auf eine AutoSellChest oeffnet das Info-GUI.
- Das AutoSellChest-GUI zeigt eigene Kisten, Status, Koordinaten, Intervall, Multiplikator, verkaufte Items und verdientes Geld.
- Ueber AutoSellChests wird optional eine TextDisplay-Anzeige mit Name, Besitzer, Status, naechstem Verkauf und Multiplikator dargestellt.
- Debuggruende zeigen jetzt genauer, ob Items z.B. am Materialfilter, an beschaedigten Items oder an fehlenden ServerShop-Eintraegen scheitern.
- Das Info-GUI enthaelt Status-Toggle, Teleport, Upgrades, Statistiken und Loeschen.
- Besitzer-Benachrichtigungen sind pro Kiste schaltbar; Config unterstuetzt Modus, Mindestwert und Cooldown.
- Das Statistik-GUI zeigt Tageswerte, Gesamtwerte, letzte Verkaeufe und Material-Auswertung direkt aus den AutoSellChest-Logs.
- Admins koennen mit `/asc admin [suche]` eine globale AutoSellChest-Uebersicht oeffnen, filtern, Details anzeigen, zur Kiste teleportieren und Kisten per Bestaetigung loeschen.
- Die Admin-Suche versteht gezielte Filter wie `owner:`, `world:`, `status:`, `notify:` und `trust:`.
- Admins koennen Name, Besitzer, Aktivstatus sowie Intervall- und Multiplikator-Level direkt im Admin-GUI bearbeiten.
- AutoSellChest-Trust ist integriert: Besitzer koennen Spieler hinzufuegen und Rechte fuer Oeffnen, Verwalten, Upgrades und Loeschen getrennt schalten.
- Trust-Eintraege lassen sich direkt im GUI per Rechtsklick schnell entfernen.
- Linksklick im GUI oeffnet Details, Rechtsklick teleportiert zur Kiste, Shift-Linksklick oeffnet die Loeschbestaetigung.
- Die Loeschfunktion im GUI nutzt eine Bestaetigungs-GUI.
- Regelmaessiger Cleanup entfernt AutoSellChests aus Cache und Datenbank, wenn die physische Kiste fehlt.
- Optionaler PlotSquared-Hook entfernt AutoSellChests aus Cache und Datenbank, wenn ein Plot geloescht wird.
- PlotSquared-Pruefungen fuer Erstellen, Bearbeiten und Abbauen sind vorbereitet; Shop-Nutzung bleibt ohne PlotSquared-`use`-Flag erlaubt.

### DirectTrade

- DirectTrade Spieler-Toggle.
- `/trade toggle`, `/trade on`, `/trade off`.
- `/trade <spieler>`, `/trade accept`, `/trade deny`, `/trade cancel`.
- Anfragen respektieren Cooldown, Timeout, Distanz und optional gleiche Welt.
- Beide Spieler sehen ein gemeinsames Handels-GUI mit eigenen und fremden Angebotsfeldern.
- Items werden aus dem Inventar in die Angebotsseite verschoben und bei Abbruch sauber zurueckgegeben.
- Geldangebote sind im GUI moeglich.
- Aenderungen setzen Ready- und Final-Status zurueck, wenn dies in der Config aktiv ist.
- Optionaler Final-Confirm vor dem Abschluss.
- Vor Abschluss werden Online-Status, Geld und Inventarplatz geprueft.
- Speicherung des DirectTrade-Toggles in `craftplay_shop_player_settings`.
- Transaktionslogging fuer abgeschlossene Trades ist eingebunden.

### AuctionHouse

- Eigenes AuctionHouse-Modul mit globalen Listings.
- `/ah`, `/ah browse`, `/ah search`, `/ah sell <preis> [menge]`, `/ah mine`, `/ah claims`.
- GUI-Startseite, Browse-Ansicht, eigene Listings und Claim-Ansicht.
- Listings werden aus dem Item in der Haupthand erstellt.
- Konfigurierbare Listing-Gebuehr aus fester Gebuehr und Prozentanteil.
- Konfigurierbare Standardlaufzeit fuer Listings.
- Kauf prueft Verfuegbarkeit, Geld und Inventarplatz.
- Verkaufserloes wird direkt per Vault an den Verkaeufer ausgezahlt.
- Abgebrochene oder abgelaufene Listings koennen abgeholt werden.
- AuctionHouse-Kaeufe und -Listings laufen ueber das vorhandene Transaktionslogging.

### PermissionShop

- PermissionShop mit Produkten aus der `config.yml`.
- Produkte unterstuetzen Preis, Slot, Material, Lore, Dauer und One-Time-Kaeufe.
- Produkte koennen mehrere Permissions, Console-Commands und Player-Commands enthalten.
- Rechte werden ueber konfigurierbare Command-Formate ausgefuehrt.
- Required-Permissions und Already-Owned-Pruefung sind eingebaut.
- GUI ist integriert und ueber das Hauptmenue erreichbar.
- Kaeufe werden ueber das Transaktionslogging erfasst.

### RankShop

- RankShop mit Produkten aus der `config.yml`.
- Produkte unterstuetzen permanente und temporaere Gruppen.
- Rang-Upgrades koennen alte Gruppen per Command-Format entfernen.
- Required-Permissions und Already-Owned-Pruefung sind eingebaut.
- GUI ist integriert und ueber das Hauptmenue erreichbar.
- Kaeufe werden ueber das Transaktionslogging erfasst.

### ReferralSystem

- Referral-Codes werden pro Spieler automatisch erzeugt und gespeichert.
- Referral-GUI mit eigenem Code, Redeem-Flow und Top-Werbern.
- Redeem per Chat-Eingabe oder ueber `/shop referral redeem <code> [paket]`.
- Mehrere Reward-Pakete aus der `config.yml`.
- Belohnungen fuer Werber und Geworbenen mit Geld, Commands und Items.
- Pending Rewards fuer Items, wenn der Spieler offline ist oder das Inventar voll ist.
- Mindestspielzeit und Selbst-Einloesungsschutz gegen einfachen Missbrauch.
- Top-Werber werden aus erfolgreichen Einloesungen berechnet.

### Importer

- EconomyShopGUI-Premium Importer mit Preview, Apply und Rollback.
- Importiert `shops/*.yml` aus EconomyShopGUI-Premium in die `server_shop.yml` von CraftplayShop.
- `merge` behaelt bestehende CraftplayShop-Kategorien und ersetzt nur gleichnamige importierte Kategorien.
- `replace` ersetzt den kompletten ServerShop nach einem automatischen Backup.
- Shop-Importer fuer `Shop - the intuitive shop plugin` mit Preview, Apply und Rollback.
- Liest die alten `Shop/Data/*.yml` Dateien pro Besitzer ein.
- Importiert `SELL`, `BUY`, `COMBO` und `BARTER` in `SELL`, `BUY`, `BUY_SELL` und `TRADE_ITEM`.
- Gamble-Shops werden bewusst uebersprungen.
- Shops ohne vorhandenes Schild oder ohne vorhandene Kiste werden beim echten Apply uebersprungen.
- Jeder Import schreibt einen Bericht nach `plugins/CraftplayShop/imports/reports/` und legt Backups unter `plugins/CraftplayShop/imports/backups/` an.

### Skeletons / vorbereitet

Folgende Module sind strukturell vorbereitet, aber noch nicht vollstaendig umgesetzt:

- PlayerShop-Trust, erweiterter Finder und erweiterte Verwaltungs-GUI
- weitergehende Protection-Logik fuer WorldGuard, Lands und BentoBox ueber die aktuelle Hook-Erkennung hinaus
- PlaceholderAPI, HeadDatabase, Floodgate, Citizens, Discord

### Datenbank

- SQLite ist produktiv aktiv.
- MySQL/MariaDB ist jetzt als echte JDBC-Option implementiert und nicht mehr nur Platzhalter.
- Tabellen werden beim Start fuer beide Datenbanktypen erstellt.
- Wichtige Upsert-Pfade wie Player-Settings und AutoSellChest-Trust unterstuetzen jetzt SQLite und MySQL getrennt korrekt.

## Wichtige Konfigurationshinweise

Vorhandene Config-, Language- und GUI-Dateien werden beim Start und Reload automatisch um fehlende Keys ergaenzt, solange diese Option aktiv ist:

```yml
files:
  autoUpdateExisting: true
```

ServerShop-Backups werden hier konfiguriert:

```yml
adminShop:
  backups:
    enabled: true
    folder: "backups/server_shop"
    cooldownSeconds: 30
    maxFiles: 25
    listLimit: 8
```

## Build

```bash
mvn clean package
```

Die fertige Plugin-JAR liegt danach unter `target/CraftplayShop-1.0.0.jar`.

## Lizenz

Proprietaer. Nutzung erlaubt. Keine Modifikation, Redistribution oder Reverse Engineering ohne Erlaubnis.

Copyright Wullverin @ Craftplay.de
