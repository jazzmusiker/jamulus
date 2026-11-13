<!--
Copyright (c) 2026 Thomas Mueller
Author(s): Thomas Mueller
Licensed under AGPL 3.0 or any later version. See COPYING for details.
-->

# Produktspezifikation: Jamulus mit Compose Multiplatform

## 1. Zweck und Ausgangslage

Dieses Dokument beschreibt eine Anwendung für Jamulus mit einer Oberfläche auf Basis von Compose Multiplatform. Sie soll die bestehende Oberfläche für Musiker auf Windows, macOS, Linux und Android funktional ersetzen. Der Serverbetrieb mit Oberfläche bleibt Desktop-Systemen vorbehalten; Android unterstützt in diesem Vorhaben ausschließlich den Client. iOS ist kein Produktziel dieser Spezifikation.

Die heutige Anwendung ist ein gemeinsames Qt/C++-Programm. Client und Server verwenden denselben Netzwerk- und Audiokern. Der Client verarbeitet Echtzeit-Audio mit OPUS über UDP und greift auf plattformspezifische Audio-APIs zu (unter anderem ASIO, Core Audio und Oboe auf Android). Der Server mischt die Audioströme. Compose darf diese zeitkritischen Aufgaben nicht in Kotlin oder im UI-Thread ausführen.

**Machbarkeit:** Compose Multiplatform kann eine gemeinsame Compose-Oberfläche für Windows-, macOS- und Linux-Desktop sowie Android bereitstellen. Es ersetzt weder Jamulus' Audio-Backends noch sein Netzwerkprotokoll. Der bestehende Android-Build verwendet Qt und Oboe; die Oberfläche und der Android-Einstiegspunkt müssen daher von `QtActivity` auf eine Android/Compose-Activity umgestellt werden, während der native Audiokern erhalten bleibt. Ein vollständiger Kotlin-Neubau wäre technisch möglich, aber für das Stabilitäts- und Latenzziel nicht der empfohlene Weg: Er müsste Audio-Ein-/Ausgabe, OPUS, Jitter-Puffer, UDP-Protokoll, Servermischer, MIDI und Aufnahme neu implementieren und über alle Plattformen erneut absichern.

**Empfohlene Architektur:** Compose Multiplatform als UI und der bestehende C++-Kern als native Bibliothek im selben Anwendungsprozess. Eine schmale, stabile C-ABI mit JNI-Bindings bietet typisierte Befehle, Snapshots und Ereignisse. Die C++-Engine läuft mit eigenem Lebenszyklus und eigenen Threads; UI- und JNI-Aufrufe gelangen nie in den Audiothread. Die bestehende JSON-RPC-API bleibt für externe Steuerprogramme verfügbar, ist aber nicht der interne UI-Transport. Die bestehende Wire-Kommunikation zwischen Jamulus-Clients, Servern und Verzeichnissen bleibt unverändert.

Die vorhandene JSON-RPC-API deckt bereits unter anderem Serverlisten, Profile, Chat, Mixer-Fader, Mute, MIDI-Einstellungen und Server-Aufnahme ab. Sie bietet derzeit nicht den gesamten UI-Umfang, insbesondere keine vollständige Abfrage/Konfiguration von Audio-Geräten und keine vollständige Laufzeitsteuerung des Verbindungsablaufs. Die native UI-Schnittstelle muss deshalb die übrigen nötigen Funktionen ergänzen. Die öffentliche JSON-RPC-Schnittstelle bleibt separat rückwärtskompatibel. Kein unvollständiger API-Funktionsumfang darf stillschweigend als Funktionsparität gelten.

## 2. Produktziele

1. Musiker können Jamulus ohne Qt-Oberfläche starten, Audio konfigurieren, einen Server finden oder direkt anwählen und live mitspielen; das gilt für Windows, macOS, Linux und Android.
2. Während einer Sitzung können sie den persönlichen Mix mit niedriger Bedienlatenz regeln, ihren Sendestream stummschalten und chatten.
3. Betreiber können einen Jamulus-Server auf Windows, macOS und Linux mit der Compose-Oberfläche konfigurieren, überwachen und Aufnahmen steuern. Der headless Betrieb ohne Compose bleibt möglich.
4. Client und Server bleiben mit bestehenden Jamulus-Versionen und Verzeichnisdiensten kompatibel.
5. Vorhandene Nutzerprofile und sinnvolle Client-Einstellungen werden beim Wechsel übernommen oder nachvollziehbar migriert.
6. Eine blockierte oder beendete Oberfläche darf den C++-Audiokern nicht in einem unsicheren Zustand hinterlassen.
7. Die Oberfläche ist in Compose-Komponenten und Funktionsbereiche gegliedert, damit Ansichten neu angeordnet und erweitert werden können, ohne Audio-/Netzwerklogik oder Engine-Schnittstelle umzubauen. Frei durch Nutzer anpassbare/verschiebbare Panels sind kein MVP-Ziel, solange dies nicht separat festgelegt wird.

## 3. Zielgruppe und Betriebsarten

- **Musiker:** richtet Audiogeräte und Profil ein, sucht einen Jamulus-Server, spielt mit und mischt die anderen Teilnehmenden lokal.
- **Serverbetreiber:** startet und überwacht einen Server, pflegt dessen Verzeichniseintrag und Begrüßungstext und steuert Aufnahmen.
- **Fortgeschrittene Nutzer:** verwenden MIDI-Regler, benutzerdefinierte Verzeichnisse, Kommandozeilenparameter und bestehende Konfigurationsdateien.

