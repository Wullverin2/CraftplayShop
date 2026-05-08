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
- Platzhalter in GUI-Namen, Lores und Button-Texten.
- PlaceholderAPI-Hook vorbereitet und optional.
- HeadDatabase-Hook vorbereitet und optional.
- GUI-Sicherheitslogik gegen Item-Entnahme, Drag, Shift-Click und Number-Key.

### Economy und Datenbank

- Vault Economy Hook.
- Vault-Auszahlung an Offline-Spieler fuer PlayerShop-Verkaeufe.
- Sauberes Deaktivieren, wenn Vault benoetigt wird und fehlt.
- SQLite-Datenbankbasis.
- Tabellen fuer Transaktionen, Spieler-Einstellungen, Imports und Import-Mappings.
- Tabelle fuer laufenden ServerShop-Stock.
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
- Mindest- und Maximalmengen pro Shop-Item fuer Kaufen und Verkaufen.
- Sell-All respektiert Verkaufs-Mindestmengen und deckelt konfigurierte Maximalmengen.
- Konfigurierbare Bestaetigungs-GUI fuer teure Kaeufe.
- Creative- und Spectator-Verkaufsschutz gegen ServerShop-, Sell-Hand-, Sell-All- und Sell-GUI-Verkaeufe.
- DB-basiertes Stock-System pro ServerShop-Item.
- Geaenderter Stock wird im Speicher gehalten und gebuendelt in die Datenbank geschrieben.
- Kaufen reduziert den Bestand, Verkaufen erhoeht ihn.
- Zu wenig Bestand blockiert Kaeufe, voller Bestand blockiert oder begrenzt Verkaeufe.
- Stock-Anzeige in ServerShop-GUI und AdminShop-Lore.
- `server_shop.yml` liefert nur den Startbestand fuer neue Stock-Zeilen; laufender Bestand wird in der Datenbank gespeichert.
- AdminShop-Editor kann Kauf-/Verkaufslimits, Stock, Max-Stock und Stock-Aktivierung bearbeiten.
- Konfigurierbare ServerShop-Schutzregeln fuer Welten und GameModes.
- Verkauf beschaedigter Items kann blockiert werden.
- ServerShop-Suche nach Item-Name, Item-ID, Material, Kategorie-ID und Kategorie-Name.
- Favoriten-System pro Spieler mit SQLite-Speicherung.
- Favoriten-GUI und Such-GUI mit Kaufen/Verkaufen per Mengenauswahl.
- Shift-Klick auf ein ServerShop-Item setzt oder entfernt den Favoriten.
- Permission-Pruefungen fuer Kaufen und Verkaufen.
- Anti-Dupe-Grundlogik mit Pruefung vor Ausfuehrung und Rollback-Versuch.
- Sell Hand.
- Sell All.
- Sell GUI.
- Transaktionslogs fuer Kauf, Verkauf und Sell-All.
- Custom Category- und Item-Lore wird im ServerShop angezeigt.
- Deaktivierte Kategorien sind im normalen ServerShop verborgen.

### AdminShop / Ingame Editor

- Ingame ServerShop-Editor ueber Admin-GUI.
- Kategorien anzeigen und bearbeiten.
- Items anzeigen und bearbeiten.
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

