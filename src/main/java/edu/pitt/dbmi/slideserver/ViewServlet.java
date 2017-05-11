package edu.pitt.dbmi.slideserver;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.*;
import javax.servlet.http.*;

/**
 *  @author Eugene Tseytlin (University of Pittsburgh)
 *  
 *  OpenSlideServer, is a digital slide image server that wrapps 
 *  OpenSlide library (http://openslide.cs.cmu.edu/) 
 *
 *  Copyright (c) 2007-2008 University of Pittsburgh
 *  All rights reserved.
 *
 *  OpenSlide is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, version 2.
 *
 *  OpenSlideServer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with OpenSlideServer. If not, see <http://www.gnu.org/licenses/>.
 *
 *  Linking OpenSlideServer statically or dynamically with other modules is
 *  making a combined work based on OpenSlideServer. Thus, the terms and
 *  conditions of the GNU General Public License cover the whole
 *  combination.
 */
public class ViewServlet extends HttpServlet {
	private final String APPLET_FILE = "/resources/ViewerApplet.html";
	private String configPath,openSlideServlet = "/servlet/OpenSlideServlet";
	
	
	/**
	 *  Initializes the servlet.
	 */
	public void init( ServletConfig config ) throws ServletException {
		super.init( config );
		configPath = config.getInitParameter("config.path");
	}
	
	/**
	 * used to load an existing project
	 * @param req
	 * @param res
	 * @throws IOException
	 */
	public void doGet( HttpServletRequest req, HttpServletResponse res ) throws IOException {
		boolean config = req.getParameterMap().containsKey("Config");
		String image = req.getParameter("Image");
		String thumbnail = req.getParameter("Thumbnail");
		if(config){
			try{
				req.getRequestDispatcher(configPath.substring(configPath.lastIndexOf("/")+1)).forward(req,res);
			}catch(ServletException  ex){
				res.setContentType("text/plain");
				res.getWriter().write("Error: Unable to forward");
			}
		}else if(image != null){
			Map<String,String> map = new HashMap<String, String>();
			map.put("IMAGE_PATH",image);
			map.put("CONFIG_PATH",configPath);
			res.setContentType("text/html");
			res.getWriter().write(getText(getClass().getResourceAsStream(APPLET_FILE),map));
		}else if(thumbnail != null && openSlideServlet != null){
			// forward to openslide to display the thumbnail
			try{
				req.getRequestDispatcher(openSlideServlet+"?action=image&path="+thumbnail).forward(req,res);
			}catch(ServletException  ex){
				res.setContentType("text/plain");
				res.getWriter().write("Error: Unable to forward");
			}
		}else{
			res.setContentType("text/plain");
			res.getWriter().write("Error: Invalid Parameters");
		}
	}
	
	/**
	 * This method gets a text file (HTML too) from input stream 
	 * reads it, puts it into string and substitutes keys for values in 
	 * from given map
	 * @param InputStream text input
	 * @param Map key/value substitution (used to substitute paths of images for example)
	 * @return String that was produced
	 * @throws IOException if something is wrong
	 * WARNING!!! if you use this to read HTML text and want to put it somewhere
	 * you should delete newlines
	 */
	public static String getText(InputStream in, Map<String,String> sub) throws IOException {
		StringBuffer strBuf = new StringBuffer();
		BufferedReader buf = new BufferedReader(new InputStreamReader(in));
		try {
			for (String line = buf.readLine(); line != null; line = buf.readLine()) {
				strBuf.append(line.trim() + "\n");
			}
		} catch (IOException ex) {
			throw ex;
		} finally {
			buf.close();
		}
		// we have our text
		String text = strBuf.toString();
		// do substitution
		if (sub != null) {
			for (String key : sub.keySet()){
				text = text.replaceAll(key,sub.get(key));
			}
		}
		return text;
	}
}