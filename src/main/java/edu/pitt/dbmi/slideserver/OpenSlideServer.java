package edu.pitt.dbmi.slideserver;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.imageio.ImageIO;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.swing.Timer;

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
public class OpenSlideServer extends HttpServlet {
	private File imageDirectory;
	private Map<String,Slide> cache;
	private Timer timer;
	private boolean supressLabels;
	
	/**
	 *  Initializes the servlet.
	 */
	public void init( ServletConfig config ) throws ServletException {
		super.init( config );
		
		String dir = config.getInitParameter("image.dir");
		imageDirectory = new File(dir);
		
		// initialize synchronized cache
		cache = new Hashtable<String,Slide>();
	
		// should labels be supressed globally?
		supressLabels = Boolean.parseBoolean(config.getInitParameter("suppress.labels"));
	
		// initialize trash compactor
		timer = new Timer(3*60*1000,new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				compactCache();
				
			}
		});
		timer.setRepeats(true);
		timer.start();
	}
	
	protected void finalize() throws Exception {
		timer.stop();
	}
	
	/**
	 * used to load an existing project
	 * @param req
	 * @param res
	 * @throws IOException
	 */
	public void doGet( HttpServletRequest req, HttpServletResponse res ) throws IOException {
		//req.setCharacterEncoding("UTF-8");
		// get action
		String action = ""+req.getParameter( "action" );
		if(action.equals( "list" ) ) {
			String path = req.getParameter("path");
			String recurse = req.getParameter("recurse");
			File file = new File(imageDirectory+"/"+filter(path));
			res.setContentType("text/plain");
			res.getWriter().write((Boolean.valueOf(recurse))?listRecursive(file,""):list(file));
		}else if(action.equals( "file" ) ) {
			String path = req.getParameter("path");
			File file = new File(imageDirectory,filter(path).replace('/',File.separatorChar));
			if(file.exists()){
				res.setContentType("image/jpeg");
				try{
					ImageIO.write(ImageIO.read(file), "jpg", res.getOutputStream());
				}catch(Exception ex){
					res.setContentType("text/plain");
					res.getWriter().write("error: "+ex.getMessage());
				}
			}else{
				res.setContentType("text/plain");
				res.getWriter().write("error: file "+path+" doesn't exist");
			}
		}else if( action.equals( "info" ) ) {
			String path = req.getParameter("path");
			try{
				compactCache();
				Slide slide = getSlide(path);
				res.setContentType("text/plain");
				slide.getSlideInfo().store(res.getOutputStream(),slide.getPath());
			}catch(IOException ex){
				res.getWriter().write("error: "+ex.getMessage());
			}
		}else if( action.equals( "region" ) ) {
			String path = req.getParameter("path");
			int x = Integer.parseInt(filterNumber(req.getParameter("x")));
			int y = Integer.parseInt(filterNumber(req.getParameter("y")));
			int w = Integer.parseInt(filterNumber(req.getParameter("width")));
			int h = Integer.parseInt(filterNumber(req.getParameter("height")));
			int rw = Integer.parseInt(filterNumber(req.getParameter("size")));
			try{
				Slide slide = getSlide(path);
				res.setContentType("image/jpeg");
				ImageIO.write(slide.getRegion(x,y,w,h,rw), "jpg", res.getOutputStream());
			}catch(IOException ex){
				res.setContentType("text/plain");
				res.getWriter().write("error: "+ex.getMessage());
			}
		}else if( action.equals( "image" ) ) {
			String path = req.getParameter("path");
			int rw = Integer.parseInt(filterNumber(req.getParameter("size")));
			if(rw == 0)
				rw = 1024;
			try{
				Slide slide = getSlide(path);
				res.setContentType("image/jpeg");
				ImageIO.write(slide.getThumbnail(rw), "jpg", res.getOutputStream());
			}catch(IOException ex){
				res.setContentType("text/plain");
				res.getWriter().write("error: "+ex.getMessage());
			}
		}else if( action.equals( "label" ) ) {
			String path = req.getParameter("path");
			int sz = Integer.parseInt(filterNumber(req.getParameter("size")));
			try{
				Slide slide = getSlide(path);
				res.setContentType("image/jpeg");
				ImageIO.write(slide.getLabel(sz),"jpg", res.getOutputStream());
			}catch(IOException ex){
				res.setContentType("text/plain");
				res.getWriter().write("error: "+ex.getMessage());
			}
		}else if( action.equals( "macro" ) ) {
			String path = req.getParameter("path");
			int sz = Integer.parseInt(filterNumber(req.getParameter("size")));
			try{
				Slide slide = getSlide(path);
				res.setContentType("image/jpeg");
				ImageIO.write(slide.getMacroImage(sz),"jpg", res.getOutputStream());
			}catch(IOException ex){
				res.setContentType("text/plain");
				res.getWriter().write("error: "+ex.getMessage());
			}
		}else{
			res.setContentType("text/plain");
			res.getWriter().write("Error: "+action+" directive not recognised");
			printUsage(res.getWriter());
		}
		
	}
	
	
	/**
	 * iterate through open images and close stale files
	 */
	private void compactCache(){
		for(String image: new HashSet<String>(cache.keySet())){
			Slide slide = cache.get(image);
			if(slide.isOld()){
				slide.dispose();
				synchronized (cache) {
					cache.remove(image);
				}
			}
		}
		System.gc();
	}
	
	/**
	 * get slide
	 * @param path
	 * @return
	 */
	private Slide getSlide(String path) throws IOException {
		path = filter(path).replace('/',File.separatorChar);
		Slide slide = cache.get(path);
		if(slide == null){
			File file = getFile(imageDirectory,path);
			// check for file
			if(!file.exists() || !file.canRead() || !file.isFile())
				throw new IOException ("file "+file+" cannot be accessed.");
			// now check on what type of image to open
			if(OpenSlideImage.isValid(file.getAbsolutePath()))
				slide = new OpenSlideImage(file);
			else
				slide = new ImageJImage(file);
			slide.setLabelAllowed(!supressLabels);
			synchronized (cache) {
				cache.put(path,slide);
			}
		}
		return slide;
	}
	
	/**
	 * get file from parent and path
	 * this takes care of funny URL ecnoding/decoding issues
	 * @param parent
	 * @param path
	 * @return
	 */
	private File getFile(File parent, String path){
		File file = new File(parent,path);
		// if we have a file that doesn't exist, AND
		// path contains spaces, it could be that the space 
		// was a + and URL encoding/decoding got messed up
		if(!file.exists() && path.contains(" ") && file.getParentFile().exists()){
			// so lets just find the M*F*er
			String target = file.getName().replaceAll(" ",".");
			try{
				for(File f: file.getParentFile().listFiles()){
					if(f.getName().matches(target)){
						file = f;
						break;
					}
				}
			}catch(PatternSyntaxException ex){
				//NOOP: if we are here, then the filename could not be made into regex
				// so just give up
			}
		}
		return file;
	}
	
	
	/**
	 * filter input string to exclude any non-kosher characters
	 * @param str
	 * @return
	 */
	private String filter(String str){
		if(str == null)
			return "";
		
		// strip characters
		//return str.replaceAll("\\.\\.","\\.").replaceAll("[^\\w\\s/\\-\\.]","");
		try {
			return URLDecoder.decode(str,"utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return str;
	}

	/**
	 * filter input string to exclude any non-kosher characters
	 * @param str
	 * @return
	 */
	private String filterNumber(String str){
		if(str == null)
			return "0";
		
		// strip characters
		return str.replaceAll("[^\\d-\\.]","");
	}

	
	/**
	 * list content of director
	 * @param filename
	 * @return
	 */
	private String list(File file){
		if(file.isDirectory()){
			StringBuffer buffer = new StringBuffer();
			for(File f: file.listFiles()){
				if(!f.isHidden() && !f.getName().startsWith("."))
					buffer.append(f.getName()+((f.isDirectory())?"/":"")+"\n");
			}
			return buffer.toString();
		}
		return "error";
	}
	
	/**
	 * list content of director
	 * @param filename
	 * @return
	 */
	private String listRecursive(File file, String prefix){
		if(file.isDirectory()){
			StringBuffer buffer = new StringBuffer();
			for(File f: file.listFiles()){
				if(!f.isHidden() && !f.getName().startsWith(".")){
					if(f.isDirectory()){
						buffer.append(listRecursive(f,prefix+f.getName()+"/"));
					}else
						buffer.append(prefix+f.getName()+"\n");
				}
			}
			return buffer.toString();
		}
		return "error";
	}
	
	
	/**
	 * print usage of this servlet
	 * @param out
	 */
	private void printUsage(PrintWriter out){
		out.write("\n\nUsage: ?action=<action>&path=<image path>[&options]\n");
		out.write("\tinfo  - display image meta-data\n");
		out.write("\tfile  - display a normal image file, not digital slide\n");
		out.write("\timage - display image thumbnail,optionally specify parameter [size]\n");
		out.write("\tlabel - display image label,optionally specify parameter [size]\n");
		out.write("\tmacro - display image macro image,optionally specify parameter [size]\n");
		out.write("\tregion- display image region\n");
		out.write("\t\tx - region x offset in absolute image coordinates (integer)\n");
		out.write("\t\ty - region y offset in absolute image coordinates (integer)\n");
		out.write("\t\twidth  - region width in absolute image coordinates (integer)\n");
		out.write("\t\theight - region height in absolute image coordinates (integer)\n");
		out.write("\t\tsize   - size of region in relative screen coordinates (integer)\n\n");
		out.write("NOTES:\nAll values for [size] parameter, indicate greatest dimension,");
		out.write(" the remaining dimension is determined by aspect ratio.\n");
		out.write("Image label may be suppressed when "+Slide.NO_LABEL+" file ");
		out.write("is located in the same image directory\n");
		
	}
}