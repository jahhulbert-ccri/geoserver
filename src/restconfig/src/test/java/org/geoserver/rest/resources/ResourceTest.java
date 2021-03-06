/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.rest.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.test.JSONAssert;

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;
import org.geoserver.rest.util.IOUtils;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import com.mockrunner.mock.web.MockHttpServletResponse;

/**
 * 
 * @author Niels Charlier
 *
 */
public class ResourceTest extends GeoServerSystemTestSupport {
    
    private final String STR_MY_TEST;
    private final String STR_MY_NEW_TEST;
    private final NamespaceContext NS_XML, NS_HTML;
    private final DateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S z");
    private final DateFormat FORMAT_HEADER = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
    
    private Resource myRes; 
    public ResourceTest() {
        CharsetEncoder encoder = Charset.defaultCharset().newEncoder();
        if (encoder.canEncode("éö")) {
            STR_MY_TEST = "This is my test. é ö";
        } else {
            STR_MY_TEST = "This is my test.";
        }
        if (encoder.canEncode("€è")) {
            STR_MY_NEW_TEST = "This is my new test. € è";
        } else {
            STR_MY_NEW_TEST = "This is my new test.";
        }

        FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        FORMAT_HEADER.setTimeZone(TimeZone.getTimeZone("GMT"));
        Map<String, String> mapXML = new HashMap<String, String>();
        mapXML.put("atom", "http://www.w3.org/2005/Atom");
        NS_XML = new SimpleNamespaceContext(mapXML);
        Map<String, String> mapHTML = new HashMap<String, String>();        
        mapHTML.put("x", "http://www.w3.org/1999/xhtml");
        NS_HTML = new SimpleNamespaceContext(mapHTML);        
    }          
    
    @Before
    public void initialise() throws IOException {

        myRes = getDataDirectory().get("/mydir/myres");
        try (OutputStreamWriter os = new OutputStreamWriter(myRes.out())) {
            os.append(STR_MY_TEST);
        }

        try (OutputStreamWriter os = new OutputStreamWriter(getDataDirectory().get("/mydir2/fake.png").out())) {
            os.append("This is not a real png file.");
        }

        try (OutputStreamWriter os = new OutputStreamWriter(getDataDirectory().get("/poëzie/café").out())) {
            os.append("The content of this file is irrelevant.");
        }

        IOUtils.copyStream(getClass().getResourceAsStream("testimage.png"),
                getDataDirectory().get("/mydir2/imagewithoutextension").out(), true, true);
    }
    
    @Test
    public void testResource() throws Exception {
        String str = getAsString("/rest/resource/mydir/myres").trim();
        Assert.assertEquals(STR_MY_TEST, str);
    }
    