Auf Windows, macOS und Linux wählt der Nutzer beim Start Client- oder Serverbetrieb. Ein als Server gestarteter Prozess kann nicht gleichzeitig Client sein, entsprechend dem bestehenden Jamulus-Betriebsmodell. Auf Android startet ausschließlich der Client. Ein Wechsel der Betriebsart erfordert das Beenden der laufenden Engine.

## 4. Funktionsumfang

### 4.1 Client: Server finden und verbinden

- Serverlisten aus allen Standardverzeichnissen und gespeicherten benutzerdefinierten Verzeichnissen abrufen und zu einer Gesamtliste zusammenführen. Die Verbindungsansicht bietet keine Verzeichnisauswahl.
- Den Status der Gesamt-Abfrage verständlich anzeigen (Laden, aktuell, leer, Fehler/Timeout); Teilausfälle einzelner Verzeichnisse melden und erreichbare Server weiter anzeigen.
- Server nach Name, Stadt, Land, Stil/Genre und Text filtern; die Liste standardmäßig nach gemessener Pingzeit aufsteigend sortieren und Server ohne Messwert ans Ende stellen. Ein alleinstehendes `#` filtert auf belegte Server. Server nach Stadt/Land gruppieren, soweit diese Angaben vorhanden sind.
- Serverdetails anzeigen: Name mit Stadt und Land in Klammern, danach „ - “ und die zugehörigen Verzeichnisnamen, Genre, Anzahl der Musiker, Ping/Latenz und verfügbare Sitzungsinformationen, soweit das Protokoll sie liefert.
- Option „alle Musiker anzeigen“ sowie Servergruppen ein- und ausklappen.
- Server durch Auswahl und explizite Aktion oder Doppelklick anwählen. Direkte Adresse `host[:port]` eingeben. Eine sichtbare Liste zuletzt verwendeter Serveradressen ist nicht Teil der gewünschten Oberfläche.
- Während des Verbindungsaufbaus Fortschritt/Fehler zeigen. Verbindung jederzeit trennen; erneute Verbindung zu einer anderen Adresse muss ohne App-Neustart möglich sein.
- IPv4 und IPv6 entsprechend den Jamulus-Netzwerkoptionen unterstützen.
- Bei Verbindungsabbruch Status und Wiederverbindungsmöglichkeit anzeigen; keinen hängenden „verbunden“-Zustand darstellen.

### 4.2 Client: Sitzung und persönlicher Mix

- Hauptansicht zeigt den eigenen Eingangspegel für links/rechts, Ein-/Ausgangsstatus, Verbindung, Ping, Audiodelay sowie die vorhandenen Delay- und Jitterbuffer-Warnzustände.
- Große, jederzeit erreichbare Aktion zum Verbinden/Trennen. Eigener Sendestream (Mute) separat vom lokalen Mithören und bereits vor dem Verbinden schaltbar, um Rückkopplungen beim Verbindungsaufbau zu verhindern; Zustand deutlich sichtbar.
- Dynamisches Mischpult mit eigenem Kanal und den verbundenen Musikern. Je Kanal: Name/Fader-Tag, Instrumenticon und -name, Länderflagge, Pegelanzeige und Lautstärkefader. Mute und Solo stehen links neben dem Lautstärkeregler und sind bei Aktivierung rot hervorgehoben. Der grüne Kanalpegel liegt im Lautstärkeregler; dessen Griff steuert den lokalen Fader. Ein L/R-Schalter klappt den Panorama-Regler ein und aus. Mehrere Solo-Kanäle sind erlaubt. Fadergruppen bewegen Kanäle synchron.
- Fader, Panorama und Gruppen lokal persistieren; Zuordnung zu einem wiedererkannten Musiker wie bisher anhand der Jamulus-Einstellungen. Neue Kanäle verwenden den einstellbaren Standardpegel.
- Kanäle bei vielen Teilnehmenden übersichtlich anordnen; Anzahl der Mischpultzeilen einstellen. Kanalsortierung unterstützen, soweit sie in den bestehenden Einstellungen auswählbar ist.
- Eigener Eingangspegel, Eingangsverstärkung und Reverb samt Reverb-Kanalwahl links/rechts bedienen. Panoramaverzögerung muss die bestehende Server-/Client-Semantik einhalten.
- Option für automatische Pegelanpassung und Feedback-Erkennung entsprechend dem Bestand.
- Eingehende Chatnachrichten anzeigen und Text senden. Chatverlauf leeren. `http`/`https`- und `www.`-Links anklickbar machen und im Systembrowser öffnen.
- Audiobenachrichtigungen entsprechend der Einstellung aktivieren/deaktivieren.

### 4.3 Client: Profil und Audioeinstellungen

**Profil:** Alias/Name, Instrument, Land/Region, Stadt und Kenntnisstand (Anfänger, Fortgeschritten oder Experte/keine Angabe). Listenwerte und Instrumentkennungen müssen aus dem bestehenden Jamulus-Kern stammen, nicht dupliziert oder unabhängig neu nummeriert werden.

**Audio und Netzwerk:**

