package org.davical.acal.xml;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.util.Log;

public class SaxDavXmlTreeBuilder  {

	public static final String TAG = "acal SaxDavXmlTreebuilder";
	
	public static SaxDavNode getXmlTree(InputStream xml) throws IOException {
		  SAXParserFactory factory = SAXParserFactory.newInstance();
		  // Android's parser is like this anyway, but others are not unless asked, and
		  // without it an element is known by its prefix as well: D:href, not href.
		  factory.setNamespaceAware(true);
		  SaxDavNode root = new SaxDavNode();
		  try {
		        SAXParser saxParser = factory.newSAXParser();
		        saxParser.parse( xml, root.getHandler() );

		  } catch (ParserConfigurationException e) {
			  //Error parsing the document;
			  Log.w(TAG, "Error setting up parser: "+e);
		  } catch (SAXException e) {
			  //Error parsing the document;
			  Log.i(TAG, "Error parsing xml document: "+e);
		  } 
		  
		  return root;
	}
}
