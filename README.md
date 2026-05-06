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
- Sauberes Deaktivieren, wenn Vault benoetigt wird und fehlt.
- SQLite-Datenbankbasis.
- Tabellen fuer Transaktionen, Spieler-Einstellungen, Imports und Import-Mappings.
- Async Transaktionslogging.
- Spieler-Einstellungen mit Sprache und DirectTrade-Status.

### ServerShop

- ServerShop wird aus `server_shop.yml` geladen.
- Kategorien mit ID, Anzeigename, Icon, Lore, Slot und Aktiv-Status.
- Items mit ID, Material, Anzeigename, Lore, Kaufpreis, Verkaufspreis, Kauf-/Verkaufsstatus und Slot.
- Haupt-GUI fuer Kategorien.
- Kategorie-GUI fuer Shop-Items.
- Kaufen per Linksklick.
- Stack kaufen per Shift-Linksklick.
- Verkaufen per Rechtsklick.
- Stack verkaufen per Shift-Rechtsklick.
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
- Lore von Kategorien und Items per Chat bearbeiten.
- Kaufpreis und Verkaufspreis per Klick erhoehen oder verringern.
- Kaufpreis und Verkaufspreis direkt per Chat setzen.
- Kaufen/Verkaufen pro Item toggeln.
- Kategorien aktivieren/deaktivieren.
- Kategorien und Items duplizieren.
- Kategorien und Items loeschen mit Sicherheitsabfrage.
- Kategorien und Items per Shift-Klick sortieren/anordnen.
- Material-Picker mit Seitennavigation.
- Material-Picker mit Suchfunktion.
- Automatische Backups vor Aenderungen an `server_shop.yml`.
- Manueller Backup-Button in der AdminShop-Kategorieuebersicht.
- Backups mit konfigurierbarem Ordner, Cooldown und Maximalanzahl.
- Manuelles ServerShop-Backup per GUI-Button und Admin-Befehl.
- Auflisten der letzten ServerShop-Backups per Admin-Befehl.

### Commands

- `/shop`
- `/shop reload`
- `/shop language`
- `/shop language <sprache>`
- `/shop sellhand`
- `/shop sellall`
- `/shop sellgui`
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

- PlayerShop / ChestShop
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
