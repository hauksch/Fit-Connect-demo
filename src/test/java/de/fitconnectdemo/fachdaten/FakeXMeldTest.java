package de.fitconnectdemo.fachdaten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class FakeXMeldTest {

    @Test
    void anfrageIsWellFormedXmlInXMeldNamespace() throws Exception {
        String xml = FakeXMeld.anfrage(UUID.randomUUID(), "Erika", "Mustermann", "1970-01-01");

        var root = parse(xml);
        assertEquals("MeldeauskunftAnfrage", root.getLocalName());
        assertEquals(FakeXMeld.NAMESPACE, root.getNamespaceURI());
        assertTrue(xml.contains("<xmeld:Vorname>Erika</xmeld:Vorname>"));
    }

    @Test
    void antwortIsWellFormedXmlInXMeldNamespace() throws Exception {
        UUID anfrageId = UUID.randomUUID();
        String xml = FakeXMeld.antwort(anfrageId, "Musterweg 1, 12345 Musterstadt");

        var root = parse(xml);
        assertEquals("MeldeauskunftAntwort", root.getLocalName());
        assertEquals(FakeXMeld.NAMESPACE, root.getNamespaceURI());
        assertTrue(xml.contains(anfrageId.toString()));
    }

    @Test
    void escapesXmlSpecialCharacters() throws Exception {
        String xml = FakeXMeld.anfrage(UUID.randomUUID(), "A&B", "<Klüßner>", "1970-01-01");
        parse(xml);
        assertTrue(xml.contains("&amp;"));
    }

    private org.w3c.dom.Element parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder()
                .parse(new InputSource(new java.io.StringReader(xml)));
        return document.getDocumentElement();
    }
}