- Verfügbare Soundgeräte aus dem C++-Audio-Backend anzeigen und Auswahl speichern.
- Ein- und Ausgangskanäle links/rechts zuordnen, sofern das Backend diese Auswahl anbietet.
- Mono/Stereo und Audioqualität/Codec nach den aktuell unterstützten Jamulus-Modi wählen.
- Bevorzugte, Standard- und sichere Soundkartenpuffer-Latenz anbieten; nicht unterstützte Modi deaktivieren. Den tatsächlich aktiven Puffer anzeigen.
- Lokalen und serverseitigen Jitterpuffer anzeigen/konfigurieren sowie automatische Jitterpufferung und kleine Netzwerkpuffer unterstützen.
- Upstream-/Datenraten-Information anzeigen, wenn sie vom Kern bereitgestellt wird.
- Änderungen an Audioformat, Gerät oder Puffer mit dem Engine-Verhalten abstimmen: sofort übernehmen, kontrolliert Audiogerät neu öffnen oder Neustart verlangen. Die Oberfläche muss die tatsächliche Wirkung und Fehler melden.
- Pegel-, Delay- und Pufferzustand aus dem Kern abonnieren; keine hochfrequenten UI-Polls und keine Audioverarbeitung auf dem UI-Thread.

### 4.4 Client: erweiterte Optionen und MIDI

- Benutzerdefinierte Verzeichnisse verwalten; sie werden zusammen mit allen Standardverzeichnissen in der Server-Gesamtansicht berücksichtigt.
- Standard-Faderpegel für neu auftauchende Teilnehmende, Eingangsverstärkung, Feedback-Erkennung, Verzögerungs-Panning, Skin/Meter-Darstellung, UI-Sprache, Audioalarme und Mixer-Zeilen konfigurieren.
- Unterstützte MIDI-Eingabegeräte auflisten. MIDI-Kanal, Pickup-Modus, eigene Stummschaltung sowie Steuerung von Fader, Panorama, Mute und Solo einrichten.
- MIDI-Learn-Workflow: Lernaktion starten, empfangenen Controller zuordnen, abbrechen und Konflikte verständlich anzeigen. MIDI-Verarbeitung verbleibt im nativen Kern.
- Optionen, die ein Audio-Neustarten benötigen, als solche kennzeichnen und erst nach Bestätigung anwenden.

### 4.5 Serverbetrieb

- Serverstart/Serverstopp und Laufstatus anzeigen; Port, maximale Kanäle und Netzwerkschnittstellen nach den bestehenden Startoptionen konfigurieren.
- Verbundene Clients als Tabelle anzeigen: IP:Port (falls angezeigt), Name, Jitterpuffer und Kanalzahl. Versions-/Betriebssysteminformationen anzeigen, wenn vorhanden.
- Serverprofil bearbeiten: Name, Stadt, Land/Region, Verzeichnisart/-adresse, Begrüßungstext. Status der Verzeichnisregistrierung samt verständlicher Fehler-/Wartezustände zeigen.
- Serverliste persistent in einer Datei speichern; Datei auswählen oder Einstellung löschen.
- Verzögerungs-Panning und unterstützte Start-/Netzwerkoptionen verwalten.
- Jam Recorder aktivieren/deaktivieren, neue Aufnahme starten, Aufnahme beenden, Aufnahmeverzeichnis festlegen und aktuellen Status/Sitzungspfad sowie Fehler anzeigen. Aufnahmezustand darf nicht nur aus dem UI-Schalter abgeleitet werden, sondern muss vom Kern bestätigt sein.
- Server-Chatnachrichten und private Nachrichten an einen Client unterstützen, falls die bestehende Bedienoberfläche/API dies für die Serverrolle anbietet.
- System-Tray-Verhalten, Start minimiert und Autostart nur auf Betriebssystemen anbieten, auf denen es unterstützt und sicher eingerichtet werden kann.
- Bestehende Kommandozeilen-/headless-Servernutzung bleibt separat verwendbar; die Desktop-Oberfläche darf keine interaktive Anmeldung als Voraussetzung für den Serverkern einführen.

### 4.6 Allgemeine Oberfläche

- Compose-Oberfläche passt sich an Maus/Tastatur auf Windows, macOS und Linux sowie Touch auf Android an. Desktop erhält Menüs/Shortcuts, skalierbares Layout, Fenstergrößen und Wiederherstellung sinnvoller Fensterzustände.
- Android unterstützt Hoch- und Querformat sowie schmale Telefone und größere Tablets. Auf Telefonen sind Verbindung, Sendemute und Status ohne verschachtelte Menüs erreichbar; Mixerkanäle lassen sich touchgerecht bedienen. Touch-Ziele sind mindestens 48 dp groß. Auf Tablets und im Querformat darf die Oberfläche mehr Mixerkanäle gleichzeitig zeigen.
- Auf Android sind Client-Sitzung, Mixer, Chat und Audio/Profil als mobil nutzbare Ansichten erreichbar. Serverfenster werden auf Android nicht angeboten.
- Chat- und Mixer-Navigation ist nur bei aktiver Serververbindung anwählbar. Nach „Trennen“ wechselt die Oberfläche zur Verbindungsansicht.
- Mixer- und Serverkarten sollen kompakt sein und geringe Innenabstände verwenden; häufig genutzte Mixer-Buttons bekommen minimale Innenabstände.
- Client-Hauptansicht, Desktop-Serveransicht und Chat sind logisch getrennte Ansichten; Chat kann am Desktop ein eigenes Fenster bleiben, wenn das im gewählten Compose-Desktop-Stack stabil funktioniert. Auf Android ist Chat Teil der Navigation und kein separates Fenster.
- Bestätigungen bei Verlassen einer aktiven Sitzung bzw. laufenden Aufnahme nur dann, wenn sonst ein überraschender Abbruch entstünde.
- Alle Steuerelemente mit zugänglichem Namen, Zustand und Tastaturfokus; Statusfarben immer zusätzlich durch Text/Icon/Beschreibung vermitteln.
- Lokalisierbare Texte. Als Ausgangspunkt die vorhandenen Jamulus-Übersetzungen nutzen; zunächst müssen Englisch und Deutsch vollständig sein. Weitere derzeit gepflegte Sprachen aus `src/translation/` können schrittweise eingebunden werden.
- Die Oberfläche soll die bestehende Informationsdichte und Live-Bedienbarkeit erhalten. Ein rein dekoratives Redesign darf Live-Pegel, Statusanzeigen oder häufig genutzte Mixer-Aktionen nicht verstecken.
- Screens, wiederverwendbare Komponenten, Theme-/Layoutwerte und Engine-Zustandsmodelle sind voneinander getrennt. Eine alternative Desktop-Anordnung oder mobile Navigation muss ohne Änderungen am C++-Kern möglich sein.