    @Test
    public void testResourceMetadataXML() throws Exception {
        XMLUnit.setXpathNamespaceContext(NS_XML);
        Document doc = getAsDOM("/rest/resource/mydir/myres?operation=mEtAdATa&format=xml");
        //print(doc);
        XMLAssert.assertXpathEvaluatesTo("myres", "/ResourceMetadata/name", doc);
        XMLAssert.assertXpathEvaluatesTo("/mydir", "/ResourceMetadata/parent/path", doc);
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/mydir", 
                "/ResourceMetadata/parent/atom:link/@href", doc);
        XMLAssert.assertXpathEvaluatesTo(FORMAT.format(myRes.lastmodified()),
                "/ResourceMetadata/lastModified", doc);
    }
    
    @Test
    public void testResourceMetadataJSON() throws Exception {
        JSON json = getAsJSON("/rest/resource/mydir/myres?operation=metadata&format=json");
        //print(json);
        String expected = "{\"ResourceMetadata\": {"
                + "  \"name\": \"myres\","
                + "  \"parent\":   {"
                + "    \"path\": \"/mydir\","
                + "    \"link\": {"
                + "       \"href\": \"http://localhost:8080/geoserver/rest/resource/mydir\","
                + "       \"rel\": \"alternate\",                "
                + "       \"type\": \"application/json\""
                + "     }"
                + "   },"
                + "  \"lastModified\": \"" + FORMAT.format(myRes.lastmodified()) + "\","
                + "  \"type\": \"resource\""
                + "}}";
        JSONAssert.assertEquals(expected, (JSONObject) json);
    }
    
    @Test
    public void testResourceMetadataHTML() throws Exception {
        XMLUnit.setXpathNamespaceContext(NS_HTML);
        Document doc = getAsDOM("/rest/resource/mydir/myres?operation=metadata&format=html");
        //print(doc);
        XMLAssert.assertXpathEvaluatesTo("Name: 'myres'", "/x:html/x:body/x:ul/x:li[1]", doc);
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/mydir", 
                "/x:html/x:body/x:ul/x:li[2]/x:a/@href", doc);
        XMLAssert.assertXpathEvaluatesTo("Type: resource", "/x:html/x:body/x:ul/x:li[3]", doc);
        XMLAssert.assertXpathEvaluatesTo("Last modified: " + new Date(myRes.lastmodified()).toString(), 
                "/x:html/x:body/x:ul/x:li[4]", doc);
    }
    
    @Test
    public void testResourceHeaders() throws Exception {
        MockHttpServletResponse response = getAsServletResponse("/rest/resource/mydir2/fake.png");
        Assert.assertEquals(FORMAT_HEADER.format(myRes.lastmodified()), response.getHeader("Last-Modified"));
        Assert.assertEquals("http://localhost:8080/geoserver/rest/resource/mydir2", 
                response.getHeader("Resource-Parent"));
        Assert.assertEquals("resource", response.getHeader("Resource-Type"));
        Assert.assertEquals("image/png", response.getHeader("Content-Type"));
    }

    @Test
    public void testSpecialCharacterNames() throws Exception {
        XMLUnit.setXpathNamespaceContext(NS_XML);
        Document doc = getAsDOM("/rest/resource/po%c3%abzie?format=xml");
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/po%C3%ABzie/caf%C3%A9", 
                "/ResourceDirectory/children/child/atom:link/@href", doc);

        MockHttpServletResponse response = getAsServletResponse("/rest/resource/po%c3%abzie/caf%c3%a9?format=xml");
        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertEquals("resource", response.getHeader("Resource-Type"));
        Assert.assertEquals("http://localhost:8080/geoserver/rest/resource/po%C3%ABzie", 
                response.getHeader("Resource-Parent"));
    }
    
    @Test
    public void testDirectoryXML() throws Exception {
        XMLUnit.setXpathNamespaceContext(NS_XML);
        Document doc = getAsDOM("/rest/resource/mydir?format=xml");
        //print(doc);
        XMLAssert.assertXpathEvaluatesTo("mydir", "/ResourceDirectory/name", doc);
        XMLAssert.assertXpathEvaluatesTo("/", "/ResourceDirectory/parent/path", doc);
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/", 
                "/ResourceDirectory/parent/atom:link/@href", doc);
        XMLAssert.assertXpathEvaluatesTo(FORMAT.format(myRes.parent().lastmodified()),
                "/ResourceDirectory/lastModified", doc);
        XMLAssert.assertXpathEvaluatesTo("myres", "/ResourceDirectory/children/child/name", doc);
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/mydir/myres", 
                "/ResourceDirectory/children/child/atom:link/@href", doc);
    }
    
    @Test
    public void testDirectoryJSON() throws Exception {
        JSON json = getAsJSON("/rest/resource/mydir?format=json");
        //print(json);
        String expected = "{\"ResourceDirectory\": {"
                + "\"name\": \"mydir\","
                + "\"parent\":   {"
                + "  \"path\": \"/\","
                + "    \"link\":     {"
                + "      \"href\": \"http://localhost:8080/geoserver/rest/resource/\","
                + "      \"rel\": \"alternate\","
                + "      \"type\": \"application/json\""
                + "  }"
                + "},"
                + "\"lastModified\": \"" + FORMAT.format(myRes.parent().lastmodified()) + "\","
                + "  \"children\": {\"child\": [  {"
                + "    \"name\": \"myres\","
                + "    \"link\":     {"
                + "      \"href\": \"http://localhost:8080/geoserver/rest/resource/mydir/myres\","
                + "      \"rel\": \"alternate\","
                + "      \"type\": \"application/octet-stream\""
                + "    }"
                + "  }]}"
                + "}}";
        JSONAssert.assertEquals(expected, (JSONObject) json);
    }
    
    @Test
    public void testDirectoryHTML() throws Exception {
        XMLUnit.setXpathNamespaceContext(NS_HTML);
        Document doc = getAsDOM("/rest/resource/mydir?format=html");
        //print(doc);
        XMLAssert.assertXpathEvaluatesTo("Name: 'mydir'", "/x:html/x:body/x:ul/x:li[1]", doc);
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/", 
                "/x:html/x:body/x:ul/x:li[2]/x:a/@href", doc);
        XMLAssert.assertXpathEvaluatesTo(
                "Last modified: " + new Date(myRes.parent().lastmodified()).toString(),
                "/x:html/x:body/x:ul/x:li[3]", doc);
        XMLAssert.assertXpathEvaluatesTo("http://localhost:8080/geoserver/rest/resource/mydir/myres", 
                "/x:html/x:body/x:ul/x:li[4]/x:ul/x:li/x:a/@href", doc);
    }
    
    @Test
    public void testDirectoryHeaders() throws Exception {
        MockHttpServletResponse response = getAsServletResponse("/rest/resource/mydir?format=xml");
        Assert.assertEquals(FORMAT_HEADER.format(myRes.parent().lastmodified()),
                response.getHeader("Last-Modified"));
        Assert.assertEquals("http://localhost:8080/geoserver/rest/resource/",
                response.getHeader("Resource-Parent"));
        Assert.assertEquals("directory", response.getHeader("Resource-Type"));
        Assert.assertEquals("application/xml", response.getHeader("Content-Type"));
    }
    
    @Test
    public void testDirectoryMimeTypes() throws Exception {
        XMLUnit.setXpathNamespaceContext(NS_XML);
        Document doc = getAsDOM("/rest/resource/mydir2?format=xml");
        //print(doc);
        XMLAssert.assertXpathEvaluatesTo("image/png", "/ResourceDirectory/children/child[name='imagewithoutextension']/atom:link/@type", doc);
        XMLAssert.assertXpathEvaluatesTo("image/png", "/ResourceDirectory/children/child[name='fake.png']/atom:link/@type", doc);
    }
        
    @Test
    public void testUpload() throws Exception {
        put("/rest/resource/mydir/mynewres", STR_MY_NEW_TEST);
        
        Resource newRes = getDataDirectory().get("/mydir/mynewres");
        try (InputStream is = newRes.in()) {
            Assert.assertEquals(STR_MY_NEW_TEST, IOUtils.toString(is));
        }
        
        newRes.delete();
    }
    
    @Test
    public void testCopy() throws Exception {
        put("/rest/resource/mydir/mynewres?operation=cOpY", "/mydir/myres");
        
        Resource newRes = getDataDirectory().get("/mydir/mynewres");
        Assert.assertTrue(Resources.exists(myRes));
        Assert.assertTrue(Resources.exists(newRes));    
        try (InputStream is = newRes.in()) {
            Assert.assertEquals(STR_MY_TEST, IOUtils.toString(is));
        }
        
        newRes.delete();
    }
    
    @Test
    public void testMove() throws Exception {
        put("/rest/resource/mydir/mynewres?operation=move", "/mydir/myres");
        
        Resource newRes = getDataDirectory().get("/mydir/mynewres");        
        Assert.assertFalse(Resources.exists(myRes));
        Assert.assertTrue(Resources.exists(newRes));        
        try (InputStream is = newRes.in()) {
            Assert.assertEquals(STR_MY_TEST, IOUtils.toString(is));
        }
        
        newRes.renameTo(myRes);
    }
    
    @Test
    public void testMoveDirectory() throws Exception {
        put("/rest/resource/mydir/mynewdir?operation=move", "/mydir");
        put("/rest/resource/mynewdir?operation=move", "/mydir");
        
        Resource newDir = getDataDirectory().get("/mynewdir");
        Assert.assertTrue(Resources.exists(newDir));      
        Assert.assertTrue(newDir.getType() == Resource.Type.DIRECTORY);    
        Assert.assertFalse(Resources.exists(myRes));      
        Assert.assertTrue(Resources.exists(getDataDirectory().get("/mynewdir/myres")));
        
        newDir.renameTo(getDataDirectory().get("/mydir"));
    }
    
    @Test
    public void testDelete() throws Exception {
        Resource newRes = getDataDirectory().get("/mydir/mynewres"); 
        Resources.copy(myRes, newRes);
        Assert.assertTrue(Resources.exists(newRes));
        
        deleteAsServletResponse("/rest/resource/mydir/mynewres");
        
        Assert.assertFalse(Resources.exists(newRes));
    }
    
    @Test
    public void testErrorResponseCodes() throws Exception {
        MockHttpServletResponse response;
                
        //get resource that doesn't exist
        response = getAsServletResponse("/rest/resource/doesntexist");
        Assert.assertEquals(404, response.getStatusCode());
        
        //delete resource that doesn't exist
        response = deleteAsServletResponse("/rest/resource/doesntexist");
        Assert.assertEquals(404, response.getStatusCode());
        
        //upload to dir
        response = putAsServletResponse("/rest/resource/mydir");
        Assert.assertEquals(405, response.getStatusCode());

        //copy dir
        response = putAsServletResponse("/rest/resource/mynewdir?operation=copy", "/mydir", "text/plain");
        Assert.assertEquals(405, response.getStatusCode());

        //copy resource that doesn't exist
        response = putAsServletResponse("/rest/resource/mynewres?operation=copy", "/doesntexist", "text/plain");
        Assert.assertEquals(404, response.getStatusCode());

        //move resource that doesn't exist
        response = putAsServletResponse("/rest/resource/mynewres?operation=move", "/doesntexist", "text/plain");
        Assert.assertEquals(404, response.getStatusCode());
        
        //post
        response = postAsServletResponse("/rest/resource/mydir", "blabla");
        Assert.assertEquals(405, response.getStatusCode());
        
    }

}
