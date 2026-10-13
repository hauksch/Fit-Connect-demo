package de.fitconnectdemo.fachdaten;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Placeholder for the real Fachdatenebene: xMeld-shaped XML for a Meldeauskunft-Anfrage and the
 * matching Antwort. This is NOT schema-valid xMeld — the namespace and element names only mimic
 * the look of {@code http://www.osci.de/xmeld23}. The whole point of the demo is the FIT-Connect
 * transport layer, so the payload just has to be well-formed XML. Replace this class with a real
 * xMeld serializer once the Fachdatenebene is in scope.
 */
public final class FakeXMeld {

    public static final String NAMESPACE = "http://www.osci.de/xmeld23";

    private FakeXMeld() {
    }

    /** A simple request for a Meldeauskunft (registration information lookup). */
    public static String anfrage(UUID anfrageId, String vorname, String familienname, String geburtsdatum) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmeld:MeldeauskunftAnfrage xmlns:xmeld="%s">
                  <xmeld:AnfrageId>%s</xmeld:AnfrageId>
                  <xmeld:Erstellungsdatum>%s</xmeld:Erstellungsdatum>
                  <xmeld:ArtDerAnfrage>einfacheMeldeauskunft</xmeld:ArtDerAnfrage>
                  <xmeld:BetroffenePerson>
                    <xmeld:Vorname>%s</xmeld:Vorname>
                    <xmeld:Familienname>%s</xmeld:Familienname>
                    <xmeld:Geburtsdatum>%s</xmeld:Geburtsdatum>
                  </xmeld:BetroffenePerson>
                </xmeld:MeldeauskunftAnfrage>
                """.formatted(
                NAMESPACE, anfrageId, LocalDate.now(), esc(vorname), esc(familienname), esc(geburtsdatum));
    }

    /** The Meldeamt's answer: a minimal fake Meldeauskunft. */
    public static String antwort(UUID anfrageId, String meldeanschrift) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmeld:MeldeauskunftAntwort xmlns:xmeld="%s">
                  <xmeld:AnfrageId>%s</xmeld:AnfrageId>
                  <xmeld:Erstellungsdatum>%s</xmeld:Erstellungsdatum>
                  <xmeld:Meldestatus>gemeldet</xmeld:Meldestatus>
                  <xmeld:Meldeanschrift>%s</xmeld:Meldeanschrift>
                </xmeld:MeldeauskunftAntwort>
                """.formatted(NAMESPACE, anfrageId, LocalDate.now(), esc(meldeanschrift));
    }

    private static String esc(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