## 5. Nichtfunktionale Anforderungen

### 5.1 Audio, Echtzeit und Stabilität

- C++ bleibt alleinige Instanz für Audio-I/O, Audiopuffer, OPUS-Encoding/Decoding, UDP-Streaming, Jitterpuffer, Mischung, MIDI-Eingang, Reverb und Jam-Aufnahme.
- Kein JNI-/Kotlin-/Compose-Aufruf, Heap-Allocation, Dateizugriff, blockierender Aufruf oder UI-Lock im nativen Audiothread.
- Native Engine-Kommandos laufen auf dedizierten Kontrollthreads. Status-/Pegelereignisse werden über begrenzte Queues an Kotlin übergeben und für die UI gebündelt/gedrosselt; Bedienereignisse (Mute, Fader, Disconnect) dürfen dadurch nicht verloren gehen.
- Desktop: reguläres Schließen der Compose-Oberfläche beendet die Engine geordnet. Bei UI-/Prozessabsturz beendet das Betriebssystem auch die eingebettete Engine; Audioressourcen müssen freigegeben werden und es darf kein falscher Wiederverbindungsstatus angezeigt werden.
- Android: der Engine-Lebenszyklus ist von der Activity getrennt. Eine Foreground-Service-Komponente hält bei aktiver Sitzung Audio und Netzwerk aufrecht, wenn die Oberfläche in den Hintergrund geht oder das Display gesperrt wird. Sie zeigt eine dauerhafte Systembenachrichtigung mit Sitzungsstatus und Aktion zum Trennen. Mikrofonberechtigung muss vor dem Start des Audioeingangs eingeholt werden; Verweigerung wird verständlich erklärt. Unterbrechungen (Anruf, Audiofokus, Gerätewechsel, Oboe-Streamfehler) werden an die Engine gemeldet und sichtbar behandelt.
- Ein voller Prozessabsturz beendet auch die Android-Engine. Neustart/Wiederverbindung nach einem vom Betriebssystem beendeten Prozess darf nur nach expliziter und zuverlässiger Erkennung erfolgen.
- Bestehender Jamulus-Build muss weiterhin ohne Compose baubar und für headless Server nutzbar bleiben.

### 5.2 Protokoll, Sicherheit und Datenschutz

- Keine Änderungen an bestehenden Jamulus-Wire-Message-IDs oder deren Datenlayout.
- Alle Werte aus Verzeichnissen/Netzwerk/JSON-RPC/JNI validieren, bevor sie angezeigt, gespeichert oder an den Kern übergeben werden. HTML aus Chat/Willkommensnachrichten darf keine Skripte oder eingebetteten Ressourcen ausführen; nur ausdrücklich erlaubte externe HTTP(S)-Links öffnen.
- Die Compose-Oberfläche erhält keinen direkten Zugriff auf Audiopuffer oder native Speicherzeiger. JNI-Handles sind opak, nur an den Engine-Lebenszyklus gebunden und nach Engine-Stopp ungültig. Die vorhandene JSON-RPC-Schnittstelle bleibt extern authentisiert und standardmäßig an Loopback gebunden.
- Keine Telemetrie oder Konto-/Cloud-Abhängigkeit einführen.
- Bestehende Verzeichnisse und Server nicht ohne Nutzeraktion kontaktieren, außer dem erwarteten Abruf im Verbindungsdialog bzw. Serverregistrierung im Desktop-Servermodus.

### 5.3 Plattformen, Installation und Lizenz

- Ziel: Windows-, macOS- und Linux-Desktop sowie Android auf den durch Compose, Kotlin/JVM und die nativen Jamulus-Builds tatsächlich unterstützten Versionen. Mindestversionen und Android `minSdk`/`targetSdk` sind vor Festlegung der Compose-/Kotlin-/JDK-Version und Veröffentlichung durch Build-Prototypen nachzuweisen. Insbesondere darf die bisherige Jamulus-Mindestversion macOS 10.10 nicht ungeprüft als Compose-kompatibel behauptet werden. Die vorhandene Android-Manifestbasis verwendet minSdk 21; ob sie mit dem gewählten Compose-/Service-Stack weiterhin getragen werden kann, ist zu verifizieren.
- Desktop-Distributionspaket enthält Compose/JVM-Laufzeit, den passenden Jamulus-Kern und alle rechtmäßig verteilbaren nativen Laufzeitbibliotheken. Das Android-Paket enthält die passenden nativen Bibliotheken für unterstützte ABIs und deklariert nur erforderliche Berechtigungen. Audio-API-Abhängigkeiten und Gerätezugriffe müssen je Plattform dokumentiert sein.
- Android muss Mikrofonberechtigung zur Laufzeit behandeln. Foreground-Service-Typen, Benachrichtigungen und Berechtigungen richten sich nach der festgelegten Android-Zielversion und den jeweils gültigen Plattform-/Store-Regeln. Der Prototyp muss aktive Sitzung bei Display-Sperre und App-Wechsel nachweisen.
- ASIO-SDK-Verteilung und Lizenzbedingungen gesondert prüfen; bestehende Lizenzrestriktionen bleiben gültig. Keine neue Abhängigkeit ohne Lizenz-, Plattform- und Wartbarkeitsprüfung.
- Bestehende AGPL-Lizenzanforderungen und Hinweise für neu hinzugefügte Dateien/Abhängigkeiten beachten.