- Erster PlayerShop-SELL-MVP ist aktiv.
- Spieler erstellen einen Kistenshop per Shift-Linksklick mit Item in der Hand auf eine Kiste.
- Der Chat-Assistent fragt ab, ob der Shop Items verkaufen oder ankaufen soll.
- Danach fragt der Chat-Assistent Menge pro Transaktion und Preis ab.
- CraftplayShop setzt automatisch ein Shop-Schild an die angeklickte Stelle der Kiste.
- Spieler koennen alternativ weiterhin manuell ein Schild mit `shop`, `sell`, `[shop]`, `[cshop]` oder `buy` neben eine Kiste setzen.
- Zeile 2 des Schilds bestimmt die Verkaufsmenge, Zeile 3 den Preis.
- Das verkaufte Item wird beim Erstellen aus dem Item in der Hand uebernommen.
- Rechtsklick auf SELL-Shop-Schild oder Kiste kauft die konfigurierte Menge aus dem Kistenbestand.
- Rechtsklick auf BUY-Shop-Schild oder Kiste verkauft die konfigurierte Menge an den Shop und legt die Items in die Shop-Kiste.
- Vor dem Kauf werden Permission, Geld, Lagerbestand und Inventarplatz geprueft.
- Bei Fehlern versucht der Kauf ein Rollback von Geld und Items.
- Besitzer erhalten das Geld ueber Vault, auch wenn sie offline sind.
- BUY-Shops ziehen dem Besitzer die Kosten per Vault ab und zahlen sie dem verkaufenden Spieler aus.
- Ueber Kistenshops schwebt eine konfigurierbare Item-Anzeige.
- Verfuegbare Display-Typen: `NONE`, `ITEM`, `GLASS_CASE`, `LARGE_ITEM`, `ITEM_FRAME`.
- Besitzer oder Admins oeffnen per Shift-Rechtsklick eine Bearbeitungs-GUI fuer Item, Menge, Preis und Display-Typ.
- PlayerShop-Hauptmenue mit allen Shops, eigener Shopliste und Suche.
- Suche per Chat nach Item, Material, Besitzer oder Shop-Typ, mit deutschen und englischen Suchbegriffen.
- Eigene Shops koennen im GUI bearbeitet, per Rechtsklick am Schild besucht und per Shift-Linksklick geloescht werden.
- Shop-Lore in der Liste zeigt Typ, Besitzer, Menge, Preis, Lagerbestand, Koordinaten, Display-Typ und Klickaktionen.
- PlayerShop-GUI-Titel, Slots, Buttons und Shop-Lore sind ueber `gui/<sprache>/playershop.yml` konfigurierbar.
- Regelmaessiger Cleanup entfernt Shops aus Cache und Datenbank, wenn Kiste oder Schild physisch fehlen.
- Fremde Spieler koennen PlayerShop-Kisten nicht als normales Inventar oeffnen.
- Besitzer oder Admins koennen einen Shop durch Abbauen von Schild oder Kiste loeschen.
- PlayerShop-Kaeufe werden in den Transaktionslogs gespeichert.
- Optionaler PlotSquared-Hook entfernt PlayerShops aus Cache und Datenbank, wenn ein Plot geloescht wird.
- BUY-, BUY_SELL- und TRADE_ITEM-Shops sind weiterhin vorbereitet, aber noch nicht vollstaendig umgesetzt.

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
- `/asc` als saubere Noch-nicht-verfuegbar-Meldung

### DirectTrade Vorbereitung

- DirectTrade Spieler-Toggle.
- `/trade toggle`, `/trade on`, `/trade off`.
- Speicherung in `craftplay_shop_player_settings`.
- Vollstaendiger DirectTrade ist noch nicht implementiert.

### Skeletons / vorbereitet

Folgende Module sind strukturell vorbereitet, aber noch nicht vollstaendig umgesetzt:

- PlayerShop BUY, BUY_SELL, TRADE_ITEM, Trust, Finder und Verwaltungs-GUI
- AutoSellChest
- vollstaendiger DirectTrade
- AuctionHouse
- RankShop
- PermissionShop
- ReferralSystem
- Protection Hooks
- PlaceholderAPI, HeadDatabase, Floodgate, Citizens, Discord
- EconomyShopGUI Importer
- Shop Intuitive Importer
- MySQL/MariaDB Service

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

Die fertige Plugin-JAR liegt danach unter `target/CraftplayShop-0.1.0.jar`.

## Lizenz

Proprietaer. Nutzung erlaubt. Keine Modifikation, Redistribution oder Reverse Engineering ohne Erlaubnis.

Copyright Wullverin @ Craftplay.de