## 6. Technische Zielarchitektur

### 6.1 Komponenten

1. **Compose Multiplatform UI:** Kotlin/Compose gemeinsame Oberflächen- und Domainlogik; Desktop-Laufzeit für Windows/macOS/Linux, Android Activity/Compose-Host für Android. Plattformabhängige Fenster-, Lifecycle-, Berechtigungs- und Servicelogik bleibt in eigenen Adaptern.
2. **Engine Facade:** typisierte Kotlin-Schnittstelle über dünne JNI-Bindings. Die C++-Bibliothek wird in den Compose-Prozess geladen; auf Android wird dieselbe native Engine über den Foreground-Service-Lebenszyklus verwaltet.
3. **Jamulus C++ Core:** bestehender Netzwerk-/Audio-/Serverkern und seine plattformspezifischen Backends. Der Android-Client verwendet weiterhin Oboe. UI-unabhängige Statusdaten werden als Snapshot/Ereignisse bereitgestellt.
4. **Settings Repository:** Compose-eigene Einstellungen getrennt von bestehenden Jamulus-Einstellungen halten; Migration für Jamulus-Konfigurationen idempotent durchführen und niemals bestehende Nutzerdateien stillschweigend überschreiben. Android-Nutzerdaten liegen im App-spezifischen Speicher.
5. **Build und Verpackung:** Compose Multiplatform wird über Gradle gebaut. Der C++-Kern wird je Zielplattform als native Bibliothek erstellt und gebündelt. qmake ist derzeit die maßgebliche native Buildbeschreibung; zuerst ist ein reproduzierbarer JNI-Prototyp damit nachzuweisen. Eine Umstellung des C++-Buildsystems auf CMake ist eine separate Entscheidung und keine Vorbedingung der Produktspezifikation.

**Technisches Hauptrisiko:** `src/main.cpp` erzeugt heute `QApplication`/`QCoreApplication` und startet Client bzw. Server direkt. Android startet das Programm über `QtActivity`; eine Compose-Activity kann den Kern daher nicht einfach wie ein gewöhnliches `.so` laden. Der Machbarkeitsprototyp muss zeigen, dass Qt Core/Network und der C++-Client ohne Qt Widgets und ohne `QtActivity` innerhalb der Compose-App initialisiert und beendet werden können. Falls nicht, ist zuerst eine native Engine-Fassade samt Qt-Runtime-Initialisierung zu entflechten. Keine Lösung darf zwei `QCoreApplication`-Instanzen oder zwei Engine-Lebenszyklen erzeugen.

### 6.2 Control-API-Mindestumfang

Vor UI-Funktionsparität muss die native Engine Facade mindestens Folgendes abbilden:

- Engine-Status, Modus, Version, Fehler und geordnetes Beenden.
- Client-Verbindungszustand sowie verbinden/trennen/erneut verbinden zu einer Adresse.
- Alle Serververzeichnisse abrufen, Serverlisten zusammenführen, aktualisieren und Serverdetails/Ping empfangen.
- Audio-Geräteliste, Kanalzuordnungen, Audioformat, Buffer-/Jittereinstellungen lesen und setzen; Gerätewechsel/Neustart quittieren.
- Eingangspegel, Delay-/Bufferstatus, Verbindungsping und Kanalpegel ereignisgesteuert liefern.
- Mixerkanäle lesen; Fader, Pan, Mute, Solo und Gruppierung setzen; eigene Sendestummschaltung getrennt steuern.
- Profil, Reverb, Chat und MIDI-Einstellungen lesen/setzen; MIDI-Geräte und Learn-Ereignisse melden.
- Im Servermodus Clientliste, Registrierungsstatus, Serverprofil, Einstellungen, Chat und Recorderzustand lesen; verfügbare Aktionen ausführen.
- Auf Android ausschließlich Clientfunktionen freigeben; Serverstart- und Recorderbefehle sind dort nicht verfügbar.

Die JNI-Schicht bleibt dünn und übersetzt zwischen einfachen, versionierbaren C-ABI-Datentypen und Kotlin-Datenklassen. Keine Qt-/C++-Objekte oder Zeiger werden direkt als Kotlin-Objekte exponiert. Callbacks laufen nie aus dem Echtzeitthread; Ereignisse werden auf kontrollierbaren nativen Threads erzeugt und sicher an Kotlin dispatcht. Bestehende Methoden und Daten der öffentlichen JSON-RPC-Schnittstelle bleiben rückwärtskompatibel. Der UI-Adapter validiert Eingaben zusätzlich clientseitig; der Kern validiert sie erneut.

### 6.3 Zustandsmodell

- UI-Zustand wird aus einem Engine-Snapshot initialisiert und danach über Ereignisse aktualisiert; UI-Aktionen aktualisieren den angezeigten Zustand erst nach Bestätigung oder zeigen einen klaren Pending-/Fehlerzustand.
- Engine-Zustände mindestens: `Stopped`, `Starting`, `Ready`, `Connecting`, `Connected`, `Reconnecting`, `Stopping`, `Failed`.
- Engine-Absturz, JNI-Fehler oder Android-Serviceverlust erzeugen eine sichtbare Fehler-/Wiederherstellungssituation. Wiederholte automatische Startversuche benötigen Backoff und dürfen keine mehrfachen Audio-Engines erzeugen.
- Android Activity-Neuerstellung (zum Beispiel Rotation oder Prozessnavigation, solange der Service lebt) darf keine zweite Engine anlegen. Start/Stop ist idempotent; Berechtigungsdialoge und Service-Verbindung haben explizite Lade-/Fehlerzustände.
- Sessiondaten (Mixer, Profil, Chat) bleiben an die konkrete Engine-/Verbindungssitzung gebunden; alte verspätete Ereignisse einer beendeten Sitzung werden verworfen.

## 7. Persistenz und Migration

- Zuerst aktuelle Jamulus-Einstellungsdatei und unterstützte Pfade auf Windows, macOS und Android lesen; nicht von einem einzigen OS-Pfad ausgehen. Auf Android liegen migrierte Nutzereinstellungen im App-spezifischen Speicher und überstehen ein normales App-Update.
- Nutzerprofil, Server-/Verzeichnispräferenzen, Audioeinstellungen, UI-Sprache, Fensterzustände, Mixer-Zeilen, Fader/Pan/Group/Mute/Solo-Defaults und MIDI-Konfiguration erhalten, soweit Datentyp und Bedeutung übereinstimmen.
- Einmalige, wiederholbare Migration mit Sicherung der Quelldatei, Versionsmarkierung und Protokoll. Ein ungültiger Eintrag darf die übrigen Einstellungen nicht verwerfen.
- Unbekannte/ältere Schlüssel erhalten oder unverändert in der Quelldatei belassen. Compose-spezifische Felder bekommen einen eigenen Namensraum.
- Nicht migrierbare Werte mit verständlicher Erklärung behandeln; keine stillen Default-Rücksetzungen.

## 8. Abnahmekriterien

Die erste veröffentlichungsfähige Version erfüllt alle folgenden Kriterien auf Windows, macOS, Linux und Android, soweit die jeweilige Audio-API verfügbar ist:

1. Start als Client, Audio-Gerät auswählen, Profil speichern, Verzeichnisliste laden, per Adresse verbinden und wieder trennen.
2. Verbindung mit bestehendem Jamulus-Server und bestehendem Jamulus-Client; keine Wire-Protokolländerung erforderlich.
3. Live-Eingangs-/Kanalpegel und Verbindungsstatus aktualisieren, ohne den Audiothread von UI- oder JNI-Latenz abhängig zu machen.
4. Im laufenden Jam Mute, lokalen Fader, Pan, Solo und Chat bedienen; korrekte Rückmeldung bei Ablehnung/Verbindungsverlust.
5. Client-Audioeinstellungen einschließlich eines Gerätewechsels anwenden oder dem Nutzer korrekt melden, falls kontrollierter Neustart nötig ist.
6. Auf Desktop als Server starten, Clients überwachen, Profil/Verzeichnis konfigurieren sowie Jam Recorder starten/stoppen und Status beobachten. Android zeigt keinen Serverbetrieb.
7. Einstellungen migrieren, ohne eine bestehende Konfiguration zu beschädigen; erneuter Start erhält neue Compose-Einstellungen.
8. Compose-Activity neu erstellen oder neu öffnen, während der Android-Foreground-Service läuft, erzeugt keine zweite Engine; Display-Sperre und App-Wechsel beenden eine aktive Jam-Session nicht unerwartet.
9. JNI-/Enginefehler, Runtime-Mikrofonverweigerung, Audiofokusverlust und Oboe-Streamfehler werden verständlich angezeigt und führen zu definierten Zuständen.
10. Tastaturbedienung, Screenreader-Namen, Skalierung und vollständige englische/deutsche Kernabläufe auf Windows/macOS sowie Touch/Screenreader auf Android prüfen.
11. Bestehende headless Client-/Server-Builds bleiben baubar und bedienbar. Echtzeit-/Latenzverhalten ist mit Messungen unter Last gegen die bisherige Anwendung verglichen; ein erfolgreicher Build allein gilt nicht als Nachweis.

## 9. Umsetzungsreihenfolge

1. **Machbarkeitsprototyp:** Compose-App für Windows, macOS, Linux und Android bauen; native C++-Bibliothek via JNI laden; Engine kontrolliert starten/stoppen; Profil/Clientstatus lesen und einen Mixer-Fader setzen. Auf Android zusätzlich Mikrofonfreigabe, Oboe-Ein-/Ausgabe, App-Wechsel und Displaysperre während einer aktiven Session prüfen. Qt-Runtime-Initialisierung, Paketgröße, ABI-Matrix, macOS-Mindestversion und Android-Store-Zielversion dokumentieren.
2. **Engine Facade erstellen:** `main()`-gebundene Anwendungsinitialisierung in einen einzeln start-/stoppbaren Engine-Lebenszyklus überführen und typsichere C-ABI/JNI-Bindings definieren. Audio-Geräte-/Clientverbindungssteuerung zuerst ergänzen. Änderungen an Threading oder Buildsystem benötigen vor Implementierung die im Projektbeitrag geforderte Spezifikationsdiskussion.
3. **Client-MVP:** Verbindungsdialog, Hauptstatus, Profileinstellungen, Audiogeräte, Mixer, Mute und Chat auf Windows, macOS, Linux und Android.
4. **Client-Parität:** erweiterte Audio-/Jitter-/MIDI- und Persistenzfunktionen, Sortierung/Filter, Übersetzungen, Android-Service und Migration.
5. **Desktop-Serveroberfläche:** Startkonfiguration, Clientliste, Verzeichnisregistrierung, Recorder und Betriebssystemintegration für Windows/macOS/Linux.
6. **Härtung und Veröffentlichung:** Plattformpakete, Installations-/Upgrade-/Deinstallationsabläufe, Fehlerfälle, Audiotests unter Last und Dokumentation.

## 10. Lokaler Prototyp: Lösungsentscheidungen und aktueller Stand

Dieser Abschnitt hält den Stand des vorhandenen lokalen Experiments fest. Er ergänzt die Zielanforderungen oben, ersetzt sie aber nicht: nicht implementierte Punkte gelten weiterhin als offen. Die Änderungen dienen dem persönlichen Ausprobieren und sind nicht als Upstream-Beitrag gedacht.

### 10.1 Bereits implementierte Compose-Funktionen

- Gemeinsame Compose-Oberfläche mit den Ansichten „Verbinden“, „Mixer“, „Chat“ und „Einstellungen“. Die Navigation zeigt gezeichnete, anklickbare Icons für Verbindung, Mixer, Chat und Einstellungen. Die Namen erscheinen nicht dauerhaft unter den Icons, sondern als Tooltip bei Hover (auf Touchgeräten per langem Druck); die Icons behalten zugängliche Beschreibungen. Ein Server-Eintrag und die frühere Server-Platzhalteransicht sind aus der Oberfläche entfernt. Serverbetrieb bleibt außerhalb dieses Client-Prototyps.
- Die Serverliste wird automatisch aus allen Standardverzeichnissen und allen gespeicherten benutzerdefinierten Verzeichnissen abgerufen und nach Serveradresse zusammengeführt. Ein Refresh fragt alle Verzeichnisse erneut ab; Teilausfälle werden neben den weiter angezeigten Treffern gemeldet. Die Verbindungsansicht enthält keine Verzeichnisauswahl oder einzelne Verzeichnisadresse. Die direkte Serveradresse und der Serverfilter bleiben verfügbar. Die Serverliste sortiert nach Ping aufsteigend; nicht gemessene Pings stehen hinten. `#` filtert belegte Server. Standortdaten stehen hinter dem Servernamen in Klammern; danach folgen „ - “ und alle Verzeichnisse, in denen der Server gefunden wurde.
- Die zuletzt verwendeten Serveradressen werden im Preference-Modell weiterhin gespeichert, aber auf ausdrücklichen Produktwunsch nicht als „Zuletzt verwendet“-Liste angezeigt.
- Profilwerte und die im Prototyp angebotenen Audio-/Netzwerkwerte werden lokal gespeichert. Desktop verwendet `java.util.prefs`; die native Jamulus-Engine speichert zusätzlich ihre Einstellungen unter `~/.jamulus-compose/Jamulus.ini` (Windows: im Benutzerverzeichnis). Android verwendet `SharedPreferences` im App-Speicher.
- Instrumentbilder und Flaggen stammen aus den Jamulus-Ressourcen unter `src/res/`. Der native Snapshot liefert IDs und Ressourcenschlüssel; Compose zeigt sie in Instrument-/Länderauswahl und Musikerkanälen an.
- Der Mixer zeigt Musikername, Instrumenticon/-name und Länderflagge. Der grüne Kanalpegel liegt auf dem Lautstärkeregler; der Slidergriff steuert den lokalen Fader. L/R klappt den Panorama-Regler ein/aus. Mute und Solo stehen links neben dem Lautstärkeregler und werden im aktiven Zustand rot dargestellt. Die Karten und Buttons verwenden kompakte Abstände.
- Die eigene Sendestummschaltung lässt sich bei verfügbarer Engine auch ohne Serververbindung aktivieren und bleibt über einen Verbindungsaufbau hinweg gesetzt. Sie gilt derzeit nur bis zum Ende des Engine-Prozesses; beim nächsten Start ist der Sendestream standardmäßig nicht stumm.
- Mixer und Chat sind nur bei aktiver Verbindung anwählbar. „Trennen“ navigiert zurück zur Verbindungsansicht. Chatlinks mit `http://`, `https://` und `www.` öffnen über Compose `LinkAnnotation.Url` den Systembrowser, ohne zusätzliche Bestätigungsseite.
- Kanal-Ping und Delay kommen aus der nativen Engine. Ping-Anfragen laufen während der Verbindung im 500-ms-Intervall; die Anzeige wird beim Trennen zurückgesetzt.

### 10.2 Native Desktop-Engine und Qt-Laufzeit

- Die macOS-Desktop-App verwendet eine JNI-Fassade auf den vorhandenen C++-Client. Sie wird mit `multiplatform/native/compose_engine.pro` und qmake gegen den Jamulus-Kern gebaut. Die Fassade erzeugt keine Qt-Widgets-Oberfläche; sie verwendet jedoch weiter Qt Core/Network und die vorhandenen Jamulus-Klassen.
- Compose-Aufrufe werden an den nativen Kontrollthread weitergereicht. Die Engine veröffentlicht JSON-Snapshots und Ereignisse an Kotlin. Der Snapshot wird zusätzlich alle 100 ms veröffentlicht; die Audioverarbeitung bleibt im nativen Jamulus-Kern.
- Compose Multiplatform ersetzt Qt Widgets, aber nicht die Qt-Abhängigkeiten des nativen C++-Clients. Daher benötigt ein Desktop-Paket weiterhin die passende Qt-Laufzeit. Das macOS-Distributable verwendet `macdeployqt`, um die benötigten Qt-Bibliotheken einzubetten. Die entsprechende Laufzeitverpackung für Windows und Linux ist noch nicht eingerichtet.
- Der macOS-Prototyp wurde auf dem vorhandenen Qt-5.15.19-Host-Kit gebaut. Standard-qmake-Pfad auf Apple Silicon ist `/opt/homebrew/opt/qt@5/bin/qmake`.
- Der aktuelle Gradle-Prototyp verwendet Kotlin 2.1.21, Compose Multiplatform 1.8.2, Android Gradle Plugin 8.10.1 und JVM-Target 17. Android ist in Gradle mit `minSdk 21`, `compileSdk 35` und `targetSdk 35` konfiguriert; daraus folgt noch keine bestätigte Geräte-/Store-Kompatibilität.
- IntelliJ startet den Desktop über die geteilte Run-Konfiguration `Jamulus Desktop` bzw. `:composeApp:run`. **Available Devices** listet Android-ADB-Geräte; der macOS-Desktop ist eine Gradle-Run-Konfiguration und kein Android-Gerät.

### 10.3 Noch nicht umgesetzt oder nicht nachgewiesen

- **Android-Engine:** Android enthält die Compose-Oberfläche, Einstellungsablage und Icon-Ressourcen, verwendet aber derzeit `UnavailableJamulusEngine`. Eine Android-App kann daher noch keine Jamulus-Verbindung aufbauen, Audio bedienen oder native Instrument-/Länder-/Audiogerätelisten laden. Der aktuelle Compile-Erfolg ist kein Nachweis eines lauffähigen Android-Clients. Der bisher untersuchte Qt-5.15.19-Pfad enthält das macOS-Host-Kit; ein Android-ABI-Kit samt `androiddeployqt` wurde nicht gefunden.
- **Serverbetrieb:** Der aktuelle Client-Prototyp bietet keine Serveransicht. Die native JNI-Fassade ist auf den Client zugeschnitten; Serverstart, Verzeichnisregistrierung, Serverclientliste und Jam Recorder fehlen.
- **Client-Parität:** MIDI, Reverb-Steuerung, Audiobenachrichtigungen und -alarme, Fadergruppen, erweiterte Kanalsortierung, Mixerzeilen und weitere Qt-Optionen sind noch nicht an die Compose-Oberfläche angebunden. Ein gespeicherter Preference-Schlüssel allein bedeutet nicht, dass eine Einstellung schon bedienbar oder wirksam ist.
- **Auslieferung:** Für Windows und Linux ist die Qt-Runtime-Verpackung noch offen. Die nativen Windows-/Linux-Desktop-Builds sowie ein Android-Native-Build wurden im bisherigen Prototyp nicht nachgewiesen.
- **Funktionstest:** Desktop- und Android-Kotlin-Compile sowie der native macOS-Build waren erfolgreich. Serververbindung, Live-Audiowiedergabe, Mixerwirkung und Chatlink-Öffnung wurden nicht als automatisierter oder dokumentierter End-to-End-Live-Test nachgewiesen.

## 11. Annahmen und noch zu bestätigende Produktentscheidungen

Diese Spezifikation setzt für die weitere Ausarbeitung voraus:

- Compose-Oberfläche ersetzt langfristig die Qt-Oberfläche; C++-Audio-, Netzwerk- und Serverkern bleibt erhalten und wird per JNI eingebunden.
- Windows/macOS/Linux unterstützen Jamulus-Client und -Server; Android unterstützt ausschließlich den Jamulus-Client.
- Aktive Android-Sitzungen sollen App-Wechsel und Displaysperre über einen Foreground Service überstehen.
- Funktionale Parität ist wichtiger als pixelgenaue Nachbildung des aktuellen Qt-Skins. Bedienabläufe und Live-Mixer-Funktionen bleiben erhalten.
- Die Erstfassung zielt auf Windows, macOS, Linux und Android; konkrete Mindestversionen folgen aus dem Plattformprototyp.

Vor einem Produktions-Neubau muss der JNI-/Qt-Runtime-Prototyp gelingen. Ein reiner Kotlin-Neubau wäre ein separates Vorhaben mit eigenem Audio-/Protokoll-Migrations- und Kompatibilitätsnachweis.

## 12. Bestandsquellen im Repository

- Funktions- und Beitragsanforderungen: `CONTRIBUTING.md`, `AGENTS.md`
- Client-/Serverstart und Kommandozeilenoptionen: `src/main.cpp`, `Jamulus.pro`, `COMPILING.md`
- Client-Hauptfenster und Serverauswahl: `src/clientdlgbase.ui`, `src/connectdlgbase.ui`, `src/clientdlg.cpp`, `src/connectdlg.cpp`
- Clientprofil, Audio, Netzwerk, MIDI: `src/clientsettingsdlgbase.ui`, `src/clientsettingsdlg.cpp`, `src/settings.h`, `src/settings.cpp`
- Mixer, Chat und Serveroberfläche: `src/audiomixerboard.cpp`, `src/chatdlg.cpp`, `src/serverdlgbase.ui`, `src/serverdlg.cpp`
- Jamulus-Wire-Protokoll: `docs/JAMULUS_PROTOCOL.md`
- Lokale Steuer-API: `docs/JSON-RPC.md`, `src/clientrpc.cpp`, `src/serverrpc.cpp`
- Audio-Backendauswahl: `src/sound/`, `Jamulus.pro`

> 🤖 Used AI: GPT-6, Codex
